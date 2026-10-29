package dev.pantheon.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pantheon.launcher.container
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.Pantheon
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusLine(modifier: Modifier = Modifier) {
    val stats = LocalContext.current.container.stats
    val snap by remember(stats) { stats.snapshots }.collectAsStateWithLifecycle(initialValue = null)
    val colors = Pantheon.colors

    val text = remember(snap, colors) {
        buildAnnotatedString {
            val s = snap
            val dim = SpanStyle(color = colors.textDim)
            val value = SpanStyle(color = colors.text)
            fun field(name: String, v: String) {
                withStyle(dim) { append(" · $name ") }
                withStyle(value) { append(v) }
            }
            withStyle(dim) { append("[SYS: ") }
            when {
                s == null -> withStyle(dim) { append("BOOT") }
                s.ok -> withStyle(SpanStyle(color = colors.accent)) { append("OK") }
                else -> withStyle(SpanStyle(color = colors.warn)) { append("WARN") }
            }
            withStyle(dim) { append("]") }
            if (s != null) {
                field("MEM", "${gb(s.memUsed)}/${gb(s.memTotal)}")
                field("NET", net(s.netBitsPerSec))
                field(if (s.charging) "CHG" else "BAT", "${s.battery}%")
                field("UP", uptime(s.uptimeMs))
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 8.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        TText(text, Modifier.basicMarquee(iterations = Int.MAX_VALUE), size = 11.sp)
    }
}

private fun gb(bytes: Long) = String.format(Locale.US, "%.1fG", bytes / 1e9)

private fun net(bps: Long) =
    if (bps < 1_000_000) "${bps / 1_000}Kb/s" else String.format(Locale.US, "%.1fMb/s", bps / 1e6)

private fun uptime(ms: Long): String {
    val m = ms / 60_000
    val h = m / 60
    val d = h / 24
    return when {
        d > 0 -> "${d}d ${h % 24}h"
        h > 0 -> "${h}h ${m % 60}m"
        else -> "${m}m"
    }
}
