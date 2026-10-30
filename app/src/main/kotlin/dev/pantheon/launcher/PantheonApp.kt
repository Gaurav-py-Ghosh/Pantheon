package dev.pantheon.launcher

import android.app.Application
import android.content.Context
import dev.pantheon.launcher.data.AppRepository
import dev.pantheon.launcher.data.Brightness
import dev.pantheon.launcher.data.CalendarRepository
import dev.pantheon.launcher.data.IconCache
import dev.pantheon.launcher.data.IconPackManager
import dev.pantheon.launcher.data.LayoutRepository
import dev.pantheon.launcher.data.MediaRepository
import dev.pantheon.launcher.data.MusicRepository
import dev.pantheon.launcher.data.NotesRepository
import dev.pantheon.launcher.data.SettingsRepository
import dev.pantheon.launcher.data.SystemStats
import dev.pantheon.launcher.data.TagResolver
import dev.pantheon.launcher.data.Torch
import dev.pantheon.launcher.data.WidgetHostRepository
import dev.pantheon.launcher.notifications.PantheonNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PantheonApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-wired singletons; a DI framework would cost startup time for no gain here. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(app)
    val iconPacks = IconPackManager(app)
    val icons = IconCache(app, settings, iconPacks, scope)
    val apps = AppRepository(app, scope, onPackageChanged = icons::evictPackage)
    val layout = LayoutRepository(app, scope)
    val stats = SystemStats(app)
    val tags = TagResolver(app)
    val torch by lazy { Torch(app) }
    val media = MediaRepository(app)
    val music = MusicRepository(app, scope)
    val calendar = CalendarRepository(app)
    val notes = NotesRepository(app, scope)
    val widgets = WidgetHostRepository(app)
    val brightness = Brightness(app)
    val notifications = PantheonNotificationListener.counts
    val notificationItems = PantheonNotificationListener.notifications

    init {
        scope.launch(Dispatchers.IO) {
            tags.warmUp()
            val (apps, layout) = combine(apps.apps, layout.layout.filterNotNull(), ::Pair)
                .first { (a, l) -> l.seeded || a.isNotEmpty() }
            if (!layout.seeded) this@AppContainer.layout.seed(apps)
            PantheonNotificationListener.refresh(app)
            music.bindSessions()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as PantheonApp).container
