package dev.pantheon.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * A pixel cat drawn in the accent color. Sprites face left; `#` is a lit pixel, `o` an eye.
 * Rows 0-4 are the head, 5-7 the body and legs, so frames only swap the bottom rows.
 */
private val HEAD = listOf(
    ".#...#.......",
    ".##.##.......",
    ".#####......#",
    ".#o#o#.....#.",
    ".#####....#..",
)
private val HEAD_BLINK = HEAD.map { it.replace('o', '#') }
private val WALK_A = listOf("..##########.", "..#########..", "..#.#...#.#..")
private val WALK_B = listOf("..##########.", "..#########..", "...#.#...#.#.")
private val SIT = listOf("..########...", "..########.##", "..##....####.")
private val SLEEP = listOf(".############", ".############", ".............")
private const val SPRITE_W = 13
private const val SPRITE_H = 8

private enum class Mood { WALK, SIT, SLEEP }

@Composable
private fun rememberIsResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/**
 * Wanders left and right inside its box, sits and blinks now and then, sleeps at night, and hops
 * with a `<3` when tapped. Animates in pixel steps at 10 fps, and only while home is in front.
 */
@Composable
fun TerminalPet(modifier: Modifier = Modifier) {
    val colors = Pantheon.colors
    val density = LocalDensity.current
    val resumed = rememberIsResumed()

    BoxWithConstraints(modifier) {
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        // Fit eight sprite rows in ~70% of the height, in whole device pixels so it stays crisp.
        val pixel = (boxH * 0.7f / SPRITE_H).toInt().coerceIn(2, with(density) { 4.dp.roundToPx() }).toFloat()
        val spriteW = SPRITE_W * pixel
        val spriteH = SPRITE_H * pixel
        val maxX = (boxW - spriteW).coerceAtLeast(0f)

        var x by remember { mutableFloatStateOf(Random.nextFloat() * maxX) }
        var facingLeft by remember { mutableStateOf(Random.nextBoolean()) }
        var mood by remember { mutableStateOf(Mood.WALK) }
        var frame by remember { mutableIntStateOf(0) }
        var blink by remember { mutableStateOf(false) }
        var hopTicks by remember { mutableIntStateOf(0) }
        var heartTicks by remember { mutableIntStateOf(0) }

        LaunchedEffect(resumed, maxX, pixel) {
            if (!resumed) return@LaunchedEffect
            var moodTicks = 0
            while (true) {
                delay(100)
                frame++
                moodTicks--
                if (hopTicks > 0) hopTicks--
                if (heartTicks > 0) heartTicks--
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val night = hour >= 23 || hour < 7
                when (mood) {
                    Mood.WALK -> {
                        x += if (facingLeft) -pixel else pixel
                        if (x <= 0f) { x = 0f; facingLeft = false }
                        if (x >= maxX) { x = maxX; facingLeft = true }
                        if (Random.nextInt(100) < 2) { mood = Mood.SIT; moodTicks = Random.nextInt(20, 60) }
                    }
                    Mood.SIT -> {
                        blink = frame % 30 in 0..1
                        if (moodTicks <= 0) {
                            mood = if (night || Random.nextInt(10) == 0) Mood.SLEEP else Mood.WALK
                            moodTicks = Random.nextInt(80, 200)
                            if (Random.nextBoolean()) facingLeft = !facingLeft
                        }
                    }
                    Mood.SLEEP -> {
                        blink = true
                        if (moodTicks <= 0 && !night) { mood = Mood.SIT; moodTicks = 20; blink = false }
                    }
                }
            }
        }

        val hop = if (hopTicks > 0) sin((6 - hopTicks) / 6f * Math.PI).toFloat() * spriteH * 0.6f else 0f
        val baseY = boxH - spriteH

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(maxX) {
                    detectTapGestures { tap ->
                        if (tap.x in x - pixel * 4..x + spriteW + pixel * 4) {
                            mood = Mood.WALK
                            blink = false
                            hopTicks = 6
                            heartTicks = 12
                        }
                    }
                },
        ) {
            val head = if (blink) HEAD_BLINK else HEAD
            val body = when (mood) {
                Mood.WALK -> if (frame % 2 == 0) WALK_A else WALK_B
                Mood.SIT -> SIT
                Mood.SLEEP -> SLEEP
            }
            // Asleep it curls into a loaf: head tucked down two rows, eyes hidden.
            val rows = if (mood == Mood.SLEEP) List(2) { "............." } + HEAD.take(3) + body else head + body
            val px = Size(pixel, pixel)
            rows.forEachIndexed { r, line ->
                line.forEachIndexed { c, ch ->
                    if (ch == '#') {
                        val col = if (facingLeft) c else SPRITE_W - 1 - c
                        drawRect(colors.accent, Offset(x + col * pixel, baseY - hop + r * pixel), px)
                    }
                }
            }
        }

        val bubble = when {
            heartTicks > 0 -> "<3"
            mood == Mood.SLEEP -> if (frame / 8 % 2 == 0) "z" else "zZ"
            else -> null
        }
        if (bubble != null) {
            val bx = (x + if (facingLeft) -pixel * 3 else spriteW).roundToInt()
            val by = (baseY - hop - pixel * 4).roundToInt().coerceAtLeast(0)
            Box(Modifier.offset { IntOffset(bx, by) }) {
                TText(bubble, color = colors.accent, size = (pixel * 3.2f / density.density).coerceIn(8f, 12f).sp, glow = true)
            }
        }
    }
}
