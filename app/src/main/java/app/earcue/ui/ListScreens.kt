package app.earcue.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.audio.EngineState
import app.earcue.data.Cue
import app.earcue.data.ScriptFormatException
import app.earcue.data.ScriptParser
import kotlinx.coroutines.delay
import java.io.File

private sealed interface CueRowItem {
    data class Header(val section: String, val slide: String, val minutes: Double, val key: Int) : CueRowItem
    data class Item(val index: Int, val cue: Cue) : CueRowItem
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CuesScreen(app: EarCueApp, st: EngineState, onBack: () -> Unit, onDetail: (Int) -> Unit) {
    val rows = remember(st.cues) {
        buildList<CueRowItem> {
            var last: String? = null
            st.cues.forEachIndexed { i, c ->
                if (c.section != last) {
                    add(CueRowItem.Header(c.section, c.slide, c.sectionMinutes, i))
                    last = c.section
                }
                add(CueRowItem.Item(i, c))
            }
        }
    }
    val start = rows.indexOfFirst { it is CueRowItem.Item && it.index == st.index }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        ScreenHeader(stringResource(R.string.cues_title), onBack, subtitle = stringResource(R.string.cues_subtitle))
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
            items(rows, key = { r -> if (r is CueRowItem.Item) "c${r.cue.id}" else "h${(r as CueRowItem.Header).key}" }) { r ->
                when (r) {
                    is CueRowItem.Header -> Row(Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (r.slide.isNotBlank()) {
                            Text(r.slide, style = MaterialTheme.typography.labelMedium, color = Ink.amber)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            r.section.ifBlank { stringResource(R.string.cues_no_section) }, style = MaterialTheme.typography.labelLarge, color = Ink.dim,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (r.minutes > 0) Text(clock((r.minutes * 60_000).toLong()), style = MaterialTheme.typography.labelMedium, color = Ink.faint)
                    }
                    is CueRowItem.Item -> {
                        val current = r.index == st.index
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (current) Ink.raised else Ink.surface)
                                .combinedClickable(
                                    onClick = { app.engine.jumpTo(r.index, play = true) },
                                    onLongClick = { onDetail(r.index) },
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Box(Modifier.width(3.dp).heightIn(min = 22.dp).clip(RoundedCornerShape(2.dp)).background(if (current) Ink.amber else Ink.surface))
                            Text(
                                "${r.index + 1}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = if (current) Ink.amber else Ink.faint, modifier = Modifier.padding(start = 8.dp).width(28.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                if (r.cue.isPartner) {
                                    Text(stringResource(R.string.partner_says, r.cue.partner), style = MaterialTheme.typography.labelSmall, color = Ink.sky)
                                    Text(r.cue.text, style = MaterialTheme.typography.bodyMedium, color = Ink.dim, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Text(r.cue.text, style = MaterialTheme.typography.bodyLarge, color = Ink.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    if (r.cue.note.isNotBlank()) Text(r.cue.note, style = MaterialTheme.typography.bodyMedium, color = Ink.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (r.cue.recording != null) Glyph(G.MIC, Ink.amber, 16.dp, Modifier.padding(start = 8.dp, top = 2.dp))
                            if (current && st.playing) Glyph(G.PLAY, Ink.amber, 14.dp, Modifier.padding(start = 8.dp, top = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CueDetailScreen(app: EarCueApp, st: EngineState, index: Int, onBack: () -> Unit) {
    val cue = st.cues.getOrNull(index)
    val scriptId = st.script?.id
    if (cue == null || scriptId == null) {
        onBack()
        return
    }
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }

    fun startRecording() {
        app.engine.stop()
        if (app.recorder.start(cue.id)) {
            recording = true
            startedAt = SystemClock.elapsedRealtime()
        } else {
            app.say(R.string.rec_failed)
        }
    }

    fun stopRecording() {
        val path = app.recorder.stop()
        recording = false
        if (path != null) {
            cue.recording?.let { File(it).delete() }
            app.db.setRecording(scriptId, cue.text, path)
            app.engine.reloadCues()
            app.say(R.string.rec_saved)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else app.say(R.string.rec_permission)
    }
    DisposableEffect(Unit) {
        onDispose { if (app.recorder.isRecording) app.recorder.stop(keep = false) }
    }
    val elapsed by produceState(0L, recording) {
        while (recording) {
            value = SystemClock.elapsedRealtime() - startedAt
            delay(200)
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            stringResource(R.string.cue_title, index + 1), onBack,
            subtitle = listOf(cue.slide, cue.section).filter { it.isNotBlank() }.joinToString(" · "),
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            Card {
                Column(Modifier.padding(20.dp)) {
                    if (cue.isPartner) Text(stringResource(R.string.partner_label, cue.partner), style = MaterialTheme.typography.labelSmall, color = Ink.sky)
                    Text(cue.text, fontSize = 22.sp, lineHeight = 31.sp, color = Ink.text)
                    if (cue.note.isNotBlank()) Text(cue.note, style = MaterialTheme.typography.bodyLarge, color = Ink.dim, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.cue_play), G.PLAY, accent = true) { app.engine.jumpTo(index, play = true) }
                PillButton(stringResource(R.string.cue_start_here), G.CHECK) {
                    app.engine.jumpTo(index, play = false)
                    app.say(R.string.cue_start_here_done)
                    onBack()
                }
            }

            SectionLabel(stringResource(R.string.rec_section))
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        stringResource(if (cue.recording != null) R.string.rec_has else R.string.rec_none),
                        style = MaterialTheme.typography.bodyMedium, color = Ink.dim,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape).background(if (recording) Ink.coral else Ink.raised)
                                .border(1.dp, if (recording) Ink.coral else Ink.line, CircleShape)
                                .clickable {
                                    if (recording) {
                                        stopRecording()
                                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startRecording()
                                    } else {
                                        permission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(if (recording) G.STOP else G.MIC, if (recording) Ink.surface else Ink.text, 26.dp) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (recording) R.string.rec_stop_hint else R.string.rec_start_hint), style = MaterialTheme.typography.titleMedium, color = Ink.text)
                            if (recording) Text(stringResource(R.string.rec_seconds, elapsed / 1000f), style = MaterialTheme.typography.labelLarge, color = Ink.coral)
                        }
                        if (cue.recording != null && !recording) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable {
                                    File(cue.recording).delete()
                                    app.db.setRecording(scriptId, cue.text, null)
                                    app.engine.reloadCues()
                                    app.say(R.string.rec_deleted)
                                },
                                contentAlignment = Alignment.Center,
                            ) { Glyph(G.TRASH, Ink.dim, 20.dp) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Paste a script as text. With [target] the text becomes a new revision of that script. */
@Composable
fun ImportScreen(app: EarCueApp, target: Long?, onBack: () -> Unit, onSaved: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val untitled = stringResource(R.string.untitled)
    val parsed = remember(text) { if (text.isBlank()) null else runCatching { ScriptParser.parse(text, untitled) } }
    val targetTitle = remember(target) { target?.let { app.db.script(it)?.title } }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        ScreenHeader(
            stringResource(if (target == null) R.string.import_title else R.string.import_title_revision), onBack,
            subtitle = targetTitle ?: stringResource(R.string.import_subtitle),
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.surface)
                    .border(1.dp, Ink.line, RoundedCornerShape(20.dp)).padding(16.dp),
            ) {
                if (text.isEmpty()) Text(TEXT_HINT, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = Ink.faint)
                BasicTextField(
                    text, { text = it }, Modifier.fillMaxSize(),
                    textStyle = TextStyle(color = Ink.text, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Ink.amber),
                )
            }
            val status = when {
                parsed == null -> stringResource(R.string.import_waiting) to Ink.faint
                parsed.isSuccess -> parsed.getOrThrow().let { p ->
                    stringResource(R.string.import_ok, p.title, p.cues.count { !it.isPartner }, p.cues.count { it.isPartner }) to Ink.mint
                }
                else -> "✕ " + (parsed.exceptionOrNull() as? ScriptFormatException)?.let { e ->
                    context.getString(e.res, *e.args)
                }.orEmpty() to Ink.coral
            }
            Text(status.first, style = MaterialTheme.typography.labelLarge, color = status.second, modifier = Modifier.padding(vertical = 12.dp))
            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.import_paste), G.PASTE) { clipboard.getText()?.text?.let { text = it } }
                PillButton(stringResource(R.string.import_save), G.CHECK, accent = true) {
                    if (parsed?.isSuccess == true) {
                        app.stageImport(text, "", target)
                        onSaved()
                    } else {
                        parsed?.exceptionOrNull()?.let { app.sayError(it) }
                    }
                }
            }
        }
    }
}

private const val TEXT_HINT = """title: My talk
speaker: P2
lang: en-US
tags: class, demo

# Opening | P1 | 2
> P1 Host: Let's start with you.
Hi everyone, thanks for coming.
// note shown on screen
Today I will talk about…"""
