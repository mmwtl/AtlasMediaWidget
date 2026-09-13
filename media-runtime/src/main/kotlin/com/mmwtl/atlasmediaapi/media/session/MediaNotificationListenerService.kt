package com.mmwtl.atlasmediaapi.media.session

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicBoolean

class MediaNotificationListenerService : NotificationListenerService() {
    companion object {
        private val isConnected = AtomicBoolean(false)

        fun isConnected(): Boolean = isConnected.get()

        fun isListenerEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            val me = ComponentName(context, MediaNotificationListenerService::class.java)
            return enabled.split(':').any { it.equals(me.flattenToString(), ignoreCase = true) }
        }

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context, MediaNotificationListenerService::class.java)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected.set(true)
    }

    override fun onListenerDisconnected() {
        if (isListenerEnabled(this)) {
            runCatching {
                requestRebind(ComponentName(this, MediaNotificationListenerService::class.java))
            }
        }
        isConnected.set(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!isConnected.get()) return
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (!isConnected.get()) return
    }

    override fun onDestroy() {
        isConnected.set(false)
        super.onDestroy()
    }
}
