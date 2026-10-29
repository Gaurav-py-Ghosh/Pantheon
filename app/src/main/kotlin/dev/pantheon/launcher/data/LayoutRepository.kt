package dev.pantheon.launcher.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

private fun newId() = UUID.randomUUID().toString()

@Serializable
data class HomeLayout(
    val pages: List<HomePage> = listOf(HomePage()),
    /** App keys by slot; null is an empty slot. */
    val dock: List<String?> = emptyList(),
    val seeded: Boolean = false,
)

@Serializable
data class HomePage(
    val id: String = newId(),
    val items: List<HomeItem> = emptyList(),
)

/** An item on the cell grid; [x], [y] are cell coordinates, [w], [h] cell spans. */
@Serializable
data class HomeItem(
    val id: String = newId(),
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val content: ItemContent,
)

@Serializable
sealed interface ItemContent {
    @Serializable
    @SerialName("app")
    data class App(val key: String) : ItemContent

    /** Recent photos from a MediaStore album; null bucket means all photos. */
    @Serializable
    @SerialName("photo")
    data class Photo(
        val bucketId: String? = null,
        val bucketName: String? = null,
        val intervalSec: Int = 30,
        val fill: Boolean = true,
        val frame: Boolean = true,
    ) : ItemContent

    /** User-picked images copied into app storage (file names under files/images). */
    @Serializable
    @SerialName("image")
    data class Images(
        val files: List<String> = emptyList(),
        val intervalSec: Int = 10,
        val fill: Boolean = true,
        val frame: Boolean = true,
    ) : ItemContent

    @Serializable
    @SerialName("clock")
    data class Clock(val frame: Boolean = true) : ItemContent

    @Serializable
    @SerialName("sysmon")
    data class Sysmon(val frame: Boolean = true) : ItemContent

    @Serializable
    @SerialName("calendar")
    data class Agenda(val frame: Boolean = true) : ItemContent

    @Serializable
    @SerialName("notes")
    data class Notes(val frame: Boolean = true) : ItemContent

    @Serializable
    @SerialName("pet")
    data class Pet(val frame: Boolean = false) : ItemContent

    /** A real Android widget hosted through AppWidgetHost. */
    @Serializable
    @SerialName("appwidget")
    data class AppWidget(val appWidgetId: Int, val provider: String, val frame: Boolean = false) : ItemContent
}

val ItemContent.hasFrame: Boolean
    get() = when (this) {
        is ItemContent.App -> false
        is ItemContent.Photo -> frame
        is ItemContent.Images -> frame
        is ItemContent.Clock -> frame
        is ItemContent.Sysmon -> frame
        is ItemContent.Agenda -> frame
        is ItemContent.Notes -> frame
        is ItemContent.Pet -> frame
        is ItemContent.AppWidget -> frame
    }

fun ItemContent.withFrame(on: Boolean): ItemContent = when (this) {
    is ItemContent.App -> this
    is ItemContent.Photo -> copy(frame = on)
    is ItemContent.Images -> copy(frame = on)
    is ItemContent.Clock -> copy(frame = on)
    is ItemContent.Sysmon -> copy(frame = on)
    is ItemContent.Agenda -> copy(frame = on)
    is ItemContent.Notes -> copy(frame = on)
    is ItemContent.Pet -> copy(frame = on)
    is ItemContent.AppWidget -> copy(frame = on)
}

class LayoutRepository(context: Context, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "layout.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val defaults = DefaultApps(context)
    private val writeLock = Mutex()

    private val _layout = MutableStateFlow<HomeLayout?>(null)
    val layout: StateFlow<HomeLayout?> = _layout.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            _layout.value = runCatching { json.decodeFromString<HomeLayout>(String(file.readFully())) }.getOrNull()
                ?: HomeLayout()
        }
    }

    fun update(transform: (HomeLayout) -> HomeLayout) {
        _layout.updateAndGet { it?.let(transform) } ?: return
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                val latest = _layout.value ?: return@withLock
                val out = file.startWrite()
                try {
                    out.write(json.encodeToString(latest).toByteArray())
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                }
            }
        }
    }

    /** First run: dock gets phone/messages/camera/email/settings, page 1 gets a clock and common apps. */
    fun seed(apps: List<AppEntry>) {
        val byPackage = apps.filter { !it.isWork }.groupBy { it.packageName }.mapValues { it.value.first().key }
        fun pick(pkg: String?) = pkg?.let(byPackage::get)

        val dock = listOfNotNull(
            pick(defaults.dialer), pick(defaults.sms), pick(defaults.camera), pick(defaults.email), pick(defaults.settings),
        ).distinct()
        val home = listOf(
            defaults.browser, defaults.files, "com.termux", "com.github.android",
            defaults.gallery, defaults.maps, defaults.music, defaults.store, defaults.calendar, defaults.clock,
        ).mapNotNull(::pick).distinct().filter { it !in dock }.take(8)

        val clock = HomeItem(x = 0, y = 0, w = 4, h = 2, content = ItemContent.Clock())
        val items = listOf(clock) + home.mapIndexed { i, key -> HomeItem(x = i % 4, y = 2 + i / 4, content = ItemContent.App(key)) }
        update { HomeLayout(pages = listOf(HomePage(items = items)), dock = dock, seeded = true) }
    }
}
