package app.earcue.ui

import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.earcue.EarCueApp
import app.earcue.audio.EngineState
import app.earcue.data.OverlaySettings
import app.earcue.data.OverlaySize
import app.earcue.data.OverlayStyle
import app.earcue.data.Settings
import app.earcue.data.dockScript
import kotlin.math.roundToInt

/**
 * A scaled-down phone screen with the floating controls and the script box on it, drawn with the
 * real components. Drag an element to move it; drag its round corner knob to resize it. The change
 * is saved when the finger lifts, and the floating windows follow it the next time they show.
 */
@Composable
fun OverlayEditor(app: EarCueApp, st: EngineState, live: Settings, onCommit: (OverlaySettings) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screen: Rect = remember {
        val wm = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds
        else Rect(0, 0, context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
    }
    val sw = screen.width().toFloat()
    val sh = screen.height().toFloat()
    // While a finger is down the editor shows its own copy; otherwise it follows the saved settings and sliders.
    var dragging by remember { mutableStateOf<OverlaySettings?>(null) }
    var origin by remember { mutableStateOf(OverlaySettings()) }
    var originPanelW by remember { mutableStateOf(1) }
    var originScriptH by remember { mutableStateOf(0f) }
    val o = dragging ?: live.overlay
    val shown = live.copy(overlay = o)
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var scriptSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(density) { 16.dp.toPx() }

    fun panelX(s: OverlaySettings) = if (s.x < 0) sw - panelSize.width - margin else s.x.toFloat()
    fun panelY(s: OverlaySettings) = if (s.y < 0) sh * 0.55f else s.y.toFloat()

    BoxWithConstraints(
        Modifier.fillMaxWidth(0.66f).aspectRatio(sw / sh).clip(RoundedCornerShape(22.dp)).background(Ink.raised)
            .border(1.dp, Ink.line, RoundedCornerShape(22.dp)),
    ) {
        val k = constraints.maxWidth / sw
        // A faint stand-in for whatever app is underneath.
        val bar = Ink.line.copy(alpha = 0.5f)
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 20f
            for (i in 0 until 9) {
                val y = size.height * (0.12f + i * 0.095f)
                drawLine(bar, Offset(u * 2, y), Offset(size.width - u * (if (i % 3 == 2) 7 else 2), y), u * 0.8f, StrokeCap.Round)
            }
        }

        // The controls' drag, shared by their own frame and, when joined, by the script box's frame.
        val startPanel = {
            origin = live.overlay.copy(x = panelX(live.overlay).roundToInt(), y = panelY(live.overlay).roundToInt())
            originPanelW = panelSize.width.coerceAtLeast(1)
            originScriptH = if (live.overlay.scriptHeight > 0) sh * live.overlay.scriptHeight / 100f else scriptSize.height.toFloat()
            dragging = origin
        }
        val movePanel = { dx: Float, dy: Float ->
            val s = origin
            val nx = (s.x + dx / k).coerceIn(0f, (sw - panelSize.width).coerceAtLeast(0f))
            val ny = (s.y + dy / k).coerceIn(0f, (sh - panelSize.height).coerceAtLeast(0f))
            dragging = s.copy(x = nx.roundToInt(), y = ny.roundToInt())
        }
        val px = panelX(o)
        val py = panelY(o)

        // ---- script box ----
        var join = 0
        if (o.scriptOn) {
            val boxH = if (o.scriptHeight > 0) sh * o.scriptHeight / 100f else scriptSize.height.toFloat()
            val spot = if (!o.scriptAttach) null else dockScript(
                px.roundToInt(), py.roundToInt(), panelSize.width, panelSize.height, boxH.roundToInt(),
                with(density) { OverlaySize.ATTACHED_MIN_WIDTH.dp.toPx() }.roundToInt(), sw.roundToInt(), sh.roundToInt(), o.scriptBelow,
            )
            if (spot != null && spot.joined) join = if (spot.below) 2 else 1
            val boxW = spot?.width?.toFloat() ?: (sw * o.scriptWidth / 100f)
            val x = spot?.x?.toFloat() ?: (sw * o.scriptLeft / 100f)
            val y = spot?.y?.toFloat() ?: (sh * o.scriptY / 100f)
            Box(
                Modifier.scaledAt(x, y, k) { scriptSize = it }
                    .width(with(density) { boxW.toDp() })
                    .then(if (o.scriptHeight > 0) Modifier.height(with(density) { boxH.toDp() }) else Modifier)
                    .graphicsLayer { alpha = o.scriptWindowAlpha },
            ) { ScriptPane(st, shown, Modifier.fillMaxSize(), join) }
            if (spot == null) {
                Box(Modifier.scaledAt(x + with(density) { 4.dp.toPx() }, y + with(density) { 8.dp.toPx() }, k) {}) { ScriptGrip(folded = false) }
            }
            Handles(
                x * k, y * k, boxW * k, boxH * k,
                label = if (spot == null) "${o.scriptWidth}%" else o.scriptHeight.takeIf { it > 0 }?.let { "↕ $it%" } ?: "↕",
                onStart = {
                    if (live.overlay.scriptAttach) {
                        startPanel()
                    } else {
                        origin = live.overlay
                        originScriptH = if (live.overlay.scriptHeight > 0) sh * live.overlay.scriptHeight / 100f else scriptSize.height.toFloat()
                        dragging = origin
                    }
                },
                onMove = { dx, dy ->
                    val s = origin
                    if (s.scriptAttach) {
                        // Joined: the script box is part of the controls, so dragging it moves them.
                        movePanel(dx, dy)
                    } else {
                        val nx = ((sw * s.scriptLeft / 100f + dx / k) * 100f / sw).coerceIn(0f, 100f - s.scriptWidth.toFloat())
                        val ny = ((sh * s.scriptY / 100f + dy / k) * 100f / sh).coerceIn(0f, 95f)
                        dragging = s.copy(scriptX = nx.roundToInt(), scriptY = ny.roundToInt())
                    }
                },
                onResize = { dx, dy ->
                    val s = origin
                    val h = ((originScriptH + dy / k) * 100f / sh).coerceIn(5f, OverlaySize.SCRIPT_HEIGHT.last.toFloat())
                    dragging = if (s.scriptAttach) {
                        // Joined: the width follows the controls; only the height is the box's own.
                        s.copy(scriptHeight = h.roundToInt())
                    } else {
                        val w = (s.scriptWidth + dx / k * 100f / sw).coerceIn(OverlaySize.SCRIPT_WIDTH.first.toFloat(), 100f - s.scriptLeft)
                        s.copy(scriptX = s.scriptLeft, scriptWidth = w.roundToInt(), scriptHeight = h.roundToInt())
                    }
                },
                onEnd = { dragging?.let(onCommit); dragging = null },
            )
        }

        // ---- floating controls ----
        Box(Modifier.scaledAt(px, py, k) { panelSize = it }) {
            OverlayPanel(app, st, shown, OverlayHost(), collapsed = false, join = join) {}
        }
        Handles(
            px * k, py * k, panelSize.width * k, panelSize.height * k,
            label = "${o.button} dp",
            onStart = { startPanel() },
            onMove = { dx, dy -> movePanel(dx, dy) },
            onResize = { dx, _ ->
                val s = origin
                // The panel's size follows its buttons: scale them (and the card width) by the drag.
                val w = originPanelW
                val ratio = (w + dx / k) / w
                val button = (s.button * ratio).roundToInt().coerceIn(OverlaySize.BUTTON)
                val width = if (s.style == OverlayStyle.CARD) (s.width * ratio).roundToInt().coerceIn(OverlaySize.WIDTH) else s.width
                dragging = s.copy(button = button, width = width)
            },
            onEnd = { dragging?.let(onCommit); dragging = null },
        )
    }
}

/** Measures [content] at its real size and draws it scaled by [k] at real-screen position ([x], [y]). */
private fun Modifier.scaledAt(x: Float, y: Float, k: Float, onSize: (IntSize) -> Unit) = layout { measurable, _ ->
    val p = measurable.measure(Constraints())
    onSize(IntSize(p.width, p.height))
    layout(0, 0) {
        p.placeWithLayer((x * k).roundToInt(), (y * k).roundToInt()) {
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** A dashed frame over an element in mock coordinates: drag inside to move, drag the corner knob to resize. */
@Composable
private fun Handles(
    x: Float, y: Float, w: Float, h: Float, label: String,
    onStart: () -> Unit, onMove: (Float, Float) -> Unit, onResize: (Float, Float) -> Unit, onEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val resize by rememberUpdatedState(onResize)
    val end by rememberUpdatedState(onEnd)
    val dash = Ink.amber
    var total by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { total = Offset.Zero; start() }, onDragEnd = { end() }, onDragCancel = { end() }) { change, d ->
                    change.consume()
                    total += d
                    move(total.x, total.y)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                dash, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
            )
        }
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = Ink.onAccent,
            modifier = Modifier.align(Alignment.TopStart).offset(x = if (y < 40f) 30.dp else 0.dp, y = if (y < 40f) 4.dp else (-18).dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Ink.amber).padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(8.dp, 8.dp).size(36.dp)
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { total = Offset.Zero; start() }, onDragEnd = { end() }, onDragCancel = { end() }) { change, d ->
                        change.consume()
                        total += d
                        resize(total.x, total.y)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val knob = Ink.amber
            val knobBg = Ink.bg
            Canvas(Modifier.size(22.dp)) {
                drawCircle(knobBg)
                drawCircle(knob, size.minDimension / 2, style = Stroke(2.dp.toPx()))
                drawLine(knob, Offset(size.width * 0.36f, size.height * 0.64f), Offset(size.width * 0.64f, size.height * 0.36f), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
