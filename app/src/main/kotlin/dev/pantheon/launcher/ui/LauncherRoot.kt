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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
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
import dev.pantheon.launcher.ui.edit.IconPicker
import dev.pantheon.launcher.ui.edit.MenuOverlay
import dev.pantheon.launcher.ui.edit.RenamePrompt
import dev.pantheon.launcher.ui.edit.ToastHost
import dev.pantheon.launcher.ui.home.DragGhost
import dev.pantheon.launcher.ui.home.HomeScreen
import dev.pantheon.launcher.ui.notifications.NotificationDrawer
import dev.pantheon.launcher.ui.settings.SettingsScreen
import dev.pantheon.launcher.ui.setup.BootScreen
import dev.pantheon.launcher.ui.sysinfo.SysinfoPanel
import dev.pantheon.launcher.ui.theme.Pantheon
import dev.pantheon.launcher.ui.widgets.NotesEditor
import dev.pantheon.launcher.ui.widgets.WidgetPicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(settings: Settings, homePresses: Flow<Unit>) {
    val context = LocalContext.current
    val view = LocalView.current
    val c = context.container
    val colors = Pantheon.colors
    val scope = rememberCoroutineScope()
    val settingsState = rememberUpdatedState(settings)

    val apps by c.apps.apps.collectAsStateWithLifecycle()
    val layoutState = c.layout.layout.collectAsStateWithLifecycle()
    val notifications by c.notifications.collectAsStateWithLifecycle()
    val notificationItems by c.notificationItems.collectAsStateWithLifecycle()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val tags = remember(apps, settings.tags) { apps.associate { it.key to c.tags.tagFor(it, settings.tags) } }

    val drawer = remember { DrawerController(scope) }
    val notifDrawer = remember { NotificationDrawerController(scope) }
    val pager = rememberPagerState { layoutState.value?.pages?.size ?: 1 }
    val launcher = remember { LauncherController(context, view, scope, drawer, notifDrawer, pager, layoutState, settingsState) }
    launcher.bridge = rememberActivityBridge()
    val drawerVisible by remember { derivedStateOf { drawer.progress > 0f } }
    val notifDrawerVisible by remember { derivedStateOf { notifDrawer.progress > 0f } }
    val dragging by remember { derivedStateOf { launcher.drag.active != null } }
    // derivedStateOf so drawer animation frames don't recompose the root.
    val backEnabled by remember { derivedStateOf { launcher.backEnabled } }

    val density = LocalDensity.current
    val scanlines = remember(settings.scanlines, density) { if (settings.scanlines) scanlineBrush(density) else null }

    BackHandler(enabled = backEnabled) { launcher.onBack() }
    LaunchedEffect(homePresses) { homePresses.collect { launcher.onHome() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { launcher.onStop() }
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(dragging) { if (dragging) launcher.edgeFlipLoop(edgePx) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .drawWithContent {
                drawContent()
                // One tiled-shader rect instead of hundreds of lines per frame.
                if (scanlines != null) drawRect(scanlines)
            },
    ) {
        val distance = constraints.maxHeight * 0.45f
        val homeLift = with(density) { 40.dp.toPx() }
        val drawerDrop = with(density) { 120.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .launcherGestures(
                    drawer = drawer,
                    notifDrawer = notifDrawer,
                    distancePx = distance,
                    enabled = !launcher.editMode,
                ),
        ) {
            layoutState.value?.let { layout ->
                HomeScreen(
                    layout = layout,
                    apps = byKey,
                    tags = tags,
                    settings = settings,
                    pagerState = pager,
                    drag = launcher.drag,
                    geometry = launcher.geometry,
                    actions = launcher,
                    onSearch = { drawer.open(focus = true) },
                    notifications = notifications,
                    onOpenSysinfo = { launcher.showingSysinfo = true },
                    modifier = Modifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val p = drawer.progress
                        val np = notifDrawer.progress
                        val combinedP = maxOf(p, np)
                        alpha = 1f - combinedP
                        translationY = if (p > 0f) -homeLift * p else homeLift * np
                        val s = 1f - 0.04f * combinedP
                        scaleX = s
                        scaleY = s
                        if (settings.glitch && (p in 0.05f..0.95f || np in 0.05f..0.95f)) {
                            val gp = if (p > 0f) p else np
                            translationX = -((gp * 137).toInt() % 7 - 3) * with(density) { 2.dp.toPx() }
                        }
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
                    onLaunch = launcher::launch,
                    onLongPress = launcher::drawerLongPress,
                    onToggleMode = {
                        scope.launch {
                            c.settings.update {
                                it.copy(drawerMode = if (it.drawerMode == DrawerMode.AZ) DrawerMode.CATEGORY else DrawerMode.AZ)
                            }
                        }
                    },
                    notifications = notifications,
                    modifier = Modifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val p = drawer.progress
                        alpha = p
                        translationY = drawerDrop * (1f - p)
                        if (settings.glitch && p in 0.05f..0.95f) {
                            translationX = ((p * 137).toInt() % 7 - 3) * with(density) { 3.dp.toPx() }
                        }
                    },
                )
            }
            if (notifDrawerVisible) {
                NotificationDrawer(
                    drawer = notifDrawer,
                    distancePx = distance,
                    notifications = notificationItems,
                    apps = apps,
                    tags = tags,
                    onOpenSysinfo = { launcher.showingSysinfo = true },
                    modifier = Modifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val np = notifDrawer.progress
                        alpha = np
                        translationY = -drawerDrop * (1f - np)
                        if (settings.glitch && np in 0.05f..0.95f) {
                            translationX = ((np * 137).toInt() % 7 - 3) * with(density) { 3.dp.toPx() }
                        }
                    },
                )
            }
        }

        DragGhost(launcher.drag, tags, settings, notifications)
        ToastHost(launcher.toast, Modifier.align(Alignment.BottomCenter))
        MenuOverlay(launcher.menu)
        if (launcher.pickingWidget != null) {
            WidgetPicker(
                onBuiltin = launcher::addBuiltin,
                onProvider = launcher::addAppWidget,
                onDismiss = { launcher.pickingWidget = null },
            )
        }
        launcher.pickingIconFor?.let { entry ->
            IconPicker(
                entry = entry,
                activePack = settings.iconPack,
                onPick = { spec -> launcher.setIconOverride(entry, spec) },
                onReset = { launcher.resetIconOverride(entry) },
                onDismiss = { launcher.pickingIconFor = null },
            )
        }
        launcher.editingNotes?.let { id -> NotesEditor(id, onClose = { launcher.editingNotes = null }) }
        launcher.renaming?.let { entry ->
            RenamePrompt(
                entry = entry,
                current = tags[entry.key].orEmpty(),
                onSubmit = { launcher.setTag(entry, it) },
                onDismiss = { launcher.renaming = null },
            )
        }
        if (launcher.showingSysinfo) {
            SysinfoPanel(onDismiss = { launcher.showingSysinfo = false })
        }
        if (launcher.showingSettings) {
            SettingsScreen(
                settings = settings,
                layoutState = layoutState,
                apps = apps,
                onUpdateSettings = launcher::updateSettings,
                onReplaceSettings = launcher::replaceSettings,
                onUpdateLayout = launcher::updateLayout,
                onRerunSetup = {
                    launcher.showingSettings = false
                    launcher.showingSetupWizard = true
                },
                onToast = { launcher.toast.show(it) },
                onDismiss = { launcher.showingSettings = false },
            )
        }
        if (!settings.setupDone || launcher.showingSetupWizard) {
            BootScreen(onComplete = {
                launcher.showingSetupWizard = false
                launcher.updateSettings { it.copy(setupDone = true) }
            })
        }
    }
}

/** A 1px dark line every 4dp, as a repeating shader so the overlay costs one draw call. */
private fun scanlineBrush(density: Density): ShaderBrush {
    val step = with(density) { 4.dp.roundToPx() }.coerceAtLeast(2)
    val line = with(density) { 1.dp.roundToPx() }.coerceIn(1, step - 1)
    val bitmap = android.graphics.Bitmap.createBitmap(1, step, android.graphics.Bitmap.Config.ARGB_8888)
    val dark = android.graphics.Color.argb(51, 0, 0, 0) // black at 20%
    for (y in 0 until line) bitmap.setPixel(0, y, dark)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}
