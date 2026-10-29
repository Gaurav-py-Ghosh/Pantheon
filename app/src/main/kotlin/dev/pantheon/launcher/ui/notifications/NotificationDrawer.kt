package dev.pantheon.launcher.ui.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.notifications.NotificationEntry
import dev.pantheon.launcher.notifications.PantheonNotificationListener
import dev.pantheon.launcher.ui.Actions
import dev.pantheon.launcher.ui.NotificationDrawerController
import dev.pantheon.launcher.ui.components.BlinkingCursor
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.Pantheon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationDrawer(
    drawer: NotificationDrawerController,
    distancePx: Float,
    notifications: List<NotificationEntry>,
    apps: List<AppEntry>,
    tags: Map<String, String>,
    onOpenSysinfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = Pantheon.colors
    val c = context.container
    val byPkg = remember(apps) { apps.groupBy { it.packageName } }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val nested = remember(drawer, distancePx) { closeOnOverscroll(drawer, distancePx) }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.96f))
            .systemBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TText("$", color = colors.textDim, size = 15.sp)
                    TText(" dmesg -w", color = colors.accent, size = 15.sp, glow = true)
                    TText(
                        " [${notifications.size} events]",
                        color = colors.textDim,
                        size = 12.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                TText(
                    "[ :q ]",
                    color = colors.accent,
                    size = 13.sp,
                    glow = true,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { drawer.close() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Quick Actions CLI Bar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickPill(label = "wifi") {
                    Actions.start(context, Intent(Settings.Panel.ACTION_WIFI))
                }
                QuickPill(label = "bt") {
                    Actions.start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                val torchOn = c.torch.on
                QuickPill(label = if (torchOn) "torch:on" else "torch:off", active = torchOn) {
                    c.torch.toggle()
                }
                QuickPill(label = "sysinfo") {
                    drawer.close()
                    onOpenSysinfo()
                }
                if (notifications.isNotEmpty()) {
                    QuickPill(label = "clear *", warn = true) {
                        PantheonNotificationListener.dismissAll()
                    }
                }
            }

            // Notifications List or Empty State
            if (notifications.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .terminalBox(colors, radius = 8.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TText("── [KERNEL DMESG: BUFFER IDLE] ──", color = colors.accent, size = 12.sp, glow = true)
                        Spacer(Modifier.height(4.dp))
                        TText("0 unhandled interrupts pending", color = colors.textDim, size = 12.sp)
                        TText("system event queue: clean", color = colors.textDim, size = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TText("~ waiting for events", color = colors.textDim, size = 12.sp)
                            BlinkingCursor(color = colors.accent, size = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(nested),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notifications, key = { it.key }) { entry ->
                        NotificationCard(
                            entry = entry,
                            tag = tags[entry.packageName].orEmpty().ifEmpty {
                                byPkg[entry.packageName]?.firstOrNull()?.let { tags[it.key] }.orEmpty()
                            },
                            appName = byPkg[entry.packageName]?.firstOrNull()?.label?.lowercase()
                                ?: entry.packageName.substringAfterLast('.'),
                            timeStr = timeFmt.format(Date(entry.postTime)),
                            onOpen = {
                                drawer.close()
                                PantheonNotificationListener.open(entry)
                            },
                            onDismiss = {
                                PantheonNotificationListener.dismiss(entry.key)
                            },
                        )
                    }
                }
            }

            // Fallback to Native System Shade
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        drawer.close()
                        Actions.expandNotifications(context)
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TText(
                    "[ $ open /system/shade › ]",
                    color = colors.textDim,
                    size = 11.sp,
                )
            }
        }
    }
}

/**
 * The list swallows vertical drags, so without this the drawer could only be closed with
 * [ :q ] or back once it had notifications. A swipe up past the end of the list now pulls the
 * drawer closed, mirroring how the app drawer closes from the top of its list.
 */
private fun closeOnOverscroll(drawer: NotificationDrawerController, distancePx: Float) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (drawer.progress < 1f && available.y > 0f) {
            drawer.dragBy(available.y, distancePx)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y < 0f && source == NestedScrollSource.UserInput) {
            drawer.dragBy(available.y, distancePx)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drawer.progress < 1f) {
            drawer.settle(available.y, distancePx)
            return available
        }
        return Velocity.Zero
    }
}

@Composable
private fun QuickPill(
    label: String,
    active: Boolean = false,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = Pantheon.colors
    val textColor = when {
        warn -> colors.warn
        active -> colors.accent
        else -> colors.text
    }
    TText(
        "[$label]",
        color = textColor,
        size = 11.sp,
        glow = active,
        modifier = Modifier
            .terminalBox(colors, radius = 6.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun NotificationCard(
    entry: NotificationEntry,
    tag: String,
    appName: String,
    timeStr: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Pantheon.colors
    Column(
        Modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 8.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Tag + App + Timestamp
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tag.isNotEmpty()) {
                    TText("[$tag] ", color = colors.accent, size = 12.sp, glow = true)
                }
                TText(appName, color = colors.text, size = 12.sp)
            }
            TText(timeStr, color = colors.textDim, size = 11.sp)
        }

        // Title
        if (entry.title.isNotBlank()) {
            TText(entry.title, color = colors.accent, size = 13.sp)
        }

        // Body text
        if (entry.text.isNotBlank()) {
            TText(entry.text, color = colors.textDim, size = 12.sp, maxLines = 3)
        }

        // Actions
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.isClearable) {
                TText(
                    "[dismiss]",
                    color = colors.textDim,
                    size = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(1.dp))
            }
            if (entry.contentIntent != null) {
                TText(
                    "[ open › ]",
                    color = colors.accent,
                    size = 11.sp,
                    glow = true,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
