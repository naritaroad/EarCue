package app.earcue.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val leftHanded: Boolean = false,
    val preset: Int = 0,
    val customLayout: DeckLayout? = null,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voice: String = "",
    /** Voice picker filter: only show voices that work without network. */
    val offlineVoices: Boolean = true,
    val engine: String = "",
    val shutterKey: Boolean = true,
    val volumeKeys: Boolean = false,
    val headsetKeys: Boolean = true,
    val autoAdvance: Boolean = false,
    val speakPartner: Boolean = false,
    val allowSpeaker: Boolean = false,
    val tapHaptics: Boolean = true,
    val overtimeHaptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showNotes: Boolean = true,
    val textScale: Float = 1f,
    val lastScript: Long = -1,
    /** "system", "light" or "dark". */
    val theme: String = "system",
    /** BCP-47 tag of the UI language, "" = follow the system. Applied by the platform on Android 13+. */
    val language: String = "",
) {
    /** The layout the home screen actually draws, already mirrored for the user's hand. */
    val deck: DeckLayout get() = (customLayout ?: Presets.all[preset.coerceIn(Presets.all.indices)].layout).forHand(leftHanded)
}

const val RATE_MIN = 0.25f
const val RATE_MAX = 2.0f

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("earcue", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(read())
    val settings: StateFlow<Settings> = flow.asStateFlow()
    val value: Settings get() = flow.value

    fun update(change: (Settings) -> Settings) {
        val next = change(flow.value)
        if (next == flow.value) return
        flow.value = next
        write(next)
    }

    private fun read() = Settings(
        leftHanded = sp.getBoolean("leftHanded", false),
        preset = sp.getInt("preset", 0),
        customLayout = DeckLayout.decode(sp.getString("customLayout", null)),
        rate = sp.getFloat("rate", 1f),
        pitch = sp.getFloat("pitch", 1f),
        voice = sp.getString("voice", "") ?: "",
        offlineVoices = sp.getBoolean("offlineVoices", true),
        engine = sp.getString("engine", "") ?: "",
        shutterKey = sp.getBoolean("shutterKey", true),
        volumeKeys = sp.getBoolean("volumeKeys", false),
        headsetKeys = sp.getBoolean("headsetKeys", true),
        autoAdvance = sp.getBoolean("autoAdvance", false),
        speakPartner = sp.getBoolean("speakPartner", false),
        allowSpeaker = sp.getBoolean("allowSpeaker", false),
        tapHaptics = sp.getBoolean("tapHaptics", true),
        overtimeHaptics = sp.getBoolean("overtimeHaptics", true),
        keepScreenOn = sp.getBoolean("keepScreenOn", true),
        showNotes = sp.getBoolean("showNotes", true),
        textScale = sp.getFloat("textScale", 1f),
        lastScript = sp.getLong("lastScript", -1),
        theme = sp.getString("theme", "system") ?: "system",
        language = sp.getString("language", "") ?: "",
    )

    private fun write(s: Settings) {
        sp.edit()
            .putBoolean("leftHanded", s.leftHanded)
            .putInt("preset", s.preset)
            .putString("customLayout", s.customLayout?.encode())
            .putFloat("rate", s.rate)
            .putFloat("pitch", s.pitch)
            .putString("voice", s.voice)
            .putBoolean("offlineVoices", s.offlineVoices)
            .putString("engine", s.engine)
            .putBoolean("shutterKey", s.shutterKey)
            .putBoolean("volumeKeys", s.volumeKeys)
            .putBoolean("headsetKeys", s.headsetKeys)
            .putBoolean("autoAdvance", s.autoAdvance)
            .putBoolean("speakPartner", s.speakPartner)
            .putBoolean("allowSpeaker", s.allowSpeaker)
            .putBoolean("tapHaptics", s.tapHaptics)
            .putBoolean("overtimeHaptics", s.overtimeHaptics)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("showNotes", s.showNotes)
            .putFloat("textScale", s.textScale)
            .putLong("lastScript", s.lastScript)
            .putString("theme", s.theme)
            .putString("language", s.language)
            .apply()
    }
}
