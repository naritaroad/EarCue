package app.earcue.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.earcue.Notice
import kotlinx.coroutines.delay

enum class G { NEXT, PREV, PLAY, STOP, LIST, LIBRARY, STAGE, LAYOUT, SETTINGS, BACK, PLUS, TRASH, MIC, CHECK, HAND, PASTE, FILE, SPARK, CLOSE, SEARCH, HISTORY, TAG, CHEVRON, GLOBE, SUN }

/** Hand-drawn line icons on a 24-unit grid, so the whole app shares one stroke weight. */
@Composable
fun Glyph(g: G, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val bg = Ink.bg
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) { drawGlyph(g, tint, bg) }
    }
}

private fun DrawScope.drawGlyph(g: G, c: Color, bg: Color) {
    val line = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(vararg pts: Pair<Float, Float>, close: Boolean = false) = Path().apply {
        moveTo(pts[0].first, pts[0].second)
        pts.drop(1).forEach { lineTo(it.first, it.second) }
        if (close) close()
    }
    when (g) {
        G.NEXT -> {
            drawPath(path(5f to 5f, 15f to 12f, 5f to 19f, close = true), c)
            drawLine(c, Offset(19f, 5f), Offset(19f, 19f), 2.8f, StrokeCap.Round)
        }
        G.PREV -> {
            drawPath(path(19f to 5f, 9f to 12f, 19f to 19f, close = true), c)
            drawLine(c, Offset(5f, 5f), Offset(5f, 19f), 2.8f, StrokeCap.Round)
        }
        G.PLAY -> drawPath(path(7f to 4.5f, 19.5f to 12f, 7f to 19.5f, close = true), c)
        G.STOP -> drawRoundRect(c, Offset(5.5f, 5.5f), Size(13f, 13f), CornerRadius(3f))
        G.LIST -> {
            listOf(6f, 12f, 18f).forEach { y ->
                drawCircle(c, 1.4f, Offset(4.5f, y))
                drawLine(c, Offset(9f, y), Offset(20f, y), 2.1f, StrokeCap.Round)
            }
        }
        G.LIBRARY -> {
            drawRoundRect(c, Offset(4f, 7f), Size(16f, 13f), CornerRadius(2.5f), style = line)
            drawLine(c, Offset(6.5f, 4f), Offset(17.5f, 4f), 2.1f, StrokeCap.Round)
            drawLine(c, Offset(8f, 12f), Offset(16f, 12f), 2.1f, StrokeCap.Round)
        }
        G.STAGE -> {
            drawPath(Path().apply {
                moveTo(15f, 3.5f)
                cubicTo(9f, 4.5f, 6f, 10f, 7.5f, 15f)
                cubicTo(9f, 19.5f, 14.5f, 21.5f, 19.5f, 18.5f)
                cubicTo(14f, 17.5f, 11.5f, 10f, 15f, 3.5f)
                close()
            }, c, style = line)
        }
        G.LAYOUT -> {
            drawRoundRect(c, Offset(3.5f, 3.5f), Size(7f, 9f), CornerRadius(2f), style = line)
            drawRoundRect(c, Offset(13.5f, 3.5f), Size(7f, 17f), CornerRadius(2f), style = line)
            drawRoundRect(c, Offset(3.5f, 15.5f), Size(7f, 5f), CornerRadius(2f), style = line)
        }
        G.SETTINGS -> {
            drawLine(c, Offset(4f, 8f), Offset(20f, 8f), 2.1f, StrokeCap.Round)
            drawLine(c, Offset(4f, 16f), Offset(20f, 16f), 2.1f, StrokeCap.Round)
            drawCircle(bg, 3f, Offset(9f, 8f)); drawCircle(c, 3f, Offset(9f, 8f), style = line)
            drawCircle(bg, 3f, Offset(15f, 16f)); drawCircle(c, 3f, Offset(15f, 16f), style = line)
        }
        G.BACK -> drawPath(path(15f to 5f, 8f to 12f, 15f to 19f), c, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        G.PLUS -> {
            drawLine(c, Offset(12f, 5f), Offset(12f, 19f), 2.2f, StrokeCap.Round)
            drawLine(c, Offset(5f, 12f), Offset(19f, 12f), 2.2f, StrokeCap.Round)
        }
        G.CLOSE -> {
            drawLine(c, Offset(6f, 6f), Offset(18f, 18f), 2.2f, StrokeCap.Round)
            drawLine(c, Offset(18f, 6f), Offset(6f, 18f), 2.2f, StrokeCap.Round)
        }
        G.TRASH -> {
            drawLine(c, Offset(4f, 7f), Offset(20f, 7f), 2.1f, StrokeCap.Round)
            drawPath(path(9.5f to 7f, 10f to 4f, 14f to 4f, 14.5f to 7f), c, style = line)
            drawPath(path(6.5f to 7f, 7.5f to 20f, 16.5f to 20f, 17.5f to 7f), c, style = line)
        }
        G.MIC -> {
            drawRoundRect(c, Offset(9f, 3f), Size(6f, 11f), CornerRadius(3f), style = line)
            drawArc(c, 0f, 180f, false, Offset(5.5f, 6f), Size(13f, 11f), style = line)
            drawLine(c, Offset(12f, 17f), Offset(12f, 21f), 2.1f, StrokeCap.Round)
        }
        G.CHECK -> drawPath(path(5f to 12.5f, 10f to 17.5f, 19f to 6.5f), c, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        G.HAND -> {
            drawPath(Path().apply {
                moveTo(8f, 13f); lineTo(8f, 5.5f)
                moveTo(11f, 12f); lineTo(11f, 3.5f)
                moveTo(14f, 12f); lineTo(14f, 4.5f)
                moveTo(17f, 13f); lineTo(17f, 7f)
                moveTo(17f, 13f)
                cubicTo(17f, 18f, 15f, 21f, 11.5f, 21f)
                cubicTo(8f, 21f, 6.5f, 19f, 4.5f, 15f)
                lineTo(4f, 13f)
                cubicTo(5f, 11.5f, 7f, 12f, 8f, 13.5f)
            }, c, style = line)
        }
        G.PASTE -> {
            drawRoundRect(c, Offset(5f, 5f), Size(14f, 16f), CornerRadius(2.5f), style = line)
            drawRoundRect(c, Offset(9f, 3f), Size(6f, 4f), CornerRadius(1.5f), style = line)
            drawLine(c, Offset(9f, 12f), Offset(15f, 12f), 2.1f, StrokeCap.Round)
            drawLine(c, Offset(9f, 16f), Offset(13f, 16f), 2.1f, StrokeCap.Round)
        }
        G.FILE -> {
            drawPath(path(6f to 3.5f, 14f to 3.5f, 19f to 8.5f, 19f to 20.5f, 6f to 20.5f, close = true), c, style = line)
            drawPath(path(14f to 3.5f, 14f to 8.5f, 19f to 8.5f), c, style = line)
        }
        G.SEARCH -> {
            drawCircle(c, 6.5f, Offset(10.5f, 10.5f), style = line)
            drawLine(c, Offset(15.5f, 15.5f), Offset(20f, 20f), 2.2f, StrokeCap.Round)
        }
        G.HISTORY -> {
            drawArc(c, 200f, 290f, false, Offset(4f, 4f), Size(16f, 16f), style = line)
            drawPath(path(3f to 7f, 4.6f to 10.6f, 8.2f to 9.2f), c, style = line)
            drawPath(path(12f to 8f, 12f to 12f, 15f to 14f), c, style = line)
        }
        G.TAG -> {
            drawPath(path(4f to 4f, 12f to 4f, 20.5f to 12.5f, 12.5f to 20.5f, 4f to 12f, close = true), c, style = line)
            drawCircle(c, 1.6f, Offset(8.5f, 8.5f))
        }
        G.CHEVRON -> drawPath(path(9f to 5f, 16f to 12f, 9f to 19f), c, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        G.GLOBE -> {
            drawCircle(c, 8.5f, Offset(12f, 12f), style = line)
            drawOval(c, Offset(8f, 3.5f), Size(8f, 17f), style = line)
            drawLine(c, Offset(3.5f, 12f), Offset(20.5f, 12f), 2.1f, StrokeCap.Round)
        }
        G.SUN -> {
            drawCircle(c, 4f, Offset(12f, 12f), style = line)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val cx = kotlin.math.cos(a).toFloat()
                val cy = kotlin.math.sin(a).toFloat()
                drawLine(c, Offset(12f + cx * 7f, 12f + cy * 7f), Offset(12f + cx * 9.5f, 12f + cy * 9.5f), 2.1f, StrokeCap.Round)
            }
        }
        G.SPARK -> {
            drawPath(Path().apply {
                moveTo(12f, 3f)
                cubicTo(12.8f, 8.5f, 15.5f, 11.2f, 21f, 12f)
                cubicTo(15.5f, 12.8f, 12.8f, 15.5f, 12f, 21f)
                cubicTo(11.2f, 15.5f, 8.5f, 12.8f, 3f, 12f)
                cubicTo(8.5f, 11.2f, 11.2f, 8.5f, 12f, 3f)
                close()
            }, c)
        }
    }
}

private val helix = PathParser().parsePathString(
    "M38,50 C38,35 48,25 61,25 C73,25 81,34 81,46 C81,55 76,60 72,64 C68,68 67,71 67,76 C67,82 62,86 56,86 C51,86 48,83 47,80"
).toPath()
private val antihelix = PathParser().parsePathString("M51,49 C51,41 56,37 61,37 C67,37 70,42 70,46 C70,51 66,54 62,57").toPath()
private val wave = PathParser().parsePathString("M33,59 A10,10 0 0 1 33,75").toPath()

private val Cream = Color(0xFFF3EFE7)

/** The EarCue mark: an ear catching a single point of sound. Brand colors, the same in both themes. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier, tile: Boolean = true) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 108f
        if (tile) drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF1F232C), Color(0xFF0E1015))),
            cornerRadius = CornerRadius(24f * k),
        )
        scale(k, k, pivot = Offset.Zero) {
            translate(left = 2f) {
                drawPath(helix, Brush.linearGradient(listOf(Color(0xFFFFC567), Color(0xFFFF7A35)), Offset(38f, 25f), Offset(81f, 86f)),
                    style = Stroke(8f, cap = StrokeCap.Round))
                drawPath(antihelix, Cream.copy(alpha = 0.9f), style = Stroke(5.5f, cap = StrokeCap.Round))
                drawCircle(Cream, 4.5f, Offset(25f, 67f))
                drawPath(wave, Cream.copy(alpha = 0.45f), style = Stroke(3.5f, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.BACK, Ink.text) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Ink.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.faint,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.surface)
            .border(1.dp, Ink.line.copy(alpha = 0.6f), RoundedCornerShape(22.dp)),
    ) { content() }
}

@Composable
fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink.text)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ink.dim)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.surface, checkedTrackColor = Ink.amber,
                uncheckedThumbColor = Ink.dim, uncheckedTrackColor = Ink.raised, uncheckedBorderColor = Ink.line,
            ),
        )
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit, onDone: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink.text, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Ink.amber)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = Ink.amber, activeTrackColor = Ink.amber, inactiveTrackColor = Ink.raised),
        )
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(Ink.line.copy(alpha = 0.5f)))
}

@Composable
fun PillButton(text: String, glyph: G?, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (accent) Ink.amber else Ink.raised)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            Glyph(glyph, if (accent) Ink.onAccent else Ink.text, 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (accent) Ink.onAccent else Ink.text, maxLines = 1)
    }
}

@Composable
fun NoticeHost(notice: Notice?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val resolved = notice?.let { stringResource(it.res, *it.args.toTypedArray()) }
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        text = resolved.orEmpty()
        visible = true
        delay(2600)
        visible = false
    }
    AnimatedVisibility(
        visible, modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        Box(
            Modifier.statusBarsPadding().padding(top = 64.dp, start = 24.dp, end = 24.dp).clip(RoundedCornerShape(50))
                .background(Ink.raised).border(1.dp, Ink.line, RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) { Text(text, style = MaterialTheme.typography.labelLarge, color = Ink.text) }
    }
}
