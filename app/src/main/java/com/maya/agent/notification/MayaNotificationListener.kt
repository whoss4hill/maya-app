package com.maya.agent.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads incoming notifications when the owner asks ("kya aaya hai?"),
 * and replies through notification actions when asked. Always says who a
 * message is from before its body. Captures nothing on its own initiative.
 */
class MayaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile var latest: List<Pair<String, String>> = emptyList() // (app, title)
            private set
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val entry = sbn.packageName to title
        latest = (listOf(entry) + latest).distinct().take(10)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
