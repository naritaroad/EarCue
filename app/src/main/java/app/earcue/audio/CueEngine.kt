package app.earcue.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import app.earcue.R
import app.earcue.data.Cue
import app.earcue.data.Db
import app.earcue.data.Prefs
import app.earcue.data.ScriptMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

data class Output(val headphones: Boolean, val muted: Boolean, val name: String)

data class VoiceOption(val name: String, val locale: Locale, val offline: Boolean, val quality: Int) {
    /** Short engine-specific id, e.g. "iol" from "en-us-x-iol-local". */
    val code: String get() = name.substringAfter("-x-", "").substringBefore("-").ifBlank { name }
}

data class EngineState(
    val script: ScriptMeta? = null,
    val cues: List<Cue> = emptyList(),
    val index: Int = 0,
    /** Whether the cue at [index] has been played; the first "next" plays the current cue instead of skipping it. */
    val played: Boolean = false,
    val playing: Boolean = false,
    val range: IntRange? = null,
    val output: Output = Output(false, false, ""),
    val ttsReady: Boolean = false,
    val voices: List<VoiceOption> = emptyList(),
    val voiceName: String = "",
    val engines: List<Pair<String, String>> = emptyList(),
    /** Package of the TTS engine actually in use (the system default when the user has not picked one). */
    val engine: String = "",
    val talkStart: Long = 0L,
    val sectionStart: Long = 0L,
    val blockedAt: Long = 0L,
    val blockedReason: Int = 0,
) {
    val cue: Cue? get() = cues.getOrNull(index)
}

/**
 * Owns playback. Every play request flushes whatever is sounding first (TTS QUEUE_FLUSH, MediaPlayer released),
 * so pressing next/previous switches instantly and two cues never overlap.
 * Nothing plays unless headphones are connected and media volume is above zero; otherwise the phone vibrates.
 */
class CueEngine(private val context: Context, private val db: Db, private val prefs: Prefs) {
    val haptics = Haptics(context)

    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private var generation = 0
    private var currentId = ""
    private var lastSection = ""
    private var warnedSection = ""

    private val headphoneTypes = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 28) add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()
    private val s: EngineState get() = _state.value
    private inline fun set(change: (EngineState) -> EngineState) {
        _state.value = change(_state.value)
    }

    private val session = MediaSession(context, "EarCue").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onSkipToNext() { if (prefs.value.headsetKeys) next() }
            override fun onSkipToPrevious() { if (prefs.value.headsetKeys) prev() }
            override fun onPlay() { if (prefs.value.headsetKeys && !s.playing) toggle() }
            override fun onPause() { if (prefs.value.headsetKeys && s.playing) toggle() }
            override fun onStop() { if (prefs.value.headsetKeys) stop() }
        })
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Sent before audio is rerouted to the loudspeaker: cut it now.
                if (s.playing && !prefs.value.allowSpeaker) {
                    silence()
                    haptics.warn()
                }
            }
            refreshOutput()
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            refreshOutput()
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            val out = refreshOutput()
            if (!out.headphones && s.playing && !prefs.value.allowSpeaker) {
                silence()
                haptics.warn()
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            checkOvertime()
            main.postDelayed(this, 1000)
        }
    }

    init {
        audio.registerAudioDeviceCallback(deviceCallback, main)
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction("android.media.VOLUME_CHANGED_ACTION")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshOutput()
        initTts()
        main.post(ticker)
    }

    // ---- script ----

    fun load(id: Long) {
        val meta = db.script(id) ?: return
        val cues = db.cues(id)
        silence()
        set {
            it.copy(
                script = meta, cues = cues, index = meta.position.coerceIn(0, maxOf(0, cues.lastIndex)),
                played = false, playing = false, range = null, talkStart = 0L, sectionStart = 0L,
            )
        }
        // Rest on the presenter's own line; partner lines before it show up as "wait for" hints.
        if (cues.isNotEmpty()) firstPlayable(s.index)?.let { i -> set { it.copy(index = i) } }
        lastSection = ""
        warnedSection = ""
        prefs.update { it.copy(lastScript = id) }
        applyVoice()
    }

    fun unload() {
        silence()
        set { it.copy(script = null, cues = emptyList(), index = 0, played = false) }
        prefs.update { it.copy(lastScript = -1) }
    }

    fun refreshMeta() {
        val id = s.script?.id ?: return
        set { it.copy(script = db.script(id)) }
    }

    fun reloadCues() {
        val id = s.script?.id ?: return
        set { it.copy(cues = db.cues(id)) }
    }

    // ---- transport ----

    fun next() {
        val st = s
        if (st.cues.isEmpty()) return
        val target = if (!st.played) firstPlayable(st.index) else step(st.index, +1)
        if (target == null) {
            haptics.edge()
            return
        }
        playAt(target) { haptics.next() }
    }

    fun prev() {
        val st = s
        if (st.cues.isEmpty()) return
        val target = step(st.index, -1)
        if (target == null) {
            haptics.edge()
            return
        }
        playAt(target) { haptics.prev() }
    }

    fun toggle() {
        if (s.playing) {
            stop()
        } else {
            val target = firstPlayable(s.index) ?: s.index
            playAt(target) { haptics.next() }
        }
    }

    fun stop() {
        if (!s.playing) return
        silence()
        if (prefs.value.tapHaptics) haptics.stop()
    }

    fun jumpTo(index: Int, play: Boolean) {
        if (s.cues.isEmpty()) return
        val i = index.coerceIn(s.cues.indices)
        if (play) {
            playAt(i) { haptics.tick() }
        } else {
            silence()
            val target = firstPlayable(i) ?: i
            set { it.copy(index = target, played = false) }
            savePosition(target)
        }
    }

    fun resetTimer() {
        set { it.copy(talkStart = 0L, sectionStart = 0L) }
        lastSection = ""
        warnedSection = ""
    }

    /** Speaks a sample line from the settings screen, under the same headphone rule. */
    fun preview(text: String) {
        if (!canPlay()) return
        silence()
        val gen = ++generation
        speakText(text, false, gen)
        set { it.copy(playing = true) }
    }

    private fun playable(i: Int) = prefs.value.speakPartner || !s.cues[i].isPartner

    private fun firstPlayable(from: Int): Int? = if (playable(from)) from else step(from, +1)

    private fun step(from: Int, dir: Int): Int? {
        var i = from + dir
        while (i in s.cues.indices) {
            if (playable(i)) return i
            i += dir
        }
        return null
    }

    private fun playAt(index: Int, feedback: () -> Unit) {
        val cues = s.cues
        if (cues.isEmpty()) return
        val i = index.coerceIn(cues.indices)
        if (!canPlay()) return
        silence()
        val gen = ++generation
        val cue = cues[i]
        val now = SystemClock.elapsedRealtime()
        val newSection = cue.section != lastSection
        lastSection = cue.section
        set {
            it.copy(
                index = i, played = true, playing = true, range = null,
                talkStart = if (it.talkStart == 0L) now else it.talkStart,
                sectionStart = if (newSection || it.sectionStart == 0L) now else it.sectionStart,
            )
        }
        if (prefs.value.tapHaptics) feedback()
        savePosition(i)
        publishSession(true, cue)

        val recording = cue.recording?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
        if (recording != null) playFile(recording, cue, gen) else speakText(cue.text, cue.isPartner, gen)
    }

    private fun speakText(text: String, partner: Boolean, gen: Int) {
        val t = tts
        if (t == null || !s.ttsReady) {
            set { it.copy(playing = false, blockedAt = SystemClock.elapsedRealtime(), blockedReason = R.string.block_tts) }
            haptics.warn()
            return
        }
        val p = prefs.value
        t.setSpeechRate(p.rate)
        // A lower pitch marks co-presenter lines when they are read aloud for rehearsal.
        t.setPitch(if (partner) p.pitch * 0.82f else p.pitch)
        currentId = "cue-$gen"
        t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), currentId)
    }

    private fun playFile(file: File, cue: Cue, gen: Int) {
        val mp = MediaPlayer()
        currentId = "cue-$gen"
        try {
            mp.setAudioAttributes(speechAttributes)
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.setOnCompletionListener { finished("cue-$gen") }
            mp.playbackParams = mp.playbackParams.setSpeed(prefs.value.rate)
            mp.start()
            player = mp
        } catch (e: Exception) {
            mp.release()
            speakText(cue.text, cue.isPartner, gen)
        }
    }

    private fun finished(id: String) {
        if (id != currentId) return
        currentId = ""
        player?.release()
        player = null
        set { it.copy(playing = false, range = null) }
        publishSession(false, s.cue)
        if (prefs.value.autoAdvance) {
            val gen = generation
            main.postDelayed({ if (gen == generation && !s.playing) next() }, 700)
        }
    }

    /** Stops all sound immediately and invalidates pending callbacks. */
    private fun silence() {
        generation++
        currentId = ""
        tts?.stop()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        if (s.playing || s.range != null) set { it.copy(playing = false, range = null) }
        publishSession(false, s.cue)
    }

    private fun savePosition(i: Int) {
        val id = s.script?.id ?: return
        io.execute { db.savePosition(id, i) }
    }

    // ---- output guard ----

    fun refreshOutput(): Output {
        val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in headphoneTypes }
        val muted = audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val name = devices.firstOrNull()?.let { d ->
            d.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
                else -> "Wired"
            }
        } ?: ""
        val out = Output(devices.isNotEmpty(), muted, name)
        if (out != s.output) set { it.copy(output = out) }
        return out
    }

    private fun canPlay(): Boolean {
        val out = refreshOutput()
        val reason = when {
            !out.headphones && !prefs.value.allowSpeaker -> R.string.block_no_headphones
            out.muted -> R.string.block_muted
            else -> null
        } ?: return true
        haptics.warn()
        set { it.copy(blockedAt = SystemClock.elapsedRealtime(), blockedReason = reason) }
        return false
    }

    // ---- timers ----

    private fun checkOvertime() {
        val st = s
        if (st.talkStart == 0L || !prefs.value.overtimeHaptics) return
        val cue = st.cue ?: return
        if (cue.sectionMinutes <= 0.0) return
        val key = "${st.script?.id}:${cue.section}"
        if (key == warnedSection) return
        if (SystemClock.elapsedRealtime() - st.sectionStart > cue.sectionMinutes * 60_000) {
            warnedSection = key
            haptics.overtime()
        }
    }

    // ---- TTS ----

    fun initTts() {
        silence()
        tts?.shutdown()
        set { it.copy(ttsReady = false) }
        val engine = prefs.value.engine
        val listener = TextToSpeech.OnInitListener { status -> main.post { onTtsReady(status) } }
        tts = if (engine.isBlank()) TextToSpeech(context, listener) else TextToSpeech(context, listener, engine)
    }

    private fun onTtsReady(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            set { it.copy(ttsReady = false) }
            return
        }
        t.setAudioAttributes(speechAttributes)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                main.post { finished(utteranceId.orEmpty()) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { finished(utteranceId.orEmpty()) }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                main.post { if (utteranceId == currentId) set { it.copy(range = start until end) } }
            }
        })
        set {
            it.copy(
                ttsReady = true,
                engines = t.engines.map { e -> e.name to e.label },
                engine = prefs.value.engine.ifBlank { t.defaultEngine.orEmpty() },
            )
        }
        applyVoice()
    }

    /** Re-applies voice, language, rate and pitch; call after the script or the voice settings change. */
    fun applyVoice() {
        val t = tts ?: return
        if (!s.ttsReady) return
        val locale = Locale.forLanguageTag(s.script?.lang ?: "en-US")
        val matching = runCatching { t.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == locale.language }
            .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
            .sortedWith(
                compareBy<Voice>({ it.isNetworkConnectionRequired }, { it.locale.country != locale.country }, { -it.quality }, { it.name })
            )
        val chosen = matching.firstOrNull { it.name == prefs.value.voice }
        if (chosen != null) t.voice = chosen else t.language = locale
        t.setSpeechRate(prefs.value.rate)
        t.setPitch(prefs.value.pitch)
        set { st ->
            st.copy(
                voices = matching.map { VoiceOption(it.name, it.locale, !it.isNetworkConnectionRequired, it.quality) },
                voiceName = chosen?.name ?: "",
            )
        }
    }

    // ---- media session (earphone buttons) ----

    private fun publishSession(playing: Boolean, cue: Cue?) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0L, 1f)
                .build()
        )
        if (cue != null) {
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, cue.text.take(60))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, s.script?.title.orEmpty())
                    .build()
            )
        }
    }

    fun setSessionActive(active: Boolean) {
        if (active) publishSession(s.playing, s.cue)
        session.isActive = active
    }
}
