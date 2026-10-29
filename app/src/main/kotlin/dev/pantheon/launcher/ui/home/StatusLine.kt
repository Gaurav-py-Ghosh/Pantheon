package dev.pantheon.launcher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pantheon.launcher.container
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.delay
import java.util.Locale

/** Field name and its widest value, e.g. RAM `99.9/99.9G`. */
private val FIELDS = listOf("RAM" to 10, "NET" to 7, "BAT" to 4, "UP" to 7)
private const val SIZE_SP = 11f

/**
 * `[SYS:OK] · RAM 2.1/4.1G · NET 0K/s · BAT 87% · UP 2h45m`, trimmed to what fits on one line.
 * Fields are chosen by their worst-case width, so they never appear and disappear as numbers
 * change, and nothing scrolls. The typewriter plays once each time home comes back into view.
 */
@Composable
fun StatusLine(
    modifier: Modifier = Modifier,
    typewriter: Boolean = true,
    onClick: () -> Unit = {},
) {
    val stats = LocalContext.current.container.stats
    val snap by remember(stats) { stats.snapshots }.collectAsStateWithLifecycle(initialValue = null)
    val colors = Pantheon.colors
    val density = LocalDensity.current

    var typeRun by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { typeRun++ }
    var visibleChars by remember { mutableIntStateOf(Int.MAX_VALUE) }
    LaunchedEffect(typeRun, typewriter) {
        if (!typewriter) return@LaunchedEffect
        for (i in 0..60) {
            visibleChars = i
            delay(7)
        }
        visibleChars = Int.MAX_VALUE
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 8.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        // JetBrains Mono advances 0.6 em per character.
        val charWidth = with(density) { SIZE_SP.sp.toDp() }.value * 0.6f
        val maxChars = (maxWidth.value / charWidth).toInt()
        val shown = remember(maxChars) {
            var used = "[SYS:WARN]".length
            FIELDS.filter { (name, width) ->
                val cost = 3 + name.length + 1 + width
                (used + cost <= maxChars).also { if (it) used += cost }
            }.map { it.first }
        }

        val text = remember(snap, colors, shown) {
            buildAnnotatedString {
                val s = snap
                val dim = SpanStyle(color = colors.textDim)
                val value = SpanStyle(color = colors.text)
                withStyle(dim) { append("[SYS:") }
                when {
                    s == null -> withStyle(dim) { append("BOOT") }
                    s.ok -> withStyle(SpanStyle(color = colors.accent)) { append("OK") }
                    else -> withStyle(SpanStyle(color = colors.warn)) { append("WARN") }
                }
                withStyle(dim) { append("]") }
                if (s != null) {
                    shown.forEach { name ->
                        val (label, v) = when (name) {
                            "RAM" -> "RAM" to "${gb(s.memUsed)}/${gb(s.memTotal)}G"
                            "NET" -> "NET" to net(s.netBitsPerSec)
                            "BAT" -> (if (s.charging) "CHG" else "BAT") to "${s.battery}%"
                            else -> "UP" to uptime(s.uptimeMs)
                        }
                        withStyle(dim) { append(" · $label ") }
                        withStyle(value) { append(v) }
                    }
                }
            }
        }
        TText(if (visibleChars >= text.length) text else text.subSequence(0, visibleChars), size = SIZE_SP.sp)
    }
}

private fun gb(bytes: Long) = String.format(Locale.US, "%.1f", bytes / 1e9)

private fun net(bps: Long) =
    if (bps < 1_000_000) "${bps / 1_000}K/s" else String.format(Locale.US, "%.1fM/s", bps / 1e6)

private fun uptime(ms: Long): String {
    val m = ms / 60_000
    val h = m / 60
    val d = h / 24
    return when {
        d > 0 -> "${d}d${h % 24}h"
        h > 0 -> "${h}h${m % 60}m"
        else -> "${m}m"
    }
}
