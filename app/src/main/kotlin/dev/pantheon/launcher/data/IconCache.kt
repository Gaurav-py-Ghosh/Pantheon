package dev.pantheon.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** [monochrome] icons are a single-color glyph meant to be tinted; others are full-color fallbacks. */
class AppIcon(val bitmap: ImageBitmap?, val monochrome: Boolean)

class IconCache(
    private val context: Context,
    private val settings: SettingsRepository,
    private val iconPacks: IconPackManager,
    scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val cache = LruCache<String, AppIcon>(400)
    private val sizePx = (context.resources.displayMetrics.density * 56).roundToInt()

    @Volatile
    private var currentPack: String? = null

    @Volatile
    private var currentOverrides: Map<String, String> = emptyMap()

    var version by mutableIntStateOf(0)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.Default.limitedParallelism(3)

    init {
        scope.launch {
            settings.settings
                .map { it.iconPack to it.iconOverrides }
                .distinctUntilChanged()
                .collect { (pack, overrides) ->
                    currentPack = pack
                    currentOverrides = overrides
                    invalidate()
                }
        }
    }

    fun peek(key: String): AppIcon? = cache.get(key)

    suspend fun load(entry: AppEntry): AppIcon? = cache.get(entry.key) ?: withContext(dispatcher) {
        cache.get(entry.key) ?: render(entry).also { cache.put(entry.key, it) }
    }

    fun evictPackage(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach(cache::remove)
        version++
    }

    fun invalidate() {
        cache.evictAll()
        iconPacks.invalidate()
        version++
    }

    private fun render(entry: AppEntry): AppIcon {
        // 1. Per-app override
        val override = currentOverrides[entry.key]
        if (!override.isNullOrBlank()) {
            val icon = resolveOverride(override)
            if (icon != null) return icon
        }

        // 2. Installed icon pack
        val pack = currentPack
        if (!pack.isNullOrBlank()) {
            val drawable = iconPacks.loadAppDrawable(pack, entry.packageName, entry.className)
            if (drawable != null) {
                return AppIcon(renderDrawable(drawable, sizePx), monochrome = false)
            }
        }

        // 3. Bundled line icons
        val bundledRes = LineIcons.bundledForPackage(entry.packageName)
        if (bundledRes != null) {
            val drawable = ContextCompat.getDrawable(context, bundledRes)
            if (drawable != null) {
                return AppIcon(renderDrawable(drawable, sizePx), monochrome = true)
            }
        }

        // 4. Monochrome adaptive layer
        val monoIcon = renderMonochrome(entry)
        if (monoIcon != null) {
            return monoIcon
        }

        // 5. Category line icon
        val catRes = LineIcons.categoryIcon(entry.category)
        if (catRes != null) {
            val drawable = ContextCompat.getDrawable(context, catRes)
            if (drawable != null) {
                return AppIcon(renderDrawable(drawable, sizePx), monochrome = true)
            }
        }

        // 6. Last resort: sentinel for boxed tag
        return AppIcon(bitmap = null, monochrome = true)
    }

    private fun resolveOverride(override: String): AppIcon? {
        if (override.startsWith("line:")) {
            val resName = override.removePrefix("line:")
            val item = LineIcons.findByResName(resName)
            val resId = item?.resId ?: context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                val drawable = ContextCompat.getDrawable(context, resId)
                if (drawable != null) {
                    return AppIcon(renderDrawable(drawable, sizePx), monochrome = true)
                }
            }
        } else if (override.startsWith("pack:")) {
            val spec = override.removePrefix("pack:")
            val slash = spec.indexOf('/')
            if (slash > 0) {
                val packPkg = spec.substring(0, slash)
                val drawName = spec.substring(slash + 1)
                val drawable = iconPacks.loadDrawable(packPkg, drawName)
                if (drawable != null) {
                    return AppIcon(renderDrawable(drawable, sizePx), monochrome = false)
                }
            }
        }
        return null
    }

    private fun renderMonochrome(entry: AppEntry): AppIcon? {
        val user = userManager.getUserForSerialNumber(entry.userSerial) ?: return null
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
        val info = runCatching { launcherApps.resolveActivity(intent, user) }.getOrNull() ?: return null
        val drawable = runCatching { info.getIcon(context.resources.displayMetrics.densityDpi) }.getOrNull() ?: return null
        if (Build.VERSION.SDK_INT < 33) return null
        val mono = (drawable as? AdaptiveIconDrawable)?.monochrome ?: return null

        val s = sizePx
        val bitmap = createBitmap(s, s)
        val canvas = Canvas(bitmap)
        val pad = s / 4
        mono.setBounds(-pad, -pad, s + pad, s + pad)
        mono.draw(canvas)
        return AppIcon(bitmap.asImageBitmap(), monochrome = true)
    }

    private fun renderDrawable(drawable: Drawable, s: Int): ImageBitmap {
        val bitmap = createBitmap(s, s)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, s, s)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
