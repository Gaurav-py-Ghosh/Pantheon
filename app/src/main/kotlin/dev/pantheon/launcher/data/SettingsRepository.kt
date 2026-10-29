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
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class LabelStyle { ICON_TAG_NAME, ICON_TAG, ICON, TAG }
enum class DrawerMode { AZ, CATEGORY }

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
    }

    val settings: Flow<Settings> = context.dataStore.data.map(::read).distinctUntilChanged()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
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
    }
}

private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
    value?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
