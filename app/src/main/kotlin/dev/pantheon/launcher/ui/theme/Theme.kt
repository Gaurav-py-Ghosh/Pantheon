package dev.pantheon.launcher.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.ColorUtils
import dev.pantheon.launcher.R
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.data.ThemeMode

@Immutable
data class PantheonColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val warn: Color,
    val error: Color,
    val isDark: Boolean,
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

object Accents {
    data class Preset(val id: String, val name: String, val dark: Color, val light: Color)

    val presets = listOf(
        Preset("green", "phosphor", Color(0xFF7EE2A8), Color(0xFF1E6B44)),
        Preset("blue", "blue", Color(0xFF6CB6FF), Color(0xFF1D5FA6)),
        Preset("neon", "neon yellow", Color(0xFFE6FF4F), Color(0xFF666700)),
        Preset("amber", "amber", Color(0xFFFFB454), Color(0xFF8A5200)),
        Preset("cyan", "cyan", Color(0xFF5EEAD4), Color(0xFF0E6E66)),
        Preset("red", "red", Color(0xFFFF6B6B), Color(0xFFA12A2A)),
        Preset("white", "mono", Color(0xFFE8E8E8), Color(0xFF202020)),
    )

    /** [spec] is a preset id or a `#RRGGBB` hex string. Light mode gets a darker, readable variant. */
    fun resolve(spec: String, dark: Boolean): Color {
        presets.firstOrNull { it.id == spec }?.let { return if (dark) it.dark else it.light }
        val custom = runCatching { Color(android.graphics.Color.parseColor(spec)) }.getOrNull()
            ?: return resolve("green", dark)
        if (dark) return custom
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(custom.toArgb(), hsl)
        hsl[2] = hsl[2].coerceAtMost(0.32f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

fun darkPalette(accent: Color, wallpaper: Boolean = false) = PantheonColors(
    background = if (wallpaper) Color(0xCC0D1013) else Color(0xFF0D1013),
    surface = if (wallpaper) Color(0xD8151A1E) else Color(0xFF151A1E),
    surfaceHigh = if (wallpaper) Color(0xD81C2227) else Color(0xFF1C2227),
    border = Color(0xFF2A3138),
    text = Color(0xFFD8DDE1),
    textDim = Color(0xFF7C858D),
    accent = accent,
    warn = Color(0xFFFFB454),
    error = Color(0xFFFF6B6B),
    isDark = true,
)

fun lightPalette(accent: Color, wallpaper: Boolean = false) = PantheonColors(
    background = if (wallpaper) Color(0xDDF4F3EE) else Color(0xFFF4F3EE),
    surface = if (wallpaper) Color(0xE8EBEAE3) else Color(0xFFEBEAE3),
    surfaceHigh = if (wallpaper) Color(0xE8E2E1D9) else Color(0xFFE2E1D9),
    border = Color(0xFFCFCDC3),
    text = Color(0xFF1B1D20),
    textDim = Color(0xFF6A6E72),
    accent = accent,
    warn = Color(0xFF9A5B00),
    error = Color(0xFFB02A2A),
    isDark = false,
)

private val LocalColors = compositionLocalOf { darkPalette(Accents.presets.first().dark) }
private val LocalGlow = compositionLocalOf { false }

object Pantheon {
    val colors: PantheonColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    /** Soft glow on accent text; dark mode only. */
    val glow: Boolean
        @Composable @ReadOnlyComposable get() = LocalGlow.current
}

@Composable
fun PantheonTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val wallpaper = settings.backgroundMode == dev.pantheon.launcher.data.BackgroundMode.WALLPAPER
    val target = remember(dark, settings.accent, wallpaper) {
        val accent = Accents.resolve(settings.accent, dark)
        if (dark) darkPalette(accent, wallpaper) else lightPalette(accent, wallpaper)
    }
    val spec = tween<Color>(durationMillis = 350)
    val colors = PantheonColors(
        background = animateColorAsState(target.background, spec, label = "bg").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        surfaceHigh = animateColorAsState(target.surfaceHigh, spec, label = "surfaceHigh").value,
        border = animateColorAsState(target.border, spec, label = "border").value,
        text = animateColorAsState(target.text, spec, label = "text").value,
        textDim = animateColorAsState(target.textDim, spec, label = "textDim").value,
        accent = animateColorAsState(target.accent, spec, label = "accent").value,
        warn = target.warn,
        error = target.error,
        isDark = dark,
    )
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalGlow provides (settings.glow && dark),
        content = content,
    )
}
