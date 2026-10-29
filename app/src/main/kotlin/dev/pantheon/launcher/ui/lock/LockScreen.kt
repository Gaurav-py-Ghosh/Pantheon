package dev.pantheon.launcher.ui.lock

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.ui.components.BlinkingCursor
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LockScreen(
    settings: Settings,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = Pantheon.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val c = context.container
    val notifications by c.notificationItems.collectAsStateWithLifecycle()
    val stats by c.stats.snapshots.collectAsStateWithLifecycle(null)

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(500)
        }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE dd MMM yyyy", Locale.getDefault()) }

    var ditherBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var granted by remember { mutableStateOf(false) }
    val glitchAlpha = remember { Animatable(1f) }

    LaunchedEffect(colors.accent, settings.lockArt) {
        ditherBitmap = if (settings.lockArt == "none") {
            null
        } else {
            DitherFilter.loadDithered(context, settings.lockArt, colors.accent.toArgb(), targetWidth = 1080)
        }
    }

    fun triggerUnlock() {
        if (granted) return
        scope.launch {
            granted = true
            glitchAlpha.animateTo(0f, tween(250))
            onUnlock()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .displayCutoutPadding()
            .padding(top = 10.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Swipe up unlocks
                    if (dragAmount < -25f) triggerUnlock()
                }
            }
            .drawWithContent {
                drawContent()
                if (settings.scanlines) {
                    val step = 4.dp.toPx()
                    val lineColor = Color.Black.copy(alpha = 0.22f)
                    val strokeW = 1.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeW,
                        )
                        y += step
                    }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer { alpha = glitchAlpha.value },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Terminal Header
            Column(
                Modifier
                    .fillMaxWidth()
                    .terminalBox(colors, radius = 6.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TText("┌── [", color = colors.border, size = 11.sp)
                        TText("PANTHEON SECURE TERMINAL", color = colors.accent, size = 11.sp, glow = true)
                        TText(" // KERNEL KEYGUARD]", color = colors.border, size = 11.sp)
                    }
                    TText("[LOCKED]", color = colors.warn, size = 11.sp, glow = true)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TText(
                        "HOST: ${Build.DEVICE.lowercase()} (${Build.HARDWARE})",
                        color = colors.textDim,
                        size = 10.sp,
                    )
                    TText("STATUS: ENCRYPTED", color = colors.textDim, size = 10.sp)
                }
            }

            // Center Area: Clock + Creation of Adam Art
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Clock + Date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TText("$ date +%T: ", color = colors.textDim, size = 13.sp)
                    TText(
                        timeFmt.format(Date(currentTime)),
                        color = colors.accent,
                        size = 32.sp,
                        glow = true,
                    )
                    BlinkingCursor(color = colors.accent, size = 28.sp)
                }
                TText(
                    dateFmt.format(Date(currentTime)).lowercase(),
                    color = colors.textDim,
                    size = 13.sp,
                )

                // Live System Status
                val uptimeHours = SystemClock.elapsedRealtime() / 3_600_000L
                val uptimeMins = (SystemClock.elapsedRealtime() % 3_600_000L) / 60_000L
                val batLevel = stats?.battery ?: 100
                TText(
                    "[SYS:OK]  ·  BAT: $batLevel%  ·  UPTIME: ${uptimeHours}h ${uptimeMins}m",
                    color = colors.text,
                    size = 11.sp,
                    modifier = Modifier
                        .terminalBox(colors, radius = 6.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )

                Spacer(Modifier.height(4.dp))

                // Dithered Creation of Adam Fresco Banner
                ditherBitmap?.let { bmp ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .terminalBox(colors, radius = 8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "lock screen art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Soft overlay text
                        TText(
                            if (settings.lockArt == "adam") "~ every project begins with a touch" else "~/img/lock",
                            color = colors.textDim.copy(alpha = 0.7f),
                            size = 10.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp),
                        )
                    }
                }

                // Event interrupt line
                if (notifications.isNotEmpty()) {
                    TText(
                        "dmesg: ${notifications.size} pending unhandled events",
                        color = colors.accent,
                        size = 11.sp,
                        glow = true,
                    )
                } else {
                    TText("dmesg: kernel buffer clean (0 pending)", color = colors.textDim, size = 11.sp)
                }
            }

            // Bottom CLI Auth Prompt & Unlock Buttons
            Column(
                Modifier
                    .fillMaxWidth()
                    .terminalBox(colors, radius = 6.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (granted) {
                    TText(
                        "── [ACCESS GRANTED // TERMINAL UNLOCKED] ──",
                        color = colors.accent,
                        size = 13.sp,
                        glow = true,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TText("login: ", color = colors.textDim, size = 12.sp)
                            TText("root", color = colors.accent, size = 12.sp, glow = true)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TText("password: ", color = colors.textDim, size = 12.sp)
                            TText("••••••••", color = colors.textDim, size = 12.sp)
                            BlinkingCursor(color = colors.accent, size = 12.sp)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TText(
                            "[ $ auth / biometric › ]",
                            color = colors.accent,
                            size = 12.sp,
                            glow = true,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { triggerUnlock() }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                        TText(
                            "[ :unlock ]",
                            color = colors.accent,
                            size = 12.sp,
                            glow = true,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { triggerUnlock() }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
