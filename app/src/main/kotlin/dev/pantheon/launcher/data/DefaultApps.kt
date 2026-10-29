package dev.pantheon.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager

/** Resolves which installed package fills each common role (dialer, camera, browser…). */
class DefaultApps(private val context: Context) {
    private val pm = context.packageManager

    private fun resolve(intent: Intent): String? =
        runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .getOrNull()
            ?.takeIf { it != "android" && !it.contains("resolver") }

    private fun category(category: String) = resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category))

    val dialer: String? get() = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    val sms: String? get() = Telephony.Sms.getDefaultSmsPackage(context)
    val camera: String? get() = resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    val email: String? get() = resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
    val browser: String? get() = resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
    val gallery: String? get() = category(Intent.CATEGORY_APP_GALLERY)
    val files: String? get() = category(Intent.CATEGORY_APP_FILES)
    val maps: String? get() = category(Intent.CATEGORY_APP_MAPS)
    val music: String? get() = category(Intent.CATEGORY_APP_MUSIC)
    val calendar: String? get() = category(Intent.CATEGORY_APP_CALENDAR)
    val contacts: String? get() = category(Intent.CATEGORY_APP_CONTACTS)
    val clock: String? get() = resolve(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    val settings: String get() = "com.android.settings"
    val store: String get() = "com.android.vending"

    /** Package to tag for apps that hold a role, e.g. the default dialer becomes `ph`. */
    fun roleTags(): Map<String, String> = buildMap {
        fun role(pkg: String?, tag: String) {
            if (pkg != null && pkg !in this) put(pkg, tag)
        }
        role(dialer, "ph")
        role(sms, "msg")
        role(camera, "cam")
        role(email, "mail")
        role(browser, "web")
        role(gallery, "img")
        role(files, "fs")
        role(settings, "set")
        role(calendar, "cal")
        role(maps, "map")
        role(music, "fm")
        role(contacts, "ppl")
        role(clock, "clk")
        role(store, "pkg")
    }
}
