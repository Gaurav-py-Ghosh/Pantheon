package dev.pantheon.launcher.data

import java.util.Collections

sealed interface DragSource {
    data class Home(val pageId: String, val itemId: String) : DragSource
    data class Dock(val slot: Int) : DragSource
}

sealed interface DropTarget {
    data class Cell(val pageId: String, val x: Int, val y: Int) : DropTarget
    data class DockSlot(val index: Int) : DropTarget
}

/** Pure layout edits. Every function returns the input unchanged when the edit is not possible. */
object LayoutOps {
    private fun HomeItem.overlaps(x: Int, y: Int, w: Int, h: Int) =
        this.x < x + w && x < this.x + this.w && this.y < y + h && y < this.y + this.h

    private fun HomeLayout.page(id: String) = pages.firstOrNull { it.id == id }

    private fun HomeLayout.mapPage(id: String, f: (HomePage) -> HomePage) =
        copy(pages = pages.map { if (it.id == id) f(it) else it })

    private fun HomeLayout.withoutItem(pageId: String, itemId: String) =
        mapPage(pageId) { p -> p.copy(items = p.items.filter { it.id != itemId }) }

    private fun HomeLayout.withItem(pageId: String, item: HomeItem) =
        mapPage(pageId) { p -> p.copy(items = p.items + item) }

    private fun List<String?>.withSlot(index: Int, key: String?): List<String?> {
        val slots = toMutableList()
        while (slots.size <= index) slots.add(null)
        slots[index] = key
        return slots
    }

    private fun appItem(x: Int, y: Int, key: String) = HomeItem(x = x, y = y, content = ItemContent.App(key))

    fun drop(layout: HomeLayout, source: DragSource, target: DropTarget, columns: Int, rows: Int): HomeLayout =
        when (source) {
            is DragSource.Home -> dropFromHome(layout, source, target, columns, rows)
            is DragSource.Dock -> dropFromDock(layout, source, target, columns, rows)
        }

    private fun dropFromHome(layout: HomeLayout, source: DragSource.Home, target: DropTarget, columns: Int, rows: Int): HomeLayout {
        val fromPage = layout.page(source.pageId) ?: return layout
        val item = fromPage.items.firstOrNull { it.id == source.itemId } ?: return layout
        return when (target) {
            is DropTarget.Cell -> {
                val toPage = layout.page(target.pageId) ?: return layout
                if (item.w > columns || item.h > rows) return layout
                val x = target.x.coerceIn(0, columns - item.w)
                val y = target.y.coerceIn(0, rows - item.h)
                val occupants = toPage.items.filter { it.id != item.id && it.overlaps(x, y, item.w, item.h) }
                val other = occupants.singleOrNull()
                when {
                    occupants.isEmpty() ->
                        layout.withoutItem(fromPage.id, item.id).withItem(toPage.id, item.copy(x = x, y = y))
                    // Same-size item sitting exactly there: swap the two.
                    other != null && other.x == x && other.y == y && other.w == item.w && other.h == item.h ->
                        layout.withoutItem(fromPage.id, item.id)
                            .withoutItem(toPage.id, other.id)
                            .withItem(toPage.id, item.copy(x = x, y = y))
                            .withItem(fromPage.id, other.copy(x = item.x, y = item.y))
                    else -> layout
                }
            }
            is DropTarget.DockSlot -> {
                val app = item.content as? ItemContent.App ?: return layout
                val occupant = layout.dock.getOrNull(target.index)
                val next = layout.withoutItem(fromPage.id, item.id).copy(dock = layout.dock.withSlot(target.index, app.key))
                if (occupant == null) next else next.withItem(fromPage.id, appItem(item.x, item.y, occupant))
            }
        }
    }

    private fun dropFromDock(layout: HomeLayout, source: DragSource.Dock, target: DropTarget, columns: Int, rows: Int): HomeLayout {
        val key = layout.dock.getOrNull(source.slot) ?: return layout
        return when (target) {
            is DropTarget.DockSlot -> {
                val other = layout.dock.getOrNull(target.index)
                layout.copy(dock = layout.dock.withSlot(target.index, key).withSlot(source.slot, other))
            }
            is DropTarget.Cell -> {
                val page = layout.page(target.pageId) ?: return layout
                val x = target.x.coerceIn(0, columns - 1)
                val y = target.y.coerceIn(0, rows - 1)
                val occupants = page.items.filter { it.overlaps(x, y, 1, 1) }
                val other = occupants.singleOrNull()
                val otherKey = (other?.content as? ItemContent.App)?.key
                when {
                    occupants.isEmpty() ->
                        layout.copy(dock = layout.dock.withSlot(source.slot, null)).withItem(page.id, appItem(x, y, key))
                    other != null && otherKey != null && other.w == 1 && other.h == 1 ->
                        layout.copy(dock = layout.dock.withSlot(source.slot, otherKey))
                            .withoutItem(page.id, other.id)
                            .withItem(page.id, appItem(other.x, other.y, key))
                    else -> layout
                }
            }
        }
    }

    fun removeItem(layout: HomeLayout, pageId: String, itemId: String) = layout.withoutItem(pageId, itemId)

    fun clearDock(layout: HomeLayout, slot: Int) = layout.copy(dock = layout.dock.withSlot(slot, null))

    /** Null when every slot is taken. */
    fun addToDock(layout: HomeLayout, key: String, slots: Int): HomeLayout? {
        val free = (0 until slots).firstOrNull { layout.dock.getOrNull(it) == null } ?: return null
        return layout.copy(dock = layout.dock.withSlot(free, key))
    }

    /** Top-left of a free [w]x[h] area, trying ([preferX], [preferY]) first. */
    fun findFree(page: HomePage, w: Int, h: Int, columns: Int, rows: Int, preferX: Int = 0, preferY: Int = 0): Pair<Int, Int>? {
        if (w > columns || h > rows) return null
        fun fits(x: Int, y: Int) = page.items.none { it.overlaps(x, y, w, h) }
        val px = preferX.coerceIn(0, columns - w)
        val py = preferY.coerceIn(0, rows - h)
        if (fits(px, py)) return px to py
        for (y in 0..rows - h) for (x in 0..columns - w) if (fits(x, y)) return x to y
        return null
    }

    /**
     * Places [content] at the preferred cell on [startPage] if free, else the first free area from
     * there on, adding a page when everything is full. Null if the item is larger than the grid.
     */
    fun place(
        layout: HomeLayout,
        content: ItemContent,
        w: Int,
        h: Int,
        startPage: Int,
        columns: Int,
        rows: Int,
        preferX: Int = 0,
        preferY: Int = 0,
    ): Pair<HomeLayout, Int>? {
        if (w > columns || h > rows) return null
        val start = startPage.coerceIn(0, layout.pages.lastIndex)
        for (i in start until layout.pages.size) {
            val page = layout.pages[i]
            val (px, py) = if (i == start) preferX to preferY else 0 to 0
            findFree(page, w, h, columns, rows, px, py)?.let { (x, y) ->
                return layout.withItem(page.id, HomeItem(x = x, y = y, w = w, h = h, content = content)) to i
            }
        }
        val page = HomePage(items = listOf(HomeItem(x = 0, y = 0, w = w, h = h, content = content)))
        return layout.copy(pages = layout.pages + page) to layout.pages.size
    }

    /** Places [key] in the first free cell from [startPage] on, adding a page if all are full. Returns the page index used. */
    fun addApp(layout: HomeLayout, key: String, startPage: Int, columns: Int, rows: Int): Pair<HomeLayout, Int> =
        place(layout, ItemContent.App(key), 1, 1, startPage, columns, rows) ?: (layout to startPage)

    /** Null when the new size would leave the grid or overlap another item. */
    fun resize(layout: HomeLayout, pageId: String, itemId: String, w: Int, h: Int, columns: Int, rows: Int): HomeLayout? {
        val page = layout.page(pageId) ?: return null
        val item = page.items.firstOrNull { it.id == itemId } ?: return null
        if (w < 1 || h < 1 || item.x + w > columns || item.y + h > rows) return null
        if (page.items.any { it.id != itemId && it.overlaps(item.x, item.y, w, h) }) return null
        return layout.mapPage(pageId) { p -> p.copy(items = p.items.map { if (it.id == itemId) it.copy(w = w, h = h) else it }) }
    }

    fun updateContent(layout: HomeLayout, pageId: String, itemId: String, f: (ItemContent) -> ItemContent) =
        layout.mapPage(pageId) { p -> p.copy(items = p.items.map { if (it.id == itemId) it.copy(content = f(it.content)) else it }) }

    fun addPage(layout: HomeLayout, at: Int) =
        layout.copy(pages = layout.pages.toMutableList().apply { add(at.coerceIn(0, size), HomePage()) })

    fun deletePage(layout: HomeLayout, pageId: String) =
        if (layout.pages.size <= 1) layout else layout.copy(pages = layout.pages.filter { it.id != pageId })

    fun movePage(layout: HomeLayout, index: Int, by: Int): HomeLayout {
        val target = index + by
        if (index !in layout.pages.indices || target !in layout.pages.indices) return layout
        return layout.copy(pages = layout.pages.toMutableList().also { Collections.swap(it, index, target) })
    }

    fun removeEmptyPage(layout: HomeLayout, pageId: String) =
        if (layout.pages.size > 1 && layout.page(pageId)?.items?.isEmpty() == true) deletePage(layout, pageId) else layout
}
