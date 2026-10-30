package dev.pantheon.launcher.ui.components

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.pantheon.launcher.ui.lock.DitherFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.data.MusicTrack
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.delay
import kotlin.math.sin

enum class IslandMode {
    CIRCLE,
    PILL,
    EXPANDED;

    companion object {
        val DOCK = PILL
    }
}

@Composable
fun MusicIsland(
    track: MusicTrack,
    modifier: Modifier = Modifier,
    initialMode: IslandMode = IslandMode.PILL,
    isAtCutout: Boolean = true,
    onModeChanged: ((IslandMode) -> Unit)? = null,
    onOpenApp: (() -> Unit)? = null,
) {
    var mode by remember(track.title) { mutableStateOf(initialMode) }

    LaunchedEffect(mode) {
        onModeChanged?.invoke(mode)
    }

    // Glance delay: in PILL or EXPANDED (when started initially on track change),
    // collapse to a sleek CIRCLE around camera cutout after glance delay
    LaunchedEffect(track.title, track.isPlaying, isAtCutout) {
        if (isAtCutout && initialMode == IslandMode.EXPANDED) {
            delay(3500L)
            mode = IslandMode.CIRCLE
        }
    }

    LaunchedEffect(mode, track.title, track.isPlaying, isAtCutout) {
        if (isAtCutout && mode == IslandMode.PILL) {
            delay(2000L)
            mode = IslandMode.CIRCLE
        }
    }

    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            if (targetState == IslandMode.EXPANDED) {
                (slideInVertically { -it / 2 } + fadeIn(tween(200))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(150)))
            } else {
                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
            }
        },
        label = "music_island_mode",
    ) { currentMode ->
        when (currentMode) {
            IslandMode.CIRCLE -> {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .then(if (isAtCutout) Modifier.padding(top = 2.dp) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    CameraCirclePill(
                        track = track,
                        onClick = { mode = IslandMode.EXPANDED },
                    )
                }
            }
            IslandMode.PILL -> {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .then(if (isAtCutout) Modifier.padding(top = 4.dp) else Modifier)
                        .pointerInput(track) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 15f) mode = IslandMode.EXPANDED
                            }
                        }
                        .pointerInput(track) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 30f) track.onPrevious()
                                else if (dragAmount < -30f) track.onNext()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DockedMusicPill(
                        track = track,
                        onExpand = { mode = IslandMode.EXPANDED },
                        isAtCutout = isAtCutout,
                        onOpenApp = onOpenApp,
                    )
                }
            }
            IslandMode.EXPANDED -> {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .then(if (isAtCutout) Modifier.statusBarsPadding().padding(top = 10.dp) else Modifier)
                        .pointerInput(track) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -15f) mode = if (isAtCutout) IslandMode.CIRCLE else IslandMode.PILL
                            }
                        }
                        .pointerInput(track) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 35f) track.onPrevious()
                                else if (dragAmount < -35f) track.onNext()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ExpandedMusicCard(
                        track = track,
                        onDock = { mode = if (isAtCutout) IslandMode.CIRCLE else IslandMode.PILL },
                        onOpenApp = onOpenApp,
                    )
                }
            }
        }
    }
}

/**
 * A sleek 40dp circular ring that directly hugs the camera punch hole.
 * Takes 0 extra status bar space and pulses subtly when music is playing.
 */
@Composable
fun CameraCirclePill(
    track: MusicTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Pantheon.colors
    val infiniteTransition = rememberInfiniteTransition(label = "circle_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Box(
        modifier = modifier
            .size(44.dp, 38.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.85f))
                .border(
                    1.5.dp,
                    if (track.isPlaying) colors.accent.copy(alpha = pulseAlpha) else colors.border,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (track.isPlaying) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = pulseAlpha)),
                )
            }
        }
    }
}

@Composable
fun DockedMusicPill(
    track: MusicTrack,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isAtCutout: Boolean = true,
    onOpenApp: (() -> Unit)? = null,
) {
    val colors = Pantheon.colors
    if (isAtCutout) {
        // Option B: Perfectly symmetric wings (weight 1f) so the 36dp gap is locked in exact center
        Row(
            modifier = modifier
                .width(200.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface.copy(alpha = 0.96f))
                .border(1.dp, colors.accent.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .clickable(onClick = onExpand)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left wing: ends right at the left of the camera gap
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                TText("♫", color = colors.accent, size = 11.sp, glow = true)
                Spacer(Modifier.width(4.dp))
                TText(
                    track.title.lowercase(),
                    color = colors.text,
                    size = 11.sp,
                    maxLines = 1,
                )
            }

            // Exact camera cutout gap centered on screen (36dp)
            Spacer(modifier = Modifier.width(36.dp))

            // Right wing: starts right at the right of the camera gap
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                AsciiVisualizer(isPlaying = track.isPlaying, barCount = 4, modifier = Modifier.height(12.dp))
                Spacer(Modifier.width(6.dp))
                TText(
                    if (track.isPlaying) "⏵" else "⏸",
                    color = if (track.isPlaying) colors.accent else colors.textDim,
                    size = 11.sp,
                    glow = track.isPlaying,
                )
            }
        }
    } else {
        // Standard compact pill (e.g. for LockScreen)
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = 0.94f))
                .border(1.dp, colors.accent.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                .clickable(onClick = onExpand)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TText("♫", color = colors.accent, size = 12.sp, glow = true)
            TText(
                "${track.title} ── ${track.artist}".lowercase(),
                color = colors.text,
                size = 12.sp,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 175.dp),
            )
            AsciiVisualizer(isPlaying = track.isPlaying, barCount = 5)
            TText(
                if (track.isPlaying) "⏵" else "⏸",
                color = if (track.isPlaying) colors.accent else colors.textDim,
                size = 12.sp,
                glow = track.isPlaying,
            )
        }
    }
}

@Composable
fun ExpandedMusicCard(
    track: MusicTrack,
    onDock: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenApp: (() -> Unit)? = null,
) {
    val colors = Pantheon.colors
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 12.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Top status header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TText("$ mpc now_playing", color = colors.accent, size = 11.sp, glow = true)
                if (track.isPlaying) BlinkingCursor(colors.accent, 11.sp)
            }
            val appLabel = track.packageName?.substringAfterLast('.')?.lowercase() ?: "audio"
            TText(
                "[♫ $appLabel]",
                color = colors.textDim,
                size = 10.sp,
                modifier = Modifier.clickable {
                    if (onOpenApp != null) onOpenApp()
                    else track.packageName?.let { pkg ->
                        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                            context.startActivity(it)
                        }
                    }
                },
            )
        }

        var artDithered by remember(track.albumArt, colors.accent) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(track.albumArt, colors.accent) {
            val raw = track.albumArt
            if (raw != null) {
                artDithered = DitherFilter.ditherBitmap(raw, colors.accent.toArgb(), targetWidth = 140)
            } else {
                artDithered = null
            }
        }

        // Track title & artist + 1-bit dithered album cover
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            artDithered?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = "album art",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable {
                        track.packageName?.let { pkg ->
                            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                                context.startActivity(it)
                            }
                        }
                    },
            ) {
                TText(
                    track.title.lowercase(),
                    color = colors.text,
                    size = 14.sp,
                    weight = FontWeight.Medium,
                    maxLines = 1,
                )
                TText(
                    track.artist.lowercase() + if (track.album.isNotBlank()) " ── ${track.album.lowercase()}" else "",
                    color = colors.textDim,
                    size = 12.sp,
                    maxLines = 1,
                )
            }
        }

        // ASCII Progress bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TText(
                formatProgress(track.positionMs, track.durationMs, length = 18),
                color = colors.accent,
                size = 11.sp,
            )
            TText(
                "${formatTime(track.positionMs)} / ${formatTime(track.durationMs)}",
                color = colors.textDim,
                size = 10.sp,
            )
        }

        // Bottom Controls: Visualizer, [|<] [||] [>|], and swipe up hint
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiVisualizer(isPlaying = track.isPlaying, barCount = 7)

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IslandButton("[ |< ]", onClick = track.onPrevious)
                IslandButton(
                    if (track.isPlaying) "[ || ]" else "[  > ]",
                    accent = true,
                    onClick = track.onPlayPause,
                )
                IslandButton("[ >| ]", onClick = track.onNext)
            }

            TText(
                "[▲ dock]",
                color = colors.accent,
                size = 11.sp,
                glow = true,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onDock)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun IslandButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val colors = Pantheon.colors
    TText(
        label,
        color = if (accent) colors.accent else colors.text,
        size = 12.sp,
        glow = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/**
 * Dancing hacker ASCII audio visualizer bars:
 * ░ ▂ ▃ ▄ ▅ ▆ ▇ █
 */
@Composable
fun AsciiVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 6,
) {
    val colors = Pantheon.colors
    val transition = rememberInfiniteTransition(label = "ascii_vis")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val chars = listOf(" ", "▂", "▃", "▄", "▅", "▆", "▇", "█")

    val barText = buildString {
        for (i in 0 until barCount) {
            if (!isPlaying) {
                append(" ")
            } else {
                val wave = (sin(phase + i * 0.9) + 1.0) / 2.0 // 0.0 .. 1.0
                val idx = (wave * (chars.size - 1)).toInt().coerceIn(0, chars.size - 1)
                append(chars[idx])
            }
        }
    }

    TText(
        barText,
        color = if (isPlaying) colors.accent else colors.textDim,
        size = 12.sp,
        glow = isPlaying,
        modifier = modifier,
    )
}

private fun formatProgress(pos: Long, dur: Long, length: Int = 16): String {
    if (dur <= 0) return "[${"-".repeat(length)}]"
    val frac = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    val filled = (frac * length).toInt().coerceIn(0, length)
    val head = if (filled < length) ">" else "="
    val empty = length - filled - (if (filled < length) 1 else 0)
    return "[" + "=".repeat(filled.coerceAtLeast(0)) + head + "-".repeat(empty.coerceAtLeast(0)) + "]"
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val m = sec / 60
    val s = sec % 60
    return String.format("%02d:%02d", m, s)
}
