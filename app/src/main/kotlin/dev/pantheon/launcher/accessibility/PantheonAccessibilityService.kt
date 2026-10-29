package dev.pantheon.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class PantheonAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg == "com.android.systemui" && isPantheonForeground) {
                if (allowNativeShadeOnce) {
                    allowNativeShadeOnce = false
                } else {
                    // Native shade attempted to open while launcher is active — dismiss and open terminal drawer
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                    onInterceptShade?.invoke()
                }
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        var instance: PantheonAccessibilityService? = null
            private set

        @Volatile
        var isPantheonForeground: Boolean = false

        @Volatile
        var allowNativeShadeOnce: Boolean = false

        @Volatile
        var onInterceptShade: (() -> Unit)? = null

        fun isConnected(): Boolean = instance != null

        fun lockScreen(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        fun expandNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
    }
}
