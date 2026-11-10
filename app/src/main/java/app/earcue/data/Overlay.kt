package app.earcue.data

import app.earcue.R

/** What the floating controls can show. Each style supports a subset; see [OverlayStyle.supported]. */
enum class OverlayItem(val label: Int) {
    PREV(R.string.ov_item_prev),
    TOGGLE(R.string.ov_item_toggle),
    NEXT(R.string.ov_item_next),
    CUE(R.string.ov_item_cue),
    NEXT_LINE(R.string.ov_item_next_line),
    PROGRESS(R.string.ov_item_progress),
    TIMER(R.string.ov_item_timer),
    /** Shows or hides the script text display. */
    SCRIPT(R.string.ov_item_script),
    OPEN(R.string.ov_item_open),
    CLOSE(R.string.ov_item_close),
}

/**
 * Floating control styles. Every style has a grip: drag it to move the controls, tap it to fold them
 * into a small tab at the screen edge.
 */
enum class OverlayStyle(val label: Int, val hint: Int, val supported: List<OverlayItem>, val defaults: Set<OverlayItem>) {
    BAR(
        R.string.ov_style_bar, R.string.ov_style_bar_hint,
        listOf(OverlayItem.PREV, OverlayItem.TOGGLE, OverlayItem.NEXT, OverlayItem.PROGRESS, OverlayItem.TIMER, OverlayItem.SCRIPT, OverlayItem.OPEN, OverlayItem.CLOSE),
        setOf(OverlayItem.PREV, OverlayItem.TOGGLE, OverlayItem.NEXT, OverlayItem.PROGRESS, OverlayItem.SCRIPT),
    ),
    SIDE(
        R.string.ov_style_side, R.string.ov_style_side_hint,
        listOf(OverlayItem.PREV, OverlayItem.TOGGLE, OverlayItem.NEXT, OverlayItem.PROGRESS, OverlayItem.TIMER, OverlayItem.SCRIPT, OverlayItem.OPEN, OverlayItem.CLOSE),
        setOf(OverlayItem.PREV, OverlayItem.TOGGLE, OverlayItem.NEXT, OverlayItem.PROGRESS, OverlayItem.SCRIPT),
    ),
    CARD(
        R.string.ov_style_card, R.string.ov_style_card_hint,
        OverlayItem.entries.toList(),
        setOf(OverlayItem.PREV, OverlayItem.TOGGLE, OverlayItem.NEXT, OverlayItem.CUE, OverlayItem.NEXT_LINE, OverlayItem.PROGRESS, OverlayItem.TIMER),
    ),
    BUBBLE(
        R.string.ov_style_bubble, R.string.ov_style_bubble_hint,
        listOf(OverlayItem.PROGRESS, OverlayItem.SCRIPT),
        setOf(OverlayItem.PROGRESS),
    ),
}

/** Size limits for the floating controls; buttons and width in dp, text in sp. */
object OverlaySize {
    val BUTTON = 36..96
    val TEXT = 11..26
    val WIDTH = 200..400
    const val OPACITY_MIN = 0.4f
    const val DEFAULT_BUTTON = 56
    const val DEFAULT_TEXT = 15
    const val DEFAULT_WIDTH = 300
    const val DEFAULT_OPACITY = 0.95f

    // Script text display. Its opacity stops at 80%: Android 12+ only lets touches pass through
    // another app's overlay window when the window is at most 80% opaque. Android looks at the
    // whole window, so the window alpha is the higher of the two and each layer is scaled inside it.
    val SCRIPT_TEXT = 14..40
    val SCRIPT_LINES = 0..4
    val SCRIPT_WIDTH = 30..100
    /** Height in % of the screen; 0 = as tall as the text. */
    val SCRIPT_HEIGHT = 0..70
    const val SCRIPT_BG_MIN = 0f
    const val SCRIPT_INK_MIN = 0.2f
    const val SCRIPT_OPACITY_MAX = 0.8f
    const val DEFAULT_SCRIPT_TEXT = 22
    const val DEFAULT_SCRIPT_LINES = 1
    const val DEFAULT_SCRIPT_WIDTH = 100
    const val DEFAULT_SCRIPT_Y = 6
    const val DEFAULT_SCRIPT_BG = 0.6f
    const val DEFAULT_SCRIPT_INK = 0.8f

    /** A joined script box is as wide as the controls, but never narrower than this (dp). */
    const val ATTACHED_MIN_WIDTH = 260
}

data class OverlaySettings(
    val style: OverlayStyle = OverlayStyle.BAR,
    /** The items picked for each style; a style missing here uses its defaults. */
    val items: Map<OverlayStyle, Set<OverlayItem>> = emptyMap(),
    val button: Int = OverlaySize.DEFAULT_BUTTON,
    val text: Int = OverlaySize.DEFAULT_TEXT,
    val width: Int = OverlaySize.DEFAULT_WIDTH,
    val opacity: Float = OverlaySize.DEFAULT_OPACITY,
    /** Window position in px from the top-left corner; negative = not placed yet. */
    val x: Int = -1,
    val y: Int = -1,
    /** Script text display: shown over other apps, never takes a touch. */
    val scriptOn: Boolean = false,
    val scriptText: Int = OverlaySize.DEFAULT_SCRIPT_TEXT,
    /** How many upcoming cues to show under the current one. */
    val scriptLines: Int = OverlaySize.DEFAULT_SCRIPT_LINES,
    /** Width in % of the screen. */
    val scriptWidth: Int = OverlaySize.DEFAULT_SCRIPT_WIDTH,
    /** Height in % of the screen; 0 = fits the text. */
    val scriptHeight: Int = 0,
    /** Left edge in % of the screen width; negative = centered. */
    val scriptX: Int = -1,
    /** Top edge in % of the screen height. */
    val scriptY: Int = OverlaySize.DEFAULT_SCRIPT_Y,
    /** Background opacity of the script box, 0 = text only. */
    val scriptBg: Float = OverlaySize.DEFAULT_SCRIPT_BG,
    /** Text opacity of the script box. */
    val scriptInk: Float = OverlaySize.DEFAULT_SCRIPT_INK,
    /** Script box joined to the controls: it sits right against them and moves with their grip. */
    val scriptAttach: Boolean = false,
    /** When joined, the side it prefers: below the controls instead of above. Flips when there is no room. */
    val scriptBelow: Boolean = false,
) {
    /** The script window's alpha: the higher layer, never above what lets touches through. */
    val scriptWindowAlpha: Float
        get() = maxOf(scriptBg, scriptInk).coerceIn(OverlaySize.SCRIPT_INK_MIN, OverlaySize.SCRIPT_OPACITY_MAX)

    /** Left edge in % with "centered" resolved. */
    val scriptLeft: Int get() = if (scriptX < 0) (100 - scriptWidth) / 2 else scriptX.coerceIn(0, 100 - scriptWidth)

    fun itemsOf(style: OverlayStyle): Set<OverlayItem> = (items[style] ?: style.defaults).filterTo(LinkedHashSet()) { it in style.supported }
    val shown: Set<OverlayItem> get() = itemsOf(style)

    fun toggle(item: OverlayItem): OverlaySettings {
        val now = itemsOf(style)
        return copy(items = items + (style to if (item in now) now - item else now + item))
    }

    fun encodeItems(): String = items.entries.joinToString(";") { (s, set) -> s.name + ":" + set.joinToString(",") { it.name } }

    companion object {
        fun decodeItems(raw: String?): Map<OverlayStyle, Set<OverlayItem>> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(';').mapNotNull { part ->
                val style = OverlayStyle.entries.firstOrNull { it.name == part.substringBefore(':') } ?: return@mapNotNull null
                val set = part.substringAfter(':', "").split(',').mapNotNullTo(LinkedHashSet()) { n -> OverlayItem.entries.firstOrNull { it.name == n } }
                style to set
            }.toMap()
        }
    }
}
