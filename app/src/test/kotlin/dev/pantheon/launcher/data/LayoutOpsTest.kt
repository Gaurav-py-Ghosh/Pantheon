package dev.pantheon.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LayoutOpsTest {
    private fun app(id: String, x: Int, y: Int, key: String = id) =
        HomeItem(id = id, x = x, y = y, content = ItemContent.App(key))

    private fun layout(vararg items: HomeItem, dock: List<String?> = emptyList()) =
        HomeLayout(pages = listOf(HomePage(id = "p1", items = items.toList()), HomePage(id = "p2")), dock = dock, seeded = true)

    private fun HomeLayout.at(pageId: String, x: Int, y: Int) =
        pages.first { it.id == pageId }.items.firstOrNull { it.x == x && it.y == y }?.id

    @Test fun movesIntoEmptyCell() {
        val out = LayoutOps.drop(layout(app("a", 0, 0)), DragSource.Home("p1", "a"), DropTarget.Cell("p1", 2, 3), 4, 6)
        assertEquals("a", out.at("p1", 2, 3))
        assertNull(out.at("p1", 0, 0))
    }

    @Test fun swapsWithOccupant() {
        val out = LayoutOps.drop(layout(app("a", 0, 0), app("b", 1, 0)), DragSource.Home("p1", "a"), DropTarget.Cell("p1", 1, 0), 4, 6)
        assertEquals("a", out.at("p1", 1, 0))
        assertEquals("b", out.at("p1", 0, 0))
    }

    @Test fun movesAcrossPages() {
        val out = LayoutOps.drop(layout(app("a", 0, 0)), DragSource.Home("p1", "a"), DropTarget.Cell("p2", 3, 1), 4, 6)
        assertEquals("a", out.at("p2", 3, 1))
        assertEquals(0, out.pages[0].items.size)
    }

    @Test fun homeToOccupiedDockSwapsOccupantBack() {
        val out = LayoutOps.drop(layout(app("a", 2, 2), dock = listOf("d0", "d1")), DragSource.Home("p1", "a"), DropTarget.DockSlot(1), 4, 6)
        assertEquals(listOf("d0", "a"), out.dock)
        val back = out.pages[0].items.single()
        assertEquals(ItemContent.App("d1"), back.content)
        assertEquals(2 to 2, back.x to back.y)
    }

    @Test fun dockToEmptyCellFreesSlot() {
        val out = LayoutOps.drop(layout(dock = listOf("d0", "d1")), DragSource.Dock(0), DropTarget.Cell("p1", 1, 1), 4, 6)
        assertEquals(listOf(null, "d1"), out.dock)
        assertEquals(ItemContent.App("d0"), out.pages[0].items.single().content)
    }

    @Test fun dockSlotsSwap() {
        val out = LayoutOps.drop(layout(dock = listOf("d0", "d1", null)), DragSource.Dock(0), DropTarget.DockSlot(2), 4, 6)
        assertEquals(listOf(null, "d1", "d0"), out.dock)
    }

    @Test fun addAppFillsFirstFreeCellThenNewPage() {
        val full = layout(app("a", 0, 0), app("b", 1, 0))
        val (next, page) = LayoutOps.addApp(full, "c", 0, 2, 1)
        assertEquals(1, page)
        assertEquals("c", (next.pages[1].items.single().content as ItemContent.App).key)
        val (grown, newPage) = LayoutOps.addApp(next, "d", 0, 1, 1)
        assertEquals(2, newPage)
        assertEquals(3, grown.pages.size)
    }

    @Test fun addToDockFullReturnsNull() {
        assertNull(LayoutOps.addToDock(layout(dock = listOf("a", "b")), "c", 2))
        assertEquals(listOf("a", "c"), LayoutOps.addToDock(layout(dock = listOf("a", null)), "c", 2)?.dock)
    }

    @Test fun onlyPageIsNeverDeleted() {
        val single = HomeLayout(pages = listOf(HomePage(id = "p1")))
        assertSame(single, LayoutOps.deletePage(single, "p1"))
        assertSame(single, LayoutOps.removeEmptyPage(single, "p1"))
    }

    @Test fun placeUsesPreferredCellWhenFree() {
        val (out, page) = LayoutOps.place(layout(), ItemContent.Clock(), 2, 2, 0, 4, 6, preferX = 1, preferY = 3)!!
        assertEquals(0, page)
        val clock = out.pages[0].items.single()
        assertEquals(listOf(1, 3, 2, 2), listOf(clock.x, clock.y, clock.w, clock.h))
    }

    @Test fun placeFindsFreeAreaAroundItems() {
        val (out, _) = LayoutOps.place(layout(app("a", 0, 0), app("b", 1, 1)), ItemContent.Notes(), 2, 2, 0, 4, 6, preferX = 0, preferY = 0)!!
        val notes = out.pages[0].items.first { it.content is ItemContent.Notes }
        assertEquals(2 to 0, notes.x to notes.y)
    }

    @Test fun placeRejectsWidgetLargerThanGrid() {
        assertNull(LayoutOps.place(layout(), ItemContent.Photo(), 5, 2, 0, 4, 6))
    }

    @Test fun resizeGrowsIntoFreeSpaceOnly() {
        val base = layout(HomeItem(id = "w", x = 0, y = 0, w = 2, h = 2, content = ItemContent.Clock()), app("a", 3, 0))
        val grown = LayoutOps.resize(base, "p1", "w", 3, 2, 4, 6)!!
        assertEquals(3, grown.pages[0].items.first { it.id == "w" }.w)
        assertNull(LayoutOps.resize(base, "p1", "w", 4, 2, 4, 6))
        assertNull(LayoutOps.resize(base, "p1", "w", 2, 7, 4, 6))
    }

    @Test fun widgetMoveNeedsWholeAreaFree() {
        val base = layout(HomeItem(id = "w", x = 0, y = 0, w = 2, h = 2, content = ItemContent.Clock()), app("a", 3, 3))
        val moved = LayoutOps.drop(base, DragSource.Home("p1", "w"), DropTarget.Cell("p1", 2, 2), 4, 6)
        assertSame(base, moved)
        val ok = LayoutOps.drop(base, DragSource.Home("p1", "w"), DropTarget.Cell("p1", 0, 4), 4, 6)
        assertEquals(0 to 4, ok.pages[0].items.first { it.id == "w" }.let { it.x to it.y })
    }
}
