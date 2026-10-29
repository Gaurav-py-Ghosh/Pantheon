package dev.pantheon.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage5Tests {

    @Test
    fun defaultSettingsIncludeStage5Fields() {
        val s = Settings()
        assertFalse("setupDone should default to false", s.setupDone)
        assertFalse("doubleTapLock should default to false", s.doubleTapLock)
        assertEquals("backgroundMode should default to SOLID", BackgroundMode.SOLID, s.backgroundMode)
        assertFalse("scanlines should default to false", s.scanlines)
        assertFalse("glitch should default to false", s.glitch)
        assertTrue("typewriter should default to true", s.typewriter)
    }

    @Test
    fun backupExportAndImportRoundTrip() {
        val customSettings = Settings(
            themeMode = ThemeMode.DARK,
            accent = "#00FFCC",
            columns = 5,
            dockSlots = 6,
            labelStyle = LabelStyle.ICON_TAG,
            dockLabels = false,
            hidden = setOf("com.example.hidden/.MainActivity#0"),
            tags = mapOf("pkg/.Act#0" to "cst"),
            searchEngine = "brave",
            drawerMode = DrawerMode.CATEGORY,
            glow = false,
            imageFill = false,
            iconPack = "com.theme.minimal",
            iconOverrides = mapOf("pkg/.Act#0" to "line:terminal"),
            setupDone = true,
            doubleTapLock = true,
            backgroundMode = BackgroundMode.WALLPAPER,
            scanlines = true,
            glitch = true,
            typewriter = false,
        )

        val layout = HomeLayout(
            pages = listOf(
                HomePage(
                    items = listOf(
                        HomeItem(x = 0, y = 0, w = 4, h = 2, content = ItemContent.Clock(frame = true)),
                        HomeItem(x = 0, y = 2, w = 1, h = 1, content = ItemContent.App("pkg/.Act#0")),
                        HomeItem(x = 1, y = 2, w = 2, h = 2, content = ItemContent.Notes(frame = false)),
                    ),
                ),
            ),
            dock = listOf("pkg/.Dialer#0", null, "pkg/.Browser#0"),
            seeded = true,
        )

        val exportedJson = BackupManager.exportToString(customSettings, layout)
        assertTrue("Exported JSON should not be empty", exportedJson.isNotBlank())
        assertTrue("Exported JSON should contain accent", exportedJson.contains("#00FFCC"))
        assertTrue("Exported JSON should contain WALLPAPER", exportedJson.contains("WALLPAPER"))

        val imported = BackupManager.importFromString(exportedJson)
        assertNotNull("Imported backup should not be null", imported)
        assertEquals(1, imported!!.version)

        val s = imported.settings
        assertEquals(customSettings.accent, s.accent)
        assertEquals(customSettings.columns, s.columns)
        assertEquals(customSettings.dockSlots, s.dockSlots)
        assertEquals(customSettings.labelStyle, s.labelStyle)
        assertEquals(customSettings.dockLabels, s.dockLabels)
        assertEquals(customSettings.hidden, s.hidden)
        assertEquals(customSettings.tags, s.tags)
        assertEquals(customSettings.searchEngine, s.searchEngine)
        assertEquals(customSettings.drawerMode, s.drawerMode)
        assertEquals(customSettings.glow, s.glow)
        assertEquals(customSettings.imageFill, s.imageFill)
        assertEquals(customSettings.iconPack, s.iconPack)
        assertEquals(customSettings.iconOverrides, s.iconOverrides)
        assertEquals(customSettings.setupDone, s.setupDone)
        assertEquals(customSettings.doubleTapLock, s.doubleTapLock)
        assertEquals(customSettings.backgroundMode, s.backgroundMode)
        assertEquals(customSettings.scanlines, s.scanlines)
        assertEquals(customSettings.glitch, s.glitch)
        assertEquals(customSettings.typewriter, s.typewriter)

        val l = imported.layout
        assertEquals(layout.pages.size, l.pages.size)
        assertEquals(layout.dock, l.dock)
        assertEquals(layout.seeded, l.seeded)
        assertEquals(3, l.pages[0].items.size)
    }

    @Test
    fun backupImportInvalidJsonReturnsNull() {
        val corrupted = "{ \"version\": 1, \"settings\": { broken json ... "
        val result = BackupManager.importFromString(corrupted)
        assertNull("Corrupted JSON should safely return null", result)
    }
}
