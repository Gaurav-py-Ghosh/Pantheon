package dev.pantheon.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String,
)

data class AppFilterData(
    val components: Map<String, String>,
    val packages: Map<String, String>,
    val allDrawables: List<String>,
)

class IconPackManager(private val context: Context) {
    private val pm = context.packageManager
    private val filterCache = ConcurrentHashMap<String, AppFilterData>()

    fun getInstalledPacks(): List<IconPackInfo> {
        val adw = Intent("org.adw.launcher.THEMES")
        val nova = Intent("com.novalauncher.THEME")
        val flags = PackageManager.GET_META_DATA
        val adwList = pm.queryIntentActivities(adw, flags)
        val novaList = pm.queryIntentActivities(nova, flags)
        val allPkgs = (adwList + novaList)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()

        return allPkgs.mapNotNull { pkg ->
            runCatching {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                IconPackInfo(pkg, label)
            }.getOrNull()
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun getAppFilter(packPackage: String): AppFilterData {
        return filterCache.getOrPut(packPackage) {
            parseAppFilter(packPackage)
        }
    }

    fun loadAppDrawable(packPackage: String, packageName: String, className: String): Drawable? {
        val filter = getAppFilter(packPackage)
        val shortClass = if (className.startsWith(packageName)) className.removePrefix(packageName) else className
        val drawableName = filter.components["$packageName/$className"]
            ?: filter.components["$packageName/$shortClass"]
            ?: filter.packages[packageName]
            ?: return null
        return loadDrawable(packPackage, drawableName)
    }

    fun loadDrawable(packPackage: String, drawableName: String): Drawable? {
        val res = runCatching { pm.getResourcesForApplication(packPackage) }.getOrNull() ?: return null
        val id = res.getIdentifier(drawableName, "drawable", packPackage)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    private fun parseAppFilter(packPackage: String): AppFilterData {
        val res = runCatching { pm.getResourcesForApplication(packPackage) }.getOrNull()
            ?: return AppFilterData(emptyMap(), emptyMap(), emptyList())

        // 1. Try res/xml/appfilter.xml
        val xmlId = res.getIdentifier("appfilter", "xml", packPackage)
        if (xmlId != 0) {
            val parser = runCatching { res.getXml(xmlId) }.getOrNull()
            if (parser != null) {
                return parseXml(parser)
            }
        }

        // 2. Try assets/appfilter.xml
        val assetStream = runCatching { res.assets.open("appfilter.xml") }.getOrNull()
        if (assetStream != null) {
            return assetStream.use { stream ->
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                val parser = factory.newPullParser()
                parser.setInput(stream, "UTF-8")
                parseXml(parser)
            }
        }

        return AppFilterData(emptyMap(), emptyMap(), emptyList())
    }

    private fun parseXml(parser: XmlPullParser): AppFilterData {
        val components = mutableMapOf<String, String>()
        val packages = mutableMapOf<String, String>()
        val drawables = mutableSetOf<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                val comp = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!comp.isNullOrBlank() && !drawable.isNullOrBlank()) {
                    val clean = cleanComponent(comp)
                    if (clean.isNotEmpty()) {
                        components[clean] = drawable
                        val pkg = clean.substringBefore('/')
                        packages.putIfAbsent(pkg, drawable)
                    }
                    drawables.add(drawable)
                }
            }
            event = parser.next()
        }
        return AppFilterData(components, packages, drawables.sorted())
    }

    private fun cleanComponent(raw: String): String {
        // Formats: ComponentInfo{pkg/cls} or ComponentInfo{pkg/cls } or pkg/cls
        val start = raw.indexOf('{')
        val end = raw.indexOf('}')
        val s = if (start >= 0 && end > start) {
            raw.substring(start + 1, end)
        } else {
            raw
        }
        return s.trim().replace("\\s+".toRegex(), "")
    }

    fun invalidate() {
        filterCache.clear()
    }
}
