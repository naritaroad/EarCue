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
    /** Voice for co-presenter lines; "" = automatic, a voice of the other gender than [voice]. */
    val partnerVoice: String = "",
    val partnerPitch: Float = 1.0f,
    /** Voice picker filter: only show voices that work without network. */
    val offlineVoices: Boolean = true,
    val engine: String = "",
    val shutterKey: Boolean = true,
    val volumeKeys: Boolean = false,
    val headsetKeys: Boolean = true,
    val autoAdvance: Boolean = false,
    val speakPartner: Boolean = false,
    /** Chime before the first line of a new slide. */
    val pageTone: Boolean = true,
    /** Chime after a line when the next line belongs to someone else. */
    val turnTone: Boolean = true,
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
    /** Floating controls over other apps; shown while EarCue is in the background. */
    val overlayOn: Boolean = false,
    val overlay: OverlaySettings = OverlaySettings(),
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

    /** Detected voice genders, cached per engine and voice name: "m"/"f"/"?" plus the measured pitch in Hz. */
    private val genders = context.getSharedPreferences("earcue-voice-gender", Context.MODE_PRIVATE)

    fun cachedGender(engine: String, voice: String): String? = genders.getString("$engine|$voice", null)

    fun cacheGender(engine: String, voice: String, value: String) {
        genders.edit().putString("$engine|$voice", value).apply()
    }

    private fun read() = Settings(
        leftHanded = sp.getBoolean("leftHanded", false),
        preset = sp.getInt("preset", 0),
        customLayout = DeckLayout.decode(sp.getString("customLayout", null)),
        rate = sp.getFloat("rate", 1f),
        pitch = sp.getFloat("pitch", 1f),
        voice = sp.getString("voice", "") ?: "",
        partnerVoice = sp.getString("partnerVoice", "") ?: "",
        partnerPitch = sp.getFloat("partnerPitch", 1f),
        offlineVoices = sp.getBoolean("offlineVoices", true),
        engine = sp.getString("engine", "") ?: "",
        shutterKey = sp.getBoolean("shutterKey", true),
        volumeKeys = sp.getBoolean("volumeKeys", false),
        headsetKeys = sp.getBoolean("headsetKeys", true),
        autoAdvance = sp.getBoolean("autoAdvance", false),
        speakPartner = sp.getBoolean("speakPartner", false),
        pageTone = sp.getBoolean("pageTone", true),
        turnTone = sp.getBoolean("turnTone", true),
        allowSpeaker = sp.getBoolean("allowSpeaker", false),
        tapHaptics = sp.getBoolean("tapHaptics", true),
        overtimeHaptics = sp.getBoolean("overtimeHaptics", true),
        keepScreenOn = sp.getBoolean("keepScreenOn", true),
        showNotes = sp.getBoolean("showNotes", true),
        textScale = sp.getFloat("textScale", 1f),
        lastScript = sp.getLong("lastScript", -1),
        theme = sp.getString("theme", "system") ?: "system",
        language = sp.getString("language", "") ?: "",
        overlayOn = sp.getBoolean("overlayOn", false),
        overlay = OverlaySettings(
            style = OverlayStyle.entries.firstOrNull { it.name == sp.getString("overlayStyle", null) } ?: OverlayStyle.BAR,
            items = OverlaySettings.decodeItems(sp.getString("overlayItems", null)),
            button = sp.getInt("overlayButton", OverlaySize.DEFAULT_BUTTON),
            text = sp.getInt("overlayText", OverlaySize.DEFAULT_TEXT),
            width = sp.getInt("overlayWidth", OverlaySize.DEFAULT_WIDTH),
            opacity = sp.getFloat("overlayOpacity", OverlaySize.DEFAULT_OPACITY),
            x = sp.getInt("overlayX", -1),
            y = sp.getInt("overlayY", -1),
            scriptOn = sp.getBoolean("scriptOn", false),
            scriptText = sp.getInt("scriptText", OverlaySize.DEFAULT_SCRIPT_TEXT),
            scriptLines = sp.getInt("scriptLines", OverlaySize.DEFAULT_SCRIPT_LINES),
            scriptWidth = sp.getInt("scriptWidth", OverlaySize.DEFAULT_SCRIPT_WIDTH),
            scriptHeight = sp.getInt("scriptHeight", 0),
            scriptX = sp.getInt("scriptX", -1),
            scriptY = sp.getInt("scriptY", OverlaySize.DEFAULT_SCRIPT_Y),
            scriptBg = sp.getFloat("scriptBg", OverlaySize.DEFAULT_SCRIPT_BG),
            scriptInk = sp.getFloat("scriptInk", OverlaySize.DEFAULT_SCRIPT_INK),
            scriptAttach = sp.getBoolean("scriptAttach", false),
            scriptBelow = sp.getBoolean("scriptBelow", false),
        ),
    )

    private fun write(s: Settings) {
        sp.edit()
            .putBoolean("leftHanded", s.leftHanded)
            .putInt("preset", s.preset)
            .putString("customLayout", s.customLayout?.encode())
            .putFloat("rate", s.rate)
            .putFloat("pitch", s.pitch)
            .putString("voice", s.voice)
            .putString("partnerVoice", s.partnerVoice)
            .putFloat("partnerPitch", s.partnerPitch)
            .putBoolean("offlineVoices", s.offlineVoices)
            .putString("engine", s.engine)
            .putBoolean("shutterKey", s.shutterKey)
            .putBoolean("volumeKeys", s.volumeKeys)
            .putBoolean("headsetKeys", s.headsetKeys)
            .putBoolean("autoAdvance", s.autoAdvance)
            .putBoolean("speakPartner", s.speakPartner)
            .putBoolean("pageTone", s.pageTone)
            .putBoolean("turnTone", s.turnTone)
            .putBoolean("allowSpeaker", s.allowSpeaker)
            .putBoolean("tapHaptics", s.tapHaptics)
            .putBoolean("overtimeHaptics", s.overtimeHaptics)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("showNotes", s.showNotes)
            .putFloat("textScale", s.textScale)
            .putLong("lastScript", s.lastScript)
            .putString("theme", s.theme)
            .putString("language", s.language)
            .putBoolean("overlayOn", s.overlayOn)
            .putString("overlayStyle", s.overlay.style.name)
            .putString("overlayItems", s.overlay.encodeItems())
            .putInt("overlayButton", s.overlay.button)
            .putInt("overlayText", s.overlay.text)
            .putInt("overlayWidth", s.overlay.width)
            .putFloat("overlayOpacity", s.overlay.opacity)
            .putInt("overlayX", s.overlay.x)
            .putInt("overlayY", s.overlay.y)
            .putBoolean("scriptOn", s.overlay.scriptOn)
            .putInt("scriptText", s.overlay.scriptText)
            .putInt("scriptLines", s.overlay.scriptLines)
            .putInt("scriptWidth", s.overlay.scriptWidth)
            .putInt("scriptHeight", s.overlay.scriptHeight)
            .putInt("scriptX", s.overlay.scriptX)
            .putInt("scriptY", s.overlay.scriptY)
            .putFloat("scriptBg", s.overlay.scriptBg)
            .putFloat("scriptInk", s.overlay.scriptInk)
            .putBoolean("scriptAttach", s.overlay.scriptAttach)
            .putBoolean("scriptBelow", s.overlay.scriptBelow)
            .apply()
    }
}
