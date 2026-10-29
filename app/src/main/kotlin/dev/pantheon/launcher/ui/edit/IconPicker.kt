package dev.pantheon.launcher.ui.edit

import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.LineIconItem
import dev.pantheon.launcher.data.LineIcons
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.JetBrainsMono
import dev.pantheon.launcher.ui.theme.Pantheon

@Composable
fun IconPicker(
    entry: AppEntry,
    activePack: String?,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Pantheon.colors
    val context = LocalContext.current
    val iconPacks = context.container.iconPacks
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        focus.requestFocus()
    }

    val bundledMatches = remember(query) {
        if (query.isBlank()) {
            LineIcons.ALL_BUNDLED_ICONS
        } else {
            LineIcons.ALL_BUNDLED_ICONS.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    val packDrawables = remember(activePack, query) {
        if (activePack.isNullOrBlank()) {
            emptyList()
        } else {
            val all = iconPacks.getAppFilter(activePack).allDrawables
            if (query.isBlank()) all else all.filter { it.contains(query, ignoreCase = true) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TText(
                    "$ pick-icon ~/apps/${entry.label.lowercase()}",
                    color = colors.accent,
                    size = 14.sp,
                    glow = true,
                    modifier = Modifier.weight(1f),
                )
                PromptButton("[reset]", accent = true, onClick = onReset)
                PromptButton("[close]", onClick = onDismiss)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .terminalBox(colors, radius = 8.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TText("$ ", color = colors.accent, size = 14.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.lowercase() },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) TText("search icons_", color = colors.textDim, size = 14.sp)
                            inner()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                )
                if (query.isNotEmpty()) {
                    TText(
                        "[x]",
                        color = colors.textDim,
                        size = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { query = "" }
                            .padding(4.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 64.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (packDrawables.isNotEmpty() && activePack != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TText("── icon pack ($activePack)", color = colors.accent, size = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(packDrawables, key = { "p:$it" }) { drawableName ->
                        PackIconCell(activePack, drawableName, onClick = { onPick("pack:$activePack/$drawableName") })
                    }
                }
                if (bundledMatches.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TText("── bundled line icons (${bundledMatches.size})", color = colors.accent, size = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(bundledMatches, key = { "b:${it.resName}" }) { item ->
                        BundledIconCell(item, onClick = { onPick("line:${item.resName}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun BundledIconCell(item: LineIconItem, onClick: () -> Unit) {
    val colors = Pantheon.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .terminalBox(colors, radius = 8.dp)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(item.resId),
            contentDescription = item.name,
            colorFilter = ColorFilter.tint(colors.text),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(4.dp))
        TText(item.name, size = 9.sp, maxLines = 1, color = colors.textDim)
    }
}

@Composable
private fun PackIconCell(packPackage: String, drawableName: String, onClick: () -> Unit) {
    val colors = Pantheon.colors
    val iconPacks = LocalContext.current.container.iconPacks
    val drawable = remember(packPackage, drawableName) { iconPacks.loadDrawable(packPackage, drawableName) }
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .terminalBox(colors, radius = 8.dp)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (drawable != null) {
                val bitmap = remember(drawable) {
                    val s = 56
                    val bm = createBitmap(s, s)
                    val canvas = Canvas(bm)
                    drawable.setBounds(0, 0, s, s)
                    drawable.draw(canvas)
                    bm.asImageBitmap()
                }
                Image(bitmap, contentDescription = drawableName, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(4.dp))
        TText(drawableName, size = 9.sp, maxLines = 1, color = colors.textDim)
    }
}
