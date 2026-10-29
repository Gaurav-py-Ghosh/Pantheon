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
import androidx.lifecycle.Lifecycle
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on home: close the drawer or go back to page one.
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        if (isHome && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) homePresses.tryEmit(Unit)
    }
}
