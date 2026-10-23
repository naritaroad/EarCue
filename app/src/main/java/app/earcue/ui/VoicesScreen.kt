package app.earcue.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.audio.EngineState
import app.earcue.audio.VoiceOption
import app.earcue.data.Settings
import java.util.Locale

/** "United States · iol", in the UI language. */
@Composable
fun voiceLabel(v: VoiceOption): String {
    val ui = LocalConfiguration.current.locales[0]
    val region = v.locale.getDisplayCountry(ui).ifBlank { v.locale.getDisplayLanguage(ui) }
    return "$region · ${v.code}"
}

/**
 * Engines ship dozens of voices per language. The picker stays short by default:
 * only the script's region and only offline voices, with chips to widen the view.
 */
@Composable
fun VoicesScreen(app: EarCueApp, st: EngineState, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val ui = LocalConfiguration.current.locales[0]
    val scriptLocale = Locale.forLanguageTag(st.script?.lang ?: "en-US")
    val regions = remember(st.voices) {
        st.voices.map { it.locale.country }.distinct().sortedWith(compareBy({ it != scriptLocale.country }, { it }))
    }
    var region by remember(regions) { mutableStateOf(scriptLocale.country.takeIf { it in regions }) }
    val matching = st.voices.filter { (region == null || it.locale.country == region) && (!settings.offlineVoices || it.offline) }
    var expanded by remember(region, settings.offlineVoices) { mutableStateOf(false) }
    // Six rows fit one screen; the chosen voice always stays visible.
    val shown = if (expanded || matching.size <= COLLAPSED) matching
    else (matching.take(COLLAPSED) + matching.filter { it.name == st.voiceName }).distinct()
    val sample = st.cues.firstOrNull { !it.isPartner }?.text ?: stringResource(R.string.voice_sample)

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        ScreenHeader(
            stringResource(R.string.voice_title), onBack,
            subtitle = stringResource(R.string.voice_subtitle, scriptLocale.getDisplayLanguage(ui), st.voices.size),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagChip(stringResource(R.string.voice_all_regions), selected = region == null) { region = null }
            regions.forEach { r ->
                val name = Locale("", r).getDisplayCountry(ui).ifBlank { r.ifBlank { "—" } }
                TagChip(name, selected = region == r) { region = r }
            }
        }
        ToggleRow(stringResource(R.string.voice_offline_only), stringResource(R.string.voice_offline_hint), settings.offlineVoices) { v ->
            app.prefs.update { it.copy(offlineVoices = v) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                VoiceRow(stringResource(R.string.voice_system), stringResource(R.string.voice_system_hint), st.voiceName.isEmpty(), true) {
                    app.prefs.update { it.copy(voice = "") }
                    app.engine.applyVoice()
                    app.engine.preview(sample)
                }
            }
            items(shown, key = { it.name }) { v ->
                VoiceRow(voiceLabel(v), v.name, st.voiceName == v.name, v.offline) {
                    app.prefs.update { it.copy(voice = v.name) }
                    app.engine.applyVoice()
                    app.engine.preview(sample)
                }
            }
            if (shown.size < matching.size) item {
                Text(
                    stringResource(R.string.voice_show_all, matching.size), style = MaterialTheme.typography.labelLarge, color = Ink.amber,
                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable { expanded = true }.padding(8.dp),
                )
            }
            if (shown.isEmpty()) item {
                Text(stringResource(R.string.voice_none), style = MaterialTheme.typography.bodyMedium, color = Ink.dim, modifier = Modifier.padding(16.dp))
            }
            item {
                Text(
                    stringResource(R.string.voice_more), style = MaterialTheme.typography.labelLarge, color = Ink.amber,
                    modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) } }
                        .padding(8.dp),
                )
            }
        }
    }
}

private const val COLLAPSED = 6

@Composable
fun VoiceRow(title: String, detail: String, selected: Boolean, offline: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) Ink.raised else Ink.surface)
            .border(1.dp, if (selected) Ink.amber.copy(alpha = 0.5f) else Ink.line.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) Ink.amber else Ink.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Ink.amber)) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Ink.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            stringResource(if (offline) R.string.voice_offline else R.string.voice_network), style = MaterialTheme.typography.labelSmall,
            color = if (offline) Ink.mint else Ink.amber,
        )
    }
}
