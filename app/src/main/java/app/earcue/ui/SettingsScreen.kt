package app.earcue.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.audio.Earcons
import app.earcue.audio.EngineState
import app.earcue.data.DeckButton
import app.earcue.data.DeckLayout
import app.earcue.data.Presets
import app.earcue.data.RATE_MAX
import app.earcue.data.RATE_MIN
import app.earcue.data.Settings
import java.util.Locale

/** UI languages, labelled in their own language. "" follows the system. */
val UiLanguages = listOf(
    "en" to "English",
    "zh-TW" to "繁體中文",
    "zh-CN" to "简体中文",
    "es" to "Español",
    "pt-BR" to "Português (Brasil)",
    "ja" to "日本語",
    "ko" to "한국어",
    "fr" to "Français",
    "de" to "Deutsch",
    "ru" to "Русский",
)

@Composable
fun SettingsScreen(app: EarCueApp, st: EngineState, settings: Settings, onBack: () -> Unit, onLayout: () -> Unit, onVoices: (partner: Boolean) -> Unit) {
    val prefs = app.prefs
    val engine = app.engine
    var rate by remember(settings.rate) { mutableFloatStateOf(settings.rate) }
    var pitch by remember(settings.pitch) { mutableFloatStateOf(settings.pitch) }
    var partnerPitch by remember(settings.partnerPitch) { mutableFloatStateOf(settings.partnerPitch) }
    var textScale by remember { mutableFloatStateOf(settings.textScale) }
    var languagesOpen by remember { mutableStateOf(false) }
    val currentLanguage = remember { app.currentLanguage() }
    val sample = st.cues.firstOrNull { !it.isPartner }?.text ?: stringResource(R.string.voice_sample)
    val partnerSample = st.cues.firstOrNull { it.isPartner }?.text ?: sample

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item { ScreenHeader(stringResource(R.string.settings_title), onBack) }

        // ---- appearance ----
        item {
            SectionLabel(stringResource(R.string.set_appearance))
            Card {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(G.SUN, Ink.dim, 20.dp)
                    Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.bodyLarge, color = Ink.text, modifier = Modifier.padding(start = 12.dp).weight(1f))
                }
                Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (key, label) ->
                        Segment(stringResource(label), settings.theme == key, Modifier.weight(1f)) { prefs.update { it.copy(theme = key) } }
                    }
                }
                Divider()
                val languageName = UiLanguages.firstOrNull { it.first.equals(currentLanguage, true) }?.second ?: stringResource(R.string.lang_system)
                Row(
                    Modifier.fillMaxWidth().clickable { languagesOpen = !languagesOpen }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.GLOBE, Ink.dim, 20.dp)
                    Text(stringResource(R.string.set_language), style = MaterialTheme.typography.bodyLarge, color = Ink.text, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    Text(languageName, style = MaterialTheme.typography.labelLarge, color = Ink.amber)
                    Glyph(G.CHEVRON, Ink.dim, 16.dp, Modifier.padding(start = 6.dp).graphicsLayer { rotationZ = if (languagesOpen) 90f else 0f })
                }
                if (languagesOpen) {
                    (listOf("" to stringResource(R.string.lang_system)) + UiLanguages).forEach { (tag, name) ->
                        val on = currentLanguage.equals(tag, true) || (tag.isEmpty() && UiLanguages.none { it.first.equals(currentLanguage, true) })
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                languagesOpen = false
                                app.setLanguage(tag)
                            }.padding(start = 50.dp, end = 18.dp, top = 11.dp, bottom = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, color = if (on) Ink.amber else Ink.text, modifier = Modifier.weight(1f))
                            if (on) Glyph(G.CHECK, Ink.amber, 18.dp)
                        }
                    }
                }
            }
        }

        // ---- hand ----
        item {
            SectionLabel(stringResource(R.string.set_hand))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(false to R.string.hand_right, true to R.string.hand_left).forEach { (left, label) ->
                    val on = settings.leftHanded == left
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (on) Ink.raised else Ink.surface)
                            .border(1.dp, if (on) Ink.amber else Ink.line.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .clickable { prefs.update { it.copy(leftHanded = left) } }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(G.HAND, if (on) Ink.amber else Ink.dim, 22.dp, Modifier.graphicsLayer { scaleX = if (left) 1f else -1f })
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = Ink.text)
                            Text(stringResource(if (left) R.string.hand_left_hint else R.string.hand_right_hint), style = MaterialTheme.typography.labelMedium, color = Ink.dim)
                        }
                    }
                }
            }
        }

        // ---- layout ----
        item {
            SectionLabel(stringResource(R.string.set_layout))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Presets.all.forEachIndexed { i, p ->
                    PresetTile(
                        stringResource(p.name), p.layout.forHand(settings.leftHanded),
                        selected = settings.customLayout == null && settings.preset == i, modifier = Modifier.weight(1f),
                    ) { prefs.update { it.copy(preset = i, customLayout = null) } }
                }
                settings.customLayout?.let {
                    PresetTile(stringResource(R.string.preset_custom), it.forHand(settings.leftHanded), selected = true, modifier = Modifier.weight(1f)) {}
                }
            }
            Text(
                stringResource(if (settings.customLayout == null) Presets.all[settings.preset].hint else R.string.preset_custom_hint),
                style = MaterialTheme.typography.bodyMedium, color = Ink.dim, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.layout_edit), G.LAYOUT, accent = true, onClick = onLayout)
                if (settings.customLayout != null) {
                    val presetName = stringResource(Presets.all[settings.preset].name)
                    PillButton(stringResource(R.string.layout_reset), G.BACK) {
                        prefs.update { it.copy(customLayout = null) }
                        app.say(R.string.layout_reset_done, presetName)
                    }
                }
            }
        }

        // ---- voice ----
        item {
            SectionLabel(stringResource(R.string.set_voice))
            Card {
                VoiceRoleRow(
                    title = stringResource(R.string.set_my_voice),
                    detail = st.voices.firstOrNull { it.name == st.voiceName }?.let { voiceLabel(it) } ?: stringResource(R.string.voice_system),
                    mark = genderMark(engine.myGender()),
                ) { onVoices(false) }
                Divider()
                SliderRow(stringResource(R.string.set_rate), rate, RATE_MIN..RATE_MAX, String.format(Locale.US, "%.2f×", rate), { rate = it }) {
                    prefs.update { it.copy(rate = ((rate * 20).toInt() / 20f).coerceIn(RATE_MIN, RATE_MAX)) }
                }
                Divider()
                SliderRow(stringResource(R.string.set_pitch), pitch, 0.5f..2f, String.format(Locale.US, "%.2f", pitch), { pitch = it }) {
                    prefs.update { it.copy(pitch = (pitch * 20).toInt() / 20f) }
                    engine.applyVoice()
                }
                Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                    PillButton(stringResource(R.string.set_preview), G.PLAY) { engine.preview(sample) }
                }
                Divider()
                VoiceRoleRow(
                    title = stringResource(R.string.set_partner_voice),
                    detail = st.voices.firstOrNull { it.name == settings.partnerVoice }?.let { voiceLabel(it) }
                        ?: stringResource(R.string.partner_auto),
                    mark = genderMark(roleGender(app, st, settings, partner = true)),
                ) { onVoices(true) }
                Divider()
                SliderRow(
                    stringResource(R.string.set_partner_pitch), partnerPitch, 0.5f..2f, String.format(Locale.US, "%.2f", partnerPitch),
                    { partnerPitch = it },
                ) {
                    prefs.update { it.copy(partnerPitch = (partnerPitch * 20).toInt() / 20f) }
                }
                Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                    PillButton(stringResource(R.string.set_preview), G.PLAY) { engine.preview(partnerSample, partner = true) }
                }
            }
        }

        if (st.engines.size > 1) item {
            SectionLabel(stringResource(R.string.set_engine))
            Card {
                st.engines.forEachIndexed { i, (pkg, label) ->
                    if (i > 0) Divider()
                    val on = st.engine == pkg
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            prefs.update { it.copy(engine = pkg, voice = "") }
                            engine.initTts()
                        }.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) Ink.amber else Ink.line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(Ink.amber)) }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink.text)
                            Text(pkg, style = MaterialTheme.typography.labelMedium, color = Ink.faint)
                        }
                    }
                }
            }
            Text(stringResource(R.string.set_engine_hint), style = MaterialTheme.typography.labelMedium, color = Ink.faint, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }

        item {
            SectionLabel(stringResource(R.string.set_playback))
            Card {
                ToggleRow(stringResource(R.string.set_partner), stringResource(R.string.set_partner_hint), settings.speakPartner) { v -> prefs.update { it.copy(speakPartner = v) } }
                Divider()
                ToggleRow(stringResource(R.string.set_page_tone), stringResource(R.string.set_page_tone_hint), settings.pageTone) { v ->
                    prefs.update { it.copy(pageTone = v) }
                    if (v) engine.previewTone(Earcons.PAGE)
                }
                Divider()
                ToggleRow(stringResource(R.string.set_turn_tone), stringResource(R.string.set_turn_tone_hint), settings.turnTone) { v ->
                    prefs.update { it.copy(turnTone = v) }
                    if (v) engine.previewTone(Earcons.TURN)
                }
                Divider()
                ToggleRow(stringResource(R.string.set_auto), stringResource(R.string.set_auto_hint), settings.autoAdvance) { v -> prefs.update { it.copy(autoAdvance = v) } }
                Divider()
                ToggleRow(stringResource(R.string.set_speaker), stringResource(R.string.set_speaker_hint), settings.allowSpeaker) { v -> prefs.update { it.copy(allowSpeaker = v) } }
            }
        }

        item {
            SectionLabel(stringResource(R.string.set_keys))
            Card {
                ToggleRow(stringResource(R.string.set_shutter), stringResource(R.string.set_shutter_hint), settings.shutterKey) { v -> prefs.update { it.copy(shutterKey = v) } }
                Divider()
                ToggleRow(stringResource(R.string.set_volume), stringResource(R.string.set_volume_hint), settings.volumeKeys) { v -> prefs.update { it.copy(volumeKeys = v) } }
                Divider()
                ToggleRow(stringResource(R.string.set_headset), stringResource(R.string.set_headset_hint), settings.headsetKeys) { v -> prefs.update { it.copy(headsetKeys = v) } }
            }
            Text(stringResource(R.string.hint_hold), style = MaterialTheme.typography.labelMedium, color = Ink.faint, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }

        item {
            SectionLabel(stringResource(R.string.set_feedback))
            Card {
                ToggleRow(stringResource(R.string.set_tap_haptics), stringResource(R.string.set_tap_haptics_hint), settings.tapHaptics) { v -> prefs.update { it.copy(tapHaptics = v) } }
                Divider()
                ToggleRow(stringResource(R.string.set_overtime), stringResource(R.string.set_overtime_hint), settings.overtimeHaptics) { v -> prefs.update { it.copy(overtimeHaptics = v) } }
                Divider()
                ToggleRow(stringResource(R.string.set_notes), stringResource(R.string.set_notes_hint), settings.showNotes) { v -> prefs.update { it.copy(showNotes = v) } }
                Divider()
                ToggleRow(stringResource(R.string.set_screen_on), stringResource(R.string.set_screen_on_hint), settings.keepScreenOn) { v -> prefs.update { it.copy(keepScreenOn = v) } }
                Divider()
                SliderRow(stringResource(R.string.set_text_size), textScale, 0.8f..1.5f, "${(textScale * 100).toInt()}%", { textScale = it }) {
                    prefs.update { it.copy(textScale = textScale) }
                }
            }
            Text(stringResource(R.string.set_guard_hint), style = MaterialTheme.typography.labelMedium, color = Ink.faint, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                LogoMark(28.dp)
                Spacer(Modifier.width(10.dp))
                Text("EarCue ${app.packageManager.getPackageInfo(app.packageName, 0).versionName}", style = MaterialTheme.typography.labelMedium, color = Ink.faint)
            }
        }
    }
}

@Composable
fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Ink.amber else Ink.raised)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Ink.onAccent else Ink.text, maxLines = 1)
    }
}

@Composable
fun PresetTile(name: String, layout: DeckLayout, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Ink.raised else Ink.surface)
            .border(1.dp, if (selected) Ink.amber else Ink.line.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LayoutThumb(layout, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = if (selected) Ink.amber else Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LayoutThumb(layout: DeckLayout, modifier: Modifier = Modifier) {
    val next = Ink.amber
    val toggle = Ink.dim.copy(alpha = 0.5f)
    val prev = Ink.line
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        layout.slots.forEach { (b, s) ->
            val color = when (b) {
                DeckButton.NEXT -> next
                DeckButton.TOGGLE -> toggle
                DeckButton.PREV -> prev
            }
            drawRoundRect(
                color,
                topLeft = Offset(s.x * size.width + gap, s.y * size.height + gap),
                size = Size(s.w * size.width - gap * 2, s.h * size.height - gap * 2),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
    }
}

@Composable
private fun VoiceRoleRow(title: String, detail: String, mark: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.SPARK, Ink.amber, 18.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink.text)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Ink.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (mark.isNotEmpty()) Text(mark, style = MaterialTheme.typography.titleMedium, color = Ink.sky, modifier = Modifier.padding(end = 8.dp))
        Glyph(G.CHEVRON, Ink.dim, 16.dp)
    }
}
