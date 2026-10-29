package dev.pantheon.launcher.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.accessibility.PantheonAccessibilityService
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.BackgroundMode
import dev.pantheon.launcher.data.BackupManager
import dev.pantheon.launcher.data.DrawerMode
import dev.pantheon.launcher.data.HomeLayout
import dev.pantheon.launcher.data.LabelStyle
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.data.ThemeMode
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.Accents
import dev.pantheon.launcher.ui.theme.JetBrainsMono
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: Settings,
    layoutState: State<HomeLayout?>,
    apps: List<AppEntry>,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    onReplaceSettings: suspend (Settings) -> Unit,
    onUpdateLayout: ((HomeLayout) -> HomeLayout) -> Unit,
    onRerunSetup: () -> Unit,
    onToast: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = Pantheon.colors
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val c = context.container

    BackHandler(onBack = onDismiss)

    // Backup launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val jsonStr = BackupManager.exportToString(settings, layoutState.value ?: HomeLayout())
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(jsonStr.toByteArray()) }
                }.onSuccess {
                    onToast("backup saved to pantheon-backup.json")
                }.onFailure {
                    onToast("export failed: ${it.message}")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val content = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                }.getOrNull()
                if (content != null) {
                    val backup = BackupManager.importFromString(content)
                    if (backup != null) {
                        onReplaceSettings(backup.settings)
                        onUpdateLayout { backup.layout }
                        onToast("backup restored successfully")
                    } else {
                        onToast("invalid backup file")
                    }
                } else {
                    onToast("failed to read file")
                }
            }
        }
    }

    var editingHex by remember { mutableStateOf(false) }
    var hexInput by remember(settings.accent) { mutableStateOf(settings.accent) }
    var showHiddenList by remember { mutableStateOf(false) }

    // Icon packs
    val installedPacks = remember { c.iconPacks.getInstalledPacks() }
    val packOptions = remember(installedPacks) {
        listOf(null to "default") + installedPacks.map { it.packageName to it.label.lowercase() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TText("~/", color = colors.textDim, size = 15.sp)
                    TText(".pantheonrc", color = colors.accent, size = 15.sp, glow = true)
                }
                TText(
                    "[ :wq ]",
                    color = colors.accent,
                    size = 13.sp,
                    glow = true,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // [theme]
            SectionBox(title = "theme") {
                CycleRow(
                    label = "mode",
                    value = settings.themeMode.name.lowercase(),
                    onPrev = {
                        val entries = ThemeMode.entries
                        val idx = (entries.indexOf(settings.themeMode) - 1 + entries.size) % entries.size
                        onUpdateSettings { it.copy(themeMode = entries[idx]) }
                    },
                    onNext = {
                        val entries = ThemeMode.entries
                        val idx = (entries.indexOf(settings.themeMode) + 1) % entries.size
                        onUpdateSettings { it.copy(themeMode = entries[idx]) }
                    },
                )

                val presetIds = remember { Accents.presets.map { it.id } }
                val currentPreset = Accents.presets.firstOrNull { it.id == settings.accent }
                CycleRow(
                    label = "accent",
                    value = currentPreset?.name ?: "custom (${settings.accent})",
                    onPrev = {
                        val idx = presetIds.indexOf(settings.accent).let { if (it <= 0) presetIds.lastIndex else it - 1 }
                        onUpdateSettings { it.copy(accent = presetIds[idx]) }
                    },
                    onNext = {
                        val idx = presetIds.indexOf(settings.accent).let { if (it < 0 || it >= presetIds.lastIndex) 0 else it + 1 }
                        onUpdateSettings { it.copy(accent = presetIds[idx]) }
                    },
                )

                // Custom hex row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TText("accent_hex", color = colors.textDim, size = 12.sp)
                    if (editingHex) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = hexInput,
                                onValueChange = { hexInput = it.take(9) },
                                singleLine = true,
                                textStyle = TextStyle(color = colors.accent, fontFamily = JetBrainsMono, fontSize = 12.sp),
                                cursorBrush = SolidColor(colors.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    val cleaned = if (!hexInput.startsWith("#")) "#$hexInput" else hexInput
                                    if (cleaned.length == 7 || cleaned.length == 9) {
                                        onUpdateSettings { it.copy(accent = cleaned) }
                                        editingHex = false
                                        onToast("accent hex applied")
                                    } else {
                                        onToast("invalid hex format (#RRGGBB)")
                                    }
                                }),
                                modifier = Modifier
                                    .width(90.dp)
                                    .terminalBox(colors, radius = 4.dp)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            TText(
                                "[ok]",
                                color = colors.accent,
                                size = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        val cleaned = if (!hexInput.startsWith("#")) "#$hexInput" else hexInput
                                        if (cleaned.length == 7 || cleaned.length == 9) {
                                            onUpdateSettings { it.copy(accent = cleaned) }
                                            editingHex = false
                                            onToast("accent hex applied")
                                        } else {
                                            onToast("invalid hex format (#RRGGBB)")
                                        }
                                    }
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TText(settings.accent, color = colors.text, size = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            TText(
                                "[edit]",
                                color = colors.accent,
                                size = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        hexInput = settings.accent
                                        editingHex = true
                                    }
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                }

                CycleRow(
                    label = "background",
                    value = settings.backgroundMode.name.lowercase(),
                    onPrev = {
                        val entries = BackgroundMode.entries
                        val idx = (entries.indexOf(settings.backgroundMode) - 1 + entries.size) % entries.size
                        onUpdateSettings { it.copy(backgroundMode = entries[idx]) }
                    },
                    onNext = {
                        val entries = BackgroundMode.entries
                        val idx = (entries.indexOf(settings.backgroundMode) + 1) % entries.size
                        onUpdateSettings { it.copy(backgroundMode = entries[idx]) }
                    },
                )

                ToggleRow(
                    label = "hide_status_bar",
                    checked = settings.hideStatusBar,
                    onToggle = { onUpdateSettings { s -> s.copy(hideStatusBar = !s.hideStatusBar) } },
                )
            }

            // [grid]
            SectionBox(title = "grid") {
                CycleRow(
                    label = "columns",
                    value = "${settings.columns}",
                    onPrev = {
                        val cols = (settings.columns - 1).coerceIn(4, 6)
                        onUpdateSettings { it.copy(columns = cols) }
                    },
                    onNext = {
                        val cols = (settings.columns + 1).coerceIn(4, 6)
                        onUpdateSettings { it.copy(columns = cols) }
                    },
                )

                val labelStyles = remember { LabelStyle.entries }
                CycleRow(
                    label = "label_style",
                    value = when (settings.labelStyle) {
                        LabelStyle.ICON_TAG_NAME -> "icon+tag+name"
                        LabelStyle.ICON_TAG -> "icon+tag"
                        LabelStyle.ICON -> "icon"
                        LabelStyle.TAG -> "tag"
                    },
                    onPrev = {
                        val idx = (labelStyles.indexOf(settings.labelStyle) - 1 + labelStyles.size) % labelStyles.size
                        onUpdateSettings { it.copy(labelStyle = labelStyles[idx]) }
                    },
                    onNext = {
                        val idx = (labelStyles.indexOf(settings.labelStyle) + 1) % labelStyles.size
                        onUpdateSettings { it.copy(labelStyle = labelStyles[idx]) }
                    },
                )
            }

            // [dock]
            SectionBox(title = "dock") {
                CycleRow(
                    label = "slots",
                    value = "${settings.dockSlots}",
                    onPrev = {
                        val slots = (settings.dockSlots - 1).coerceIn(4, 6)
                        onUpdateSettings { it.copy(dockSlots = slots) }
                    },
                    onNext = {
                        val slots = (settings.dockSlots + 1).coerceIn(4, 6)
                        onUpdateSettings { it.copy(dockSlots = slots) }
                    },
                )

                ToggleRow(
                    label = "dock_labels",
                    checked = settings.dockLabels,
                    onToggle = { onUpdateSettings { s -> s.copy(dockLabels = !s.dockLabels) } },
                )
            }

            // [drawer]
            SectionBox(title = "drawer") {
                val drawerModes = remember { DrawerMode.entries }
                CycleRow(
                    label = "mode",
                    value = when (settings.drawerMode) {
                        DrawerMode.AZ -> "a-z"
                        DrawerMode.CATEGORY -> "category"
                    },
                    onPrev = {
                        val idx = (drawerModes.indexOf(settings.drawerMode) - 1 + drawerModes.size) % drawerModes.size
                        onUpdateSettings { it.copy(drawerMode = drawerModes[idx]) }
                    },
                    onNext = {
                        val idx = (drawerModes.indexOf(settings.drawerMode) + 1) % drawerModes.size
                        onUpdateSettings { it.copy(drawerMode = drawerModes[idx]) }
                    },
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TText("hidden_apps", color = colors.textDim, size = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TText("${settings.hidden.size} hidden", color = colors.text, size = 12.sp)
                        if (settings.hidden.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            TText(
                                if (showHiddenList) "[hide]" else "[show]",
                                color = colors.accent,
                                size = 12.sp,
                                modifier = Modifier
                                    .clickable { showHiddenList = !showHiddenList }
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                }

                if (showHiddenList && settings.hidden.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .terminalBox(colors, radius = 6.dp)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val byKey = remember(apps) { apps.associateBy { it.key } }
                        for (key in settings.hidden) {
                            val entry = byKey[key]
                            val name = entry?.label ?: key.substringBefore('/')
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TText(name.lowercase(), color = colors.text, size = 11.sp)
                                TText(
                                    "[unhide]",
                                    color = colors.accent,
                                    size = 11.sp,
                                    modifier = Modifier
                                        .clickable {
                                            onUpdateSettings { s -> s.copy(hidden = s.hidden - key) }
                                            onToast("unhidden $name")
                                        }
                                        .padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // [search]
            SectionBox(title = "search") {
                val engines = remember { listOf("default", "google", "ddg", "bing", "brave") }
                CycleRow(
                    label = "engine",
                    value = settings.searchEngine,
                    onPrev = {
                        val idx = (engines.indexOf(settings.searchEngine) - 1 + engines.size) % engines.size
                        onUpdateSettings { it.copy(searchEngine = engines[idx]) }
                    },
                    onNext = {
                        val idx = (engines.indexOf(settings.searchEngine) + 1) % engines.size
                        onUpdateSettings { it.copy(searchEngine = engines[idx]) }
                    },
                )
            }

            // [icons]
            SectionBox(title = "icons") {
                val currentPackLabel = packOptions.firstOrNull { it.first == settings.iconPack }?.second ?: "default"
                CycleRow(
                    label = "icon_pack",
                    value = currentPackLabel,
                    onPrev = {
                        val currIdx = packOptions.indexOfFirst { it.first == settings.iconPack }.coerceAtLeast(0)
                        val prevIdx = (currIdx - 1 + packOptions.size) % packOptions.size
                        val chosen = packOptions[prevIdx]
                        onUpdateSettings { it.copy(iconPack = chosen.first) }
                        onToast("icon pack: ${chosen.second}")
                    },
                    onNext = {
                        val currIdx = packOptions.indexOfFirst { it.first == settings.iconPack }.coerceAtLeast(0)
                        val nextIdx = (currIdx + 1) % packOptions.size
                        val chosen = packOptions[nextIdx]
                        onUpdateSettings { it.copy(iconPack = chosen.first) }
                        onToast("icon pack: ${chosen.second}")
                    },
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TText("custom_icons", color = colors.textDim, size = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TText("${settings.iconOverrides.size} overridden", color = colors.text, size = 12.sp)
                        if (settings.iconOverrides.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            TText(
                                "[reset all]",
                                color = colors.warn,
                                size = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        onUpdateSettings { it.copy(iconOverrides = emptyMap()) }
                                        onToast("all custom icon overrides reset")
                                    }
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }

            // [widgets]
            SectionBox(title = "widgets") {
                ToggleRow(
                    label = "image_fill_default",
                    checked = settings.imageFill,
                    onToggle = { onUpdateSettings { s -> s.copy(imageFill = !s.imageFill) } },
                )
            }

            // [gestures]
            SectionBox(title = "gestures") {
                ToggleRow(
                    label = "double_tap_lock",
                    checked = settings.doubleTapLock,
                    onToggle = {
                        val next = !settings.doubleTapLock
                        onUpdateSettings { s -> s.copy(doubleTapLock = next) }
                        if (next && !PantheonAccessibilityService.isConnected()) {
                            onToast("enable pantheon in accessibility settings to lock")
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    },
                )
                ToggleRow(
                    label = "lock_screen",
                    checked = settings.lockScreen,
                    onToggle = { onUpdateSettings { s -> s.copy(lockScreen = !s.lockScreen) } },
                )
            }

            // [effects]
            SectionBox(title = "effects") {
                ToggleRow(
                    label = "glow",
                    checked = settings.glow,
                    onToggle = { onUpdateSettings { s -> s.copy(glow = !s.glow) } },
                )
                ToggleRow(
                    label = "typewriter",
                    checked = settings.typewriter,
                    onToggle = { onUpdateSettings { s -> s.copy(typewriter = !s.typewriter) } },
                )
                ToggleRow(
                    label = "scanlines",
                    checked = settings.scanlines,
                    onToggle = { onUpdateSettings { s -> s.copy(scanlines = !s.scanlines) } },
                )
                ToggleRow(
                    label = "glitch",
                    checked = settings.glitch,
                    onToggle = { onUpdateSettings { s -> s.copy(glitch = !s.glitch) } },
                )
            }

            // [backup]
            SectionBox(title = "backup") {
                ActionRow(
                    label = "export",
                    buttonLabel = "[ export to file ]",
                    onClick = { exportLauncher.launch("pantheon-backup.json") },
                )
                ActionRow(
                    label = "import",
                    buttonLabel = "[ import from file ]",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                )
            }

            // [about]
            SectionBox(title = "about") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TText("app", color = colors.textDim, size = 12.sp)
                    TText("pantheon v0.1.0", color = colors.text, size = 12.sp)
                }
                ActionRow(
                    label = "setup",
                    buttonLabel = "[ rerun boot wizard ]",
                    onClick = onRerunSetup,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionBox(title: String, content: @Composable () -> Unit) {
    val colors = Pantheon.colors
    Column(
        Modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 8.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TText("── [$title]", color = colors.accent, size = 12.sp, glow = true)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun CycleRow(
    label: String,
    value: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = Pantheon.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText(label, color = colors.textDim, size = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TText("‹", color = colors.textDim, size = 14.sp, modifier = Modifier.clickable(onClick = onPrev).padding(horizontal = 6.dp))
            TText(value, color = colors.accent, size = 12.sp, glow = true)
            TText("›", color = colors.textDim, size = 14.sp, modifier = Modifier.clickable(onClick = onNext).padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = Pantheon.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText(label, color = colors.textDim, size = 12.sp)
        TText(
            if (checked) "[x]" else "[ ]",
            color = if (checked) colors.accent else colors.textDim,
            size = 13.sp,
            glow = checked,
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    val colors = Pantheon.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText(label, color = colors.textDim, size = 12.sp)
        TText(
            buttonLabel,
            color = colors.accent,
            size = 12.sp,
            glow = true,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
