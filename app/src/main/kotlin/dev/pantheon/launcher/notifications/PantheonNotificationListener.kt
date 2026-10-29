package dev.pantheon.launcher.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
    val autoCancel: Boolean = false,
    val actions: List<NotificationAction> = emptyList(),
)

/** A notification button; [remoteInput] is set for inline-reply actions. */
data class NotificationAction(
    val title: String,
    val intent: PendingIntent,
    val remoteInput: RemoteInput? = null,
)

class PantheonNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        _dnd.value = currentInterruptionFilter.isDnd()
        updateCounts()
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        super.onInterruptionFilterChanged(interruptionFilter)
        _dnd.value = interruptionFilter.isDnd()
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
        val shown = active.filter { sbn ->
            !sbn.isOngoing &&
                (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) &&
                sbn.packageName != packageName
        }
        // A group summary repeats its children; drop it when any child is present.
        val groupsWithChildren = shown.filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .mapNotNull { it.groupKey }.toSet()
        val valid = shown.filter { sbn ->
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 || sbn.groupKey !in groupsWithChildren
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
                autoCancel = sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
                actions = sbn.notification.actions.orEmpty().mapNotNull { a ->
                    val intent = a.actionIntent ?: return@mapNotNull null
                    NotificationAction(
                        title = a.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                        intent = intent,
                        remoteInput = a.remoteInputs?.firstOrNull { it.allowFreeFormInput },
                    )
                }.take(3),
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

        private val _dnd = MutableStateFlow(false)
        /** True while Do Not Disturb (priority, alarms or total silence) is on. */
        val dnd: StateFlow<Boolean> = _dnd.asStateFlow()

        private fun Int.isDnd() = this != INTERRUPTION_FILTER_ALL && this != INTERRUPTION_FILTER_UNKNOWN

        /** Listeners may set the interruption filter without separate DND-policy access. */
        fun setDnd(on: Boolean): Boolean {
            val inst = activeInstance ?: return false
            return runCatching {
                inst.requestInterruptionFilter(if (on) INTERRUPTION_FILTER_PRIORITY else INTERRUPTION_FILTER_ALL)
            }.isSuccess
        }

        fun open(entry: NotificationEntry) {
            val inst = activeInstance
            val sent = entry.contentIntent?.let { send(inst, it, null) } ?: false
            if (sent && entry.autoCancel) dismiss(entry.key)
        }

        /** Fires a notification button, attaching [reply] for inline-reply actions. */
        fun runAction(action: NotificationAction, reply: String? = null): Boolean {
            val fillIn = if (action.remoteInput != null && !reply.isNullOrBlank()) {
                Intent().also {
                    RemoteInput.addResultsToIntent(
                        arrayOf(action.remoteInput),
                        it,
                        Bundle().apply { putCharSequence(action.remoteInput.resultKey, reply) },
                    )
                }
            } else {
                null
            }
            return send(activeInstance, action.intent, fillIn)
        }

        /**
         * Android 14+ only lets a PendingIntent start an activity from here if the sender opts in;
         * the launcher is the visible app, so it may.
         */
        private fun send(context: Context?, intent: PendingIntent, fillIn: Intent?): Boolean = runCatching {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic().apply {
                    @Suppress("DEPRECATION")
                    setPendingIntentBackgroundActivityStartMode(
                        if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                        else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }.toBundle()
            } else {
                null
            }
            intent.send(context, 0, fillIn, null, null, null, options)
        }.isSuccess

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
