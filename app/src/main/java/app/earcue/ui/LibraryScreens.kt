package app.earcue.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.audio.EngineState
import app.earcue.data.Revision
import app.earcue.data.RevisionKind
import app.earcue.data.ScriptMeta
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(app: EarCueApp, st: EngineState, onBack: () -> Unit, onPaste: () -> Unit, onOpened: () -> Unit, onDetail: (Long) -> Unit) {
    val scripts = remember(st.script, st.cues) { app.db.listScripts() }
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    val textHits = remember(query, scripts) { if (query.isBlank()) emptySet() else app.db.scriptsWithText(query.trim()) }
    val allTags = remember(scripts) {
        scripts.flatMap { it.tags }.groupingBy { it.lowercase() }.eachCount().entries
            .sortedWith(compareBy({ -it.value }, { it.key })).map { e -> scripts.flatMap { it.tags }.first { it.lowercase() == e.key } }
    }
    val q = query.trim()
    val shown = scripts.filter { s ->
        (tag == null || s.tags.any { it.equals(tag, ignoreCase = true) }) &&
            (q.isEmpty() || s.title.contains(q, true) || s.speaker.contains(q, true) || s.tags.any { it.contains(q, true) } || s.id in textHits)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.importUri(uri)
    }

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { ScreenHeader(stringResource(R.string.lib_title), onBack, subtitle = stringResource(R.string.lib_subtitle)) }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(G.FILE, stringResource(R.string.lib_import_file), ".json / .txt", Modifier.weight(1f)) { picker.launch(arrayOf("*/*")) }
                ActionTile(G.PASTE, stringResource(R.string.lib_paste), stringResource(R.string.lib_paste_sub), Modifier.weight(1f), onClick = onPaste)
            }
        }
        item {
            SearchField(query, { query = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        }
        if (allTags.isNotEmpty()) item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TagChip(stringResource(R.string.lib_all), selected = tag == null) { tag = null }
                allTags.forEach { t -> TagChip("#$t", selected = tag.equals(t, true)) { tag = if (tag.equals(t, true)) null else t } }
            }
        }
        item {
            SectionLabel(stringResource(R.string.lib_count, shown.size, scripts.size), Modifier.padding(horizontal = 16.dp))
        }
        items(shown, key = { it.id }) { s ->
            val current = s.id == st.script?.id
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(if (current) Ink.raised else Ink.surface)
                    .border(1.dp, if (current) Ink.amber.copy(alpha = 0.5f) else Ink.line.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .combinedClickable(onClick = { app.engine.load(s.id); onOpened() }, onLongClick = { onDetail(s.id) })
                    .padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium, color = Ink.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (current) {
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.lib_in_use), style = MaterialTheme.typography.labelSmall, color = Ink.amber)
                        }
                    }
                    Text(
                        listOf(s.speaker, stringResource(R.string.lib_cues, s.cueCount), "r${s.headRev}", s.lang, date(s.updatedAt)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = Ink.dim, modifier = Modifier.padding(top = 3.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (s.tags.isNotEmpty()) {
                        Text(
                            s.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelMedium, color = Ink.sky,
                            modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onDetail(s.id) }, contentAlignment = Alignment.Center) {
                    Glyph(G.HISTORY, Ink.dim, 20.dp)
                }
            }
        }
        if (shown.isEmpty() && scripts.isNotEmpty()) item {
            Text(stringResource(R.string.lib_no_match), style = MaterialTheme.typography.bodyMedium, color = Ink.dim, modifier = Modifier.padding(24.dp))
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Card {
                    Column(Modifier.padding(18.dp)) {
                        Text(stringResource(R.string.lib_ai_title), style = MaterialTheme.typography.titleMedium, color = Ink.text)
                        Text(stringResource(R.string.lib_ai_body), style = MaterialTheme.typography.bodyMedium, color = Ink.dim, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Ink.surface).border(1.dp, Ink.line, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.SEARCH, Ink.dim, 18.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(stringResource(R.string.lib_search), style = MaterialTheme.typography.bodyLarge, color = Ink.faint)
            BasicTextField(
                value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Ink.text, fontSize = 16.sp),
                cursorBrush = SolidColor(Ink.amber),
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(24.dp).clip(CircleShape).clickable { onChange("") }, contentAlignment = Alignment.Center) {
                Glyph(G.CLOSE, Ink.dim, 16.dp)
            }
        }
    }
}

@Composable
fun TagChip(label: String, selected: Boolean, trailing: G? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Ink.amber else Ink.surface)
            .border(1.dp, if (selected) Ink.amber else Ink.line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Ink.onAccent else Ink.text, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Glyph(trailing, if (selected) Ink.onAccent else Ink.dim, 14.dp)
        }
    }
}

@Composable
private fun ActionTile(glyph: G, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Ink.surface).border(1.dp, Ink.line.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Glyph(glyph, Ink.amber, 22.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = Ink.text)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = Ink.faint, maxLines = 1)
    }
}

@Composable
private fun date(ms: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(ms))
}

/**
 * One script's page: switch to it, update it from a new file (a new revision), edit tags,
 * browse the revision history and restore any earlier revision.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptDetailScreen(app: EarCueApp, st: EngineState, scriptId: Long, onBack: () -> Unit, onUse: () -> Unit, onPasteRevision: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val meta: ScriptMeta? = remember(refresh, st.script, st.cues) { app.db.script(scriptId) }
    val history: List<Revision> = remember(refresh, st.script, st.cues) { app.db.revisions(scriptId) }
    var restore by remember { mutableStateOf<Revision?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            app.importUri(uri, target = scriptId)
            refresh++
        }
    }
    if (meta == null) {
        onBack()
        return
    }

    fun addTag() {
        if (newTag.isNotBlank()) {
            app.setTags(scriptId, meta.tags + newTag.split(',', '，', '、'))
            newTag = ""
            refresh++
        }
    }

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            ScreenHeader(meta.title, onBack, subtitle = listOf(meta.speaker, stringResource(R.string.lib_cues, meta.cueCount), meta.lang).filter { it.isNotBlank() }.joinToString(" · "))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                PillButton(stringResource(R.string.detail_use), G.PLAY, accent = true) {
                    app.engine.load(scriptId)
                    onUse()
                }
                PillButton(stringResource(R.string.detail_update_file), G.FILE) { picker.launch(arrayOf("*/*")) }
                PillButton(stringResource(R.string.detail_update_paste), G.PASTE, onClick = onPasteRevision)
            }
        }

        item {
            SectionLabel(stringResource(R.string.detail_tags))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                meta.tags.forEach { t ->
                    TagChip("#$t", selected = false, trailing = G.CLOSE) {
                        app.setTags(scriptId, meta.tags - t)
                        refresh++
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, Ink.line, RoundedCornerShape(50)).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(110.dp)) {
                        if (newTag.isEmpty()) Text(stringResource(R.string.detail_tag_hint), style = MaterialTheme.typography.labelLarge, color = Ink.faint)
                        BasicTextField(
                            newTag, { newTag = it }, singleLine = true,
                            textStyle = TextStyle(color = Ink.text, fontSize = 14.sp),
                            cursorBrush = SolidColor(Ink.amber),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addTag() }),
                        )
                    }
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable { addTag() }, contentAlignment = Alignment.Center) {
                        Glyph(G.PLUS, Ink.amber, 16.dp)
                    }
                }
            }
        }

        item { SectionLabel(stringResource(R.string.detail_history, history.size)) }
        items(history, key = { it.rev }) { r ->
            val head = r.rev == meta.headRev
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                // Timeline rail
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp).padding(top = 18.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (head) Ink.amber else Ink.line))
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (head) Ink.raised else Ink.surface)
                        .border(1.dp, if (head) Ink.amber.copy(alpha = 0.5f) else Ink.line.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("r${r.rev}", style = MaterialTheme.typography.titleMedium, color = if (head) Ink.amber else Ink.text)
                        Spacer(Modifier.width(8.dp))
                        Text(revisionLine(r), style = MaterialTheme.typography.bodyMedium, color = Ink.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (head) Text(stringResource(R.string.detail_current), style = MaterialTheme.typography.labelSmall, color = Ink.amber)
                    }
                    Text(
                        listOf(date(r.createdAt), stringResource(R.string.lib_cues, r.cueCount), "+${r.added} −${r.removed}").joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = Ink.dim, modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!head) {
                        Text(
                            stringResource(R.string.detail_restore), style = MaterialTheme.typography.labelLarge, color = Ink.amber,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { restore = r }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.detail_history_hint), style = MaterialTheme.typography.labelMedium, color = Ink.faint, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        }
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.detail_delete), style = MaterialTheme.typography.labelLarge, color = Ink.coral,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { confirmDelete = true }.padding(8.dp),
            )
        }
    }

    restore?.let { r ->
        AlertDialog(
            onDismissRequest = { restore = null },
            containerColor = Ink.surface,
            title = { Text(stringResource(R.string.restore_title, r.rev), color = Ink.text) },
            text = { Text(stringResource(R.string.restore_body, r.rev), color = Ink.dim) },
            confirmButton = {
                TextButton(onClick = { app.revert(scriptId, r.rev); restore = null; refresh++ }) { Text(stringResource(R.string.detail_restore), color = Ink.amber) }
            },
            dismissButton = { TextButton(onClick = { restore = null }) { Text(stringResource(R.string.cancel), color = Ink.text) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Ink.surface,
            title = { Text(stringResource(R.string.delete_title), color = Ink.text) },
            text = { Text(stringResource(R.string.delete_body, meta.title), color = Ink.dim) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; app.deleteScript(scriptId); onBack() }) { Text(stringResource(R.string.delete), color = Ink.coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel), color = Ink.text) } },
        )
    }
}

@Composable
private fun revisionLine(r: Revision): String = when (r.kind) {
    RevisionKind.IMPORT -> if (r.arg.isBlank()) stringResource(R.string.rev_import) else stringResource(R.string.rev_import_file, r.arg)
    RevisionKind.UPDATE -> if (r.arg.isBlank()) stringResource(R.string.rev_update) else stringResource(R.string.rev_update_file, r.arg)
    RevisionKind.REVERT -> stringResource(R.string.rev_revert, r.arg)
}
