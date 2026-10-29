package dev.pantheon.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** [monochrome] icons are a single-color glyph meant to be tinted; others are full-color fallbacks. */
class AppIcon(val bitmap: ImageBitmap, val monochrome: Boolean)

class IconCache(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val cache = LruCache<String, AppIcon>(400)
    private val sizePx = (context.resources.displayMetrics.density * 56).roundToInt()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.Default.limitedParallelism(3)

    fun peek(key: String): AppIcon? = cache.get(key)

    suspend fun load(entry: AppEntry): AppIcon? = cache.get(entry.key) ?: withContext(dispatcher) {
        cache.get(entry.key) ?: render(entry)?.also { cache.put(entry.key, it) }
    }

    fun evictPackage(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach(cache::remove)
    }

    private fun render(entry: AppEntry): AppIcon? {
        val user = userManager.getUserForSerialNumber(entry.userSerial) ?: return null
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
        val info = runCatching { launcherApps.resolveActivity(intent, user) }.getOrNull() ?: return null
        val drawable = runCatching { info.getIcon(context.resources.displayMetrics.densityDpi) }.getOrNull() ?: return null
        val mono = if (Build.VERSION.SDK_INT >= 33) (drawable as? AdaptiveIconDrawable)?.monochrome else null

        val s = sizePx
        val bitmap = createBitmap(s, s)
        val canvas = Canvas(bitmap)
        if (mono != null) {
            // Adaptive layers are 108dp with a 72dp visible viewport: overdraw 25% on each side.
            val pad = s / 4
            mono.setBounds(-pad, -pad, s + pad, s + pad)
            mono.draw(canvas)
        } else {
            drawable.setBounds(0, 0, s, s)
            drawable.draw(canvas)
        }
        return AppIcon(bitmap.asImageBitmap(), monochrome = mono != null)
    }
}
