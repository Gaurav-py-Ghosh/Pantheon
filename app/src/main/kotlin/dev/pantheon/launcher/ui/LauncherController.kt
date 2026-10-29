package dev.pantheon.launcher.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.DragSource
import dev.pantheon.launcher.data.DropTarget
import dev.pantheon.launcher.data.HomeItem
import dev.pantheon.launcher.data.HomeLayout
import dev.pantheon.launcher.data.HomePage
import dev.pantheon.launcher.data.ItemContent
import dev.pantheon.launcher.data.LayoutOps
import dev.pantheon.launcher.data.Settings
import dev.pantheon.launcher.data.ThemeMode
import dev.pantheon.launcher.data.hasFrame
import dev.pantheon.launcher.data.withFrame
import dev.pantheon.launcher.ui.edit.ActiveDrag
import dev.pantheon.launcher.ui.edit.DragController
import dev.pantheon.launcher.ui.edit.HomeGeometry
import dev.pantheon.launcher.ui.edit.MenuController
import dev.pantheon.launcher.ui.edit.MenuItem
import dev.pantheon.launcher.ui.edit.MenuSpec
import dev.pantheon.launcher.ui.edit.ToastController
import dev.pantheon.launcher.ui.home.HomeActions
import dev.pantheon.launcher.ui.theme.Accents
import dev.pantheon.launcher.ui.widgets.WidgetKind
import dev.pantheon.launcher.ui.widgets.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil

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
    var pickingIconFor by mutableStateOf<AppEntry?>(null)
    var showingSettings by mutableStateOf(false)
    var showingSysinfo by mutableStateOf(false)
    var showingSetupWizard by mutableStateOf(false)

    /** Where a widget picked from the picker goes; null when the picker is closed. */
    var pickingWidget by mutableStateOf<WidgetTarget?>(null)
    var editingNotes by mutableStateOf<String?>(null)
    override var permissionTick by mutableIntStateOf(0)
        private set
    lateinit var bridge: ActivityBridge

    /** Page added by dragging past the last page; removed again if the drop leaves it empty. */
    private var autoPageId: String? = null

    private val layout get() = layoutState.value
    private val settings get() = settingsState.value

    private fun currentPage(): HomePage? = layout?.let { l -> l.pages.getOrNull(pager.currentPage.coerceIn(0, l.pages.lastIndex)) }

    fun updateSettings(transform: (Settings) -> Settings) {
        scope.launch { c.settings.update(transform) }
    }

    suspend fun replaceSettings(newSettings: Settings) {
        c.settings.replace(newSettings)
    }

    fun updateLayout(transform: (HomeLayout) -> HomeLayout) {
        c.layout.update(transform)
    }

    override fun onDoubleTap() {
        if (!settings.doubleTapLock) return
        val locked = dev.pantheon.launcher.accessibility.PantheonAccessibilityService.lockScreen()
        if (!locked) {
            toast.show("enable pantheon in accessibility settings to lock")
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
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
                    add(MenuItem("change icon") { pickingIconFor = entry })
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
                    MenuItem("change icon") { pickingIconFor = entry },
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

    fun setIconOverride(entry: AppEntry, spec: String) {
        updateSettings { s -> s.copy(iconOverrides = s.iconOverrides + (entry.key to spec)) }
        pickingIconFor = null
        toast.show("icon changed: ${entry.label.lowercase()}")
    }

    fun resetIconOverride(entry: AppEntry) {
        updateSettings { s -> s.copy(iconOverrides = s.iconOverrides - entry.key) }
        pickingIconFor = null
        toast.show("icon reset: ${entry.label.lowercase()}")
    }

    // ── home menu ─────────────────────────────────────────

    override fun emptyLongPress(at: Offset) {
        val anchor = Rect(at, Size.Zero)
        val cell = geometry.targetAt(at, currentPage()?.id) as? DropTarget.Cell
        val target = WidgetTarget(pager.currentPage, cell?.x ?: 0, cell?.y ?: 0)
        menu.show(
            MenuSpec(
                anchor,
                "home",
                listOf(
                    MenuItem("edit layout") { editMode = true },
                    MenuItem("add widget", hint = "›") { pickingWidget = target },
                    MenuItem("add image") { addImageWidget(target) },
                    MenuItem("pages", hint = "›") { pagesMenu(anchor) },
                    MenuItem("settings") { showingSettings = true },
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
        drag.start(ActiveDrag(source, entry, entry.label, grabbedAt - bounds.topLeft, bounds.size), finger)
    }

    override fun widgetDragStart(item: HomeItem, label: String, source: DragSource, bounds: Rect, finger: Offset, grabbedAt: Offset) {
        menu.dismiss()
        drag.start(ActiveDrag(source, null, label, grabbedAt - bounds.topLeft, bounds.size, IntSize(item.w, item.h)), finger)
    }

    override fun dragMove(finger: Offset) = drag.move(finger)

    override fun dragEnd() {
        val (active, finger) = drag.finish() ?: return
        val target = geometry.dropTarget(active, finger, currentPage()?.id)
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

    // ── widgets ───────────────────────────────────────────

    private fun updateItem(pageId: String, itemId: String, f: (ItemContent) -> ItemContent) {
        c.layout.update { LayoutOps.updateContent(it, pageId, itemId, f) }
    }

    /** Puts [content] at [target] (or the nearest free area) and shows the page it landed on. */
    private fun placeContent(content: ItemContent, w: Int, h: Int, target: WidgetTarget): Boolean {
        val l = layout ?: return false
        val placed = LayoutOps.place(l, content, w.coerceAtMost(settings.columns), h.coerceAtMost(geometry.rows), target.pageIndex, settings.columns, geometry.rows, target.x, target.y)
        if (placed == null) {
            toast.show("doesn't fit on this grid")
            return false
        }
        val (next, page) = placed
        c.layout.update { next }
        if (page != pager.currentPage) {
            scope.launch {
                snapshotFlow { pager.pageCount }.first { it > page }
                pager.animateScrollToPage(page)
            }
        }
        return true
    }

    fun addBuiltin(kind: WidgetKind) {
        val target = pickingWidget ?: WidgetTarget(pager.currentPage, 0, 0)
        pickingWidget = null
        when (kind) {
            WidgetKind.IMAGE -> addImageWidget(target)
            WidgetKind.PHOTO -> ensurePhotoAccess { placeContent(kind.create(settings.imageFill), kind.w, kind.h, target) }
            WidgetKind.CALENDAR -> {
                placeContent(kind.create(settings.imageFill), kind.w, kind.h, target)
                if (!c.calendar.hasAccess()) requestCalendarAccess()
            }
            else -> placeContent(kind.create(settings.imageFill), kind.w, kind.h, target)
        }
    }

    private fun addImageWidget(target: WidgetTarget) {
        bridge.pickImages { uris ->
            if (uris.isEmpty()) return@pickImages
            scope.launch {
                toast.show("importing ${uris.size} image${if (uris.size > 1) "s" else ""}…")
                val files = c.media.importImages(uris)
                if (files.isEmpty()) {
                    toast.show("couldn't read those images")
                } else if (placeContent(ItemContent.Images(files, fill = settings.imageFill), WidgetKind.IMAGE.w, WidgetKind.IMAGE.h, target)) {
                    toast.show("image widget ready · long-press to tweak")
                }
            }
        }
    }

    override fun pickImagesFor(pageId: String, itemId: String) {
        bridge.pickImages { uris ->
            if (uris.isEmpty()) return@pickImages
            scope.launch {
                val files = c.media.importImages(uris)
                updateItem(pageId, itemId) { (it as? ItemContent.Images)?.copy(files = it.files + files) ?: it }
            }
        }
    }

    private fun ensurePhotoAccess(then: () -> Unit) {
        if (c.media.hasPhotoAccess()) return then()
        bridge.requestPermissions(c.media.photoPermissions()) { granted ->
            permissionTick++
            if (granted) then() else toast.show("photo access denied")
        }
    }

    override fun requestPhotoAccess() = ensurePhotoAccess {}

    override fun requestCalendarAccess() {
        bridge.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) { permissionTick++ }
    }

    override fun openNotes(itemId: String) {
        editingNotes = itemId
    }

    /** Binds (asking the user once if needed), runs the widget's own setup screen if it has one, then places it. */
    fun addAppWidget(info: AppWidgetProviderInfo) {
        val target = pickingWidget ?: WidgetTarget(pager.currentPage, 0, 0)
        pickingWidget = null
        val host = c.widgets.host
        val id = host.allocateAppWidgetId()
        val place = {
            val cellW = geometry.cellWidthPx.coerceAtLeast(1f)
            val w = maxOf(info.targetCellWidth, ceil(info.minWidth / cellW).toInt(), 1)
            val h = maxOf(info.targetCellHeight, ceil(info.minHeight / geometry.cellHeightPx).toInt(), 1)
            if (!placeContent(ItemContent.AppWidget(id, info.provider.flattenToString()), w, h, target)) host.deleteAppWidgetId(id)
        }
        val configureThenPlace = {
            val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
            if (info.configure != null && !optional) {
                bridge.configureWidget(host, id) { ok -> if (ok) place() else host.deleteAppWidgetId(id) }
            } else {
                place()
            }
        }
        if (c.widgets.manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configureThenPlace()
        } else {
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            bridge.startForResult(bind) { ok -> if (ok) configureThenPlace() else host.deleteAppWidgetId(id) }
        }
    }

    override fun widgetLongPress(item: HomeItem, pageId: String, bounds: Rect) {
        val content = item.content
        val items = buildList {
            when (content) {
                is ItemContent.Photo -> {
                    add(MenuItem("album", hint = "›") { ensurePhotoAccess { albumMenu(bounds, pageId, item.id) } })
                    add(intervalItem(bounds, pageId, item.id, content.intervalSec))
                    add(MenuItem(if (content.fill) "fit image" else "fill image") { updateItem(pageId, item.id) { (it as ItemContent.Photo).copy(fill = !content.fill) } })
                }
                is ItemContent.Images -> {
                    add(MenuItem("add images") { pickImagesFor(pageId, item.id) })
                    add(intervalItem(bounds, pageId, item.id, content.intervalSec))
                    add(MenuItem(if (content.fill) "fit image" else "fill image") { updateItem(pageId, item.id) { (it as ItemContent.Images).copy(fill = !content.fill) } })
                }
                is ItemContent.Notes -> add(MenuItem("edit") { openNotes(item.id) })
                is ItemContent.AppWidget -> {
                    val info = c.widgets.info(content.appWidgetId)
                    if (info?.configure != null) {
                        add(MenuItem("reconfigure") { bridge.configureWidget(c.widgets.host, content.appWidgetId) { } })
                    }
                }
                else -> Unit
            }
            add(MenuItem("frame", hint = if (content.hasFrame) "on" else "off") { updateItem(pageId, item.id) { it.withFrame(!content.hasFrame) } })
            add(MenuItem("resize") {
                editMode = true
                toast.show("drag the ◢ corner · [done] when finished")
            })
            add(MenuItem("remove") { removeWidget(pageId, item) })
        }
        menu.show(MenuSpec(bounds, content.label(), items))
    }

    private fun intervalItem(anchor: Rect, pageId: String, itemId: String, current: Int) =
        MenuItem("interval", hint = "${current}s ›") {
            menu.show(
                MenuSpec(
                    anchor,
                    "change every",
                    listOf(5, 10, 30, 60, 300).map { sec ->
                        MenuItem(if (sec < 60) "${sec}s" else "${sec / 60}m", hint = if (sec == current) "●" else null) {
                            updateItem(pageId, itemId) { c0 ->
                                when (c0) {
                                    is ItemContent.Photo -> c0.copy(intervalSec = sec)
                                    is ItemContent.Images -> c0.copy(intervalSec = sec)
                                    else -> c0
                                }
                            }
                        }
                    },
                ),
            )
        }

    private fun albumMenu(anchor: Rect, pageId: String, itemId: String) {
        scope.launch {
            val albums = c.media.albums()
            val choose = { id: String?, name: String? ->
                updateItem(pageId, itemId) { (it as? ItemContent.Photo)?.copy(bucketId = id, bucketName = name) ?: it }
            }
            menu.show(
                MenuSpec(
                    anchor,
                    "albums",
                    listOf(MenuItem("all photos") { choose(null, null) }) +
                        albums.take(9).map { a -> MenuItem(a.name.lowercase(), hint = "${a.count}") { choose(a.id, a.name) } },
                ),
            )
        }
    }

    private fun removeWidget(pageId: String, item: HomeItem) {
        when (val content = item.content) {
            is ItemContent.Images -> c.media.deleteImported(content.files)
            is ItemContent.AppWidget -> c.widgets.host.deleteAppWidgetId(content.appWidgetId)
            is ItemContent.Notes -> c.notes.delete(item.id)
            else -> Unit
        }
        c.layout.update { LayoutOps.removeItem(it, pageId, item.id) }
    }

    override fun resize(pageId: String, itemId: String, w: Int, h: Int) {
        val l = layout ?: return
        val next = LayoutOps.resize(l, pageId, itemId, w, h, settings.columns, geometry.rows)
        if (next == null) toast.show("no room · move things out of the way first") else c.layout.update { next }
    }

    // ── system navigation ─────────────────────────────────

    val backEnabled: Boolean
        get() = showingSettings || showingSysinfo || showingSetupWizard || pickingIconFor != null || renaming != null || menu.spec != null || editMode || drawer.progress > 0f

    fun onBack() {
        when {
            showingSettings -> showingSettings = false
            showingSysinfo -> showingSysinfo = false
            showingSetupWizard -> showingSetupWizard = false
            pickingIconFor != null -> pickingIconFor = null
            renaming != null -> renaming = null
            menu.spec != null -> menu.dismiss()
            editMode -> editMode = false
            drawer.progress > 0f -> drawer.close()
        }
    }

    suspend fun onHome() {
        val busy = backEnabled || pickingWidget != null || editingNotes != null || pickingIconFor != null || showingSettings || showingSysinfo || showingSetupWizard
        showingSettings = false
        showingSysinfo = false
        showingSetupWizard = false
        pickingIconFor = null
        pickingWidget = null
        editingNotes = null
        renaming = null
        menu.dismiss()
        editMode = false
        dragCancel()
        if (drawer.progress > 0f) drawer.close()
        if (!busy) pager.animateScrollToPage(0)
    }

    /** Leaving home (another app, or a picker/permission screen we opened) resets transient state. */
    fun onStop() {
        drawer.snapClosed()
        showingSettings = false
        showingSysinfo = false
        showingSetupWizard = false
        pickingIconFor = null
        renaming = null
        menu.dismiss()
        dragCancel()
    }
}

data class WidgetTarget(val pageIndex: Int, val x: Int, val y: Int)
