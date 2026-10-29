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

    /** First run: dock gets phone/messages/camera/email/settings, page 1 gets common apps. */
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

        val items = home.mapIndexed { i, key -> HomeItem(x = i % 4, y = i / 4, content = ItemContent.App(key)) }
        update { HomeLayout(pages = listOf(HomePage(items = items)), dock = dock, seeded = true) }
    }
}
