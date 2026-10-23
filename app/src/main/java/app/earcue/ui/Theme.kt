package app.earcue.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** One warm accent over neutral surfaces. Dark is "ink" (low glow on stage), light is "paper". */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val amber: Color,
    val amberLight: Color,
    val ember: Color,
    val onAccent: Color,
    val coral: Color,
    val mint: Color,
    val sky: Color,
)

val InkPalette = Palette(
    dark = true,
    bg = Color(0xFF0B0C10),
    surface = Color(0xFF14161C),
    raised = Color(0xFF1B1E26),
    line = Color(0xFF2A2E39),
    text = Color(0xFFF3EFE7),
    dim = Color(0xFF9096A6),
    faint = Color(0xFF5B6070),
    amber = Color(0xFFFFB547),
    amberLight = Color(0xFFFFC567),
    ember = Color(0xFFFF8A3D),
    onAccent = Color(0xFF0B0C10),
    coral = Color(0xFFFF6B5E),
    mint = Color(0xFF66E0B0),
    sky = Color(0xFF8AB4FF),
)

val PaperPalette = Palette(
    dark = false,
    bg = Color(0xFFF6F3EE),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFEDE8E0),
    line = Color(0xFFDCD5CA),
    text = Color(0xFF1B1C20),
    dim = Color(0xFF5D6270),
    faint = Color(0xFF9A9EA9),
    amber = Color(0xFFE48A12),
    amberLight = Color(0xFFFFC567),
    ember = Color(0xFFF07A2C),
    onAccent = Color(0xFF1B1C20),
    coral = Color(0xFFD9443F),
    mint = Color(0xFF1C9467),
    sky = Color(0xFF2F64CC),
)

private val LocalPalette = staticCompositionLocalOf { InkPalette }

/** The active palette. Read it in composition and pass colors into draw lambdas. */
val Ink: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.02.em),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em),
)

@Composable
fun isDarkTheme(theme: String): Boolean = when (theme) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun EarCueTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) InkPalette else PaperPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.amber, onPrimary = p.onAccent, secondary = p.sky, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.raised, onSurfaceVariant = p.dim,
            outline = p.line, error = p.coral,
        )
    } else {
        lightColorScheme(
            primary = p.amber, onPrimary = p.onAccent, secondary = p.sky, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.raised, onSurfaceVariant = p.dim,
            outline = p.line, error = p.coral,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
