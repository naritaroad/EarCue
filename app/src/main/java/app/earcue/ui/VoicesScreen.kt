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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import app.earcue.audio.Gender
import app.earcue.audio.VoiceOption
import app.earcue.data.Settings
import java.util.Locale
import kotlin.math.abs

/** "United States · iol", in the UI language. */
@Composable
fun voiceLabel(v: VoiceOption): String {
    val ui = LocalConfiguration.current.locales[0]
    val region = v.locale.getDisplayCountry(ui).ifBlank { v.locale.getDisplayLanguage(ui) }
    return "$region · ${v.code}"
}

fun genderMark(g: Gender?): String = when (g) {
    Gender.FEMALE -> "♀"
    Gender.MALE -> "♂"
    else -> ""
}

/** A ready-made voice: the n-th voice of a gender at a fixed pitch, resolved against what the engine has installed. */
data class VoicePreset(val name: Int, val gender: Gender, val rank: Int, val pitch: Float)

val VoicePresets = listOf(
    VoicePreset(R.string.vp_female, Gender.FEMALE, 0, 1.0f),
    VoicePreset(R.string.vp_male, Gender.MALE, 0, 1.0f),
    VoicePreset(R.string.vp_bright_female, Gender.FEMALE, 1, 1.15f),
    VoicePreset(R.string.vp_deep_male, Gender.MALE, 1, 0.85f),
    VoicePreset(R.string.vp_soft_female, Gender.FEMALE, 2, 0.92f),
    VoicePreset(R.string.vp_light_male, Gender.MALE, 2, 1.1f),
)

/** The voice name a preset maps to on this phone, or null when no voice of that gender is installed. */
fun resolvePreset(app: EarCueApp, p: VoicePreset): String? {
    val all = app.engine.voicesOf(p.gender)
    val list = all.filter { it.offline }.ifEmpty { all }
    return if (list.isEmpty()) null else list[p.rank % list.size].name
}

/** Sets the voice and pitch for my lines or the partner's, then plays a sample in it. */
fun chooseVoice(app: EarCueApp, partner: Boolean, voice: String, pitch: Float, sample: String) {
    app.prefs.update { if (partner) it.copy(partnerVoice = voice, partnerPitch = pitch) else it.copy(voice = voice, pitch = pitch) }
    app.engine.applyVoice()
    app.engine.preview(sample, partner)
}

/** The gender heard for a role right now; the automatic partner voice is the other gender than mine. */
fun roleGender(app: EarCueApp, st: EngineState, settings: Settings, partner: Boolean): Gender {
    if (!partner) return app.engine.myGender()
    if (st.voices.any { it.name == settings.partnerVoice }) return st.genders[settings.partnerVoice] ?: Gender.UNKNOWN
    return if (app.engine.myGender() == Gender.MALE) Gender.FEMALE else Gender.MALE
}

/**
 * Two roles share this screen: the voice for my own lines and the voice for partner lines.
 * Engines ship dozens of voices per language, so the list stays short by default (script region,
 * offline voices, six rows) and the gender switch and presets cover the common choices in one tap.
 */
@Composable
fun VoicesScreen(
    app: EarCueApp,
    st: EngineState,
    settings: Settings,
    partner: Boolean,
    onRole: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val ui = LocalConfiguration.current.locales[0]
    val scriptLocale = Locale.forLanguageTag(st.script?.lang ?: "en-US")
    val regions = remember(st.voices) {
        st.voices.map { it.locale.country }.distinct().sortedWith(compareBy({ it != scriptLocale.country }, { it }))
    }
    var region by remember(regions) { mutableStateOf(scriptLocale.country.takeIf { it in regions }) }
    var genderFilter by remember { mutableStateOf<Gender?>(null) }
    val matching = st.voices.filter {
        (region == null || it.locale.country == region) && (!settings.offlineVoices || it.offline) &&
            (genderFilter == null || st.genders[it.name] == genderFilter)
    }
    var expanded by remember(region, settings.offlineVoices, genderFilter) { mutableStateOf(false) }
    // A name saved under another engine counts as "none": playback ignores it too.
    val current = if (partner) settings.partnerVoice.takeIf { n -> st.voices.any { it.name == n } }.orEmpty() else st.voiceName
    // Six rows fit one screen; the chosen voice always stays visible.
    val shown = if (expanded || matching.size <= COLLAPSED) matching
    else (matching.take(COLLAPSED) + matching.filter { it.name == current }).distinct()
    val sample = st.cues.firstOrNull { it.isPartner == partner }?.text
        ?: st.cues.firstOrNull()?.text ?: stringResource(R.string.voice_sample)
    val rolePitch = if (partner) settings.partnerPitch else settings.pitch
    var pitch by remember(partner, rolePitch) { mutableFloatStateOf(rolePitch) }
    val heard = roleGender(app, st, settings, partner)

    fun pickGender(g: Gender) {
        val name = resolvePreset(app, VoicePresets.first { it.gender == g && it.rank == 0 })
        if (name == null) {
            app.say(if (g == Gender.FEMALE) R.string.msg_no_female else R.string.msg_no_male)
            if (!partner) chooseVoice(app, false, settings.voice, if (g == Gender.FEMALE) 1.3f else 0.72f, sample)
            return
        }
        chooseVoice(app, partner, name, 1.0f, sample)
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        ScreenHeader(
            stringResource(R.string.voice_title), onBack,
            subtitle = stringResource(R.string.voice_subtitle, scriptLocale.getDisplayLanguage(ui), st.voices.size),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment(stringResource(R.string.voice_role_me), !partner, Modifier.weight(1f)) { onRole(false) }
            Segment(stringResource(R.string.voice_role_partner), partner, Modifier.weight(1f)) { onRole(true) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
            if (partner) item {
                Card {
                    ToggleRow(stringResource(R.string.set_partner), stringResource(R.string.set_partner_hint), settings.speakPartner) { v ->
                        app.prefs.update { it.copy(speakPartner = v) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Female / male in one tap
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Segment("♀  " + stringResource(R.string.voice_gender_female), heard == Gender.FEMALE, Modifier.weight(1f)) { pickGender(Gender.FEMALE) }
                    Segment("♂  " + stringResource(R.string.voice_gender_male), heard == Gender.MALE, Modifier.weight(1f)) { pickGender(Gender.MALE) }
                }
                Card(Modifier.padding(top = 10.dp)) {
                    SliderRow(
                        stringResource(if (partner) R.string.set_partner_pitch else R.string.set_pitch), pitch, 0.5f..2f,
                        String.format(Locale.US, "%.2f", pitch), { pitch = it },
                    ) {
                        val v = (pitch * 20).toInt() / 20f
                        app.prefs.update { if (partner) it.copy(partnerPitch = v) else it.copy(pitch = v) }
                        app.engine.applyVoice()
                        app.engine.preview(sample, partner)
                    }
                }
                if (st.probing) {
                    Text(
                        stringResource(R.string.voice_probing), style = MaterialTheme.typography.labelMedium, color = Ink.dim,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
            }

            // Presets
            item {
                SectionLabel(stringResource(R.string.voice_presets))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoicePresets.forEach { p ->
                        val name = resolvePreset(app, p)
                        val selected = name != null && name == current && abs(rolePitch - p.pitch) < 0.03f
                        val option = st.voices.firstOrNull { it.name == name }
                        PresetCard(
                            title = stringResource(p.name),
                            mark = genderMark(p.gender),
                            detail = option?.code ?: stringResource(R.string.vp_unavailable),
                            enabled = name != null,
                            selected = selected,
                        ) {
                            if (name == null) app.say(if (p.gender == Gender.FEMALE) R.string.msg_no_female else R.string.msg_no_male)
                            else chooseVoice(app, partner, name, p.pitch, sample)
                        }
                    }
                }
            }

            // Full list with filters
            item {
                SectionLabel(stringResource(R.string.voice_all))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TagChip(stringResource(R.string.lib_all), selected = genderFilter == null) { genderFilter = null }
                    TagChip("♀ " + stringResource(R.string.voice_gender_female), selected = genderFilter == Gender.FEMALE) { genderFilter = Gender.FEMALE }
                    TagChip("♂ " + stringResource(R.string.voice_gender_male), selected = genderFilter == Gender.MALE) { genderFilter = Gender.MALE }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
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
            }
            item {
                if (partner) {
                    VoiceRow(stringResource(R.string.partner_auto), stringResource(R.string.partner_auto_hint), current.isEmpty(), true, null) {
                        chooseVoice(app, true, "", settings.partnerPitch, sample)
                    }
                } else {
                    VoiceRow(
                        stringResource(R.string.voice_system), stringResource(R.string.voice_system_hint), current.isEmpty(), true,
                        st.genders[st.defaultVoice],
                    ) { chooseVoice(app, false, "", settings.pitch, sample) }
                }
            }
            items(shown, key = { it.name }) { v ->
                VoiceRow(voiceLabel(v), v.name, current == v.name, v.offline, st.genders[v.name]) {
                    chooseVoice(app, partner, v.name, rolePitch, sample)
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
private fun PresetCard(title: String, mark: String, detail: String, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(132.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) Ink.raised else Ink.surface)
            .border(1.dp, if (selected) Ink.amber else Ink.line.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(mark, style = MaterialTheme.typography.titleLarge, color = if (enabled) Ink.amber else Ink.faint)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (enabled) Ink.text else Ink.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Ink.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun VoiceRow(title: String, detail: String, selected: Boolean, offline: Boolean, gender: Gender?, onClick: () -> Unit) {
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
        val mark = genderMark(gender)
        if (mark.isNotEmpty()) {
            Text(mark, style = MaterialTheme.typography.titleMedium, color = Ink.sky, modifier = Modifier.padding(end = 10.dp))
        }
        Text(
            stringResource(if (offline) R.string.voice_offline else R.string.voice_network), style = MaterialTheme.typography.labelSmall,
            color = if (offline) Ink.mint else Ink.amber,
        )
    }
}
