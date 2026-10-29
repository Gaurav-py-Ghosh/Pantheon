package dev.pantheon.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pantheon.launcher.ui.LauncherRoot
import dev.pantheon.launcher.ui.theme.Pantheon
import dev.pantheon.launcher.ui.theme.PantheonTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        // stateNotNeeded: never restore saved state, always start clean.
        super.onCreate(null)
        enableEdgeToEdge()
        val settingsRepo = container.settings
        // One tiny DataStore read so the first frame already has the right theme.
        val initial = runBlocking { settingsRepo.settings.first() }

        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(initial)
            PantheonTheme(settings) {
                val dark = Pantheon.colors.isDark
                LaunchedEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                LauncherRoot(settings, homePresses)
            }
        }
    }

    /** Set by the launcher before starting a widget's setup screen. */
    var onConfigureResult: ((Boolean) -> Unit)? = null

    @Deprecated("AppWidgetHost only offers a requestCode-based configure API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            onConfigureResult?.invoke(resultCode == RESULT_OK)
            onConfigureResult = null
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { container.widgets.host.startListening() }
        dev.pantheon.launcher.notifications.PantheonNotificationListener.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        runCatching { container.widgets.host.stopListening() }
    }

    private var hasFocus = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        this.hasFocus = hasFocus
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on home: close the drawer or go back to page one.
        // The activity is paused before this call, so check focus and "brought to front" (as Launcher3 does).
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        val broughtToFront = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0
        if (isHome && hasFocus && !broughtToFront) homePresses.tryEmit(Unit)
    }

    companion object {
        const val REQUEST_CONFIGURE_WIDGET = 0x5041
    }
}
