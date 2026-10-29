package dev.pantheon.launcher.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.DragSource
import dev.pantheon.launcher.data.DropTarget
import dev.pantheon.launcher.data.HomeItem
import dev.pantheon.launcher.data.HomeLayout
import dev.pantheon.launcher.data.HomePage
import dev.pantheon.launcher.data.ItemContent
import dev.pantheon.launcher.data.LabelStyle
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.ui.components.AppIconImage
import dev.pantheon.launcher.ui.components.BlinkingCursor
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.edit.CoordsHolder
import dev.pantheon.launcher.ui.edit.DragController
import dev.pantheon.launcher.ui.edit.HomeGeometry
import dev.pantheon.launcher.ui.edit.rememberItemDrag
import dev.pantheon.launcher.ui.theme.Pantheon
import dev.pantheon.launcher.ui.widgets.WidgetItem
import kotlin.math.roundToInt

typealias LaunchFn = (AppEntry, Rect?) -> Unit

/** Everything the home screen can ask the launcher to do. */
@Stable
interface HomeActions {
    val editMode: Boolean
    fun launch(entry: AppEntry, bounds: Rect?)
    fun appLongPress(entry: AppEntry, source: DragSource, bounds: Rect)
    fun emptyLongPress(at: Offset)
    fun dragStart(entry: AppEntry, source: DragSource, bounds: Rect, finger: Offset, grabbedAt: Offset)
    fun dragMove(finger: Offset)
    fun dragEnd()
    fun dragCancel()
    fun addPage()
    fun deletePage()
    fun exitEdit()

    /** Bumped after a permission prompt so widgets re-check their access. */
    val permissionTick: Int
    fun widgetLongPress(item: HomeItem, pageId: String, bounds: Rect)
    fun widgetDragStart(item: HomeItem, label: String, source: DragSource, bounds: Rect, finger: Offset, grabbedAt: Offset)
    fun resize(pageId: String, itemId: String, w: Int, h: Int)
    fun openNotes(itemId: String)
    fun pickImagesFor(pageId: String, itemId: String)
    fun requestPhotoAccess()
    fun requestCalendarAccess()
}

@Composable
fun HomeScreen(
    layout: HomeLayout,
    apps: Map<String, AppEntry>,
    tags: Map<String, String>,
    settings: Settings,
    pagerState: PagerState,
    drag: DragController,
    geometry: HomeGeometry,
    actions: HomeActions,
    onSearch: () -> Unit,
    notifications: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cellH = cellHeight(settings.labelStyle)
    SideEffect {
        geometry.columns = settings.columns
        geometry.cellHeightPx = with(density) { cellH.toPx() }
        geometry.dockSlots = settings.dockSlots
    }
    val target by remember(layout) {
        derivedStateOf {
            drag.active?.let { a -> geometry.dropTarget(a, drag.finger, layout.pages.getOrNull(pagerState.currentPage)?.id) }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        if (actions.editMode) EditBar(actions) else StatusLine()
        Spacer(Modifier.height(12.dp))
        SearchStub(onSearch)
        Spacer(Modifier.height(16.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { geometry.grid = it.boundsInWindow() },
            key = { layout.pages.getOrNull(it)?.id ?: it },
            // Keep every page composed so a drag can carry an item across pages.
            beyondViewportPageCount = layout.pages.size,
        ) { index ->
            layout.pages.getOrNull(index)?.let { page ->
                HomeGrid(
                    page = page,
                    columns = settings.columns,
                    style = settings.labelStyle,
                    cellH = cellH,
                    apps = apps,
                    tags = tags,
                    drag = drag,
                    actions = actions,
                    highlight = (target as? DropTarget.Cell)?.takeIf { it.pageId == page.id },
                    notifications = notifications,
                )
            }
        }
        if (layout.pages.size > 1) PageIndicator(pagerState.currentPage, layout.pages.size)
        Spacer(Modifier.height(10.dp))
        Dock(layout, apps, tags, settings, drag, geometry, actions, highlight = (target as? DropTarget.DockSlot)?.index, notifications = notifications)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SearchStub(onClick: () -> Unit) {
    val colors = Pantheon.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .terminalBox(colors, radius = 10.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText("usr", color = colors.accent, size = 15.sp, glow = true)
        TText(" / search", color = colors.textDim, size = 15.sp)
        BlinkingCursor(colors.accent, 15.sp)
    }
}

@Composable
private fun EditBar(actions: HomeActions) {
    val colors = Pantheon.colors
    Row(
        Modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 8.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText("$ edit --layout", color = colors.accent, size = 12.sp, glow = true, modifier = Modifier.weight(1f).padding(start = 6.dp))
        BarButton("[+pg]", onClick = actions::addPage)
        BarButton("[-pg]", onClick = actions::deletePage)
        BarButton("[done]", accent = true, onClick = actions::exitEdit)
    }
}

@Composable
private fun BarButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val colors = Pantheon.colors
    TText(
        label,
        color = if (accent) colors.accent else colors.text,
        size = 12.sp,
        glow = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
    )
}

fun cellHeight(style: LabelStyle): Dp = when (style) {
    LabelStyle.ICON_TAG_NAME -> 92.dp
    LabelStyle.ICON_TAG -> 76.dp
    LabelStyle.ICON -> 64.dp
    LabelStyle.TAG -> 48.dp
}

@Composable
private fun HomeGrid(
    page: HomePage,
    columns: Int,
    style: LabelStyle,
    cellH: Dp,
    apps: Map<String, AppEntry>,
    tags: Map<String, String>,
    drag: DragController,
    actions: HomeActions,
    highlight: DropTarget.Cell?,
    notifications: Map<String, Int> = emptyMap(),
) {
    val colors = Pantheon.colors
    val currentActions by rememberUpdatedState(actions)
    val coords = remember { CoordsHolder() }
    var resizing by remember { mutableStateOf<ResizePreview?>(null) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords.value = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        val c = coords.value?.takeIf { it.isAttached } ?: return@detectTapGestures
                        if (!currentActions.editMode) currentActions.emptyLongPress(c.localToWindow(offset))
                    },
                )
            },
    ) {
        val cellW = maxWidth / columns
        val rows = (maxHeight / cellH).toInt().coerceAtLeast(1)

        if (actions.editMode || drag.active != null) GridGuides(columns, rows, cellW, cellH)
        highlight?.let { cell ->
            val span = drag.active?.span ?: IntSize(1, 1)
            val x = cell.x.coerceIn(0, (columns - span.width).coerceAtLeast(0))
            val y = cell.y.coerceIn(0, (rows - span.height).coerceAtLeast(0))
            Outline(cellW * x, cellH * y, cellW * span.width, cellH * span.height, filled = true)
        }
        page.items.forEach { item ->
            if (item.x + item.w > columns || item.y + item.h > rows) return@forEach
            key(item.id) {
                Box(
                    Modifier
                        .offset(cellW * item.x, cellH * item.y)
                        .size(cellW * item.w, cellH * item.h),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val content = item.content) {
                        is ItemContent.App -> apps[content.key]?.let { entry ->
                            AppCell(
                                entry = entry,
                                tag = tags[entry.key].orEmpty(),
                                style = style,
                                iconSize = 30.dp,
                                source = DragSource.Home(page.id, item.id),
                                actions = actions,
                                drag = drag,
                                notificationCount = notifications[entry.packageName] ?: 0,
                            )
                        }
                        else -> WidgetItem(item, page.id, cellW * item.w, cellH * item.h, actions, drag)
                    }
                }
            }
        }

        // Edit mode: a ◢ handle on each widget's corner resizes it in whole cells.
        if (actions.editMode) {
            val cellWpx = with(LocalDensity.current) { cellW.toPx() }
            val cellHpx = with(LocalDensity.current) { cellH.toPx() }
            page.items.filter { it.content !is ItemContent.App && it.x + it.w <= columns && it.y + it.h <= rows }.forEach { item ->
                key("resize:${item.id}") {
                    Box(
                        Modifier
                            .offset(cellW * (item.x + item.w) - 34.dp, cellH * (item.y + item.h) - 34.dp)
                            .size(34.dp)
                            .pointerInput(item.id, item.w, item.h, columns, rows, cellWpx, cellHpx) {
                                var dx = 0f
                                var dy = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        dx = 0f
                                        dy = 0f
                                        resizing = ResizePreview(item.id, item.w, item.h)
                                    },
                                    onDragEnd = {
                                        resizing?.let { r -> currentActions.resize(page.id, item.id, r.w, r.h) }
                                        resizing = null
                                    },
                                    onDragCancel = { resizing = null },
                                ) { change, amount ->
                                    change.consume()
                                    dx += amount.x
                                    dy += amount.y
                                    resizing = ResizePreview(
                                        item.id,
                                        ((item.w * cellWpx + dx) / cellWpx).roundToInt().coerceIn(1, columns - item.x),
                                        ((item.h * cellHpx + dy) / cellHpx).roundToInt().coerceIn(1, rows - item.y),
                                    )
                                }
                            },
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        TText("◢", color = colors.accent, size = 20.sp, glow = true, modifier = Modifier.padding(end = 6.dp, bottom = 4.dp))
                    }
                }
            }
            resizing?.let { r ->
                page.items.firstOrNull { it.id == r.itemId }?.let { item ->
                    Outline(cellW * item.x, cellH * item.y, cellW * r.w, cellH * r.h, filled = false)
                }
            }
        }
    }
}

private data class ResizePreview(val itemId: String, val w: Int, val h: Int)

@Composable
private fun Outline(x: Dp, y: Dp, w: Dp, h: Dp, filled: Boolean) {
    val colors = Pantheon.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .offset(x, y)
            .size(w, h)
            .padding(3.dp)
            .background(if (filled) colors.accent.copy(alpha = 0.08f) else Color.Transparent, shape)
            .border(1.dp, colors.accent, shape),
    )
}

@Composable
private fun GridGuides(columns: Int, rows: Int, cellW: Dp, cellH: Dp) {
    val color = Pantheon.colors.border
    Canvas(Modifier.size(cellW * columns, cellH * rows)) {
        val cw = cellW.toPx()
        val ch = cellH.toPx()
        val inset = 4.dp.toPx()
        val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
        for (y in 0 until rows) for (x in 0 until columns) {
            drawRoundRect(
                color = color,
                topLeft = Offset(x * cw + inset, y * ch + inset),
                size = Size(cw - inset * 2, ch - inset * 2),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = stroke,
            )
        }
    }
}

/** Icon, `[tag]` and name, without any gestures. Shared by grid, dock and the drag ghost. */
@Composable
fun AppCellContent(
    entry: AppEntry,
    tag: String,
    style: LabelStyle,
    iconSize: Dp,
    notificationCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val colors = Pantheon.colors
    val displayTag = if (notificationCount > 0) {
        if (tag.isNotEmpty()) "$tag:$notificationCount" else "$notificationCount"
    } else {
        tag
    }
    Column(
        modifier.padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (style != LabelStyle.TAG) AppIconImage(entry, iconSize)
        if (style != LabelStyle.ICON) {
            Spacer(Modifier.height(6.dp))
            TText("[$displayTag]", color = colors.accent, size = if (style == LabelStyle.TAG) 14.sp else 11.sp, glow = true)
        }
        if (style == LabelStyle.ICON_TAG_NAME) {
            TText(entry.label.lowercase(), size = 12.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    entry: AppEntry,
    tag: String,
    style: LabelStyle,
    iconSize: Dp,
    source: DragSource,
    actions: HomeActions,
    drag: DragController,
    notificationCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val coords = remember { CoordsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dimmed = drag.active?.source == source
    val scale by animateFloatAsState(
        targetValue = if (pressed && !actions.editMode) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val dragModifier = rememberItemDrag(
        isArmed = { actions.editMode || drag.armed == source },
        coordinates = { coords.value },
        onStart = { finger, grabbedAt -> coords.bounds()?.let { actions.dragStart(entry, source, it, finger, grabbedAt) } },
        onMove = actions::dragMove,
        onEnd = actions::dragEnd,
        onCancel = actions::dragCancel,
        onRelease = drag::disarm,
    )
    AppCellContent(
        entry = entry,
        tag = tag,
        style = style,
        iconSize = iconSize,
        notificationCount = notificationCount,
        modifier = modifier
            .onGloballyPositioned { coords.value = it }
            .then(dragModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (dimmed) 0.25f else 1f
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = {
                    if (!actions.editMode) {
                        coords.bounds()?.let { b ->
                            drag.arm(source)
                            actions.appLongPress(entry, source, b)
                        }
                    }
                },
                onClick = { if (!actions.editMode) actions.launch(entry, coords.bounds()) },
            ),
    )
}

@Composable
private fun Dock(
    layout: HomeLayout,
    apps: Map<String, AppEntry>,
    tags: Map<String, String>,
    settings: Settings,
    drag: DragController,
    geometry: HomeGeometry,
    actions: HomeActions,
    highlight: Int?,
    notifications: Map<String, Int> = emptyMap(),
) {
    val colors = Pantheon.colors
    val showEmpty = actions.editMode || drag.active != null
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { geometry.dock = it.boundsInWindow() }
            .terminalBox(colors, radius = 14.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(settings.dockSlots) { slot ->
            val entry = layout.dock.getOrNull(slot)?.let(apps::get)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (highlight == slot) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .padding(horizontal = 3.dp)
                            .border(1.dp, colors.accent, RoundedCornerShape(10.dp)),
                    )
                }
                if (entry != null) {
                    AppCell(
                        entry = entry,
                        tag = tags[entry.key].orEmpty(),
                        style = dockStyle(settings),
                        iconSize = 28.dp,
                        source = DragSource.Dock(slot),
                        actions = actions,
                        drag = drag,
                        notificationCount = notifications[entry.packageName] ?: 0,
                    )
                } else if (showEmpty) {
                    TText("[+]", color = colors.textDim, size = 12.sp)
                }
            }
        }
    }
}

fun dockStyle(settings: Settings) = if (settings.dockLabels) LabelStyle.ICON_TAG_NAME else LabelStyle.ICON

@Composable
private fun PageIndicator(current: Int, count: Int) {
    val colors = Pantheon.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
        TText("[ ", color = colors.textDim, size = 12.sp)
        repeat(count) { i ->
            TText(if (i == current) "●" else "○", color = if (i == current) colors.accent else colors.textDim, size = 12.sp)
            if (i < count - 1) TText(" ", size = 12.sp)
        }
        TText(" ]", color = colors.textDim, size = 12.sp)
    }
}

/** The floating copy of an item under the finger while dragging. */
@Composable
fun DragGhost(
    drag: DragController,
    tags: Map<String, String>,
    settings: Settings,
    notifications: Map<String, Int> = emptyMap(),
) {
    val active = drag.active ?: return
    val density = LocalDensity.current
    val colors = Pantheon.colors
    val entry = active.entry
    val modifier = Modifier
        .size(with(density) { active.size.width.toDp() }, with(density) { active.size.height.toDp() })
        .graphicsLayer {
            val topLeft = drag.finger - active.touchOffset
            translationX = topLeft.x
            translationY = topLeft.y
            val s = if (entry != null) 1.12f else 1.03f
            scaleX = s
            scaleY = s
            alpha = if (entry != null) 0.95f else 0.85f
        }
    if (entry == null) {
        Box(modifier.terminalBox(colors, radius = 10.dp), contentAlignment = Alignment.Center) {
            TText("[${active.label}]", color = colors.accent, size = 14.sp, glow = true)
        }
        return
    }
    val style = if (active.source is DragSource.Dock) dockStyle(settings) else settings.labelStyle
    AppCellContent(
        entry = entry,
        tag = tags[entry.key].orEmpty(),
        style = style,
        iconSize = if (active.source is DragSource.Dock) 28.dp else 30.dp,
        notificationCount = notifications[entry.packageName] ?: 0,
        modifier = modifier,
    )
}
