package dev.pantheon.launcher.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.DragSource
import dev.pantheon.launcher.data.HomeLayout
import dev.pantheon.launcher.data.HomePage
import dev.pantheon.launcher.data.LayoutOps
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.data.ThemeMode
import dev.pantheon.launcher.ui.edit.ActiveDrag
import dev.pantheon.launcher.ui.edit.DragController
import dev.pantheon.launcher.ui.edit.HomeGeometry
import dev.pantheon.launcher.ui.edit.MenuController
import dev.pantheon.launcher.ui.edit.MenuItem
import dev.pantheon.launcher.ui.edit.MenuSpec
import dev.pantheon.launcher.ui.edit.ToastController
import dev.pantheon.launcher.ui.home.HomeActions
import dev.pantheon.launcher.ui.theme.Accents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen behavior: menus, drag and drop, pages, edit mode, back and home handling. */
@Stable
class LauncherController(
    private val context: Context,
    private val view: View,
    private val scope: CoroutineScope,
    val drawer: DrawerController,
    val pager: PagerState,
    private val layoutState: State<HomeLayout?>,
    private val settingsState: State<Settings>,
) : HomeActions {
    private val c = context.container
    val drag = DragController()
    val menu = MenuController()
    val toast = ToastController(scope)
    val geometry = HomeGeometry()

    override var editMode by mutableStateOf(false)
        private set
    var renaming by mutableStateOf<AppEntry?>(null)

    /** Page added by dragging past the last page; removed again if the drop leaves it empty. */
    private var autoPageId: String? = null

    private val layout get() = layoutState.value
    private val settings get() = settingsState.value

    private fun currentPage(): HomePage? = layout?.let { l -> l.pages.getOrNull(pager.currentPage.coerceIn(0, l.pages.lastIndex)) }

    private fun updateSettings(transform: (Settings) -> Settings) {
        scope.launch { c.settings.update(transform) }
    }

    // ── apps ──────────────────────────────────────────────

    override fun launch(entry: AppEntry, bounds: Rect?) = Actions.launchApp(view, entry, bounds)

    override fun appLongPress(entry: AppEntry, source: DragSource, bounds: Rect) {
        menu.show(
            MenuSpec(
                bounds,
                entry.label.lowercase(),
                buildList {
                    add(MenuItem("open") { launch(entry, bounds) })
                    add(MenuItem("rename tag") { renaming = entry })
                    add(MenuItem("change icon", enabled = false, hint = "stage 4"))
                    add(MenuItem("app info") { c.apps.openAppInfo(entry, null) })
                    add(hideItem(entry))
                    when (source) {
                        is DragSource.Home -> add(MenuItem("remove from home") { c.layout.update { LayoutOps.removeItem(it, source.pageId, source.itemId) } })
                        is DragSource.Dock -> add(MenuItem("remove from dock") { c.layout.update { LayoutOps.clearDock(it, source.slot) } })
                    }
                    add(MenuItem("uninstall") { Actions.uninstall(context, entry, c.apps.userFor(entry)) })
                },
            ),
        )
    }

    fun drawerLongPress(entry: AppEntry, bounds: Rect) {
        menu.show(
            MenuSpec(
                bounds,
                entry.label.lowercase(),
                listOf(
                    MenuItem("add to home") { addToHome(entry) },
                    MenuItem("add to dock") { addToDock(entry) },
                    MenuItem("rename tag") { renaming = entry },
                    MenuItem("change icon", enabled = false, hint = "stage 4"),
                    MenuItem("app info") { c.apps.openAppInfo(entry, null) },
                    hideItem(entry),
                    MenuItem("uninstall") { Actions.uninstall(context, entry, c.apps.userFor(entry)) },
                ),
            ),
        )
    }

    private fun hideItem(entry: AppEntry): MenuItem {
        val hidden = entry.key in settings.hidden
        val name = entry.label.lowercase()
        return MenuItem(if (hidden) "unhide" else "hide from drawer") {
            updateSettings { s -> s.copy(hidden = if (hidden) s.hidden - entry.key else s.hidden + entry.key) }
            toast.show(if (hidden) "unhidden $name" else "hidden $name · type ls -a to find it")
        }
    }

    private fun addToHome(entry: AppEntry) {
        val l = layout ?: return
        val (next, page) = LayoutOps.addApp(l, entry.key, pager.currentPage, settings.columns, geometry.rows)
        c.layout.update { next }
        toast.show("added ${entry.label.lowercase()} → page ${page + 1}")
    }

    private fun addToDock(entry: AppEntry) {
        val next = layout?.let { LayoutOps.addToDock(it, entry.key, settings.dockSlots) }
        if (next == null) {
            toast.show("dock full · drag an app out first")
        } else {
            c.layout.update { next }
            toast.show("docked ${entry.label.lowercase()}")
        }
    }

    fun setTag(entry: AppEntry, tag: String?) {
        updateSettings { s -> s.copy(tags = if (tag.isNullOrBlank()) s.tags - entry.key else s.tags + (entry.key to tag)) }
        renaming = null
    }

    // ── home menu ─────────────────────────────────────────

    override fun emptyLongPress(at: Offset) {
        val anchor = Rect(at, Size.Zero)
        menu.show(
            MenuSpec(
                anchor,
                "home",
                listOf(
                    MenuItem("edit layout") { editMode = true },
                    MenuItem("add widget", enabled = false, hint = "stage 3"),
                    MenuItem("add image", enabled = false, hint = "stage 3"),
                    MenuItem("pages", hint = "›") { pagesMenu(anchor) },
                    MenuItem("theme", hint = "›") { themeMenu(anchor) },
                    MenuItem("accent", hint = "›") { accentMenu(anchor) },
                    MenuItem("settings", enabled = false, hint = "stage 5"),
                ),
            ),
        )
    }

    private fun pagesMenu(anchor: Rect) {
        val count = layout?.pages?.size ?: 1
        val index = pager.currentPage
        menu.show(
            MenuSpec(
                anchor,
                "pages ${index + 1}/$count",
                listOf(
                    MenuItem("+ add page") { addPage() },
                    MenuItem("- delete this page", enabled = count > 1) { deletePage() },
                    MenuItem("← move left", enabled = index > 0) { movePage(-1) },
                    MenuItem("move right →", enabled = index < count - 1) { movePage(1) },
                ),
            ),
        )
    }

    private fun themeMenu(anchor: Rect) {
        menu.show(
            MenuSpec(
                anchor,
                "theme",
                ThemeMode.entries.map { mode ->
                    MenuItem(mode.name.lowercase(), hint = if (settings.themeMode == mode) "●" else null) {
                        updateSettings { it.copy(themeMode = mode) }
                    }
                },
            ),
        )
    }

    private fun accentMenu(anchor: Rect) {
        menu.show(
            MenuSpec(
                anchor,
                "accent",
                Accents.presets.map { preset ->
                    MenuItem(preset.name, hint = if (settings.accent == preset.id) "●" else null) {
                        updateSettings { it.copy(accent = preset.id) }
                    }
                },
            ),
        )
    }

    // ── pages ─────────────────────────────────────────────

    override fun addPage() {
        val at = pager.currentPage + 1
        c.layout.update { LayoutOps.addPage(it, at) }
        scope.launch {
            snapshotFlow { pager.pageCount }.first { it > at }
            pager.animateScrollToPage(at)
        }
    }

    override fun deletePage() {
        val page = currentPage() ?: return
        if ((layout?.pages?.size ?: 1) <= 1) {
            toast.show("can't delete the only page")
            return
        }
        val delete = { c.layout.update { LayoutOps.deletePage(it, page.id) } }
        if (page.items.isEmpty()) {
            delete()
        } else {
            menu.show(
                MenuSpec(
                    Rect(geometry.grid.center, Size.Zero),
                    "delete page with ${page.items.size} items?",
                    listOf(MenuItem("yes, delete") { delete() }, MenuItem("cancel")),
                ),
            )
        }
    }

    private fun movePage(by: Int) {
        val index = pager.currentPage
        c.layout.update { LayoutOps.movePage(it, index, by) }
        scope.launch { pager.scrollToPage(index + by) }
    }

    override fun exitEdit() {
        editMode = false
    }

    // ── drag and drop ─────────────────────────────────────

    override fun dragStart(entry: AppEntry, source: DragSource, bounds: Rect, finger: Offset, grabbedAt: Offset) {
        menu.dismiss()
        // Anchor to the touch-down point so the ghost keeps the spot that was grabbed under the finger.
        drag.start(ActiveDrag(source, entry, grabbedAt - bounds.topLeft, bounds.size), finger)
    }

    override fun dragMove(finger: Offset) = drag.move(finger)

    override fun dragEnd() {
        val (active, finger) = drag.finish() ?: return
        val target = geometry.targetAt(active.center(finger), currentPage()?.id)
        if (target != null) c.layout.update { LayoutOps.drop(it, active.source, target, settings.columns, geometry.rows) }
        cleanupAutoPage()
    }

    override fun dragCancel() {
        drag.cancel()
        cleanupAutoPage()
    }

    private fun cleanupAutoPage() {
        autoPageId?.let { id -> c.layout.update { LayoutOps.removeEmptyPage(it, id) } }
        autoPageId = null
    }

    /** While dragging, holding the item at the left or right edge flips pages (and adds one past the end). */
    suspend fun edgeFlipLoop(edgePx: Float) {
        snapshotFlow {
            if (drag.active == null) return@snapshotFlow 0
            val f = drag.finger
            val grid = geometry.grid
            when {
                f.y !in grid.top..grid.bottom -> 0
                f.x < grid.left + edgePx -> -1
                f.x > grid.right - edgePx -> 1
                else -> 0
            }
        }
            .distinctUntilChanged()
            .collectLatest { dir ->
                if (dir == 0) return@collectLatest
                while (true) {
                    delay(600)
                    flip(dir)
                }
            }
    }

    private suspend fun flip(dir: Int) {
        val l = layout ?: return
        val target = pager.currentPage + dir
        if (target < 0) return
        if (target >= l.pages.size) {
            if (autoPageId != null || l.pages.last().items.isEmpty()) return
            val page = HomePage()
            autoPageId = page.id
            c.layout.update { it.copy(pages = it.pages + page) }
            snapshotFlow { pager.pageCount }.first { it > target }
        }
        pager.animateScrollToPage(target)
    }

    // ── system navigation ─────────────────────────────────

    val backEnabled: Boolean
        get() = renaming != null || menu.spec != null || editMode || drawer.progress > 0f

    fun onBack() {
        when {
            renaming != null -> renaming = null
            menu.spec != null -> menu.dismiss()
            editMode -> editMode = false
            drawer.progress > 0f -> drawer.close()
        }
    }

    suspend fun onHome() {
        val busy = backEnabled
        renaming = null
        menu.dismiss()
        editMode = false
        dragCancel()
        if (drawer.progress > 0f) drawer.close()
        if (!busy) pager.animateScrollToPage(0)
    }

    fun onStop() {
        drawer.snapClosed()
        renaming = null
        menu.dismiss()
        editMode = false
        dragCancel()
    }
}
