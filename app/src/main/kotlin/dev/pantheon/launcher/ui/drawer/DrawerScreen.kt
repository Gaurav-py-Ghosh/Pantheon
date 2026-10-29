package dev.pantheon.launcher.ui.drawer

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.DrawerMode
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.search.Hit
import dev.pantheon.launcher.search.Search
import dev.pantheon.launcher.ui.Actions
import dev.pantheon.launcher.ui.DrawerController
import dev.pantheon.launcher.ui.components.AppIconImage
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.home.LaunchFn
import dev.pantheon.launcher.ui.theme.JetBrainsMono
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun DrawerScreen(
    drawer: DrawerController,
    distancePx: Float,
    apps: List<AppEntry>,
    tags: Map<String, String>,
    settings: Settings,
    onLaunch: LaunchFn,
    onLongPress: (AppEntry, Rect) -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Pantheon.colors
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(drawer.focusSearch) {
        if (drawer.focusSearch) {
            snapshotFlow { drawer.progress }.first { it > 0.5f }
            focus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(drawer.dismissTick) {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    val listHidden = Search.isListHidden(query)
    val visible = remember(apps, settings.hidden, listHidden) {
        if (listHidden) apps else apps.filter { it.key !in settings.hidden }
    }
    val hits = remember(query, visible, tags) { Search.run(query, visible, tags) }

    fun activate(hit: Hit, bounds: Rect?) {
        when (hit) {
            is Hit.App -> onLaunch(hit.entry, bounds)
            is Hit.Cmd -> Actions.runCommand(context, hit.command)?.let { query = it }
            is Hit.Calc -> Actions.copy(context, hit.value)
            is Hit.Web -> Actions.webSearch(context, hit.query, settings.searchEngine)
        }
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TText("~/apps", color = colors.accent, size = 14.sp, glow = true)
                TText(" $ ls  (${visible.size})", color = colors.textDim, size = 13.sp, modifier = Modifier.weight(1f))
                TText(
                    if (settings.drawerMode == DrawerMode.AZ) "[a-z]" else "[cat]",
                    color = colors.accent,
                    size = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleMode)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .terminalBox(colors)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TText("usr / ", color = colors.accent, size = 15.sp, glow = true)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 15.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { hits.firstOrNull()?.let { activate(it, null) } }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) TText("search_", color = colors.textDim, size = 15.sp)
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    TText(
                        "[x]",
                        color = colors.textDim,
                        size = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { query = "" }
                            .padding(4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val nested = remember(drawer, distancePx) { drawerNestedScroll(drawer, distancePx) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (query.isBlank()) {
                    AppList(visible, tags, settings, nested, onLaunch, onLongPress)
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().nestedScroll(nested),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(hits, key = ::hitKey) { hit ->
                            HitRow(
                                hit = hit,
                                tags = tags,
                                hidden = settings.hidden,
                                onClick = { b -> activate(hit, b) },
                                onLongClick = (hit as? Hit.App)?.let { h -> { b: Rect -> onLongPress(h.entry, b) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hitKey(hit: Hit): String = when (hit) {
    is Hit.App -> "a:${hit.entry.key}"
    is Hit.Cmd -> "c:${hit.command.name}"
    is Hit.Calc -> "calc"
    is Hit.Web -> "web"
}

/** Lets a downward drag at the top of the list pull the drawer closed. */
private fun drawerNestedScroll(drawer: DrawerController, distancePx: Float) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (drawer.progress < 1f && available.y < 0f) {
            drawer.dragBy(available.y, distancePx)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y > 0f && source == NestedScrollSource.UserInput) {
            drawer.dragBy(available.y, distancePx)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drawer.progress < 1f) {
            drawer.settle(available.y, distancePx)
            return available
        }
        return Velocity.Zero
    }
}

private fun sectionOf(entry: AppEntry, mode: DrawerMode): String = when (mode) {
    DrawerMode.AZ -> entry.label.firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"
    DrawerMode.CATEGORY -> when (entry.category) {
        ApplicationInfo.CATEGORY_GAME -> "games"
        ApplicationInfo.CATEGORY_AUDIO -> "audio"
        ApplicationInfo.CATEGORY_VIDEO -> "video"
        ApplicationInfo.CATEGORY_IMAGE -> "images"
        ApplicationInfo.CATEGORY_SOCIAL -> "social"
        ApplicationInfo.CATEGORY_NEWS -> "news"
        ApplicationInfo.CATEGORY_MAPS -> "maps"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "accessibility"
        else -> "misc"
    }
}

@Composable
private fun AppList(
    apps: List<AppEntry>,
    tags: Map<String, String>,
    settings: Settings,
    nested: NestedScrollConnection,
    onLaunch: LaunchFn,
    onLongPress: (AppEntry, Rect) -> Unit,
) {
    val colors = Pantheon.colors
    val mode = settings.drawerMode
    val groups = remember(apps, mode) {
        val grouped = apps.groupBy { sectionOf(it, mode) }.toList()
        if (mode == DrawerMode.CATEGORY) grouped.sortedBy { (name, _) -> if (name == "misc") "~" else name } else grouped
    }
    val headerIndex = remember(groups) {
        var index = 0
        groups.associate { (name, list) -> (name to index).also { index += 1 + list.size } }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(nested),
            contentPadding = PaddingValues(bottom = 24.dp, end = if (mode == DrawerMode.AZ) 28.dp else 0.dp),
        ) {
            groups.forEach { (name, list) ->
                item(key = "h:$name") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
                        TText(if (mode == DrawerMode.AZ) "── $name" else "/apps/$name", color = colors.accent, size = 12.sp)
                        TText("  ${list.size}", color = colors.textDim, size = 12.sp)
                    }
                }
                items(list, key = { it.key }) { entry ->
                    HitRow(
                        hit = Hit.App(entry),
                        tags = tags,
                        hidden = settings.hidden,
                        onClick = { b -> onLaunch(entry, b) },
                        onLongClick = { b -> onLongPress(entry, b) },
                    )
                }
            }
        }
        if (mode == DrawerMode.AZ && groups.size > 1) {
            AlphaRail(
                letters = groups.map { it.first },
                active = activeLetter,
                onActive = { letter ->
                    activeLetter = letter
                    letter?.let { headerIndex[it] }?.let { scope.launch { listState.scrollToItem(it) } }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            activeLetter?.let { letter ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .terminalBox(colors, radius = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TText(letter, color = colors.accent, size = 34.sp, glow = true)
                }
            }
        }
    }
}

@Composable
private fun AlphaRail(letters: List<String>, active: String?, onActive: (String?) -> Unit, modifier: Modifier = Modifier) {
    val colors = Pantheon.colors
    val haptic = LocalHapticFeedback.current
    val currentOnActive by rememberUpdatedState(onActive)
    Column(
        modifier
            .width(24.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .pointerInput(letters) {
                var last: String? = null
                fun pick(y: Float) {
                    val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    val letter = letters[i]
                    if (letter != last) {
                        last = letter
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnActive(letter)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    last = null
                    currentOnActive(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            TText(letter, color = if (letter == active) colors.accent else colors.textDim, size = 10.sp)
        }
    }
}

private class BoundsHolder {
    var rect: Rect? = null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HitRow(
    hit: Hit,
    tags: Map<String, String>,
    hidden: Set<String>,
    onClick: (Rect?) -> Unit,
    onLongClick: ((Rect) -> Unit)? = null,
) {
    val colors = Pantheon.colors
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) colors.surface else Color.Transparent)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongClick?.let { cb -> { bounds.rect?.let(cb) } },
                onClick = { onClick(bounds.rect) },
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (hit) {
            is Hit.App -> {
                val entry = hit.entry
                AppIconImage(entry, 22.dp)
                Spacer(Modifier.width(12.dp))
                TText("[${tags[entry.key].orEmpty()}]", color = colors.accent, size = 12.sp, glow = true, modifier = Modifier.width(64.dp))
                TText(entry.label.lowercase(), size = 14.sp, modifier = Modifier.weight(1f))
                if (entry.isWork) TText(" [w]", color = colors.textDim, size = 11.sp)
                if (entry.key in hidden) TText(" [h]", color = colors.textDim, size = 11.sp)
            }
            is Hit.Cmd -> {
                TText("$", color = colors.accent, size = 14.sp, glow = true, modifier = Modifier.width(34.dp))
                TText(hit.command.word, size = 14.sp, modifier = Modifier.width(110.dp))
                TText(hit.command.hint, color = colors.textDim, size = 12.sp, modifier = Modifier.weight(1f))
            }
            is Hit.Calc -> {
                TText("=", color = colors.accent, size = 14.sp, glow = true, modifier = Modifier.width(34.dp))
                TText(hit.value, size = 16.sp, modifier = Modifier.weight(1f))
                TText("tap to copy", color = colors.textDim, size = 11.sp)
            }
            is Hit.Web -> {
                TText(">", color = colors.accent, size = 14.sp, glow = true, modifier = Modifier.width(34.dp))
                TText("search web: \"${hit.query}\"", size = 14.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
