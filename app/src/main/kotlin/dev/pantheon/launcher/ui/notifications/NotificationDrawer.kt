package dev.pantheon.launcher.ui.notifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.notifications.NotificationAction
import dev.pantheon.launcher.notifications.NotificationEntry
import dev.pantheon.launcher.notifications.PantheonNotificationListener
import dev.pantheon.launcher.ui.Actions
import dev.pantheon.launcher.ui.NotificationDrawerController
import dev.pantheon.launcher.ui.components.BlinkingCursor
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.JetBrainsMono
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

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
    val colors = Pantheon.colors
    val byPkg = remember(apps) { apps.groupBy { it.packageName } }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val nested = remember(drawer, distancePx) { closeOnOverscroll(drawer, distancePx) }
    val context = LocalContext.current

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.96f))
            .systemBarsPadding()
            .displayCutoutPadding()
            .imePadding()
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

            QuickControls(hasNotifications = notifications.isNotEmpty(), onSysinfo = {
                drawer.close()
                onOpenSysinfo()
            })
            BrightnessBar()

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
                        val app = byPkg[entry.packageName]?.firstOrNull()
                        NotificationCard(
                            entry = entry,
                            tag = app?.let { tags[it.key] }.orEmpty(),
                            appName = app?.label?.lowercase() ?: entry.packageName.substringAfterLast('.'),
                            timeStr = timeFmt.format(Date(entry.postTime)),
                            onOpen = {
                                drawer.close()
                                PantheonNotificationListener.open(entry)
                            },
                            onDismiss = { PantheonNotificationListener.dismiss(entry.key) },
                            modifier = Modifier.animateItem(),
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
                TText("[ $ open /system/shade › ]", color = colors.textDim, size = 11.sp)
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

/**
 * Android doesn't let apps flip Wi-Fi, mobile data or Bluetooth directly, so those open the
 * system's quick panels. DND and the torch toggle in place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickControls(hasNotifications: Boolean, onSysinfo: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val dnd by PantheonNotificationListener.dnd.collectAsState()
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuickPill("wifi") { Actions.start(context, Intent(Settings.Panel.ACTION_WIFI)) }
        QuickPill("net") { Actions.start(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
        QuickPill("bt") { Actions.start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        QuickPill("vol") { Actions.start(context, Intent(Settings.Panel.ACTION_VOLUME)) }
        QuickPill(if (dnd) "dnd:on" else "dnd:off", active = dnd) {
            if (!PantheonNotificationListener.setDnd(!dnd)) {
                Actions.start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        val torchOn = c.torch.on
        QuickPill(if (torchOn) "torch:on" else "torch:off", active = torchOn) { c.torch.toggle() }
        QuickPill("sysinfo", onClick = onSysinfo)
        if (hasNotifications) QuickPill("clear *", warn = true) { PantheonNotificationListener.dismissAll() }
    }
}

/** `bri [||||||||······] 62% [auto]`: drag or tap along the bar. */
@Composable
private fun BrightnessBar() {
    val colors = Pantheon.colors
    val brightness = LocalContext.current.container.brightness
    var canWrite by remember { mutableStateOf(brightness.canWrite()) }
    var level by remember { mutableFloatStateOf(brightness.level()) }
    var auto by remember { mutableStateOf(brightness.isAuto()) }
    // Picks up the grant (and changes made elsewhere) when coming back from Android settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canWrite = brightness.canWrite()
        level = brightness.level()
        auto = brightness.isAuto()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 6.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText("bri", color = colors.textDim, size = 11.sp)
        Spacer(Modifier.width(8.dp))
        if (!canWrite) {
            TText(
                "[ grant: modify system settings › ]",
                color = colors.accent,
                size = 11.sp,
                glow = true,
                modifier = Modifier
                    .weight(1f)
                    .clickable { brightness.requestWriteAccess() }
                    .padding(vertical = 4.dp),
            )
            return@Row
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(Unit) {
                    var lastWritten = -1
                    fun setAt(x: Float) {
                        level = (x / size.width).coerceIn(0f, 1f)
                        auto = false
                        val step = (level * 254).roundToInt()
                        if (step != lastWritten) {
                            lastWritten = step
                            brightness.setLevel(level)
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        setAt(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            setAt(change.position.x)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val chars = (maxWidth.value / 7.3f).toInt().coerceIn(8, 48) - 2
            val filled = (level * chars).roundToInt()
            TText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textDim)) { append("[") }
                    withStyle(SpanStyle(color = colors.accent)) { append("|".repeat(filled)) }
                    withStyle(SpanStyle(color = colors.border)) { append("·".repeat(chars - filled)) }
                    withStyle(SpanStyle(color = colors.textDim)) { append("]") }
                },
                size = 12.sp,
            )
        }
        Spacer(Modifier.width(6.dp))
        TText("${(level * 100).roundToInt()}%".padStart(4), size = 11.sp)
        Spacer(Modifier.width(4.dp))
        TText(
            if (auto) "[auto]" else "[man]",
            color = if (auto) colors.accent else colors.textDim,
            size = 11.sp,
            glow = auto,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    auto = !auto
                    brightness.setAuto(auto)
                }
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
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

/**
 * Swipe sideways to dismiss. The gesture sits on an untransformed outer box and the card moves
 * inside it, so finger deltas stay true while the card slides.
 */
@Composable
private fun NotificationCard(
    entry: NotificationEntry,
    tag: String,
    appName: String,
    timeStr: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Pantheon.colors
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var replyTo by remember { mutableStateOf<NotificationAction?>(null) }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .then(
                if (!entry.isClearable) {
                    Modifier
                } else {
                    Modifier.pointerInput(entry.key) {
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().x
                                val fling = abs(v) > 1_200f && sign(v) == sign(offsetX)
                                val far = abs(offsetX) > width * 0.35f
                                scope.launch {
                                    if (far || fling) {
                                        animate(offsetX, sign(offsetX) * width, animationSpec = tween(150)) { x, _ -> offsetX = x }
                                        onDismiss()
                                    } else {
                                        animate(offsetX, 0f, animationSpec = spring(dampingRatio = 0.8f)) { x, _ -> offsetX = x }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { animate(offsetX, 0f) { x, _ -> offsetX = x } }
                            },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            offsetX += dx
                        }
                    }
                },
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offsetX
                    alpha = 1f - (abs(offsetX) / width).coerceIn(0f, 1f) * 0.8f
                }
                .terminalBox(colors, radius = 8.dp)
                .clickable(enabled = entry.contentIntent != null, onClick = onOpen)
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
                    if (tag.isNotEmpty()) TText("[$tag] ", color = colors.accent, size = 12.sp, glow = true)
                    TText(appName, color = colors.text, size = 12.sp)
                }
                TText(timeStr, color = colors.textDim, size = 11.sp)
            }

            if (entry.title.isNotBlank()) TText(entry.title, color = colors.accent, size = 13.sp)
            if (entry.text.isNotBlank()) TText(entry.text, color = colors.textDim, size = 12.sp, maxLines = 3)

            replyTo?.let { action ->
                ReplyField(
                    hint = action.remoteInput?.label?.toString()?.lowercase() ?: "reply",
                    onSend = { text ->
                        if (PantheonNotificationListener.runAction(action, text)) replyTo = null
                    },
                    onCancel = { replyTo = null },
                )
            }

            // Notification buttons, then dismiss
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                entry.actions.forEach { action ->
                    CardButton(action.title.lowercase(), accent = true) {
                        if (action.remoteInput != null) replyTo = action else PantheonNotificationListener.runAction(action)
                    }
                }
                if (entry.isClearable) CardButton("dismiss", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun CardButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val colors = Pantheon.colors
    TText(
        "[$label]",
        color = if (accent) colors.accent else colors.textDim,
        size = 11.sp,
        glow = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** Inline reply: `> message_  [send] [x]`, sent back through the notification's RemoteInput. */
@Composable
private fun ReplyField(hint: String, onSend: (String) -> Unit, onCancel: () -> Unit) {
    val colors = Pantheon.colors
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .terminalBox(colors, radius = 6.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText("> ", color = colors.accent, size = 13.sp, glow = true)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend(text) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) TText(hint, color = colors.textDim, size = 13.sp)
                    inner()
                }
            },
        )
        CardButton("send", accent = true) { if (text.isNotBlank()) onSend(text) }
        CardButton("x", onClick = onCancel)
    }
}
