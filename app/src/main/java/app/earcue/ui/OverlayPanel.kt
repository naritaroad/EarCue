package app.earcue.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.audio.EngineState
import app.earcue.data.DeckButton
import app.earcue.data.OverlayItem
import app.earcue.data.OverlayStyle
import app.earcue.data.Settings
import kotlinx.coroutines.delay

/** Callbacks the floating window needs from its host; the settings preview passes no-ops for the window ones. */
class OverlayHost(
    val onDrag: (Offset) -> Unit = {},
    val onDragEnd: () -> Unit = {},
    val onOpen: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onScript: () -> Unit = {},
)

/**
 * The floating controls, in the style and with the items the user picked. Shared by the overlay window
 * and the live preview in settings, so the preview is exactly what floats over other apps.
 */
@Composable
fun OverlayPanel(
    app: EarCueApp, st: EngineState, settings: Settings, host: OverlayHost, collapsed: Boolean,
    /** A joined script box: 1 = above these controls, 2 = below; their touching corners are squared. */
    join: Int = 0,
    onCollapse: (Boolean) -> Unit,
) {
    val o = settings.overlay
    val engine = app.engine
    val btn = o.button.dp
    val text = o.text.sp

    // Flash the border coral when the earphone guard blocks a press (the phone also vibrates).
    var seenBlock by remember { mutableLongStateOf(st.blockedAt) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(st.blockedAt) {
        if (st.blockedAt == seenBlock) return@LaunchedEffect
        seenBlock = st.blockedAt
        repeat(3) { flash = true; delay(180); flash = false; delay(140) }
    }
    val border by animateColorAsState(if (flash) Ink.coral else Ink.line, tween(90), label = "ovBorder")

    val grip = Modifier
        .pointerInput(Unit) { detectTapGestures { onCollapse(!collapsed) } }
        .pointerInput(Unit) {
            detectDragGestures(onDragEnd = host.onDragEnd, onDragCancel = host.onDragEnd) { change, amount ->
                change.consume()
                host.onDrag(amount)
            }
        }

    val alpha = Modifier.graphicsLayer { this.alpha = o.opacity }
    if (collapsed) {
        // Folded: a small tab that keeps the grip; tap to unfold, drag to move.
        Box(
            alpha.then(grip).clip(RoundedCornerShape(14.dp)).background(Ink.surface).border(1.dp, border, RoundedCornerShape(14.dp))
                .size(width = 26.dp, height = 58.dp),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.GRIP, Ink.amber, 20.dp) }
        return
    }

    val items = o.shown
    val r = 22.dp
    val shape = when {
        o.style == OverlayStyle.BUBBLE -> RoundedCornerShape(50)
        join == 1 -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = r, bottomEnd = r)
        join == 2 -> RoundedCornerShape(topStart = r, topEnd = r, bottomStart = 0.dp, bottomEnd = 0.dp)
        else -> RoundedCornerShape(r)
    }
    val panel = alpha.clip(shape).background(Ink.surface).border(1.dp, border, shape)
    val keys: @Composable (Dp) -> Unit = { size ->
        listOf(OverlayItem.PREV to DeckButton.PREV, OverlayItem.TOGGLE to DeckButton.TOGGLE, OverlayItem.NEXT to DeckButton.NEXT)
            .filter { it.first in items }
            .forEach { (_, b) -> OverlayKey(b, st, size, onPress = { engine.press(b) }, onRelease = { engine.release() }) }
    }

    when (o.style) {
        OverlayStyle.BAR -> Row(
            panel.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GripHandle(grip, Modifier.size(width = 22.dp, height = btn))
            keys(btn)
            InfoBlock(st, items, text, Alignment.Start, Modifier.padding(horizontal = 4.dp))
            SmallKeys(items, btn, host, o.scriptOn)
        }

        OverlayStyle.SIDE -> Column(
            panel.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GripHandle(grip, Modifier.size(width = btn, height = 22.dp), rotate = true)
            keys(btn)
            InfoBlock(st, items, text, Alignment.CenterHorizontally, Modifier.widthIn(max = btn + 24.dp))
            SmallKeys(items, btn, host, o.scriptOn, vertical = true)
        }

        OverlayStyle.CARD -> Column(panel.width(o.width.dp).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GripHandle(grip, Modifier.size(width = 22.dp, height = 34.dp))
                Spacer(Modifier.width(6.dp))
                InfoRow(st, items, text, Modifier.weight(1f))
                SmallKeys(items, 48.dp, host, o.scriptOn)
            }
            CardText(st, settings, items, text)
            if (items.any { it == OverlayItem.PREV || it == OverlayItem.TOGGLE || it == OverlayItem.NEXT }) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(OverlayItem.PREV to DeckButton.PREV, OverlayItem.TOGGLE to DeckButton.TOGGLE, OverlayItem.NEXT to DeckButton.NEXT)
                        .filter { it.first in items }
                        .forEach { (_, b) ->
                            OverlayKey(b, st, btn, Modifier.weight(if (b == DeckButton.NEXT) 1.6f else 1f), wide = true,
                                onPress = { engine.press(b) }, onRelease = { engine.release() })
                        }
                }
            }
        }

        OverlayStyle.BUBBLE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GripHandle(grip, alpha.clip(RoundedCornerShape(12.dp)).background(Ink.surface).border(1.dp, border, RoundedCornerShape(12.dp))
                    .size(width = 22.dp, height = 44.dp))
                if (OverlayItem.SCRIPT in items) {
                    Box(alpha) { IconKey(G.LIST, stringResource(R.string.ov_item_script), 32.dp, o.scriptOn, host.onScript) }
                }
            }
            Spacer(Modifier.width(4.dp))
            Bubble(st, btn * 1.25f, OverlayItem.PROGRESS in items, border, alpha, onPress = { engine.press(DeckButton.NEXT) }, onRelease = { engine.release() })
        }
    }
}

@Composable
private fun GripHandle(gestures: Modifier, modifier: Modifier, rotate: Boolean = false) {
    Box(modifier.then(gestures), contentAlignment = Alignment.Center) {
        Glyph(G.GRIP, Ink.faint, 18.dp, Modifier.graphicsLayer { rotationZ = if (rotate) 90f else 0f })
    }
}

@Composable
private fun OverlayKey(
    button: DeckButton, st: EngineState, size: Dp, modifier: Modifier = Modifier, wide: Boolean = false,
    onPress: () -> Unit, onRelease: () -> Unit,
) {
    val keyModifier = if (wide) modifier.height(size) else modifier.size(size)
    PressKey(onPress, onRelease, keyModifier) { pressed ->
        val shape = RoundedCornerShape(size * 0.3f)
        val stopping = button == DeckButton.TOGGLE && st.playing
        val (fg, bg) = when {
            button == DeckButton.NEXT -> Ink.onAccent to Modifier.background(
                Brush.linearGradient(if (pressed) listOf(Ink.ember, Ink.ember) else listOf(Ink.amberLight, Ink.ember)),
            )
            stopping -> Ink.coral to Modifier.background(Ink.coral.copy(alpha = if (pressed) 0.28f else 0.15f)).border(1.dp, Ink.coral.copy(alpha = 0.6f), shape)
            else -> Ink.text to Modifier.background(if (pressed) Ink.line else Ink.raised)
        }
        val glyph = when (button) {
            DeckButton.NEXT -> G.NEXT
            DeckButton.PREV -> G.PREV
            DeckButton.TOGGLE -> if (st.playing) G.STOP else G.PLAY
        }
        Box(Modifier.fillMaxSize().clip(shape).then(bg), contentAlignment = Alignment.Center) {
            Glyph(glyph, fg, size * 0.42f)
        }
    }
}

/** Open EarCue / turn off, smaller than the transport keys. */
@Composable
private fun SmallKeys(items: Set<OverlayItem>, btn: Dp, host: OverlayHost, scriptOn: Boolean, vertical: Boolean = false) {
    val size = (btn * 0.62f).coerceAtLeast(30.dp)
    val content: @Composable () -> Unit = {
        if (OverlayItem.SCRIPT in items) IconKey(G.LIST, stringResource(R.string.ov_item_script), size, scriptOn, host.onScript)
        if (OverlayItem.OPEN in items) IconKey(G.OPEN, stringResource(R.string.ov_item_open), size, false, host.onOpen)
        if (OverlayItem.CLOSE in items) IconKey(G.CLOSE, stringResource(R.string.ov_item_close), size, false, host.onClose)
    }
    if (vertical) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
private fun IconKey(glyph: G, label: String, size: Dp, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (active) Ink.amber else Ink.raised)
            .semantics { contentDescription = label }
            .pointerInput(onClick) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, if (active) Ink.onAccent else Ink.dim, size * 0.48f) }
}

@Composable
private fun rememberNow(st: EngineState): Long {
    val now by produceState(SystemClock.elapsedRealtime(), st.talkStart) {
        while (st.talkStart != 0L) {
            value = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    return now
}

private fun progressLabel(st: EngineState): String = if (st.cues.isEmpty()) "–" else "${st.index + 1} / ${st.cues.size}"

/** Section time, against the target when the script gives one; coral once over. */
@Composable
private fun TimerLabel(st: EngineState, size: TextUnit) {
    val now = rememberNow(st)
    val cue = st.cue
    val target = ((cue?.sectionMinutes ?: 0.0) * 60_000).toLong()
    val section = if (st.talkStart == 0L) 0L else now - st.sectionStart
    val over = target > 0 && section > target
    val label = if (target > 0) "${clock(section)} / ${clock(target)}" else clock(section)
    Text(label, fontSize = size, color = if (over) Ink.coral else Ink.dim, maxLines = 1)
}

@Composable
private fun InfoBlock(st: EngineState, items: Set<OverlayItem>, text: TextUnit, align: Alignment.Horizontal, modifier: Modifier) {
    if (OverlayItem.PROGRESS !in items && OverlayItem.TIMER !in items) return
    Column(modifier, horizontalAlignment = align) {
        if (OverlayItem.PROGRESS in items) Text(progressLabel(st), fontSize = text, fontWeight = FontWeight.SemiBold, color = Ink.text, maxLines = 1)
        if (OverlayItem.TIMER in items) TimerLabel(st, text * 0.8f)
    }
}

@Composable
private fun InfoRow(st: EngineState, items: Set<OverlayItem>, text: TextUnit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val slide = st.cue?.slide.orEmpty()
        if (slide.isNotBlank()) {
            Text(
                slide, fontSize = text * 0.75f, color = Ink.amber, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ink.amber.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (OverlayItem.PROGRESS in items) {
            Text(progressLabel(st), fontSize = text * 0.8f, fontWeight = FontWeight.SemiBold, color = Ink.text, maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        if (OverlayItem.TIMER in items) TimerLabel(st, text * 0.8f)
    }
}

@Composable
private fun CardText(st: EngineState, settings: Settings, items: Set<OverlayItem>, text: TextUnit) {
    val cue = st.cue
    if (OverlayItem.CUE in items) {
        if (cue == null) {
            Text(stringResource(R.string.no_script), fontSize = text, color = Ink.dim, modifier = Modifier.padding(top = 8.dp))
        } else {
            if (cue.isPartner) Text(stringResource(R.string.partner_label, cue.partner), fontSize = text * 0.7f, color = Ink.sky, modifier = Modifier.padding(top = 8.dp))
            val base = if (cue.isPartner) Ink.dim else Ink.text
            val r = st.range
            val annotated = buildAnnotatedString {
                val t = cue.text
                if (st.playing && r != null && r.first >= 0 && r.last < t.length && r.first <= r.last) {
                    withStyle(SpanStyle(color = base)) { append(t.substring(0, r.first)) }
                    withStyle(SpanStyle(color = Ink.amber)) { append(t.substring(r.first, r.last + 1)) }
                    withStyle(SpanStyle(color = base.copy(alpha = 0.55f))) { append(t.substring(r.last + 1)) }
                } else {
                    withStyle(SpanStyle(color = base)) { append(t) }
                }
            }
            Text(
                annotated, fontSize = text, lineHeight = text * 1.4f, fontWeight = FontWeight.Medium, maxLines = 5,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (OverlayItem.NEXT_LINE in items) {
        val next = nextPreview(st, settings)
        if (next.isNotBlank()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Glyph(G.NEXT, Ink.faint, 12.dp, Modifier.padding(top = 3.dp))
                Spacer(Modifier.width(6.dp))
                Text(next, fontSize = text * 0.8f, lineHeight = text * 1.1f, color = Ink.faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One round Next button with a ring that fills as the talk advances. */
@Composable
private fun Bubble(st: EngineState, size: Dp, showProgress: Boolean, border: Color, alpha: Modifier, onPress: () -> Unit, onRelease: () -> Unit) {
    val ring = Ink.amber
    val track = Ink.line
    val total = st.cues.size.coerceAtLeast(1)
    val fraction = (st.index + if (st.played) 1 else 0).toFloat() / total
    PressKey(onPress, onRelease, alpha.size(size)) { pressed ->
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(Ink.surface).border(1.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (showProgress) {
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    val w = 3.dp.toPx()
                    val arc = Size(this.size.width - w, this.size.height - w)
                    val tl = Offset(w / 2, w / 2)
                    drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(w))
                    drawArc(ring, -90f, 360f * fraction, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            Box(
                Modifier.fillMaxSize().padding(if (showProgress) 8.dp else 4.dp).clip(CircleShape).background(
                    Brush.linearGradient(if (pressed) listOf(Ink.ember, Ink.ember) else listOf(Ink.amberLight, Ink.ember)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(G.NEXT, Ink.onAccent, size * 0.3f)
                    if (showProgress && size >= 56.dp) {
                        Text(progressLabel(st), fontSize = (size.value * 0.14f).sp, color = Ink.onAccent, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * The script text display: the current cue (with the spoken word lit) and the next few cues.
 * The window that holds it takes no touches, so the app underneath stays usable through it.
 * The caller applies [OverlaySettings.scriptWindowAlpha] to the whole box (the window alpha on the
 * real overlay); background and text are scaled inside it so each ends at its own opacity.
 */
@Composable
fun ScriptPane(st: EngineState, settings: Settings, modifier: Modifier = Modifier, join: Int = 0) {
    val o = settings.overlay
    val window = o.scriptWindowAlpha
    val bgAlpha = (o.scriptBg / window).coerceIn(0f, 1f)
    val inkAlpha = (o.scriptInk / window).coerceIn(0f, 1f)
    fun Color.ink() = copy(alpha = alpha * inkAlpha)
    val text = o.scriptText.sp
    val cue = st.cue
    val r = 18.dp
    val shape = when (join) {
        1 -> RoundedCornerShape(topStart = r, topEnd = r, bottomStart = 0.dp, bottomEnd = 0.dp)
        2 -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = r, bottomEnd = r)
        else -> RoundedCornerShape(r)
    }
    Column(
        // A free-standing box keeps room on the left for its handle; a joined one has none.
        modifier.clip(shape).background(Ink.bg.copy(alpha = bgAlpha))
            .padding(start = if (o.scriptAttach) 18.dp else 34.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
    ) {
        if (cue == null) {
            Text(stringResource(R.string.no_script), fontSize = text, color = Ink.dim.ink())
            return@Column
        }
        if (cue.isPartner) Text(stringResource(R.string.partner_label, cue.partner), fontSize = text * 0.6f, color = Ink.sky.ink())
        val base = (if (cue.isPartner) Ink.dim else Ink.text).ink()
        val lit = Ink.amber.ink()
        val r = st.range
        val t = cue.text
        val annotated = buildAnnotatedString {
            if (st.playing && r != null && r.first >= 0 && r.last < t.length && r.first <= r.last) {
                withStyle(SpanStyle(color = base)) { append(t.substring(0, r.first)) }
                withStyle(SpanStyle(color = lit)) { append(t.substring(r.first, r.last + 1)) }
                withStyle(SpanStyle(color = base.copy(alpha = base.alpha * 0.55f))) { append(t.substring(r.last + 1)) }
            } else {
                withStyle(SpanStyle(color = base)) { append(t) }
            }
        }
        Text(annotated, fontSize = text, lineHeight = text * 1.35f, fontWeight = FontWeight.Medium)
        if (o.scriptLines > 0) {
            st.cues.drop(st.index + 1).take(o.scriptLines).forEach { next ->
                Text(
                    if (next.isPartner) stringResource(R.string.partner_label, next.partner) + "  " + next.text else next.text,
                    fontSize = text * 0.72f, lineHeight = text * 0.98f,
                    color = (if (next.isPartner) Ink.sky.copy(alpha = 0.75f) else Ink.faint).ink(),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** The handle beside the script box: drag to move the box, tap to fold the text away. */
@Composable
fun ScriptGrip(folded: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Ink.surface).border(1.dp, Ink.line, RoundedCornerShape(12.dp))
            .size(width = 26.dp, height = 52.dp),
        contentAlignment = Alignment.Center,
    ) { Glyph(G.GRIP, if (folded) Ink.amber else Ink.faint, 18.dp) }
}
