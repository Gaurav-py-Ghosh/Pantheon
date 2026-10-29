package dev.pantheon.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val postTime: Long,
    val isClearable: Boolean,
    val contentIntent: PendingIntent? = null,
)

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
        _notifications.value = emptyList()
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
        val valid = active.filter { sbn ->
            !sbn.isOngoing &&
                (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) &&
                sbn.packageName != packageName
        }
        val map = valid
            .groupBy { it.packageName }
            .mapValues { (_, sbns) ->
                sbns.sumOf { sbn -> maxOf(1, sbn.notification.number) }
            }
        _counts.value = map

        val entries = valid.map { sbn ->
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
                ?: sbn.packageName.substringAfterLast('.')
            val text = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

            NotificationEntry(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title,
                text = text,
                subText = subText,
                postTime = sbn.postTime,
                isClearable = sbn.isClearable,
                contentIntent = sbn.notification.contentIntent,
            )
        }.sortedByDescending { it.postTime }

        _notifications.value = entries
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

        private val _notifications = MutableStateFlow<List<NotificationEntry>>(emptyList())
        val notifications: StateFlow<List<NotificationEntry>> = _notifications.asStateFlow()

        @Volatile
        private var activeInstance: PantheonNotificationListener? = null

        fun dismiss(key: String) {
            runCatching { activeInstance?.cancelNotification(key) }
        }

        fun dismissAll() {
            runCatching { activeInstance?.cancelAllNotifications() }
        }

        fun open(entry: NotificationEntry) {
            runCatching { entry.contentIntent?.send() }
        }

        fun isAccessGranted(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }

        fun refresh(context: Context) {
            if (!isAccessGranted(context)) {
                _counts.value = emptyMap()
                _notifications.value = emptyList()
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
