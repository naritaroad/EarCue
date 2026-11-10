package app.earcue.data

/** Where a joined script box goes, in screen pixels. */
data class DockSpot(val x: Int, val y: Int, val width: Int, val below: Boolean, val joined: Boolean)

/**
 * Places a script box of height [paneH] against the controls at ([px], [py], [pw] x [ph]) on a
 * [sw] x [sh] screen. It is as wide as the controls, at least [minW]; it prefers [preferBelow]'s side
 * and flips when that side has no room. [DockSpot.joined] says the two are exactly as wide, so their
 * touching corners can be squared off and they read as one box.
 */
fun dockScript(px: Int, py: Int, pw: Int, ph: Int, paneH: Int, minW: Int, sw: Int, sh: Int, preferBelow: Boolean): DockSpot {
    val w = maxOf(pw, minW).coerceAtMost(sw)
    // Line up with the controls' left edge, or their right edge when that would run off screen.
    val x = if (px + w <= sw) px else (px + pw - w).coerceAtLeast(0)
    val roomAbove = py
    val roomBelow = sh - (py + ph)
    val below = if (preferBelow) roomBelow >= paneH || roomBelow >= roomAbove else !(roomAbove >= paneH || roomAbove >= roomBelow)
    val y = if (below) py + ph else py - paneH
    return DockSpot(x, y.coerceIn(0, (sh - paneH).coerceAtLeast(0)), w, below, joined = w == pw)
}
