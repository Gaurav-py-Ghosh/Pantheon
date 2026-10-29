package dev.pantheon.launcher.ui.sysinfo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.Pantheon
import java.net.NetworkInterface
import java.util.Locale

@Composable
fun SysinfoPanel(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = Pantheon.colors
    val scroll = rememberScrollState()
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    BackHandler(onBack = onDismiss)

    val battery = remember(context) { getBatteryInfo(context) }
    val network = remember(context) { getNetworkInfo(context) }
    val storage = remember { getStorageInfo() }
    val uptime = remember { formatUptime(SystemClock.elapsedRealtime()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.96f))
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TText("$", color = colors.textDim, size = 15.sp)
                    TText(" sysinfo", color = colors.accent, size = 15.sp, glow = true)
                }
                TText(
                    "[close]",
                    color = colors.textDim,
                    size = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Section: Device
            InfoSection(title = "DEVICE") {
                InfoRow("model", "${Build.MANUFACTURER.uppercase(Locale.US)} ${Build.MODEL}")
                InfoRow("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoRow("build", Build.DISPLAY)
                InfoRow("hardware", "${Build.BOARD} / ${Build.HARDWARE}")
                InfoRow("uptime", uptime)
            }

            // Section: Battery
            InfoSection(title = "BATTERY") {
                InfoRow("level", "${battery.level}% (${battery.plugged})")
                InfoRow("temp", String.format(Locale.US, "%.1f°C", battery.temperature))
                InfoRow("health", battery.health)
                InfoRow("tech", battery.technology)
            }

            // Section: Storage
            InfoSection(title = "STORAGE") {
                val pct = if (storage.total > 0) ((storage.used * 100) / storage.total).toInt() else 0
                InfoRow("internal", "${formatBytes(storage.used)} / ${formatBytes(storage.total)} ($pct%)")
                InfoRow("available", formatBytes(storage.free))
            }

            // Section: Network
            InfoSection(title = "NETWORK") {
                InfoRow("type", network.type)
                InfoRow("vpn", if (network.vpn) "ACTIVE" else "INACTIVE")
                InfoRow("ipv4", network.ipv4)
                InfoRow("ipv6", network.ipv6)
            }

            // Section: Display
            InfoSection(title = "DISPLAY") {
                val widthPx = (config.screenWidthDp * density.density).toInt()
                val heightPx = (config.screenHeightDp * density.density).toInt()
                InfoRow("resolution", "${widthPx}x${heightPx} px")
                InfoRow("density", "${config.densityDpi} dpi (${String.format(Locale.US, "%.1fx", density.density)})")
                InfoRow("font scale", String.format(Locale.US, "%.2fx", config.fontScale))
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    val colors = Pantheon.colors
    Column(
        Modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 8.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TText("── $title", color = colors.accent, size = 12.sp, glow = true)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = Pantheon.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TText(label, color = colors.textDim, size = 12.sp)
        TText(value, color = colors.text, size = 12.sp)
    }
}

private data class BatteryStats(
    val level: Int,
    val temperature: Double,
    val health: String,
    val plugged: String,
    val technology: String,
)

private fun getBatteryInfo(context: Context): BatteryStats {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
    val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
    val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
        BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE"
        BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
        else -> "UNKNOWN"
    }
    val plugged = when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
        else -> "BATTERY"
    }
    val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
    return BatteryStats(pct, temp, health, plugged, tech)
}

private data class NetworkStats(
    val type: String,
    val vpn: Boolean,
    val ipv4: String,
    val ipv6: String,
)

private fun getNetworkInfo(context: Context): NetworkStats {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
    val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    val type = when {
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WI-FI"
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
        vpn -> "VPN"
        else -> "DISCONNECTED"
    }
    val ips = mutableListOf<String>()
    runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
        for (iface in interfaces) {
            if (iface.isLoopback || !iface.isUp) continue
            for (addr in iface.inetAddresses) {
                if (!addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: continue
                    ips += host.substringBefore('%')
                }
            }
        }
    }
    val ipv4 = ips.firstOrNull { it.contains('.') } ?: "none"
    val ipv6 = ips.firstOrNull { it.contains(':') } ?: "none"
    return NetworkStats(type, vpn, ipv4, ipv6)
}

private data class StorageStats(
    val total: Long,
    val used: Long,
    val free: Long,
)

private fun getStorageInfo(): StorageStats {
    val stat = StatFs(Environment.getDataDirectory().path)
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = (total - free).coerceAtLeast(0)
    return StorageStats(total, used, free)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1e9
    return String.format(Locale.US, "%.1f GB", gb)
}

private fun formatUptime(ms: Long): String {
    val s = ms / 1_000
    val m = s / 60
    val h = m / 60
    val d = h / 24
    return when {
        d > 0 -> "${d}d ${h % 24}h ${m % 60}m"
        h > 0 -> "${h}h ${m % 60}m"
        else -> "${m}m ${s % 60}s"
    }
}
