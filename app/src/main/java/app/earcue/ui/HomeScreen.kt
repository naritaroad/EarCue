package app.earcue.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.audio.EngineState
import app.earcue.audio.Output
import app.earcue.data.Cue
import app.earcue.data.DeckButton
import app.earcue.data.RATE_MAX
import app.earcue.data.RATE_MIN
import app.earcue.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun HomeScreen(app: EarCueApp, st: EngineState, settings: Settings, go: (Screen) -> Unit) {
    val engine = app.engine
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val deckHeight = maxHeight * 0.42f
        Column(Modifier.fillMaxSize()) {
            TopBar(st, settings, onScripts = { go(Screen.LIBRARY) })
            if (st.script == null || st.cues.isEmpty()) {
                EmptyState(Modifier.weight(1f), onLibrary = { go(Screen.LIBRARY) })
            } else {
                NowPanel(app, st, settings, Modifier.weight(1f))
            }
            ControlStrip(app, st, settings, onVoice = { go(Screen.VOICES) })
            Deck(
                settings.deck,
                DeckLook(st.playing, st.played, nextPreview(st, settings)),
                Modifier.fillMaxWidth().height(deckHeight).padding(horizontal = 11.dp),
                onRelease = { engine.release() },
            ) { button ->
                engine.press(button)
            }
            BottomNav(go)
        }
    }
}

private fun nextPreview(st: EngineState, settings: Settings): String {
    val start = if (st.played) st.index + 1 else st.index
    return st.cues.drop(start).firstOrNull { settings.speakPartner || !it.isPartner }?.text.orEmpty()
}

@Composable
private fun TopBar(st: EngineState, settings: Settings, onScripts: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoMark(36.dp)
        Column(
            Modifier.weight(1f).padding(start = 12.dp, end = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onScripts)
                .padding(vertical = 2.dp, horizontal = 4.dp),
        ) {
            Text("EARCUE", style = MaterialTheme.typography.labelSmall, color = Ink.faint)
            Text(
                st.script?.title ?: stringResource(R.string.no_script), style = MaterialTheme.typography.titleMedium, color = Ink.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        OutputPill(st.output, settings.allowSpeaker, st.blockedAt)
    }
}

@Composable
private fun OutputPill(output: Output, allowSpeaker: Boolean, blockedAt: Long) {
    val (color, label) = when {
        output.headphones && output.muted -> Ink.amber to stringResource(R.string.out_muted)
        output.headphones -> Ink.mint to output.name.ifBlank { stringResource(R.string.out_headphones) }
        allowSpeaker -> Ink.sky to stringResource(R.string.out_speaker)
        else -> Ink.coral to stringResource(R.string.out_none)
    }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(blockedAt) {
        if (blockedAt == 0L) return@LaunchedEffect
        repeat(3) {
            flash = true; delay(180); flash = false; delay(140)
        }
    }
    val border by animateColorAsState(if (flash) Ink.coral else Ink.line, tween(90), label = "pill")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Ink.surface).border(1.dp, border, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
    }
}

@Composable
private fun NowPanel(app: EarCueApp, st: EngineState, settings: Settings, modifier: Modifier) {
    val cue = st.cue ?: return
    val now by produceState(SystemClock.elapsedRealtime(), st.talkStart) {
        while (st.talkStart != 0L) {
            value = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            if (cue.slide.isNotBlank()) {
                Text(
                    cue.slide, style = MaterialTheme.typography.labelMedium, color = Ink.amber,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.amber.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(cue.section, style = MaterialTheme.typography.labelMedium, color = Ink.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TimerText(st, cue, now) {
                app.engine.resetTimer()
                app.say(R.string.msg_timer_reset)
            }
        }

        val partners = precedingPartners(st)
        key(cue.id) {
            Column(Modifier.weight(1f).padding(top = 14.dp).verticalScroll(rememberScrollState())) {
                partners.takeLast(2).forEach { PartnerLine(it) }
                AnimatedContent(
                    targetState = cue.id,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                    label = "cue",
                ) { _ -> CueText(cue, st, settings.textScale) }
                if (settings.showNotes && cue.note.isNotBlank()) {
                    Text(
                        cue.note, style = MaterialTheme.typography.bodyMedium.copy(fontSize = (15 * settings.textScale).sp, lineHeight = (22 * settings.textScale).sp),
                        color = Ink.dim, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        ProgressRow(st)
    }
}

/** Partner lines right before the current cue: what the co-presenter says before it is your turn. */
private fun precedingPartners(st: EngineState): List<Cue> {
    val out = mutableListOf<Cue>()
    var i = st.index - 1
    while (i >= 0 && st.cues[i].isPartner) {
        out.add(0, st.cues[i])
        i--
    }
    return out
}

@Composable
private fun PartnerLine(cue: Cue) {
    Row(Modifier.padding(bottom = 10.dp)) {
        Box(Modifier.padding(top = 3.dp).width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(Ink.sky.copy(alpha = 0.5f)))
        Column(Modifier.padding(start = 10.dp)) {
            Text(stringResource(R.string.wait_for, cue.partner), style = MaterialTheme.typography.labelSmall, color = Ink.sky)
            Text(cue.text, style = MaterialTheme.typography.bodyMedium, color = Ink.dim, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CueText(cue: Cue, st: EngineState, scale: Float) {
    val base = if (cue.isPartner) Ink.dim else Ink.text
    val text = cue.text
    val r = st.range
    val annotated = buildAnnotatedString {
        if (st.playing && r != null && r.first >= 0 && r.last < text.length && r.first <= r.last) {
            withStyle(SpanStyle(color = base)) { append(text.substring(0, r.first)) }
            withStyle(SpanStyle(color = Ink.amber)) { append(text.substring(r.first, r.last + 1)) }
            withStyle(SpanStyle(color = base.copy(alpha = 0.5f))) { append(text.substring(r.last + 1)) }
        } else {
            withStyle(SpanStyle(color = base)) { append(text) }
        }
    }
    Column {
        if (cue.isPartner) Text(stringResource(R.string.partner_label, cue.partner), style = MaterialTheme.typography.labelSmall, color = Ink.sky, modifier = Modifier.padding(bottom = 4.dp))
        Text(
            annotated,
            fontSize = (25 * scale).sp, lineHeight = (35 * scale).sp, fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
        )
    }
}

@Composable
private fun TimerText(st: EngineState, cue: Cue, now: Long, onReset: () -> Unit) {
    if (st.talkStart == 0L) {
        if (cue.sectionMinutes > 0) Text(stringResource(R.string.timer_target, clock((cue.sectionMinutes * 60_000).toLong())), style = MaterialTheme.typography.labelMedium, color = Ink.faint)
        return
    }
    val section = now - st.sectionStart
    val total = now - st.talkStart
    val over = cue.sectionMinutes > 0 && section > cue.sectionMinutes * 60_000
    val totalLabel = stringResource(R.string.timer_total, clock(total))
    val label = buildString {
        append(clock(section))
        if (cue.sectionMinutes > 0) append(" / ").append(clock((cue.sectionMinutes * 60_000).toLong()))
        append("  ·  ").append(totalLabel)
    }
    Text(
        label, style = MaterialTheme.typography.labelMedium, color = if (over) Ink.coral else Ink.dim,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReset).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
private fun ProgressRow(st: EngineState) {
    val total = st.cues.size.coerceAtLeast(1)
    val fraction = (st.index + if (st.played) 1 else 0).toFloat() / total
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Ink.raised)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Ink.amber))
        }
        Spacer(Modifier.width(12.dp))
        Text("${st.index + 1} / ${st.cues.size}", style = MaterialTheme.typography.labelMedium, color = Ink.dim)
    }
}

@Composable
private fun ControlStrip(app: EarCueApp, st: EngineState, settings: Settings, onVoice: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Speed stepper
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Ink.surface).border(1.dp, Ink.line, RoundedCornerShape(50)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−") { stepRate(app, -0.05f) }
            Text(String.format(Locale.US, "%.2f×", settings.rate), style = MaterialTheme.typography.labelLarge, color = Ink.text)
            StepButton("+") { stepRate(app, +0.05f) }
        }
        val mark = genderMark(app.engine.myGender())
        val voice = (if (mark.isEmpty()) "" else "$mark ") +
            (st.voices.firstOrNull { it.name == st.voiceName }?.let { voiceLabel(it) } ?: stringResource(R.string.voice_default))
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Ink.surface).border(1.dp, Ink.line, RoundedCornerShape(50))
                .clickable(onClick = onVoice).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(G.SPARK, Ink.amber, 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(voice, style = MaterialTheme.typography.labelLarge, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Ink.surface).border(1.dp, Ink.line, RoundedCornerShape(50))
                .clickable { app.prefs.update { it.copy(leftHanded = !it.leftHanded) }; app.engine.haptics.tick() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(G.HAND, Ink.text, 16.dp, Modifier.graphicsLayer { scaleX = if (settings.leftHanded) 1f else -1f })
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (settings.leftHanded) R.string.hand_left_short else R.string.hand_right_short), style = MaterialTheme.typography.labelLarge, color = Ink.text)
        }
    }
}

private fun stepRate(app: EarCueApp, delta: Float) {
    app.prefs.update { it.copy(rate = (((it.rate + delta) * 100).roundToInt() / 100f).coerceIn(RATE_MIN, RATE_MAX)) }
}

/** Steps once on touch-down, then keeps stepping (and speeds up) while held. */
@Composable
private fun StepButton(label: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (pressed) Ink.raised else Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    step()
                    val repeat = scope.launch {
                        delay(400)
                        var gap = 160L
                        while (isActive) {
                            step()
                            delay(gap)
                            gap = (gap * 0.9).toLong().coerceAtLeast(70L)
                        }
                    }
                    waitForUpOrCancellation()
                    repeat.cancel()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 18.sp, color = Ink.dim)
    }
}

@Composable
private fun BottomNav(go: (Screen) -> Unit) {
    val items = listOf(
        Triple(G.LIST, R.string.nav_cues, Screen.CUES),
        Triple(G.LIBRARY, R.string.nav_library, Screen.LIBRARY),
        Triple(G.STAGE, R.string.nav_stage, Screen.STAGE),
        Triple(G.LAYOUT, R.string.nav_layout, Screen.LAYOUT),
        Triple(G.SETTINGS, R.string.nav_settings, Screen.SETTINGS),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        items.forEach { (glyph, label, screen) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { go(screen) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Glyph(glyph, Ink.dim, 20.dp)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = Ink.dim, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onLibrary: () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoMark(88.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, color = Ink.text)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyMedium, color = Ink.dim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        PillButton(stringResource(R.string.empty_import), G.PLUS, accent = true, onClick = onLibrary)
    }
}
