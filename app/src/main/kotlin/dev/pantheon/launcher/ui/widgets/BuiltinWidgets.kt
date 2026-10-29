package dev.pantheon.launcher.ui.widgets

import android.app.AlarmManager
import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AgendaEvent
import dev.pantheon.launcher.data.ItemContent
import dev.pantheon.launcher.data.PhotoRef
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.home.HomeActions
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ── clock ─────────────────────────────────────────────────

@Composable
fun ClockWidget() {
    val context = LocalContext.current
    val colors = Pantheon.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    val minute = now / 60_000
    val is24 = DateFormat.is24HourFormat(context)
    val time = remember(minute, is24) { SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(minute) { SimpleDateFormat("EEE dd MMM yyyy", Locale.getDefault()).format(Date(now)).lowercase() }
    val alarm = remember(minute) {
        context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime?.let {
            SimpleDateFormat(if (is24) "EEE HH:mm" else "EEE hh:mm a", Locale.getDefault()).format(Date(it)).lowercase()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        val big = minOf(maxHeight.value * 0.42f, maxWidth.value * 0.2f).coerceIn(18f, 64f).sp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            TText("$ date", color = colors.textDim, size = 11.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                TText(time, color = colors.accent, size = big, weight = FontWeight.Medium, glow = true)
                TText(
                    ":%02d".format(now / 1_000 % 60) + if (is24) "" else " " + SimpleDateFormat("a", Locale.getDefault()).format(Date(now)).lowercase(),
                    color = colors.textDim,
                    size = 14.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            TText(date, size = 13.sp)
            if (alarm != null) TText("alarm  $alarm", color = colors.textDim, size = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ── sysmon ────────────────────────────────────────────────

@Composable
fun SysmonWidget() {
    val stats = LocalContext.current.container.stats
    val snap by remember(stats) { stats.snapshots }.collectAsStateWithLifecycle(initialValue = null)
    val colors = Pantheon.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Narrow (2 columns): percentages instead of used/total so values don't get cut off.
        val compact = maxWidth < 240.dp
        val valueChars = if (compact) 5 else 11
        val chars = ((maxWidth.value / 7.3f).toInt() - 6 - valueChars).coerceIn(4, 32)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TText("$ htop --lite", color = colors.textDim, size = 11.sp)
            val s = snap
            if (s == null) {
                TText("sampling…", color = colors.textDim, size = 12.sp)
                return@Column
            }
            fun amount(used: Long, total: Long) = if (compact) "${(used * 100 / total.coerceAtLeast(1))}%" else "${gb(used)}/${gb(total)}"
            Meter("MEM", s.memUsed.toFloat() / s.memTotal, amount(s.memUsed, s.memTotal), chars)
            Meter("STO", s.storageUsed.toFloat() / s.storageTotal, amount(s.storageUsed, s.storageTotal), chars)
            Meter(if (s.charging) "CHG" else "BAT", s.battery / 100f, "${s.battery}%", chars, invert = true)
            TText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textDim)) { append("NET ") }
                    withStyle(SpanStyle(color = colors.accent)) { append("↓") }
                    append(rate(s.rxBitsPerSec).let { if (compact) it.removeSuffix("b/s") else it } + if (compact) " " else "  ")
                    withStyle(SpanStyle(color = colors.accent)) { append("↑") }
                    append(rate(s.txBitsPerSec).let { if (compact) it.removeSuffix("b/s") else it })
                },
                size = 12.sp,
            )
            TText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textDim)) { append("UP  ") }
                    append(uptime(s.uptimeMs))
                },
                size = 12.sp,
            )
        }
    }
}

/** `MEM [|||||·····] 4.2G/8.0G`; turns amber when nearly full (or nearly empty for battery). */
@Composable
private fun Meter(label: String, fraction: Float, value: String, chars: Int, invert: Boolean = false) {
    val colors = Pantheon.colors
    val f = fraction.coerceIn(0f, 1f)
    val filled = (f * chars).roundToInt()
    val hot = if (invert) f < 0.15f else f > 0.85f
    TText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.textDim)) { append("$label [") }
            withStyle(SpanStyle(color = if (hot) colors.warn else colors.accent)) { append("|".repeat(filled)) }
            withStyle(SpanStyle(color = colors.border)) { append("·".repeat(chars - filled)) }
            withStyle(SpanStyle(color = colors.textDim)) { append("] ") }
            append(value)
        },
        size = 12.sp,
    )
}

private fun gb(bytes: Long) = String.format(Locale.US, "%.1fG", bytes / 1e9)
private fun rate(bps: Long) = if (bps < 1_000_000) "${bps / 1_000}Kb/s" else String.format(Locale.US, "%.1fMb/s", bps / 1e6)
private fun uptime(ms: Long): String {
    val m = ms / 60_000
    val h = m / 60
    val d = h / 24
    return if (d > 0) "${d}d ${h % 24}h ${m % 60}m" else "${h}h ${m % 60}m"
}

// ── calendar ──────────────────────────────────────────────

@Composable
fun AgendaWidget(actions: HomeActions) {
    val repo = LocalContext.current.container.calendar
    val colors = Pantheon.colors
    val tick = actions.permissionTick
    val hasAccess = remember(tick) { repo.hasAccess() }
    if (!hasAccess) {
        GrantPrompt("calendar", actions::requestCalendarAccess)
        return
    }
    val events by produceState<List<AgendaEvent>?>(null, tick) {
        while (true) {
            value = repo.upcoming()
            delay(5 * 60_000L)
        }
    }
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val timeFmt = remember(is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mma", Locale.getDefault()) }
    val tomorrowStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        TText("$ cal --agenda", color = colors.textDim, size = 11.sp)
        val list = events
        when {
            list == null -> TText("reading…", color = colors.textDim, size = 12.sp)
            list.isEmpty() -> TText("no events · enjoy the silence", color = colors.textDim, size = 12.sp)
            else -> {
                var lastDay = ""
                list.take(8).forEach { e ->
                    val day = if (e.begin >= tomorrowStart) "tomorrow" else "today"
                    if (day != lastDay) {
                        TText("── $day", color = colors.accent, size = 11.sp)
                        lastDay = day
                    }
                    Row {
                        TText(if (e.allDay) "all-day" else timeFmt.format(Date(e.begin)).lowercase(), color = colors.accent, size = 12.sp, modifier = Modifier.width(64.dp))
                        TText(e.title, size = 12.sp)
                    }
                }
            }
        }
    }
}

// ── notes ─────────────────────────────────────────────────

@Composable
fun NotesWidget(itemId: String, actions: HomeActions) {
    val notes = LocalContext.current.container.notes
    val text by remember(itemId) { notes.text(itemId) }.collectAsState()
    val colors = Pantheon.colors
    Column(
        Modifier
            .fillMaxSize()
            .clickable { actions.openNotes(itemId) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        TText("$ cat todo.txt", color = colors.textDim, size = 11.sp)
        Spacer(Modifier.height(4.dp))
        if (text.isBlank()) {
            TText("~ empty · tap to write", color = colors.textDim, size = 12.sp)
        } else {
            TText(text, size = 12.sp, maxLines = 20)
        }
    }
}

// ── photo + image slideshows ──────────────────────────────

@Composable
private fun rememberIsResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/** Current slide index; advances every [intervalSec] while the launcher is in front. Manual steps restart the timer. */
@Composable
private fun rememberSlideIndex(count: Int, intervalSec: Int): MutableIntState {
    val index = remember { mutableIntStateOf(0) }
    val resumed = rememberIsResumed()
    LaunchedEffect(count, intervalSec, resumed, index.intValue) {
        if (count > 1 && resumed) {
            delay(intervalSec * 1_000L)
            index.intValue = (index.intValue + 1) % count
        }
    }
    return index
}

private fun Modifier.swipeSteps(enabled: Boolean, onPrev: () -> Unit, onNext: () -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val threshold = 48.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (total > threshold) onPrev() else if (total < -threshold) onNext()
            },
        ) { change, dx ->
            change.consume()
            total += dx
        }
    }

@Composable
private fun SlideImage(key: Any, fill: Boolean, load: suspend () -> ImageBitmap?) {
    val bitmap by produceState<ImageBitmap?>(null, key) { value = load() }
    Box(Modifier.fillMaxSize()) {
        bitmap?.let {
            Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = if (fill) ContentScale.Crop else ContentScale.Fit)
        }
    }
}

/** `[ ● ○ ○ ○ ]` with `<  >` arrows, like the reference design. */
@Composable
private fun SlideControls(count: Int, index: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val colors = Pantheon.colors
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        val window = 5
        val start = (index - window / 2).coerceIn(0, (count - window).coerceAtLeast(0))
        TText("[ ", color = colors.textDim, size = 12.sp)
        for (i in start until minOf(count, start + window)) {
            TText(if (i == index) "● " else "○ ", color = if (i == index) colors.accent else colors.textDim, size = 12.sp)
        }
        TText("]", color = colors.textDim, size = 12.sp)
        Spacer(Modifier.weight(1f))
        TText("<", color = colors.textDim, size = 14.sp, modifier = Modifier.clickable(onClick = onPrev).padding(horizontal = 10.dp))
        TText(">", color = colors.textDim, size = 14.sp, modifier = Modifier.clickable(onClick = onNext).padding(horizontal = 10.dp))
    }
}

@Composable
private fun ColumnScope.SlideArea(
    count: Int,
    index: MutableIntState,
    fill: Boolean,
    editMode: Boolean,
    image: @Composable (Int) -> Unit,
) {
    val colors = Pantheon.colors
    val prev = { index.intValue = (index.intValue - 1 + count) % count }
    val next = { index.intValue = (index.intValue + 1) % count }
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceHigh)
            .swipeSteps(!editMode && count > 1, prev, next),
    ) {
        Crossfade(targetState = index.intValue.coerceIn(0, (count - 1).coerceAtLeast(0)), animationSpec = tween(700), label = "slide") { i ->
            image(i)
        }
    }
    if (count > 1) SlideControls(count, index.intValue, prev, next)
}

@Composable
fun PhotoWidget(content: ItemContent.Photo, actions: HomeActions) {
    val media = LocalContext.current.container.media
    val colors = Pantheon.colors
    val tick = actions.permissionTick
    val title = "/home/media/" + (content.bucketName?.lowercase()?.replace(' ', '_') ?: "gallery")
    WidgetFrame(title, content.frame) {
        val hasAccess = remember(tick) { media.hasPhotoAccess() }
        if (!hasAccess) {
            GrantPrompt("photos", actions::requestPhotoAccess)
            return@WidgetFrame
        }
        val photos by produceState(emptyList<PhotoRef>(), content.bucketId, tick) { value = media.photos(content.bucketId) }
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TText("no photos here", color = colors.textDim, size = 12.sp) }
            return@WidgetFrame
        }
        val index = rememberSlideIndex(photos.size, content.intervalSec)
        val current = photos[index.intValue.coerceIn(0, photos.lastIndex)]
        val exif by produceState("", current.uri) { value = media.exifLine(current.uri) }
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (content.frame) 8.dp else 0.dp)) {
            val density = LocalDensity.current
            val realPx = with(density) {
                maxOf(maxWidth.roundToPx(), maxHeight.roundToPx()).coerceAtLeast(480)
            }
            Column(Modifier.fillMaxSize()) {
                if (content.frame) {
                    TText(current.name + if (exif.isNotEmpty()) " • $exif" else "", color = colors.textDim, size = 11.sp)
                    Spacer(Modifier.height(6.dp))
                }
                SlideArea(photos.size, index, content.fill, actions.editMode) { i ->
                    val photo = photos[i]
                    SlideImage(photo.uri, content.fill) { media.thumbnail(photo.uri, realPx) }
                }
            }
        }
    }
}

@Composable
fun ImagesWidget(content: ItemContent.Images, pageId: String, itemId: String, actions: HomeActions) {
    val media = LocalContext.current.container.media
    val colors = Pantheon.colors
    val currentActions by rememberUpdatedState(actions)
    WidgetFrame("~/img/slideshow", content.frame) {
        if (content.files.isEmpty()) {
            Box(Modifier.fillMaxSize().clickable { currentActions.pickImagesFor(pageId, itemId) }, contentAlignment = Alignment.Center) {
                TText("[ + add images ]", color = colors.accent, size = 13.sp, glow = true)
            }
            return@WidgetFrame
        }
        val index = rememberSlideIndex(content.files.size, content.intervalSec)
        val px = with(LocalDensity.current) { 420.dp.roundToPx() }
        Column(Modifier.fillMaxSize().padding(if (content.frame) 8.dp else 0.dp)) {
            SlideArea(content.files.size, index, content.fill, actions.editMode) { i ->
                val name = content.files[i]
                SlideImage(name, content.fill) { media.imported(name, px) }
            }
        }
    }
}
