package dev.pantheon.launcher.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Drawer open fraction, driven directly by the finger and settled with a spring. */
@Stable
class DrawerController(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set

    /** True when the drawer was opened from the search bar and should raise the keyboard. */
    var focusSearch by mutableStateOf(false)
        private set

    /** Bumped whenever the drawer starts closing, so the search field can drop focus. */
    var dismissTick by mutableIntStateOf(0)
        private set

    private var job: Job? = null
    private var closingDrag = false
    private val spec = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

    fun open(focus: Boolean) {
        focusSearch = focus
        animateTo(1f)
    }

    fun close() = animateTo(0f)

    fun snapClosed() {
        job?.cancel()
        progress = 0f
        focusSearch = false
        dismissTick++
    }

    fun dragBy(dy: Float, distance: Float) {
        job?.cancel()
        if (dy > 0f && !closingDrag) {
            closingDrag = true
            dismissTick++
        }
        progress = (progress - dy / distance).coerceIn(0f, 1f)
    }

    /** [velocityY] in px/s, positive is downward. */
    fun settle(velocityY: Float, distance: Float) {
        val v = -velocityY / distance
        val target = when {
            v > 1.5f -> 1f
            v < -1.5f -> 0f
            progress > 0.45f -> 1f
            else -> 0f
        }
        animateTo(target, v)
    }

    private fun animateTo(target: Float, velocity: Float = 0f) {
        closingDrag = false
        if (target == 0f) {
            focusSearch = false
            dismissTick++
        }
        job?.cancel()
        job = scope.launch {
            animate(progress, target, velocity, spec) { value, _ -> progress = value }
        }
    }
}

/**
 * Root-level vertical gestures: swipe up drags the drawer open, swipe down pulls the
 * notification shade. Placed on an untransformed parent so finger coordinates stay true
 * while the home and drawer layers move.
 */
fun Modifier.launcherGestures(
    drawer: DrawerController,
    distancePx: Float,
    enabled: Boolean,
    onPullDown: () -> Unit,
): Modifier =
    pointerInput(drawer, distancePx, enabled) {
        if (!enabled) return@pointerInput
        val pullThreshold = 72.dp.toPx()
        val tracker = VelocityTracker()
        var pulled = 0f
        var fired = false
        detectVerticalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                pulled = 0f
                fired = false
            },
            onDragEnd = { drawer.settle(tracker.calculateVelocity().y, distancePx) },
            onDragCancel = { drawer.settle(0f, distancePx) },
        ) { change, dy ->
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
            if (drawer.progress > 0f || dy < 0f) {
                drawer.dragBy(dy, distancePx)
            } else if (!fired) {
                pulled += dy
                if (pulled > pullThreshold) {
                    fired = true
                    onPullDown()
                }
            }
        }
    }
