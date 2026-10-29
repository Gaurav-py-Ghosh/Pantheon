package dev.pantheon.launcher.data

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ContentUris
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/** Hosting for real Android widgets. */
class WidgetHostRepository(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, HOST_ID)

    fun providers(): List<AppWidgetProviderInfo> {
        val profiles = context.getSystemService(LauncherApps::class.java).profiles
        return profiles.flatMap { runCatching { manager.getInstalledProvidersForProfile(it) }.getOrDefault(emptyList()) }
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    private companion object {
        const val HOST_ID = 0x5041
    }
}

data class AgendaEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

class CalendarRepository(private val context: Context) {
    fun hasAccess() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events from the start of today through the end of tomorrow. */
    suspend fun upcoming(): List<AgendaEvent> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 2 * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val now = System.currentTimeMillis()
        val out = mutableListOf<AgendaEvent>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                ),
                null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val event = AgendaEvent(c.getString(0) ?: "(untitled)", c.getLong(1), c.getLong(2), c.getInt(3) == 1)
                    if (event.allDay || event.end > now) out += event
                }
            }
        }
        out
    }
}

/** One text file per notes widget, cached as flows so edits show up on the home screen at once. */
class NotesRepository(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "notes")
    private val flows = mutableMapOf<String, MutableStateFlow<String>>()

    fun text(id: String): StateFlow<String> = flows.getOrPut(id) {
        MutableStateFlow("").also { flow ->
            scope.launch(Dispatchers.IO) {
                val file = File(dir, "$id.txt")
                if (file.exists()) flow.value = file.readText()
            }
        }
    }

    fun save(id: String, text: String) {
        (text(id) as MutableStateFlow).value = text
        scope.launch(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, "$id.txt").writeText(text)
        }
    }

    fun delete(id: String) {
        flows.remove(id)
        scope.launch(Dispatchers.IO) { File(dir, "$id.txt").delete() }
    }
}
