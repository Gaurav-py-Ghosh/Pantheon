package dev.pantheon.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppEntry(
    /** Stable id: `pkg/.Activity#userSerial`. */
    val key: String,
    val label: String,
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val isWork: Boolean,
    val category: Int,
) {
    val component: ComponentName get() = ComponentName(packageName, className)
}

/**
 * Every launchable activity across all profiles. Serves a disk cache first so the
 * home screen draws on the first frame, then refreshes from [LauncherApps].
 */
class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPackageChanged: (String) -> Unit,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val cache = AtomicFile(File(context.filesDir, "apps-cache.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshLock = Mutex()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            runCatching { json.decodeFromString<List<AppEntry>>(String(cache.readFully())) }
                .getOrNull()
                ?.let { cached -> if (_apps.value.isEmpty()) _apps.value = cached }
            refresh()
        }
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                packageNames.forEach(::changed)
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                packageNames.forEach(::changed)
        }, Handler(Looper.getMainLooper()))
    }

    private fun changed(packageName: String) {
        onPackageChanged(packageName)
        scope.launch(Dispatchers.IO) { refresh() }
    }

    private suspend fun refresh() = refreshLock.withLock {
        val me = Process.myUserHandle()
        val list = launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { info ->
                AppEntry(
                    key = "${info.componentName.flattenToShortString()}#$serial",
                    label = info.label.toString().trim(),
                    packageName = info.componentName.packageName,
                    className = info.componentName.className,
                    userSerial = serial,
                    isWork = user != me,
                    category = info.applicationInfo.category,
                )
            }
        }
            .filter { it.packageName != context.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        _apps.value = list
        runCatching {
            val out = cache.startWrite()
            try {
                out.write(json.encodeToString(list).toByteArray())
                cache.finishWrite(out)
            } catch (e: Exception) {
                cache.failWrite(out)
            }
        }
    }

    fun userFor(entry: AppEntry): UserHandle? = userManager.getUserForSerialNumber(entry.userSerial)

    fun launch(entry: AppEntry, sourceBounds: Rect?, options: Bundle?) {
        val user = userFor(entry) ?: return
        runCatching { launcherApps.startMainActivity(entry.component, user, sourceBounds, options) }
    }

    fun openAppInfo(entry: AppEntry, sourceBounds: Rect?) {
        val user = userFor(entry) ?: return
        runCatching { launcherApps.startAppDetailsActivity(entry.component, user, sourceBounds, null) }
    }
}
