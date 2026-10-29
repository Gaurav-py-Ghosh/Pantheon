package dev.pantheon.launcher.ui.edit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.DragSource
import dev.pantheon.launcher.data.DropTarget
import kotlin.math.max

data class ActiveDrag(val source: DragSource, val entry: AppEntry, val touchOffset: Offset, val size: Size) {
    /** Center of the dragged ghost, which decides the drop cell. */
    fun center(finger: Offset) = finger - touchOffset + Offset(size.width / 2f, size.height / 2f)
}

/** Finger positions are window coordinates. */
@Stable
class DragController {
    var active by mutableStateOf<ActiveDrag?>(null)
        private set
    var finger by mutableStateOf(Offset.Zero)
        private set

    /** Item that was long-pressed and may turn into a drag if the finger moves. */
    var armed by mutableStateOf<DragSource?>(null)
        private set

    fun arm(source: DragSource) {
        armed = source
    }

    fun disarm() {
        armed = null
    }

    fun start(drag: ActiveDrag, at: Offset) {
        active = drag
        finger = at
    }

    fun move(to: Offset) {
        finger = to
    }

    fun finish(): Pair<ActiveDrag, Offset>? {
        val drag = active ?: return null
        active = null
        armed = null
        return drag to finger
    }

    fun cancel() {
        active = null
        armed = null
    }
}

/** Where the grid and dock sit on screen, written during layout and read on drop. */
class HomeGeometry {
    var grid = Rect.Zero
    var dock = Rect.Zero
    var columns = 4
    var cellHeightPx = 1f
    var dockSlots = 5

    val rows: Int get() = max(1, (grid.height / cellHeightPx).toInt())

    fun targetAt(p: Offset, pageId: String?): DropTarget? {
        if (dock.contains(p)) {
            return DropTarget.DockSlot(((p.x - dock.left) / (dock.width / dockSlots)).toInt().coerceIn(0, dockSlots - 1))
        }
        if (pageId != null && grid.contains(p)) {
            val x = ((p.x - grid.left) / (grid.width / columns)).toInt().coerceIn(0, columns - 1)
            val y = ((p.y - grid.top) / cellHeightPx).toInt().coerceIn(0, rows - 1)
            return DropTarget.Cell(pageId, x, y)
        }
        return null
    }
}

class CoordsHolder {
    var value: LayoutCoordinates? = null

    fun bounds(): Rect? = value?.takeIf { it.isAttached }?.let { c ->
        val topLeft = c.localToWindow(Offset.Zero)
        Rect(topLeft, Size(c.size.width.toFloat(), c.size.height.toFloat()))
    }
}

/**
 * Turns a press into a drag once [isArmed] is true (after a long-press, or always in edit
 * mode). Until then it leaves events alone so taps, the pager and the drawer swipe still work.
 */
@Composable
fun rememberItemDrag(
    isArmed: () -> Boolean,
    coordinates: () -> LayoutCoordinates?,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onRelease: () -> Unit,
): Modifier {
    val armed by rememberUpdatedState(isArmed)
    val coords by rememberUpdatedState(coordinates)
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    val release by rememberUpdatedState(onRelease)
    return Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var dragging = false
            var released = false
            var travelled = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    released = true
                    if (dragging) {
                        change.consume()
                        end()
                    }
                    break
                }
                if (!armed()) {
                    if (change.isConsumed) break
                    continue
                }
                travelled += change.positionChange().getDistance()
                change.consume()
                val window = coords()?.takeIf { it.isAttached }?.localToWindow(change.position) ?: continue
                if (dragging) {
                    move(window)
                } else if (travelled > viewConfiguration.touchSlop) {
                    dragging = true
                    start(window)
                }
            }
            if (dragging && !released) cancel()
            if (!dragging) release()
        }
    }
}
