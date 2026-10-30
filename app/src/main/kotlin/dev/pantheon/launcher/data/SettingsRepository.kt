package dev.pantheon.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
enum class LabelStyle { ICON_TAG_NAME, ICON_TAG, ICON, TAG }

@Serializable
enum class DrawerMode { AZ, CATEGORY }

@Serializable
enum class BackgroundMode { SOLID, WALLPAPER }

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Accent preset id or `#RRGGBB`. */
    val accent: String = "green",
    val columns: Int = 4,
    val dockSlots: Int = 5,
    val labelStyle: LabelStyle = LabelStyle.ICON_TAG_NAME,
    val dockLabels: Boolean = true,
    val hidden: Set<String> = emptySet(),
    /** App key to custom tag. */
    val tags: Map<String, String> = emptyMap(),
    /** `default` (browser's engine), `google`, `ddg`, `bing`, `brave`. */
    val searchEngine: String = "default",
    val drawerMode: DrawerMode = DrawerMode.AZ,
    val glow: Boolean = true,
    /** Default fill (crop) vs fit for new photo and image widgets. */
    val imageFill: Boolean = true,
    /** Package name of the active icon pack, or null for default. */
    val iconPack: String? = null,
    /** App key to icon specifier (`line:res_name` or `pack:pkg/drawable_name`). */
    val iconOverrides: Map<String, String> = emptyMap(),
    /** Whether first-run boot setup wizard has completed. */
    val setupDone: Boolean = false,
    /** Double-tap empty space on home screen to lock. */
    val doubleTapLock: Boolean = false,
    /** Solid terminal background or wallpaper with dark/light overlay. */
    val backgroundMode: BackgroundMode = BackgroundMode.SOLID,
    /** CRT scanline overlay effect. */
    val scanlines: Boolean = false,
    /** Terminal glitch effect on drawer transition. */
    val glitch: Boolean = false,
    /** Typewriter effect on status line return. */
    val typewriter: Boolean = true,
    /** Hide system status bar for full-bleed terminal mode. */
    val hideStatusBar: Boolean = true,
    /** Terminal lockscreen with dithered Creation of Adam art and biometric auth. */
    val lockScreen: Boolean = true,
    /** Lock screen art: `adam` (Creation of Adam), `none`, or an imported image file name. */
    val lockArt: String = "adam",
    /** The pixel cat in the top strip. */
    val pet: Boolean = true,
    /** Dynamic Music Island banner & pill when music is playing. */
    val musicIsland: Boolean = true,
    /** Type of pixel pet: cat, dog, dino, duck, ghost, crab. */
    val petType: String = "cat",
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val columns = intPreferencesKey("columns")
        val dockSlots = intPreferencesKey("dock_slots")
        val labelStyle = stringPreferencesKey("label_style")
        val dockLabels = booleanPreferencesKey("dock_labels")
        val hidden = stringSetPreferencesKey("hidden")
        val tags = stringPreferencesKey("tags")
        val searchEngine = stringPreferencesKey("search_engine")
        val drawerMode = stringPreferencesKey("drawer_mode")
        val glow = booleanPreferencesKey("glow")
        val imageFill = booleanPreferencesKey("image_fill")
        val iconPack = stringPreferencesKey("icon_pack")
        val iconOverrides = stringPreferencesKey("icon_overrides")
        val setupDone = booleanPreferencesKey("setup_done")
        val doubleTapLock = booleanPreferencesKey("double_tap_lock")
        val backgroundMode = stringPreferencesKey("background_mode")
        val scanlines = booleanPreferencesKey("scanlines")
        val glitch = booleanPreferencesKey("glitch")
        val typewriter = booleanPreferencesKey("typewriter")
        val hideStatusBar = booleanPreferencesKey("hide_status_bar")
        val lockScreen = booleanPreferencesKey("lock_screen")
        val lockArt = stringPreferencesKey("lock_art")
        val pet = booleanPreferencesKey("pet")
        val musicIsland = booleanPreferencesKey("music_island")
        val petType = stringPreferencesKey("pet_type")
    }

    val settings: Flow<Settings> = context.dataStore.data.map(::read).distinctUntilChanged()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    suspend fun replace(newSettings: Settings) {
        context.dataStore.edit { prefs -> write(prefs, newSettings) }
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            themeMode = enumOr(p[K.theme], d.themeMode),
            accent = p[K.accent] ?: d.accent,
            columns = (p[K.columns] ?: d.columns).coerceIn(4, 6),
            dockSlots = (p[K.dockSlots] ?: d.dockSlots).coerceIn(4, 6),
            labelStyle = enumOr(p[K.labelStyle], d.labelStyle),
            dockLabels = p[K.dockLabels] ?: d.dockLabels,
            hidden = p[K.hidden] ?: d.hidden,
            tags = p[K.tags]?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: d.tags,
            searchEngine = p[K.searchEngine] ?: d.searchEngine,
            drawerMode = enumOr(p[K.drawerMode], d.drawerMode),
            glow = p[K.glow] ?: d.glow,
            imageFill = p[K.imageFill] ?: d.imageFill,
            iconPack = p[K.iconPack],
            iconOverrides = p[K.iconOverrides]?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: d.iconOverrides,
            setupDone = p[K.setupDone] ?: d.setupDone,
            doubleTapLock = p[K.doubleTapLock] ?: d.doubleTapLock,
            backgroundMode = enumOr(p[K.backgroundMode], d.backgroundMode),
            scanlines = p[K.scanlines] ?: d.scanlines,
            glitch = p[K.glitch] ?: d.glitch,
            typewriter = p[K.typewriter] ?: d.typewriter,
            hideStatusBar = p[K.hideStatusBar] ?: d.hideStatusBar,
            lockScreen = p[K.lockScreen] ?: d.lockScreen,
            lockArt = p[K.lockArt] ?: d.lockArt,
            pet = p[K.pet] ?: d.pet,
            musicIsland = p[K.musicIsland] ?: d.musicIsland,
            petType = p[K.petType] ?: d.petType,
        )
    }

    private fun write(p: MutablePreferences, s: Settings) {
        p[K.theme] = s.themeMode.name
        p[K.accent] = s.accent
        p[K.columns] = s.columns
        p[K.dockSlots] = s.dockSlots
        p[K.labelStyle] = s.labelStyle.name
        p[K.dockLabels] = s.dockLabels
        p[K.hidden] = s.hidden
        p[K.tags] = Json.encodeToString(s.tags)
        p[K.searchEngine] = s.searchEngine
        p[K.drawerMode] = s.drawerMode.name
        p[K.glow] = s.glow
        p[K.imageFill] = s.imageFill
        if (s.iconPack != null) p[K.iconPack] = s.iconPack else p.remove(K.iconPack)
        p[K.iconOverrides] = Json.encodeToString(s.iconOverrides)
        p[K.setupDone] = s.setupDone
        p[K.doubleTapLock] = s.doubleTapLock
        p[K.backgroundMode] = s.backgroundMode.name
        p[K.scanlines] = s.scanlines
        p[K.glitch] = s.glitch
        p[K.typewriter] = s.typewriter
        p[K.hideStatusBar] = s.hideStatusBar
        p[K.lockScreen] = s.lockScreen
        p[K.lockArt] = s.lockArt
        p[K.pet] = s.pet
        p[K.musicIsland] = s.musicIsland
        p[K.petType] = s.petType
    }
}

private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
    value?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
