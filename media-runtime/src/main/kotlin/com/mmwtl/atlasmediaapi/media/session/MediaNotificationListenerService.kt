package com.mmwtl.atlasmediaapi.media.session

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArraySet

class MediaNotificationListenerService : NotificationListenerService() {
    companion object {
        private val isConnected = AtomicBoolean(false)
        private val connectionListeners = CopyOnWriteArraySet<(Boolean) -> Unit>()

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

        fun addConnectionListener(listener: (Boolean) -> Unit) {
            connectionListeners += listener
            if (isConnected.get()) listener(true)
        }

        fun removeConnectionListener(listener: (Boolean) -> Unit) {
            connectionListeners -= listener
        }

        private fun notifyConnectionChanged(connected: Boolean) {
            connectionListeners.forEach { listener ->
                runCatching { listener(connected) }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected.set(true)
        notifyConnectionChanged(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected.set(false)
        notifyConnectionChanged(false)
        if (isListenerEnabled(this)) {
            runCatching {
                requestRebind(ComponentName(this, MediaNotificationListenerService::class.java))
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!isConnected.get()) return
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (!isConnected.get()) return
    }

    override fun onDestroy() {
        isConnected.set(false)
        notifyConnectionChanged(false)
    }
}
