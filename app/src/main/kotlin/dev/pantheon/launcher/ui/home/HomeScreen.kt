package dev.pantheon.launcher.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.HomeLayout
import dev.pantheon.launcher.data.HomePage
import dev.pantheon.launcher.data.ItemContent
import dev.pantheon.launcher.data.LabelStyle
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.ui.components.AppIconImage
import dev.pantheon.launcher.ui.components.BlinkingCursor
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.Pantheon

typealias LaunchFn = (AppEntry, Rect?) -> Unit

@Composable
fun HomeScreen(
    layout: HomeLayout,
    apps: Map<String, AppEntry>,
    tags: Map<String, String>,
    settings: Settings,
    pagerState: PagerState,
    onSearch: () -> Unit,
    onLaunch: LaunchFn,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        StatusLine()
        Spacer(Modifier.height(12.dp))
        SearchStub(onSearch)
        Spacer(Modifier.height(16.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            key = { layout.pages.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1,
        ) { index ->
            layout.pages.getOrNull(index)?.let { page ->
                HomeGrid(page, settings.columns, settings.labelStyle, apps, tags, onLaunch)
            }
        }
        if (layout.pages.size > 1) PageIndicator(pagerState.currentPage, layout.pages.size)
        Spacer(Modifier.height(10.dp))
        val dock = (0 until settings.dockSlots).map { layout.dock.getOrNull(it)?.let(apps::get) }
        Dock(dock, tags, settings.dockLabels, onLaunch)
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

private fun cellHeight(style: LabelStyle): Dp = when (style) {
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
    apps: Map<String, AppEntry>,
    tags: Map<String, String>,
    onLaunch: LaunchFn,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellW = maxWidth / columns
        val cellH = cellHeight(style)
        val rows = (maxHeight / cellH).toInt()
        page.items.forEach { item ->
            if (item.x + item.w > columns || item.y + item.h > rows) return@forEach
            Box(
                Modifier
                    .offset(cellW * item.x, cellH * item.y)
                    .size(cellW * item.w, cellH * item.h),
                contentAlignment = Alignment.Center,
            ) {
                when (val content = item.content) {
                    is ItemContent.App -> apps[content.key]?.let { entry ->
                        AppCell(entry, tags[entry.key].orEmpty(), style, 30.dp, onLaunch)
                    }
                }
            }
        }
    }
}

private class BoundsHolder {
    var rect: Rect? = null
}

@Composable
fun AppCell(entry: AppEntry, tag: String, style: LabelStyle, iconSize: Dp, onLaunch: LaunchFn, modifier: Modifier = Modifier) {
    val colors = Pantheon.colors
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Column(
        modifier
            .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interaction, indication = null) { onLaunch(entry, bounds.rect) }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (style != LabelStyle.TAG) AppIconImage(entry, iconSize)
        if (style != LabelStyle.ICON) {
            Spacer(Modifier.height(6.dp))
            TText("[$tag]", color = colors.accent, size = if (style == LabelStyle.TAG) 14.sp else 11.sp, glow = true)
        }
        if (style == LabelStyle.ICON_TAG_NAME) {
            TText(entry.label.lowercase(), size = 12.sp)
        }
    }
}

@Composable
private fun Dock(slots: List<AppEntry?>, tags: Map<String, String>, labels: Boolean, onLaunch: LaunchFn) {
    val colors = Pantheon.colors
    Row(
        Modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 14.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEach { entry ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (entry != null) {
                    AppCell(
                        entry = entry,
                        tag = tags[entry.key].orEmpty(),
                        style = if (labels) LabelStyle.ICON_TAG_NAME else LabelStyle.ICON,
                        iconSize = 28.dp,
                        onLaunch = onLaunch,
                    )
                }
            }
        }
    }
}

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
