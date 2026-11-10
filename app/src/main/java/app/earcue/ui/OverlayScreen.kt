package app.earcue.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.audio.EngineState
import app.earcue.data.OverlayItem
import app.earcue.data.OverlaySettings
import app.earcue.data.OverlaySize
import app.earcue.data.OverlayStyle
import app.earcue.data.Settings
import app.earcue.overlay.OverlayService
import kotlin.math.roundToInt

private val TRANSPORT = setOf(OverlayItem.PREV, OverlayItem.TOGGLE, OverlayItem.NEXT)

@Composable
fun OverlayScreen(app: EarCueApp, st: EngineState, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = app.prefs
    val o = settings.overlay
    var canDraw by remember { mutableStateOf(SystemSettings.canDrawOverlays(context)) }
    // Set when the user switched the controls on but first had to grant the permission in system settings.
    var wantOn by rememberSaveable { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun turnOn() {
        prefs.update { it.copy(overlayOn = true) }
        OverlayService.sync(context)
        // The service notification is how the controls are turned off from anywhere; ask once on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openPermission() {
        context.startActivity(
            Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // Coming back from the system permission page: re-check, and finish switching on if the user allowed it.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDraw = SystemSettings.canDrawOverlays(context)
                if (wantOn) {
                    wantOn = false
                    if (canDraw) turnOn() else app.say(R.string.ov_perm_denied)
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun edit(change: (OverlaySettings) -> OverlaySettings) = prefs.update { it.copy(overlay = change(it.overlay)) }

    var button by remember(o.button) { mutableFloatStateOf(o.button.toFloat()) }
    var text by remember(o.text) { mutableFloatStateOf(o.text.toFloat()) }
    var width by remember(o.width) { mutableFloatStateOf(o.width.toFloat()) }
    var opacity by remember(o.opacity) { mutableFloatStateOf(o.opacity) }
    var scriptText by remember(o.scriptText) { mutableFloatStateOf(o.scriptText.toFloat()) }
    var scriptLines by remember(o.scriptLines) { mutableFloatStateOf(o.scriptLines.toFloat()) }
    var scriptBg by remember(o.scriptBg) { mutableFloatStateOf(o.scriptBg) }
    var scriptInk by remember(o.scriptInk) { mutableFloatStateOf(o.scriptInk) }
    // The preview follows the sliders while they move; the saved settings change when they are let go.
    val live = settings.copy(
        overlay = o.copy(
            button = button.roundToInt(), text = text.roundToInt(), width = width.roundToInt(), opacity = opacity,
            scriptText = scriptText.roundToInt(), scriptLines = scriptLines.roundToInt(),
            scriptBg = scriptBg, scriptInk = scriptInk,
        ),
    )

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item { ScreenHeader(stringResource(R.string.ov_title), onBack) }

        item {
            Card {
                ToggleRow(stringResource(R.string.ov_enable), stringResource(R.string.ov_enable_hint), settings.overlayOn && canDraw) { on ->
                    when {
                        !on -> prefs.update { it.copy(overlayOn = false) }
                        canDraw -> turnOn()
                        else -> {
                            wantOn = true
                            openPermission()
                        }
                    }
                }
                if (!canDraw) {
                    Divider()
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(stringResource(R.string.ov_perm_needed), style = MaterialTheme.typography.bodyMedium, color = Ink.dim)
                        PillButton(stringResource(R.string.ov_perm_grant), G.SETTINGS, Modifier.padding(top = 10.dp), accent = true) {
                            wantOn = true
                            openPermission()
                        }
                        // Android 13+ greys the switch out for apps installed outside a store until
                        // "Allow restricted settings" is chosen in App info. Every Releases download hits this.
                        if (Build.VERSION.SDK_INT >= 33) {
                            Text(
                                stringResource(R.string.ov_perm_restricted), style = MaterialTheme.typography.bodyMedium, color = Ink.dim,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                            PillButton(stringResource(R.string.ov_app_info), G.FILE, Modifier.padding(top = 10.dp)) {
                                wantOn = true
                                context.startActivity(
                                    Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.ov_keys_hint), style = MaterialTheme.typography.labelMedium, color = Ink.faint, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }

        // ---- position and size: a scaled-down screen to drag things around on ----
        item {
            SectionLabel(stringResource(R.string.ov_editor))
            Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
                OverlayEditor(app, st, live) { changed -> edit { changed } }
            }
            Text(stringResource(R.string.ov_editor_hint), style = MaterialTheme.typography.labelMedium, color = Ink.faint, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }

        // ---- style ----
        item {
            SectionLabel(stringResource(R.string.ov_style))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayStyle.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { style ->
                            StyleTile(style, o.style == style, Modifier.weight(1f)) {
                                collapsed = false
                                edit { it.copy(style = style) }
                            }
                        }
                    }
                }
            }
            Text(stringResource(o.style.hint), style = MaterialTheme.typography.bodyMedium, color = Ink.dim, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        }

        // ---- items ----
        item {
            SectionLabel(stringResource(R.string.ov_items))
            Card {
                val shown = o.shown
                o.style.supported.forEachIndexed { i, item ->
                    if (i > 0) Divider()
                    val on = item in shown
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            // Bar, side column and card keep at least one of Previous / Play / Next.
                            if (on && item in TRANSPORT && (shown intersect TRANSPORT) == setOf(item)) app.say(R.string.ov_min_one)
                            else edit { it.toggle(item) }
                        }.padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(item.label), style = MaterialTheme.typography.bodyLarge, color = Ink.text, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (on) Ink.amber else Ink.raised)
                                .border(1.dp, if (on) Ink.amber else Ink.line, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Glyph(G.CHECK, Ink.onAccent, 16.dp) }
                    }
                }
            }
            if (o.style == OverlayStyle.BUBBLE) {
                Text(stringResource(R.string.ov_items_bubble_hint), style = MaterialTheme.typography.labelMedium, color = Ink.faint, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
        }

        // ---- size ----
        item {
            SectionLabel(stringResource(R.string.ov_size))
            Card {
                SliderRow(
                    stringResource(R.string.ov_size_button), button,
                    OverlaySize.BUTTON.first.toFloat()..OverlaySize.BUTTON.last.toFloat(), "${button.roundToInt()} dp", { button = it },
                ) { edit { it.copy(button = button.roundToInt()) } }
                Divider()
                SliderRow(
                    stringResource(R.string.ov_size_text), text,
                    OverlaySize.TEXT.first.toFloat()..OverlaySize.TEXT.last.toFloat(), "${text.roundToInt()} sp", { text = it },
                ) { edit { it.copy(text = text.roundToInt()) } }
                if (o.style == OverlayStyle.CARD) {
                    Divider()
                    SliderRow(
                        stringResource(R.string.ov_size_width), width,
                        OverlaySize.WIDTH.first.toFloat()..OverlaySize.WIDTH.last.toFloat(), "${width.roundToInt()} dp", { width = it },
                    ) { edit { it.copy(width = width.roundToInt()) } }
                }
                Divider()
                SliderRow(
                    stringResource(R.string.ov_opacity), opacity, OverlaySize.OPACITY_MIN..1f, "${(opacity * 100).roundToInt()}%", { opacity = it },
                ) { edit { it.copy(opacity = (opacity * 20).roundToInt() / 20f) } }
            }
        }

        // ---- script text display ----
        item {
            SectionLabel(stringResource(R.string.ov_script))
            Card {
                ToggleRow(stringResource(R.string.ov_script_on), stringResource(R.string.ov_script_on_hint), o.scriptOn) { v ->
                    edit { it.copy(scriptOn = v) }
                }
                Divider()
                ToggleRow(stringResource(R.string.ov_script_attach), stringResource(R.string.ov_script_attach_hint), o.scriptAttach) { v ->
                    edit { it.copy(scriptAttach = v) }
                }
                if (o.scriptAttach) {
                    Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Segment(stringResource(R.string.ov_dock_above), !o.scriptBelow, Modifier.weight(1f)) { edit { it.copy(scriptBelow = false) } }
                        Segment(stringResource(R.string.ov_dock_below), o.scriptBelow, Modifier.weight(1f)) { edit { it.copy(scriptBelow = true) } }
                    }
                }
                Divider()
                SliderRow(
                    stringResource(R.string.ov_size_text), scriptText,
                    OverlaySize.SCRIPT_TEXT.first.toFloat()..OverlaySize.SCRIPT_TEXT.last.toFloat(), "${scriptText.roundToInt()} sp", { scriptText = it },
                ) { edit { it.copy(scriptText = scriptText.roundToInt()) } }
                Divider()
                SliderRow(
                    stringResource(R.string.ov_script_lines), scriptLines,
                    OverlaySize.SCRIPT_LINES.first.toFloat()..OverlaySize.SCRIPT_LINES.last.toFloat(), "${scriptLines.roundToInt()}", { scriptLines = it },
                ) { edit { it.copy(scriptLines = scriptLines.roundToInt()) } }
                Divider()
                SliderRow(
                    stringResource(R.string.ov_script_bg), scriptBg, OverlaySize.SCRIPT_BG_MIN..OverlaySize.SCRIPT_OPACITY_MAX,
                    "${(scriptBg * 100).roundToInt()}%", { scriptBg = it },
                ) { edit { it.copy(scriptBg = (scriptBg * 20).roundToInt() / 20f) } }
                Divider()
                SliderRow(
                    stringResource(R.string.ov_script_ink), scriptInk, OverlaySize.SCRIPT_INK_MIN..OverlaySize.SCRIPT_OPACITY_MAX,
                    "${(scriptInk * 100).roundToInt()}%", { scriptInk = it },
                ) { edit { it.copy(scriptInk = (scriptInk * 20).roundToInt() / 20f) } }
            }
            Text(stringResource(R.string.ov_script_opacity_hint), style = MaterialTheme.typography.labelMedium, color = Ink.faint, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            Row(Modifier.padding(top = 12.dp)) {
                PillButton(stringResource(R.string.ov_reset), G.BACK) {
                    edit {
                        it.copy(
                            button = OverlaySize.DEFAULT_BUTTON, text = OverlaySize.DEFAULT_TEXT, width = OverlaySize.DEFAULT_WIDTH,
                            opacity = OverlaySize.DEFAULT_OPACITY, x = -1, y = -1,
                            scriptText = OverlaySize.DEFAULT_SCRIPT_TEXT, scriptLines = OverlaySize.DEFAULT_SCRIPT_LINES,
                            scriptWidth = OverlaySize.DEFAULT_SCRIPT_WIDTH, scriptHeight = 0, scriptX = -1, scriptY = OverlaySize.DEFAULT_SCRIPT_Y,
                            scriptBg = OverlaySize.DEFAULT_SCRIPT_BG, scriptInk = OverlaySize.DEFAULT_SCRIPT_INK,
                        )
                    }
                    app.say(R.string.ov_reset_done)
                }
            }
        }
    }
}

@Composable
private fun StyleTile(style: OverlayStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Ink.raised else Ink.surface)
            .border(1.dp, if (selected) Ink.amber else Ink.line.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyleThumb(style, if (selected) Ink.amber else Ink.dim)
        Text(
            stringResource(style.label), style = MaterialTheme.typography.titleMedium, color = if (selected) Ink.amber else Ink.text,
            maxLines = 2, modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** A tiny drawing of each style's shape. */
@Composable
private fun StyleThumb(style: OverlayStyle, color: androidx.compose.ui.graphics.Color) {
    val c = color
    androidx.compose.foundation.Canvas(Modifier.size(30.dp)) {
        val u = size.minDimension / 30f
        val r = androidx.compose.ui.geometry.CornerRadius(3 * u)
        fun box(x: Float, y: Float, w: Float, h: Float, fill: Boolean = true) = drawRoundRect(
            c, androidx.compose.ui.geometry.Offset(x * u, y * u), androidx.compose.ui.geometry.Size(w * u, h * u), r,
            style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else androidx.compose.ui.graphics.drawscope.Stroke(1.6f * u),
        )
        when (style) {
            OverlayStyle.BAR -> { box(1f, 10f, 28f, 10f, fill = false); box(5f, 13f, 5f, 4f); box(12.5f, 13f, 5f, 4f); box(20f, 13f, 5f, 4f) }
            OverlayStyle.SIDE -> { box(10f, 1f, 10f, 28f, fill = false); box(13f, 5f, 4f, 5f); box(13f, 12.5f, 4f, 5f); box(13f, 20f, 4f, 5f) }
            OverlayStyle.CARD -> { box(2f, 3f, 26f, 24f, fill = false); box(6f, 8f, 18f, 2.5f); box(6f, 13f, 13f, 2.5f); box(6f, 19f, 18f, 4f) }
            OverlayStyle.BUBBLE -> drawCircle(c, 11f * u, androidx.compose.ui.geometry.Offset(15f * u, 15f * u))
        }
    }
}
