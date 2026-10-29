package dev.pantheon.launcher.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PantheonNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        updateCounts()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (activeInstance === this) {
            activeInstance = null
        }
        _counts.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateCounts()
    }

    fun updateCounts() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val map = active
            .filter { sbn ->
                !sbn.isOngoing &&
                    (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) &&
                    sbn.packageName != packageName
            }
            .groupBy { it.packageName }
            .mapValues { (_, sbns) ->
                sbns.sumOf { sbn -> maxOf(1, sbn.notification.number) }
            }
        _counts.value = map
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

        @Volatile
        private var activeInstance: PantheonNotificationListener? = null

        fun isAccessGranted(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }

        fun refresh(context: Context) {
            if (!isAccessGranted(context)) {
                _counts.value = emptyMap()
                return
            }
            val inst = activeInstance
            if (inst != null) {
                inst.updateCounts()
            } else {
                runCatching {
                    requestRebind(ComponentName(context, PantheonNotificationListener::class.java))
                }
            }
        }
    }
}
