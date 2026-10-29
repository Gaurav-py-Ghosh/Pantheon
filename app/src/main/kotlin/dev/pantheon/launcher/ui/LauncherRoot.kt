package dev.pantheon.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.DrawerMode
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.ui.drawer.DrawerScreen
import dev.pantheon.launcher.ui.home.HomeScreen
import dev.pantheon.launcher.ui.home.LaunchFn
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(settings: Settings, homePresses: Flow<Unit>) {
    val context = LocalContext.current
    val view = LocalView.current
    val c = context.container
    val colors = Pantheon.colors
    val scope = rememberCoroutineScope()

    val apps by c.apps.apps.collectAsStateWithLifecycle()
    val layout by c.layout.layout.collectAsStateWithLifecycle()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val tags = remember(apps, settings.tags) { apps.associate { it.key to c.tags.tagFor(it, settings.tags) } }

    val drawer = remember { DrawerController(scope) }
    val drawerVisible by remember { derivedStateOf { drawer.progress > 0f } }
    val pager = rememberPagerState { layout?.pages?.size ?: 1 }
    val launch: LaunchFn = remember(view) { { entry, bounds -> Actions.launchApp(view, entry, bounds) } }

    BackHandler(enabled = drawerVisible) { drawer.close() }
    LaunchedEffect(homePresses) {
        homePresses.collect {
            if (drawer.progress > 0f) drawer.close() else pager.animateScrollToPage(0)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { drawer.snapClosed() }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val distance = constraints.maxHeight * 0.45f
        val density = LocalDensity.current
        val homeLift = with(density) { 40.dp.toPx() }
        val drawerDrop = with(density) { 120.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .launcherGestures(drawer, distance, onPullDown = { Actions.expandNotifications(context) }),
        ) {
            layout?.let { l ->
                HomeScreen(
                    layout = l,
                    apps = byKey,
                    tags = tags,
                    settings = settings,
                    pagerState = pager,
                    onSearch = { drawer.open(focus = true) },
                    onLaunch = launch,
                    modifier = Modifier.graphicsLayer {
                        val p = drawer.progress
                        alpha = 1f - p
                        translationY = -homeLift * p
                        val s = 1f - 0.04f * p
                        scaleX = s
                        scaleY = s
                    },
                )
            }
            if (drawerVisible) {
                DrawerScreen(
                    drawer = drawer,
                    distancePx = distance,
                    apps = apps,
                    tags = tags,
                    settings = settings,
                    onLaunch = launch,
                    onToggleMode = {
                        scope.launch {
                            c.settings.update {
                                it.copy(drawerMode = if (it.drawerMode == DrawerMode.AZ) DrawerMode.CATEGORY else DrawerMode.AZ)
                            }
                        }
                    },
                    modifier = Modifier.graphicsLayer {
                        val p = drawer.progress
                        alpha = p
                        translationY = drawerDrop * (1f - p)
                    },
                )
            }
        }
    }
}
