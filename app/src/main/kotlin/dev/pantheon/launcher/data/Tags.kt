package dev.pantheon.launcher.data

import android.content.Context

/** Order: user override, role (default dialer etc.), known package preset, generated from the label. */
class TagResolver(context: Context) {
    private val defaults = DefaultApps(context)

    @Volatile
    private var roles: Map<String, String>? = null

    fun warmUp() {
        if (roles == null) roles = defaults.roleTags()
    }

    fun tagFor(entry: AppEntry, custom: Map<String, String>): String =
        custom[entry.key]
            ?: (roles ?: defaults.roleTags().also { roles = it })[entry.packageName]
            ?: TagGenerator.preset(entry.packageName)
            ?: TagGenerator.auto(entry.label)
}

object TagGenerator {
    private val presets = mapOf(
        "com.android.chrome" to "web",
        "org.mozilla.firefox" to "ffx",
        "com.brave.browser" to "brv",
        "com.github.android" to "git",
        "com.termux" to "sh",
        "com.google.android.gm" to "mail",
        "com.google.android.apps.photos" to "img",
        "com.google.android.apps.maps" to "map",
        "com.google.android.youtube" to "yt",
        "com.google.android.apps.youtube.music" to "ytm",
        "com.spotify.music" to "fm",
        "com.whatsapp" to "wa",
        "org.telegram.messenger" to "tg",
        "org.thoughtcrime.securesms" to "sig",
        "com.discord" to "dc",
        "com.instagram.android" to "ig",
        "com.twitter.android" to "x",
        "com.reddit.frontpage" to "rdt",
        "com.netflix.mediaclient" to "nfx",
        "com.google.android.keep" to "txt",
        "com.google.android.calculator" to "calc",
        "com.google.android.apps.docs" to "drv",
        "com.openai.chatgpt" to "gpt",
        "com.anthropic.claude" to "ai",
        "com.google.android.apps.nbu.files" to "fs",
        "com.google.android.documentsui" to "fs",
        "com.android.vending" to "pkg",
        "com.android.settings" to "set",
    )

    fun preset(packageName: String): String? = presets[packageName]

    /** "Play Store" → `ps`, "WhatsApp" → `wa`, "Spotify" → `spt`. */
    fun auto(label: String): String {
        val words = label.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "app"
        if (words.size >= 2) return words.take(3).joinToString("") { it.first().lowercase() }
        val word = words.first()
        val caps = word.filter { it.isUpperCase() }
        if (caps.length >= 2) return caps.take(3).lowercase()
        val lower = word.lowercase()
        if (lower.length <= 3) return lower
        val tag = StringBuilder().append(lower[0])
        for (c in lower.drop(1)) {
            if (tag.length == 3) break
            if (c !in "aeiou") tag.append(c)
        }
        return if (tag.length < 3) lower.take(3) else tag.toString()
    }
}
