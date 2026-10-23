package app.earcue.data

import app.earcue.R
import org.json.JSONObject

enum class DeckButton { PREV, TOGGLE, NEXT }

/** A button's rectangle inside the control deck, all values as fractions (0..1) of the deck size. */
data class Slot(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun mirrored() = copy(x = 1f - x - w)

    fun clamped(): Slot {
        val cw = w.coerceIn(MIN_W, 1f)
        val ch = h.coerceIn(MIN_H, 1f)
        return Slot(x.coerceIn(0f, 1f - cw), y.coerceIn(0f, 1f - ch), cw, ch)
    }

    companion object {
        const val MIN_W = 0.18f
        const val MIN_H = 0.14f
    }
}

/**
 * Layouts are stored in right-hand form; left-handed users see them mirrored,
 * so a custom arrangement follows the user when they switch hands.
 */
data class DeckLayout(val slots: Map<DeckButton, Slot>) {
    fun forHand(leftHanded: Boolean) = if (leftHanded) DeckLayout(slots.mapValues { it.value.mirrored() }) else this

    fun encode(): String = JSONObject().apply {
        slots.forEach { (b, s) -> put(b.name, "${s.x},${s.y},${s.w},${s.h}") }
    }.toString()

    companion object {
        fun decode(raw: String?): DeckLayout? = runCatching {
            val o = JSONObject(raw ?: return null)
            DeckLayout(DeckButton.entries.associateWith { b ->
                val (x, y, w, h) = o.getString(b.name).split(",").map { it.toFloat() }
                Slot(x, y, w, h).clamped()
            })
        }.getOrNull()
    }
}

data class Preset(val name: Int, val hint: Int, val layout: DeckLayout)

object Presets {
    val all = listOf(
        Preset(
            R.string.preset_thumb, R.string.preset_thumb_hint,
            DeckLayout(mapOf(
                DeckButton.TOGGLE to Slot(0f, 0f, 0.36f, 0.40f),
                DeckButton.PREV to Slot(0f, 0.40f, 0.36f, 0.60f),
                DeckButton.NEXT to Slot(0.36f, 0f, 0.64f, 1f),
            )),
        ),
        Preset(
            R.string.preset_split, R.string.preset_split_hint,
            DeckLayout(mapOf(
                DeckButton.PREV to Slot(0f, 0f, 0.5f, 0.40f),
                DeckButton.TOGGLE to Slot(0.5f, 0f, 0.5f, 0.40f),
                DeckButton.NEXT to Slot(0f, 0.40f, 1f, 0.60f),
            )),
        ),
        Preset(
            R.string.preset_rows, R.string.preset_rows_hint,
            DeckLayout(mapOf(
                DeckButton.TOGGLE to Slot(0f, 0f, 1f, 0.24f),
                DeckButton.NEXT to Slot(0f, 0.24f, 1f, 0.50f),
                DeckButton.PREV to Slot(0f, 0.74f, 1f, 0.26f),
            )),
        ),
    )
}
