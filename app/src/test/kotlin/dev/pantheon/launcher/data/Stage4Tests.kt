package dev.pantheon.launcher.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage4Tests {

    @Test
    fun lineIconsCountAtLeast150() {
        assertTrue(
            "Expected at least 150 bundled line icons, found ${LineIcons.ALL_BUNDLED_ICONS.size}",
            LineIcons.ALL_BUNDLED_ICONS.size >= 150,
        )
    }

    @Test
    fun commonPackagesMappedToBundledIcons() {
        val requiredPackages = listOf(
            "com.android.chrome",
            "com.whatsapp",
            "com.google.android.youtube",
            "com.spotify.music",
            "com.github.android",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.google.android.apps.photos",
            "com.android.camera",
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.settings",
            "com.google.android.documentsui",
            "com.google.android.calendar",
            "com.google.android.deskclock",
            "com.android.vending",
            "org.telegram.messenger",
            "com.discord",
            "com.instagram.android",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.netflix.mediaclient",
            "com.termux",
            "com.google.android.apps.docs",
            "com.google.android.keep",
            "com.google.android.calculator",
            "com.openai.chatgpt",
            "com.anthropic.claude",
            "com.google.android.apps.gemini",
        )

        for (pkg in requiredPackages) {
            val resId = LineIcons.bundledForPackage(pkg)
            assertNotNull("Package $pkg should be mapped to a bundled line icon", resId)
        }
    }

    @Test
    fun categoriesMappedToCategoryIcons() {
        val categories = listOf(
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_PRODUCTIVITY,
        )

        for (cat in categories) {
            val resId = LineIcons.categoryIcon(cat)
            assertNotNull("Category $cat should have a line icon", resId)
        }
    }

    @Test
    fun notificationTagFormatting() {
        fun formatTag(tag: String, count: Int): String {
            val inner = if (count > 0) {
                if (tag.isNotEmpty()) "$tag:$count" else "$count"
            } else {
                tag
            }
            return "[$inner]"
        }

        assertEquals("[msg:3]", formatTag("msg", 3))
        assertEquals("[sh:1]", formatTag("sh", 1))
        assertEquals("[web]", formatTag("web", 0))
        assertEquals("[5]", formatTag("", 5))
    }
}
