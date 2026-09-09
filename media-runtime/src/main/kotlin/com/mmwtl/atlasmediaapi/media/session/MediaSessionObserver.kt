package com.mmwtl.atlasmediaapi.media.session

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import com.mmwtl.atlasmediaapi.media.bridge.CARPLAY_MEDIA_SESSION_PACKAGES
import com.mmwtl.atlasmediaapi.media.bridge.MediaStateHub
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

class MediaSessionObserver(
    private val context: Context,
    private val hub: MediaStateHub,
) {
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val ownPackage = context.packageName

    private var cbThread: HandlerThread? = null
    private var cbHandler: Handler? = null
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val activeControllers = CopyOnWriteArrayList<MediaController>()
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var isStarted = false

    fun start() {
        synchronized(this) {
            if (isStarted) return
            isStarted = true

            val thread = HandlerThread("atlas-media-session-cb").apply { start() }
            cbThread = thread
            val handler = Handler(thread.looper)
            cbHandler = handler

            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                handler.post {
                    updateControllers(controllers.orEmpty())
                }
            }
            sessionListener = listener

            val component = MediaNotificationListenerService.getComponentName(context)
            try {
                sessionManager.addOnActiveSessionsChangedListener(listener, component, handler)
                val initial = sessionManager.getActiveSessions(component).orEmpty()
                updateControllers(initial)
            } catch (e: SecurityException) {
                Timber.w(e, "Notification access not granted for MediaSessionManager")
                hub.onMediaController(null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize MediaSessionObserver")
                hub.onMediaController(null)
            }
        }
    }

    fun stop() {
        synchronized(this) {
            if (!isStarted) return
            isStarted = false

            val listener = sessionListener
            if (listener != null) {
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(listener) }
                    .onFailure(Timber::e)
                sessionListener = null
            }

            callbacks.forEach { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }.onFailure(Timber::e)
            }
            callbacks.clear()
            activeControllers.clear()

            cbThread?.quitSafely()
            cbThread = null
            cbHandler = null

            hub.onMediaController(null)
            hub.onCpaaMediaController(null)
        }
    }

    fun getActiveControllers(): List<MediaController> = activeControllers.toList()

    private fun updateControllers(controllers: List<MediaController>) {
        val filtered = controllers.filter { it.packageName != ownPackage }
        activeControllers.clear()
        activeControllers.addAll(filtered)

        // Clean up old callbacks
        callbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }.onFailure(Timber::e)
        }
        callbacks.clear()

        val handler = cbHandler ?: return
        filtered.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    dispatchSessionUpdate()
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    dispatchSessionUpdate()
                }
            }
            runCatching { controller.registerCallback(callback, handler) }
                .onSuccess { callbacks[controller] = callback }
                .onFailure(Timber::e)
        }

        dispatchSessionUpdate()
    }

    private fun dispatchSessionUpdate() {
        val controllers = activeControllers.toList()
        val active = pickActive(controllers)
        hub.onMediaController(active)

        val carPlay = controllers.firstOrNull { it.packageName in CARPLAY_MEDIA_SESSION_PACKAGES }
            ?: controllers.firstOrNull { controller ->
                val pkg = controller.packageName?.lowercase(java.util.Locale.ROOT).orEmpty()
                pkg.contains("carplay") || pkg.contains("cpaa") || pkg.contains("autolink") || pkg.contains("carlink")
            }
        hub.onCpaaMediaController(carPlay)
    }

    private fun pickActive(controllers: List<MediaController>): MediaController? {
        if (controllers.isEmpty()) return null
        return controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
    }
}
