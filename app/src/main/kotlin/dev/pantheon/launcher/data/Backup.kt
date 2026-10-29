package dev.pantheon.launcher.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PantheonBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: Settings,
    val layout: HomeLayout,
)

object BackupManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToString(settings: Settings, layout: HomeLayout): String {
        val backup = PantheonBackup(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            settings = settings,
            layout = layout,
        )
        return json.encodeToString(backup)
    }

    fun importFromString(content: String): PantheonBackup? = runCatching {
        json.decodeFromString<PantheonBackup>(content)
    }.getOrNull()
}
