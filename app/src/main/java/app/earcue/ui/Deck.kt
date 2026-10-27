package app.earcue.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.earcue.R
import app.earcue.data.DeckButton
import app.earcue.data.DeckLayout

data class DeckLook(
    val playing: Boolean,
    val started: Boolean,
    val nextPreview: String,
    val stage: Boolean = false,
)

/**
 * Renders the user's button layout. Buttons fire on touch-down so a cue switches the instant the thumb lands;
 * [onRelease] lets a held button keep acting until the thumb lifts.
 */
@Composable
fun Deck(
    layout: DeckLayout,
    look: DeckLook,
    modifier: Modifier = Modifier,
    onRelease: () -> Unit = {},
    onPress: (DeckButton) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        layout.slots.forEach { (button, slot) ->
            PressKey(
                onPress = { onPress(button) },
                onRelease = onRelease,
                modifier = Modifier.offset(w * slot.x, h * slot.y).size(w * slot.w, h * slot.h).padding(5.dp),
            ) { pressed -> DeckFace(button, look, pressed) }
        }
    }
}

@Composable
private fun PressKey(onPress: () -> Unit, onRelease: () -> Unit, modifier: Modifier, content: @Composable (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val fire by rememberUpdatedState(onPress)
    val lift by rememberUpdatedState(onRelease)
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, tween(if (pressed) 60 else 160), label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    fire()
                    waitForUpOrCancellation()
                    pressed = false
                    lift()
                }
            },
    ) { content(pressed) }
}

@Composable
fun DeckFace(button: DeckButton, look: DeckLook, pressed: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    val stopping = button == DeckButton.TOGGLE && look.playing
    val label = stringResource(
        when (button) {
            DeckButton.NEXT -> R.string.btn_next
            DeckButton.PREV -> R.string.btn_prev
            DeckButton.TOGGLE -> if (look.playing) R.string.btn_stop else if (look.started) R.string.btn_replay else R.string.btn_play
        }
    )
    val glyph = when (button) {
        DeckButton.NEXT -> G.NEXT
        DeckButton.PREV -> G.PREV
        DeckButton.TOGGLE -> if (look.playing) G.STOP else G.PLAY
    }
    val fg: Color
    val bgMod: Modifier
    if (look.stage) {
        val border by animateColorAsState(if (pressed) Ink.amber else Ink.line.copy(alpha = 0.55f), label = "b")
        fg = if (pressed) Ink.amber else Ink.faint
        bgMod = Modifier.background(if (pressed) Ink.amber.copy(alpha = 0.10f) else Color.Black).border(1.5.dp, border, shape)
    } else when {
        button == DeckButton.NEXT -> {
            fg = Ink.onAccent
            bgMod = Modifier.background(
                Brush.linearGradient(if (pressed) listOf(Ink.ember, Ink.ember) else listOf(Ink.amberLight, Ink.ember)),
            )
        }
        stopping -> {
            fg = Ink.coral
            bgMod = Modifier.background(Ink.coral.copy(alpha = if (pressed) 0.26f else 0.14f)).border(1.5.dp, Ink.coral.copy(alpha = 0.6f), shape)
        }
        else -> {
            fg = Ink.text
            bgMod = Modifier.background(if (pressed) Ink.line else Ink.raised).border(1.dp, Ink.line, shape)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().clip(shape).then(bgMod), contentAlignment = Alignment.Center) {
        val glyphSize: Dp = min(min(maxWidth, maxHeight) * 0.32f, 64.dp)
        val roomy = maxHeight > 120.dp && maxWidth > 120.dp
        val labelled = maxHeight > 84.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Glyph(glyph, fg, glyphSize)
            if (labelled) {
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
            }
            if (button == DeckButton.NEXT && roomy && !look.stage && look.nextPreview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    look.nextPreview, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.62f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        }
    }
}
