package app.earcue.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.sqrt

enum class Gender { FEMALE, MALE, UNKNOWN }

/**
 * Android's TTS API does not say whether a voice is male or female, so EarCue listens instead:
 * each voice speaks one short line into a WAV file and [PitchEstimate] measures its median pitch.
 * Runs on its own TextToSpeech instance so it never touches the playback queue.
 */
class VoiceProbe(
    private val context: Context,
    private val engine: String,
    private val onResult: (voice: String, gender: Gender, hz: Float) -> Unit,
    private val onDone: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val queue = ArrayDeque<Voice>()
    private val file = File(context.cacheDir, "voice-probe.wav")
    private var tts: TextToSpeech? = null
    private var current: Voice? = null
    private var sample = ""
    private var finished = false

    private val timeout = Runnable { current?.let { report(it, 0f) } }

    fun start(voices: List<Voice>, sample: String) {
        queue.addAll(voices)
        this.sample = sample
        val listener = TextToSpeech.OnInitListener { status ->
            main.post { if (status == TextToSpeech.SUCCESS) ready() else finish() }
        }
        tts = if (engine.isBlank()) TextToSpeech(context, listener) else TextToSpeech(context, listener, engine)
    }

    fun cancel() {
        queue.clear()
        finish()
    }

    private fun ready() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                main.post { analyze(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { current?.takeIf { "probe-${it.name}" == utteranceId }?.let { report(it, 0f) } }
            }
        })
        nextVoice()
    }

    private fun nextVoice() {
        val t = tts ?: return
        val v = queue.removeFirstOrNull() ?: return finish()
        current = v
        file.delete()
        t.voice = v
        t.setPitch(1f)
        t.setSpeechRate(1f)
        if (t.synthesizeToFile(sample, Bundle(), file, "probe-${v.name}") != TextToSpeech.SUCCESS) {
            report(v, 0f)
            return
        }
        main.postDelayed(timeout, TIMEOUT_MS)
    }

    private fun analyze(utteranceId: String?) {
        val v = current ?: return
        if (utteranceId != "probe-${v.name}") return
        main.removeCallbacks(timeout)
        io.execute {
            val hz = runCatching { PitchEstimate.medianHz(file) }.getOrDefault(0f)
            main.post { report(v, hz) }
        }
    }

    private fun report(v: Voice, hz: Float) {
        if (current != v || finished) return
        main.removeCallbacks(timeout)
        current = null
        onResult(v.name, PitchEstimate.gender(hz), hz)
        nextVoice()
    }

    private fun finish() {
        if (finished) return
        finished = true
        main.removeCallbacks(timeout)
        tts?.shutdown()
        tts = null
        file.delete()
        io.shutdown()
        onDone()
    }

    companion object {
        const val TIMEOUT_MS = 8_000L
    }
}

/** Median speaking pitch of a 16-bit PCM WAV file, by normalized autocorrelation on voiced frames. */
object PitchEstimate {
    /**
     * Measured on Google TTS (en-US/en-GB): male voices 110–155 Hz, female voices 198–242 Hz.
     * The split sits in the middle of that gap, about 20 Hz from either side.
     */
    private const val SPLIT_HZ = 175f

    fun gender(hz: Float): Gender = when {
        hz <= 0f -> Gender.UNKNOWN
        hz < SPLIT_HZ -> Gender.MALE
        else -> Gender.FEMALE
    }

    fun medianHz(file: File): Float {
        val wav = readWav(file) ?: return 0f
        val x = wav.samples
        val sr = wav.rate
        val frame = (sr * 0.04).toInt()
        val hop = frame / 2
        val minLag = sr / 400
        val maxLag = sr / 60
        val peak = x.maxOfOrNull { kotlin.math.abs(it) } ?: return 0f
        if (peak <= 0f) return 0f
        val gate = peak * 0.08f
        val found = ArrayList<Float>()
        var start = 0
        while (start + frame + maxLag < x.size) {
            var e0 = 0.0
            for (i in 0 until frame) e0 += x[start + i] * x[start + i]
            if (sqrt(e0 / frame) >= gate) {
                val r = FloatArray(maxLag + 1)
                var best = 0f
                for (lag in minLag..maxLag) {
                    var dot = 0.0
                    var el = 0.0
                    for (i in 0 until frame) {
                        val b = x[start + i + lag]
                        dot += x[start + i] * b
                        el += b * b
                    }
                    val v = if (el > 0) (dot / sqrt(e0 * el)).toFloat() else 0f
                    r[lag] = v
                    if (v > best) best = v
                }
                if (best >= 0.6f) {
                    // The shortest lag that is nearly as strong as the best one: avoids reading 2T (an octave low).
                    var pick = -1
                    for (lag in minLag + 1 until maxLag) {
                        if (r[lag] >= best * 0.88f && r[lag] >= r[lag - 1] && r[lag] >= r[lag + 1]) {
                            pick = lag
                            break
                        }
                    }
                    if (pick > 0) found += sr.toFloat() / pick
                }
            }
            start += hop
        }
        if (found.size < 5) return 0f
        found.sort()
        return found[found.size / 2]
    }

    private class Wav(val rate: Int, val samples: FloatArray)

    private fun readWav(file: File): Wav? {
        val b = file.readBytes()
        if (b.size < 44 || String(b, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        fun u16(p: Int) = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)
        fun u32(p: Int) = u16(p) or (u16(p + 2) shl 16)
        var pos = 12
        var rate = 0
        var channels = 1
        var bits = 16
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val len = u32(pos + 4)
            if (id == "fmt ") {
                channels = u16(pos + 10)
                rate = u32(pos + 12)
                bits = u16(pos + 22)
            } else if (id == "data") {
                if (bits != 16 || rate <= 0 || channels <= 0) return null
                val off = pos + 8
                // Streamed files may leave the data length at 0: read to the end of the file.
                val size = if (len <= 0 || off + len > b.size) b.size - off else len
                val n = size / 2 / channels
                val out = FloatArray(n)
                for (i in 0 until n) {
                    val p = off + i * 2 * channels
                    out[i] = (u16(p).toShort()).toFloat() / 32768f
                }
                return Wav(rate, out)
            }
            pos += 8 + len + (len and 1)
        }
        return null
    }
}
