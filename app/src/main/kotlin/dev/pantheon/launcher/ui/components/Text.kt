package dev.pantheon.launcher.ui.components

import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.ui.theme.JetBrainsMono
import dev.pantheon.launcher.ui.theme.Pantheon
import dev.pantheon.launcher.ui.theme.PantheonColors

@Composable
private fun monoStyle(color: Color, size: TextUnit, weight: FontWeight, glow: Boolean, align: TextAlign): TextStyle {
    val resolved = if (color.isSpecified) color else Pantheon.colors.text
    return TextStyle(
        color = resolved,
        fontSize = size,
        fontFamily = JetBrainsMono,
        fontWeight = weight,
        textAlign = align,
        shadow = if (glow && Pantheon.glow) Shadow(resolved.copy(alpha = 0.55f), Offset.Zero, 14f) else null,
    )
}

/** Monospace text, the only text style in the launcher. */
@Composable
fun TText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    glow: Boolean = false,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = monoStyle(color, size, weight, glow, align),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun TText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: TextUnit = 13.sp,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = monoStyle(color, size, FontWeight.Normal, glow = false, align = TextAlign.Start),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
fun BlinkingCursor(color: Color, size: TextUnit) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1060
                1f at 0
                1f at 529
                0f at 530
                0f at 1060
            },
        ),
        label = "blink",
    )
    TText("_", Modifier.graphicsLayer { this.alpha = alpha }, color = color, size = size, glow = true)
}

fun Modifier.terminalBox(colors: PantheonColors, radius: Dp = 10.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(colors.surface).border(1.dp, colors.border, shape)
}
