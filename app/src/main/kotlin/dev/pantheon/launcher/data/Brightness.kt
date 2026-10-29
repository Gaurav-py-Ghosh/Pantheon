package dev.pantheon.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Screen brightness through the system setting, so it sticks after leaving the launcher.
 * Writing needs the "modify system settings" grant, which the user gives once in Android settings.
 */
class Brightness(private val context: Context) {
    private val resolver = context.contentResolver

    fun canWrite(): Boolean = Settings.System.canWrite(context)

    /** 0..1 */
    fun level(): Float =
        runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)

    fun isAuto(): Boolean = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    /** Dragging the slider switches adaptive brightness off, as the system slider does. */
    fun setLevel(level: Float): Boolean = runCatching {
        if (isAuto()) setAuto(false)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (1 + level.coerceIn(0f, 1f) * 254).toInt())
    }.getOrDefault(false)

    fun setAuto(on: Boolean): Boolean = runCatching {
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }.getOrDefault(false)

    fun requestWriteAccess() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
