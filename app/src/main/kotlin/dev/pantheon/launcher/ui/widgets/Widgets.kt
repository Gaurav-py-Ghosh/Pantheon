package dev.pantheon.launcher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.data.DragSource
import dev.pantheon.launcher.data.HomeItem
import dev.pantheon.launcher.data.ItemContent
import dev.pantheon.launcher.data.hasFrame
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.TerminalPet
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.edit.CoordsHolder
import dev.pantheon.launcher.ui.edit.DragController
import dev.pantheon.launcher.ui.edit.rememberItemDrag
import dev.pantheon.launcher.ui.home.HomeActions
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.withTimeoutOrNull

enum class WidgetKind(val id: String, val desc: String, val w: Int, val h: Int) {
    PHOTO("photo", "album slideshow with exif", 4, 3),
    IMAGE("image", "your own images, slideshow", 2, 2),
    CLOCK("clock", "time, date, next alarm", 4, 2),
    SYSMON("sysmon", "mem, storage, battery, net", 4, 2),
    CALENDAR("calendar", "today and tomorrow", 4, 2),
    NOTES("notes", "quick scratchpad", 2, 2),
    PET("pet", "a pixel cat that lives here", 2, 1),
    ;

    fun create(fill: Boolean): ItemContent = when (this) {
        PHOTO -> ItemContent.Photo(fill = fill)
        IMAGE -> ItemContent.Images(fill = fill)
        CLOCK -> ItemContent.Clock()
        SYSMON -> ItemContent.Sysmon()
        CALENDAR -> ItemContent.Agenda()
        NOTES -> ItemContent.Notes()
        PET -> ItemContent.Pet()
    }
}

fun ItemContent.label(): String = when (this) {
    is ItemContent.App -> "app"
    is ItemContent.Photo -> "photo"
    is ItemContent.Images -> "image"
    is ItemContent.Clock -> "clock"
    is ItemContent.Sysmon -> "sysmon"
    is ItemContent.Agenda -> "calendar"
    is ItemContent.Notes -> "notes"
    is ItemContent.Pet -> "pet"
    is ItemContent.AppWidget -> "widget"
}

/** Optional terminal frame: thin border plus a path-style title bar. */
@Composable
fun WidgetFrame(title: String?, frame: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = Pantheon.colors
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxSize().then(if (frame) Modifier.terminalBox(colors, 10.dp) else Modifier.clip(shape))) {
        if (frame && title != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                TText(title, size = 13.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
        content()
    }
}

@Composable
fun GrantPrompt(what: String, onGrant: () -> Unit) {
    val colors = Pantheon.colors
    Box(Modifier.fillMaxSize().clickable(onClick = onGrant), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TText("permission denied: $what", color = colors.textDim, size = 11.sp)
            TText("[ grant access ]", color = colors.accent, size = 13.sp, glow = true, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * Long-press that works on top of clickable children: it watches the Initial pass, and once it
 * fires it consumes the rest of the gesture so the children see a cancel instead of a click.
 */
fun Modifier.longPressOverChildren(enabled: () -> Boolean, onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!enabled()) return@awaitEachGesture
            val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                    if (!change.pressed || change.isConsumed) return@withTimeoutOrNull true
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            if (cancelled != null) return@awaitEachGesture
            onLongPress()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
            }
        }
    }

/** A widget on the grid: long-press menu, drag after long-press (or always in edit mode), frame and content. */
@Composable
fun WidgetItem(
    item: HomeItem,
    pageId: String,
    width: Dp,
    height: Dp,
    actions: HomeActions,
    drag: DragController,
) {
    val source = DragSource.Home(pageId, item.id)
    val coords = remember { CoordsHolder() }
    val haptic = LocalHapticFeedback.current
    val currentActions by rememberUpdatedState(actions)
    val currentItem by rememberUpdatedState(item)
    val dimmed = drag.active?.source == source
    val label = item.content.label()
    val dragModifier = rememberItemDrag(
        isArmed = { actions.editMode || drag.armed == source },
        coordinates = { coords.value },
        onStart = { finger, grabbedAt -> coords.bounds()?.let { actions.widgetDragStart(item, label, source, it, finger, grabbedAt) } },
        onMove = actions::dragMove,
        onEnd = actions::dragEnd,
        onCancel = actions::dragCancel,
        onRelease = drag::disarm,
    )
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .onGloballyPositioned { coords.value = it }
            .then(dragModifier)
            .longPressOverChildren(enabled = { !currentActions.editMode }) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coords.bounds()?.let { b ->
                    drag.arm(source)
                    currentActions.widgetLongPress(currentItem, pageId, b)
                }
            }
            .graphicsLayer { alpha = if (dimmed) 0.25f else 1f },
    ) {
        val content = item.content
        val frame = content.hasFrame
        when (content) {
            is ItemContent.Photo -> PhotoWidget(content, actions)
            is ItemContent.Images -> ImagesWidget(content, pageId, item.id, actions)
            is ItemContent.Clock -> WidgetFrame("~/clock", frame) { ClockWidget() }
            is ItemContent.Sysmon -> WidgetFrame("~/sysmon", frame) { SysmonWidget() }
            is ItemContent.Agenda -> WidgetFrame("~/calendar", frame) { AgendaWidget(actions) }
            is ItemContent.Notes -> WidgetFrame("~/notes/todo.txt", frame) { NotesWidget(item.id, actions) }
            is ItemContent.Pet -> WidgetFrame("~/pet", frame) { TerminalPet(Modifier.fillMaxSize().padding(6.dp)) }
            is ItemContent.AppWidget -> HostedWidget(content, width - 8.dp, height - 8.dp)
            is ItemContent.App -> Unit
        }
        // Edit mode: the widget is just a block to move, so swallow taps meant for its content.
        if (actions.editMode) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
    }
}
