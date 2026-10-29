package dev.pantheon.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.ItemContent
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.edit.PromptButton
import dev.pantheon.launcher.ui.theme.JetBrainsMono
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HostedWidget(content: ItemContent.AppWidget, width: Dp, height: Dp) {
    val context = LocalContext.current
    val widgets = context.container.widgets
    val info = remember(content.appWidgetId) { widgets.info(content.appWidgetId) }
    val colors = Pantheon.colors
    if (info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TText("[widget unavailable]", color = colors.textDim, size = 12.sp)
        }
        return
    }
    val label = remember(info) { info.loadLabel(context.packageManager).lowercase() }
    WidgetFrame("~/widgets/$label", content.frame) {
        AndroidView(
            factory = { ctx -> widgets.host.createView(ctx, content.appWidgetId, info).apply { setPadding(0, 0, 0, 0) } },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val key = "${width.value.toInt()}x${height.value.toInt()}"
                if (view.tag != key) {
                    view.tag = key
                    view.updateAppWidgetSize(Bundle(), listOf(SizeF(width.value, height.value)))
                }
            },
        )
    }
}

private data class ProviderRow(val info: AppWidgetProviderInfo, val app: String, val label: String, val size: String)

/** Full-screen `ls /widgets`: the built-ins first, then every installed Android widget grouped by app. */
@Composable
fun WidgetPicker(onBuiltin: (WidgetKind) -> Unit, onProvider: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = Pantheon.colors
    BackHandler(onBack = onDismiss)
    val providers by produceState(emptyList<ProviderRow>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            context.container.widgets.providers().map { info ->
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                    .getOrDefault(info.provider.packageName)
                val size = if (info.targetCellWidth > 0) "${info.targetCellWidth}×${info.targetCellHeight}" else ""
                ProviderRow(info, app.lowercase(), info.loadLabel(pm).lowercase(), size)
            }.sortedWith(compareBy({ it.app }, { it.label }))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TText("$ ls /widgets", color = colors.accent, size = 14.sp, glow = true, modifier = Modifier.weight(1f))
                PromptButton("[close]", onClick = onDismiss)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item { PickerHeader("built-in") }
                items(WidgetKind.entries) { kind ->
                    PickerRow("[${kind.id}]", kind.desc, "${kind.w}×${kind.h}") { onBuiltin(kind) }
                }
                var lastApp = ""
                providers.forEach { row ->
                    if (row.app != lastApp) {
                        val app = row.app
                        item(key = "h:$app") { PickerHeader(app) }
                        lastApp = app
                    }
                    item(key = "p:${row.info.provider.flattenToShortString()}:${row.info.profile.hashCode()}") {
                        PickerRow(row.label, null, row.size) { onProvider(row.info) }
                    }
                }
                if (providers.isEmpty()) item { TText("loading android widgets…", color = colors.textDim, size = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }
}

@Composable
private fun PickerHeader(text: String) {
    TText("── $text", color = Pantheon.colors.accent, size = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PickerRow(title: String, desc: String?, size: String, onClick: () -> Unit) {
    val colors = Pantheon.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText(title, color = if (desc != null) colors.accent else colors.text, size = 14.sp, modifier = Modifier.width(120.dp).takeIf { desc != null } ?: Modifier.weight(1f))
        if (desc != null) TText(desc, color = colors.textDim, size = 12.sp, modifier = Modifier.weight(1f))
        TText(size, color = colors.textDim, size = 12.sp)
    }
}

/** `nano ~/notes/todo.txt`: full-screen editor so the keyboard never squashes the home grid. */
@Composable
fun NotesEditor(itemId: String, onClose: () -> Unit) {
    val notes = LocalContext.current.container.notes
    val colors = Pantheon.colors
    val saved by remember(itemId) { notes.text(itemId) }.collectAsState()
    var draft by remember(itemId) { mutableStateOf<String?>(null) }
    val text = draft ?: saved
    val focus = remember { FocusRequester() }
    val close = {
        notes.save(itemId, text)
        onClose()
    }
    BackHandler(onBack = close)
    // Also save when closed some other way (home press, leaving the app).
    val latest by rememberUpdatedState(text)
    DisposableEffect(itemId) { onDispose { if (draft != null) notes.save(itemId, latest) } }
    LaunchedEffect(itemId) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) { detectTapGestures { focus.requestFocus() } },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TText("$ nano ~/notes/todo.txt", color = colors.accent, size = 14.sp, glow = true, modifier = Modifier.weight(1f))
                PromptButton("[save]", accent = true, onClick = close)
            }
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = text,
                onValueChange = { draft = it },
                textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            TText("^S [save] · back saves too", color = colors.textDim, size = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
