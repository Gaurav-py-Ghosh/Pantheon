package dev.pantheon.launcher.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import androidx.compose.ui.geometry.Rect
import dev.pantheon.launcher.container
import dev.pantheon.launcher.data.AppEntry
import dev.pantheon.launcher.data.DefaultApps
import dev.pantheon.launcher.search.Command
import kotlin.math.roundToInt

object Actions {
    /** Launches with a clip-reveal animation growing out of the tapped icon. */
    fun launchApp(view: View, entry: AppEntry, bounds: Rect?) {
        val rect = bounds?.let {
            android.graphics.Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
        }
        val options = rect?.let {
            ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
        }
        view.context.container.apps.launch(entry, rect, options)
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(context: Context) {
        val expanded = runCatching {
            val statusBar = context.getSystemService("statusbar") ?: return@runCatching false
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar)
            true
        }.getOrDefault(false)
        if (!expanded) {
            dev.pantheon.launcher.accessibility.PantheonAccessibilityService.expandNotifications()
        }
    }

    fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    fun webSearch(context: Context, query: String, engine: String) {
        val base = when (engine) {
            "google" -> "https://www.google.com/search?q="
            "ddg" -> "https://duckduckgo.com/?q="
            "bing" -> "https://www.bing.com/search?q="
            "brave" -> "https://search.brave.com/search?q="
            else -> null
        }
        if (base == null) {
            // "default": hand the query to the default browser so it uses its own engine.
            val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
            DefaultApps(context).browser?.let { pkg ->
                if (start(context, Intent(intent).setPackage(pkg))) return
            }
            if (start(context, intent)) return
        }
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse((base ?: "https://www.google.com/search?q=") + Uri.encode(query))))
    }

    fun uninstall(context: Context, entry: AppEntry, user: UserHandle?) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.packageName}"))
        user?.let { intent.putExtra(Intent.EXTRA_USER, it) }
        start(context, intent)
    }

    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("pantheon", text))
    }

    /** Returns replacement query text for commands that only edit the prompt. */
    fun runCommand(context: Context, command: Command): String? {
        when (command) {
            Command.LS_A -> return "ls -a "
            Command.CALC -> return "calc "
            Command.WIFI -> start(context, Intent(Settings.Panel.ACTION_WIFI))
            Command.BT -> start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            Command.TORCH -> context.container.torch.toggle()
            Command.SETTINGS -> start(context, Intent(Settings.ACTION_SETTINGS))
        }
        return null
    }
}
