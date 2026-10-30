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
import dev.pantheon.launcher.data.SysSnapshot
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val SPRITE_W = 13
private const val SPRITE_H = 8

private enum class Mood { WALK, SIT, SLEEP, HUNGRY, HYPER }

private val FORTUNES = listOf(
    "$ sudo rm -rf /",
    "[all systems nominal]",
    "[uptime: 42h]",
    "404 nap not found",
    "$ git push --force",
    "[status: 200 OK]",
    "ping 127.0.0.1",
    "<3",
)

private val HUNGRY_BUBBLES = listOf(
    "*feed me*",
    "[low_pwr]",
    "*hungry*",
    "[bat: low]",
    "<:(",
)

private val HYPER_BUBBLES = listOf(
    "⚡ 100%",
    "*zoom*",
    "[hyper!]",
    "*overclock*",
    "[full power!]",
)

private data class PetSprite(
    val head: List<String>,
    val headBlink: List<String>,
    val walkA: List<String>,
    val walkB: List<String>,
    val sit: List<String>,
    val sleep: List<String>,
    val sound: String,
)

private val CAT_SPRITE = PetSprite(
    head = listOf(
        ".#...#.......",
        ".##.##.......",
        ".#####......#",
        ".#o#o#.....#.",
        ".#####....#..",
    ),
    headBlink = listOf(
        ".#...#.......",
        ".##.##.......",
        ".#####......#",
        ".#####.....#.",
        ".#####....#..",
    ),
    walkA = listOf("..##########.", "..#########..", "..#.#...#.#.."),
    walkB = listOf("..##########.", "..#########..", "...#.#...#.#."),
    sit = listOf("..########...", "..########.##", "..##....####."),
    sleep = listOf(".############", ".############", "............."),
    sound = "<3",
)

private val DOG_SPRITE = PetSprite(
    head = listOf(
        ".##..........",
        ".####.......#",
        ".##o#......#.",
        ".####.....#..",
        "..#####..#...",
    ),
    headBlink = listOf(
        ".##..........",
        ".####.......#",
        ".####......#.",
        ".####.....#..",
        "..#####..#...",
    ),
    walkA = listOf("..##########.", "..#########..", "..#.#...#.#.."),
    walkB = listOf("..##########.", "..#########..", "...#.#...#.#."),
    sit = listOf("..########...", "..########.##", "..##....####."),
    sleep = listOf(".############", ".############", "............."),
    sound = "[woof!]",
)

private val DINO_SPRITE = PetSprite(
    head = listOf(
        "....######...",
        "....#o####...",
        "....######...",
        "....####.....",
        "...########..",
    ),
    headBlink = listOf(
        "....######...",
        "....######...",
        "....######...",
        "....####.....",
        "...########..",
    ),
    walkA = listOf("..#########..", "...#...#.....", "...##..##...."),
    walkB = listOf("..#########..", "....#...#....", "...##..##...."),
    sit = listOf("..#########..", "..##########.", "....####....."),
    sleep = listOf(".############", ".############", "............."),
    sound = "*rawr*",
)

private val DUCK_SPRITE = PetSprite(
    head = listOf(
        "......####...",
        "....###o##...",
        "...######....",
        ".....#####...",
        "....#######..",
    ),
    headBlink = listOf(
        "......####...",
        "....######...",
        "...######....",
        ".....#####...",
        "....#######..",
    ),
    walkA = listOf("..#########..", "...#######...", "....#...#...."),
    walkB = listOf("..#########..", "...#######...", ".....#...#..."),
    sit = listOf("..#########..", "..#########..", "....#####...."),
    sleep = listOf("..#########..", ".............", "............."),
    sound = "[quack]",
)

private val GHOST_SPRITE = PetSprite(
    head = listOf(
        "....#####....",
        "...#######...",
        "..##o###o##..",
        "..#########..",
        "..#########..",
    ),
    headBlink = listOf(
        "....#####....",
        "...#######...",
        "..#########..",
        "..#########..",
        "..#########..",
    ),
    walkA = listOf("..#########..", "..#########..", "...#.#.#.#..."),
    walkB = listOf("..#########..", "..#########..", "...#.#.#.#..."),
    sit = listOf("..#########..", "..#########..", "....#####...."),
    sleep = listOf("....#####....", "...#######...", "............."),
    sound = "[boo!]",
)

private val CRAB_SPRITE = PetSprite(
    head = listOf(
        ".#.........#.",
        ".##...#...##.",
        ".###.###.###.",
        "..##o###o##..",
        "...#######...",
    ),
    headBlink = listOf(
        ".#.........#.",
        ".##...#...##.",
        ".###.###.###.",
        "..#########..",
        "...#######...",
    ),
    walkA = listOf("..#########..", ".###.###.###.", ".#.........#."),
    walkB = listOf("..#########..", ".###.###.###.", "..#.......#.."),
    sit = listOf("..#########..", "..#########..", "...#######..."),
    sleep = listOf("...#######...", "..#########..", "............."),
    sound = "*snip*",
)

@Composable
private fun rememberIsResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/**
 * Camera-aware cyber pixel pet that wanders, acknowledges the punch-hole cutout,
 * perches against it, peeks, and responds to taps and long-presses to cycle pets.
 */
@Composable
fun TerminalPet(
    modifier: Modifier = Modifier,
    petType: String = "cat",
    sysSnapshot: SysSnapshot? = null,
    onCycle: (() -> Unit)? = null,
) {
    val colors = Pantheon.colors
    val density = LocalDensity.current
    val resumed = rememberIsResumed()

    val sprite = when (petType.lowercase()) {
        "dog" -> DOG_SPRITE
        "dino" -> DINO_SPRITE
        "duck" -> DUCK_SPRITE
        "ghost" -> GHOST_SPRITE
        "crab" -> CRAB_SPRITE
        else -> CAT_SPRITE
    }

    BoxWithConstraints(modifier) {
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val pixel = (boxH * 0.7f / SPRITE_H).toInt().coerceIn(2, with(density) { 4.dp.roundToPx() }).toFloat()
        val spriteW = SPRITE_W * pixel
        val spriteH = SPRITE_H * pixel
        val maxX = (boxW - spriteW).coerceAtLeast(0f)

        var x by remember { mutableFloatStateOf(0f) }
        var facingLeft by remember { mutableStateOf(true) }
        var mood by remember { mutableStateOf(Mood.WALK) }
        var frame by remember { mutableIntStateOf(0) }
        var blink by remember { mutableStateOf(false) }
        var hopTicks by remember { mutableIntStateOf(0) }
        var heartTicks by remember { mutableIntStateOf(0) }
        var customBubble by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(resumed, maxX, pixel, petType, sysSnapshot) {
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
                val isCharging = sysSnapshot?.charging == true
                val isLowBattery = (sysSnapshot?.battery ?: 100) <= 20 && !isCharging
                val isHighNet = (sysSnapshot?.netBitsPerSec ?: 0L) > 4_000_000L

                // System-aware mood shifts
                if (isLowBattery && mood != Mood.HUNGRY && heartTicks <= 0) {
                    mood = Mood.HUNGRY
                    moodTicks = 120
                } else if (isCharging && mood != Mood.HYPER && heartTicks <= 0) {
                    mood = Mood.HYPER
                    moodTicks = 120
                } else if (night && mood != Mood.SLEEP && heartTicks <= 0) {
                    mood = Mood.SLEEP
                }

                when (mood) {
                    Mood.WALK -> {
                        x += if (facingLeft) -pixel else pixel

                        if (x <= 0f) { x = 0f; facingLeft = false }
                        if (x >= maxX) { x = maxX; facingLeft = true }

                        if (Random.nextInt(100) < 3) {
                            mood = Mood.SIT
                            moodTicks = Random.nextInt(20, 60)
                        }
                        // Random fortunes in normal mode
                        if (frame % 110 == 0 && Random.nextInt(100) < 35 && heartTicks <= 0) {
                            customBubble = FORTUNES.random()
                            heartTicks = 22
                        }
                    }
                    Mood.HUNGRY -> {
                        // Walks sluggishly when phone battery is <= 20%
                        if (frame % 2 == 0) x += if (facingLeft) -pixel else pixel
                        if (x <= 0f) { x = 0f; facingLeft = false }
                        if (x >= maxX) { x = maxX; facingLeft = true }
                        blink = frame % 18 in 0..2
                        if (frame % 45 in 0..14 && heartTicks <= 0) {
                            customBubble = HUNGRY_BUBBLES.random()
                        }
                    }
                    Mood.HYPER -> {
                        // High speed + frequent hops when charging or fast net
                        x += if (facingLeft) -pixel * 1.5f else pixel * 1.5f
                        if (x <= 0f) { x = 0f; facingLeft = false }
                        if (x >= maxX) { x = maxX; facingLeft = true }
                        if (frame % 8 == 0) hopTicks = 5
                        if (frame % 40 in 0..12 && heartTicks <= 0) {
                            customBubble = HYPER_BUBBLES.random()
                        }
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
                        if (moodTicks <= 0 && !night) {
                            mood = Mood.SIT
                            moodTicks = 20
                            blink = false
                        }
                    }
                }
            }
        }

        val hop = if (hopTicks > 0) sin((6 - hopTicks) / 6f * Math.PI).toFloat() * spriteH * 0.7f else 0f
        val baseY = boxH - spriteH

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(maxX, onCycle) {
                    detectTapGestures(
                        onTap = { tap ->
                            if (tap.x in (x - pixel * 5)..(x + spriteW + pixel * 5)) {
                                blink = false
                                when (mood) {
                                    Mood.HUNGRY -> {
                                        customBubble = "*nom nom!*"
                                        hopTicks = 8
                                        heartTicks = 24
                                        mood = Mood.WALK
                                    }
                                    Mood.HYPER -> {
                                        customBubble = "*supercharged!*"
                                        hopTicks = 9
                                        heartTicks = 24
                                    }
                                    else -> {
                                        customBubble = sprite.sound
                                        hopTicks = 7
                                        heartTicks = 18
                                    }
                                }
                            }
                        },
                        onLongPress = { tap ->
                            if (tap.x in (x - pixel * 5)..(x + spriteW + pixel * 5)) {
                                onCycle?.invoke()
                                hopTicks = 8
                                heartTicks = 16
                                customBubble = "*switch!*"
                            }
                        },
                    )
                },
        ) {
            val head = if (blink) sprite.headBlink else sprite.head
            val body = when (mood) {
                Mood.WALK, Mood.HYPER -> if (frame % 2 == 0) sprite.walkA else sprite.walkB
                Mood.HUNGRY -> if (frame % 2 == 0) sprite.sit else sprite.walkA
                Mood.SIT -> sprite.sit
                Mood.SLEEP -> sprite.sleep
            }
            val rows = if (mood == Mood.SLEEP) List(2) { "............." } + sprite.head.take(3) + body else head + body
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
            heartTicks > 0 -> customBubble ?: sprite.sound
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
