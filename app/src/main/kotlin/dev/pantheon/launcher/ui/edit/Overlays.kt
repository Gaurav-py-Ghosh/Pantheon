package dev.pantheon.launcher.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.JetBrainsMono
import dev.pantheon.launcher.ui.theme.Pantheon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

data class MenuItem(
    val label: String,
    val enabled: Boolean = true,
    val hint: String? = null,
    val action: () -> Unit = {},
)

/** [anchor] is the window rect the menu opens next to (below it if there is room, else above). */
data class MenuSpec(val anchor: Rect, val title: String, val items: List<MenuItem>)

@Stable
class MenuController {
    var spec by mutableStateOf<MenuSpec?>(null)
        private set

    fun show(spec: MenuSpec) {
        this.spec = spec
    }

    fun dismiss() {
        spec = null
    }
}

@Composable
fun MenuOverlay(menu: MenuController) {
    val spec = menu.spec ?: return
    val appear = remember(spec) { Animatable(0f) }
    LaunchedEffect(spec) { appear.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 800f)) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.3f * appear.value)) }
            .pointerInput(spec) { detectTapGestures { menu.dismiss() } },
    ) {
        Layout(
            content = {
                MenuCard(
                    spec = spec,
                    onPick = { item ->
                        menu.dismiss()
                        item.action()
                    },
                    modifier = Modifier.graphicsLayer {
                        val a = appear.value
                        alpha = a
                        scaleX = 0.92f + 0.08f * a
                        scaleY = 0.92f + 0.08f * a
                    },
                )
            },
        ) { measurables, constraints ->
            val card = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val margin = 12.dp.roundToPx()
                val x = (spec.anchor.center.x - card.width / 2f).roundToInt()
                    .coerceIn(margin, max(margin, constraints.maxWidth - card.width - margin))
                val below = spec.anchor.bottom + margin
                val y = if (below + card.height <= constraints.maxHeight - margin * 4) below else spec.anchor.top - margin - card.height
                card.place(x, y.roundToInt().coerceIn(margin, max(margin, constraints.maxHeight - card.height - margin)))
            }
        }
    }
}

@Composable
private fun MenuCard(spec: MenuSpec, onPick: (MenuItem) -> Unit, modifier: Modifier = Modifier) {
    val colors = Pantheon.colors
    Column(
        modifier
            .width(248.dp)
            .terminalBox(colors, radius = 12.dp)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            TText("$ ", color = colors.accent, size = 12.sp, glow = true)
            TText(spec.title, color = colors.textDim, size = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        spec.items.forEach { item -> MenuRow(item, onClick = { onPick(item) }) }
    }
}

@Composable
private fun MenuRow(item: MenuItem, onClick: () -> Unit) {
    val colors = Pantheon.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(if (pressed) colors.surfaceHigh else Color.Transparent)
            .clickable(interaction, indication = null, enabled = item.enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText(">", color = if (item.enabled) colors.accent else colors.textDim, size = 13.sp, glow = item.enabled)
        Spacer(Modifier.width(10.dp))
        TText(item.label, color = if (item.enabled) colors.text else colors.textDim, size = 13.sp, modifier = Modifier.weight(1f))
        item.hint?.let { TText(it, color = colors.textDim, size = 11.sp) }
    }
}

@Composable
fun RenamePrompt(entry: AppEntry, current: String, onSubmit: (String?) -> Unit, onDismiss: () -> Unit) {
    val colors = Pantheon.colors
    var value by remember(entry.key) { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(entry.key) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(300.dp)
                .terminalBox(colors, radius = 12.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            TText("$ tag --set ${entry.label.lowercase()}", color = colors.accent, size = 12.sp, glow = true, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TText("[", color = colors.accent, size = 22.sp)
                BasicTextField(
                    value = value,
                    onValueChange = { v -> value = v.lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(5) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 22.sp, color = colors.accent),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(value) }),
                    modifier = Modifier
                        .width(84.dp)
                        .focusRequester(focus),
                )
                TText("]", color = colors.accent, size = 22.sp)
            }
            Spacer(Modifier.height(6.dp))
            TText("max 5 chars · a-z 0-9 -", color = colors.textDim, size = 11.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PromptButton("[reset]") { onSubmit(null) }
                PromptButton("[cancel]", onClick = onDismiss)
                PromptButton("[ok]", accent = true) { onSubmit(value) }
            }
        }
    }
}

@Composable
fun PromptButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val colors = Pantheon.colors
    TText(
        label,
        color = if (accent) colors.accent else colors.textDim,
        size = 13.sp,
        glow = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Stable
class ToastController(private val scope: CoroutineScope) {
    var message by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun show(text: String) {
        message = text
        job?.cancel()
        job = scope.launch {
            delay(2_200)
            message = null
        }
    }
}

@Composable
fun ToastHost(toast: ToastController, modifier: Modifier = Modifier) {
    val colors = Pantheon.colors
    var last by remember { mutableStateOf("") }
    val current = toast.message
    SideEffect { if (current != null) last = current }
    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 132.dp),
    ) {
        Box(Modifier.terminalBox(colors, radius = 10.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row {
                TText("$ ", color = colors.accent, size = 12.sp, glow = true)
                TText(current ?: last, size = 12.sp, maxLines = 2)
            }
        }
    }
}
