package dev.pantheon.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.delay

/**
 * Camera-cutout calibrated battery charging ring and low-battery alert HUD.
 * Frames the physical punch-hole lens with neon energy sweep and power banners.
 */
@Composable
fun BatteryCutoutRing(
    charging: Boolean,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Pantheon.colors
    var wasCharging by remember { mutableStateOf(charging) }
    var showGlancePill by remember { mutableStateOf(false) }
    val sweepAngle = remember { Animatable(0f) }

    // When charging begins: trigger 360 degree sweep & 3.5s glance pill
    LaunchedEffect(charging) {
        if (charging && !wasCharging) {
            showGlancePill = true
            sweepAngle.snapTo(0f)
            sweepAngle.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            )
            delay(2800L)
            showGlancePill = false
        }
        wasCharging = charging
    }

    val infiniteTransition = rememberInfiniteTransition(label = "charging_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val isLowBattery = batteryLevel <= 15 && !charging

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        // Center camera punch hole ring (30dp diameter, top padding 2dp, center at y = 17dp)
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (charging) {
                // Continuous electric charging ring
                Canvas(Modifier.size(30.dp)) {
                    val strokeW = 1.8.dp.toPx()
                    drawCircle(
                        color = colors.accent.copy(alpha = pulseAlpha),
                        radius = (size.minDimension - strokeW) / 2f,
                        style = Stroke(width = strokeW),
                    )
                    // If sweeping on connect: draw bright white energy head
                    if (sweepAngle.value in 1f..359f) {
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = sweepAngle.value,
                            useCenter = false,
                            style = Stroke(width = strokeW * 1.6f, cap = StrokeCap.Round),
                        )
                    }
                }
                // Central tiny bolt
                TText("⚡", color = colors.accent, size = 9.sp, glow = true)
            } else if (isLowBattery) {
                // Critical low battery warning halo
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.82f))
                        .border(1.5.dp, colors.warn.copy(alpha = pulseAlpha), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    TText("!", color = colors.warn, size = 11.sp, glow = true)
                }
            }
        }

        // Dropdown HUD banner on power connect / low battery tap
        AnimatedVisibility(
            visible = showGlancePill,
            enter = slideInVertically { -it } + fadeIn(tween(180)),
            exit = slideOutVertically { -it } + fadeOut(tween(180)),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.surface.copy(alpha = 0.95f))
                    .border(1.dp, colors.accent.copy(alpha = 0.85f), RoundedCornerShape(15.dp))
                    .clickable { showGlancePill = false }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TText("⚡", color = colors.accent, size = 11.sp, glow = true)
                Spacer(Modifier.width(6.dp))
                TText(
                    "chg +${batteryLevel}% · ${if (batteryLevel >= 80) "trickle" else "fast_chg"}",
                    color = colors.text,
                    size = 11.sp,
                )
            }
        }
    }
}
