package dev.pantheon.launcher.ui.components

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.pantheon.launcher.MainActivity
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.Settings as PantheonSettings
import dev.pantheon.launcher.ui.theme.PantheonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MusicIslandOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isViewAttached = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WindowManager::class.java)

        scope.launch {
            combine(
                container.music.currentTrack,
                container.settings.settings,
                MainActivity.isForegroundFlow,
            ) { track, settings, isForeground ->
                Triple(track, settings, isForeground)
            }.collect { (track, settings, isForeground) ->
                val canDraw = runCatching { Settings.canDrawOverlays(this@MusicIslandOverlayService) }.getOrDefault(false)
                val shouldShow = settings.musicIsland &&
                    track != null &&
                    !isForeground &&
                    canDraw

                if (shouldShow) {
                    showOverlay(settings)
                } else {
                    hideOverlay()
                }
            }
        }
    }

    private fun showOverlay(initialSettings: PantheonSettings) {
        if (isViewAttached) return
        val wm = windowManager ?: return

        val density = resources.displayMetrics.density
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MusicIslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@MusicIslandOverlayService)
            setViewTreeViewModelStoreOwner(this@MusicIslandOverlayService)
            setContent {
                val settings by container.settings.settings.collectAsStateWithLifecycle(initialSettings)
                val track by container.music.currentTrack.collectAsStateWithLifecycle()
                PantheonTheme(settings) {
                    if (track != null) {
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            MusicIsland(
                                track = track!!,
                                initialMode = IslandMode.EXPANDED,
                                isAtCutout = true,
                                onModeChanged = { newMode ->
                                    val currentParams = overlayParams ?: return@MusicIsland
                                    when (newMode) {
                                        IslandMode.CIRCLE -> {
                                            currentParams.width = (48 * density).toInt()
                                            currentParams.height = (40 * density).toInt()
                                        }
                                        IslandMode.PILL -> {
                                            currentParams.width = (210 * density).toInt()
                                            currentParams.height = (48 * density).toInt()
                                        }
                                        IslandMode.EXPANDED -> {
                                            currentParams.width = WindowManager.LayoutParams.MATCH_PARENT
                                            currentParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                                        }
                                    }
                                    runCatching { wm.updateViewLayout(this@apply, currentParams) }
                                },
                            )
                        }
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        overlayParams = params

        runCatching {
            wm.addView(composeView, params)
            overlayView = composeView
            isViewAttached = true
        }
    }

    private fun hideOverlay() {
        if (!isViewAttached) return
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        isViewAttached = false
    }

    override fun onDestroy() {
        hideOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
