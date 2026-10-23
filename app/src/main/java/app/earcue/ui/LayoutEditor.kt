package app.earcue.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.data.DeckButton
import app.earcue.data.DeckLayout
import app.earcue.data.Presets
import app.earcue.data.Settings
import app.earcue.data.Slot
import kotlin.math.roundToInt

/**
 * Drag a button to move it, drag its corner to resize. The editor sits where the home deck sits,
 * so what the thumb learns here is exactly what it gets on the home screen.
 */
@Composable
fun LayoutEditorScreen(app: EarCueApp, settings: Settings, onBack: () -> Unit) {
    val left = settings.leftHanded
    var slots by remember { mutableStateOf(settings.deck.slots) }
    var base by remember { mutableStateOf(settings.preset) }
    val dirty = slots != settings.deck.slots

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val deckHeight = maxHeight * 0.42f
        Column(Modifier.fillMaxSize()) {
            val handLabel = stringResource(if (left) R.string.hand_left else R.string.hand_right)
            val unsaved = stringResource(R.string.layout_unsaved)
            ScreenHeader(stringResource(R.string.layout_title), onBack, subtitle = handLabel + (if (dirty) " · $unsaved" else ""))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.layout_help), style = MaterialTheme.typography.bodyMedium, color = Ink.dim)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.all.forEachIndexed { i, p ->
                        val layout = p.layout.forHand(left)
                        PresetTile(stringResource(p.name), layout, selected = slots == layout.slots, modifier = Modifier.weight(1f)) {
                            base = i
                            slots = layout.slots
                            app.engine.haptics.tick()
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.layout_tip),
                    style = MaterialTheme.typography.labelMedium, color = Ink.faint,
                )
            }
            EditableDeck(slots, Modifier.fillMaxWidth().height(deckHeight).padding(horizontal = 11.dp)) { b, s ->
                slots = slots + (b to s)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val baseName = stringResource(Presets.all[base].name)
                PillButton(stringResource(R.string.layout_reset), G.BACK, Modifier.weight(1f)) {
                    slots = Presets.all[base].layout.forHand(left).slots
                    app.prefs.update { it.copy(preset = base, customLayout = null) }
                    app.say(R.string.layout_reset_done, baseName)
                }
                PillButton(stringResource(R.string.save), G.CHECK, Modifier.weight(1f), accent = true) {
                    val stored = DeckLayout(slots).forHand(left) // mirroring is its own inverse
                    val match = Presets.all.indexOfFirst { it.layout == stored }
                    app.prefs.update {
                        if (match >= 0) it.copy(preset = match, customLayout = null) else it.copy(preset = base, customLayout = stored)
                    }
                    app.say(R.string.layout_saved)
                    onBack()
                }
            }
        }
    }
}

private fun Float.snap(step: Float = 0.02f) = (this / step).roundToInt() * step

private fun Slot.snapped() = Slot(x.snap(), y.snap(), w.snap(), h.snap()).clamped()

@Composable
private fun EditableDeck(slots: Map<DeckButton, Slot>, modifier: Modifier, onChange: (DeckButton, Slot) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(26.dp)).border(1.dp, Ink.line.copy(alpha = 0.5f), RoundedCornerShape(26.dp))) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        slots.forEach { (button, slot) ->
            key(button) {
                val current by rememberUpdatedState(slot)
                val emit by rememberUpdatedState(onChange)
                Box(
                    Modifier
                        .offset { IntOffset((slot.x * wPx).roundToInt(), (slot.y * hPx).roundToInt()) }
                        .size(with(density) { (slot.w * wPx).toDp() }, with(density) { (slot.h * hPx).toDp() })
                        .padding(5.dp)
                        .pointerInput(button) {
                            detectDragGestures(onDragEnd = { emit(button, current.snapped()) }) { change, drag ->
                                change.consume()
                                val c = current
                                emit(button, c.copy(x = c.x + drag.x / wPx, y = c.y + drag.y / hPx).clamped())
                            }
                        },
                ) {
                    DeckFace(button, DeckLook(playing = false, started = true, nextPreview = ""), pressed = false)
                    val dash = Ink.text.copy(alpha = 0.35f)
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            dash, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(30.dp.toPx()),
                        )
                    }
                    Text(
                        "${(slot.w * 100).roundToInt()}×${(slot.h * 100).roundToInt()}",
                        style = MaterialTheme.typography.labelSmall, color = Ink.text.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Ink.bg.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(44.dp)
                            .pointerInput(button) {
                                detectDragGestures(onDragEnd = { emit(button, current.snapped()) }) { change, drag ->
                                    change.consume()
                                    val c = current
                                    emit(
                                        button,
                                        c.copy(
                                            w = (c.w + drag.x / wPx).coerceIn(Slot.MIN_W, 1f - c.x),
                                            h = (c.h + drag.y / hPx).coerceIn(Slot.MIN_H, 1f - c.y),
                                        ),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val knobBg = Ink.bg.copy(alpha = 0.7f)
                        val knob = Ink.text
                        Canvas(Modifier.size(26.dp)) {
                            drawCircle(knobBg)
                            drawCircle(knob, size.minDimension / 2, style = Stroke(2.dp.toPx()))
                            drawLine(knob, Offset(size.width * 0.38f, size.height * 0.62f), Offset(size.width * 0.62f, size.height * 0.38f), 2.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}
