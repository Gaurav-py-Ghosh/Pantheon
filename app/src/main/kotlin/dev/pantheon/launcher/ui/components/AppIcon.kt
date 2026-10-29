package dev.pantheon.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.ui.theme.Pantheon

@Composable
fun AppIconImage(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val icons = LocalContext.current.container.icons
    val tags = LocalContext.current.container.tags
    // Keyed on the app and icon version so changes invalidate immediately.
    var icon by remember(entry.key, icons.version) { mutableStateOf(icons.peek(entry.key)) }
    LaunchedEffect(entry.key, icons.version) {
        if (icon == null) icon = icons.load(entry)
    }
    val tint = Pantheon.colors.text
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val ic = icon
        if (ic != null) {
            if (ic.bitmap != null) {
                val filter = remember(ic.monochrome, tint) {
                    if (ic.monochrome) ColorFilter.tint(tint) else luminanceTint(tint)
                }
                Image(ic.bitmap, contentDescription = entry.label, modifier = Modifier.fillMaxSize(), colorFilter = filter)
            } else {
                val tag = remember(entry) { tags.tagFor(entry, emptyMap()) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Pantheon.colors.border, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    TText("[$tag]", color = Pantheon.colors.accent, size = (size.value * 0.28f).sp, glow = true)
                }
            }
        }
    }
}

/** Full-color icons without a monochrome layer: map luminance onto the tint so they match the set. */
private fun luminanceTint(c: Color): ColorFilter {
    val k = 1.15f
    val (r, g, b) = Triple(c.red * k, c.green * k, c.blue * k)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                .299f * r, .587f * r, .114f * r, 0f, 0f,
                .299f * g, .587f * g, .114f * g, 0f, 0f,
                .299f * b, .587f * b, .114f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}
