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
    private var sessionListenerRegistered = false
    private var isStarted = false
    private var generation = 0L
    private val notificationConnectionListener: (Boolean) -> Unit = ::onNotificationListenerStateChanged

    fun start() {
        android.util.Log.i("AtlasMediaSessions", "Observer start; listener connected=" + MediaNotificationListenerService.isConnected())
        synchronized(this) {
            if (isStarted) return
            isStarted = true
            generation++

            val thread = HandlerThread("atlas-media-session-cb").apply { start() }
            cbThread = thread
            val handler = Handler(thread.looper)
            cbHandler = handler

            MediaNotificationListenerService.addConnectionListener(notificationConnectionListener)
        }
    }

    fun stop() {
        synchronized(this) {
            if (!isStarted) return
            isStarted = false
            generation++

            val listener = sessionListener.takeIf { sessionListenerRegistered }
            val registeredCallbacks = callbacks.toMap()
            sessionListenerRegistered = false
            sessionListener = null
            MediaNotificationListenerService.removeConnectionListener(notificationConnectionListener)
            callbacks.clear()
            activeControllers.clear()

            cbHandler?.removeCallbacksAndMessages(null)
            cbHandler?.post {
                listener?.let {
                    runCatching { sessionManager.removeOnActiveSessionsChangedListener(it) }
                        .onFailure(Timber::e)
                }
                registeredCallbacks.forEach { (controller, callback) ->
                    runCatching { controller.unregisterCallback(callback) }.onFailure(Timber::e)
                }
            }
            cbThread?.quitSafely()
            cbThread = null
            cbHandler = null

            hub.onMediaController(null)
            hub.onCpaaMediaController(null)
        }
    }

    fun getActiveControllers(): List<MediaController> = activeControllers.toList()

    private fun registerOrRefreshLocked(expectedGeneration: Long) {
        if (!isGenerationActiveLocked(expectedGeneration)) return
        val handler = cbHandler ?: return
        val component = MediaNotificationListenerService.getComponentName(context)
        clearControllersLocked()
        unregisterSessionListenerLocked()
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            handler.post {
                updateControllers(controllers.orEmpty(), expectedGeneration)
            }
        }
        sessionListener = listener
        try {
            sessionManager.addOnActiveSessionsChangedListener(listener, component, handler)
            sessionListenerRegistered = true
            val initial = sessionManager.getActiveSessions(component).orEmpty()
            android.util.Log.i("AtlasMediaSessions", "Registered; active sessions=" + initial.size)
            updateControllers(initial, expectedGeneration)
        } catch (e: SecurityException) {
            android.util.Log.w("AtlasMediaSessions", "Session registration denied", e)
            unregisterSessionListenerLocked()
            Timber.w(e, "Notification access not granted for MediaSessionManager")
            hub.onMediaController(null)
        } catch (e: Exception) {
            unregisterSessionListenerLocked()
            Timber.e(e, "Failed to initialize MediaSessionObserver")
            hub.onMediaController(null)
        }
    }

    private fun onNotificationListenerStateChanged(connected: Boolean) {
        android.util.Log.i("AtlasMediaSessions", "Listener connected=$connected; observer started=$isStarted")
        synchronized(this) {
            if (!isStarted) return
            val handler = cbHandler ?: return
            val currentGeneration = ++generation
            handler.removeCallbacksAndMessages(null)
            handler.post {
                synchronized(this) {
                    if (!isGenerationActiveLocked(currentGeneration)) return@synchronized
                    if (connected) {
                        registerOrRefreshLocked(currentGeneration)
                    } else {
                        clearControllersLocked()
                        unregisterSessionListenerLocked()
                        hub.onMediaController(null)
                        hub.onCpaaMediaController(null)
                    }
                }
            }
        }
    }

    private fun updateControllers(controllers: List<MediaController>, expectedGeneration: Long) {
        synchronized(this) {
            if (!isGenerationActiveLocked(expectedGeneration)) return
            val filtered = controllers.filter {
                it.packageName != ownPackage && it.packageName != "com.geely.mediacenterservice"
            }
            clearControllersLocked()
            activeControllers.addAll(filtered)
            val handler = cbHandler ?: return
            filtered.forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        handler.post { dispatchSessionUpdate(expectedGeneration) }
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        handler.post { dispatchSessionUpdate(expectedGeneration) }
                    }

                    override fun onSessionDestroyed() {
                        handler.post { onSessionDestroyed(controller, expectedGeneration) }
                    }
                }
                runCatching { controller.registerCallback(callback, handler) }
                    .onSuccess { callbacks[controller] = callback }
                    .onFailure(Timber::e)
            }

            dispatchSessionUpdate(expectedGeneration)
        }
    }

    private fun onSessionDestroyed(controller: MediaController, expectedGeneration: Long) {
        synchronized(this) {
            if (!isGenerationActiveLocked(expectedGeneration)) return
            callbacks.remove(controller)?.let { callback ->
                runCatching { controller.unregisterCallback(callback) }.onFailure(Timber::e)
            }
            activeControllers.remove(controller)
            dispatchSessionUpdate(expectedGeneration)
        }
    }

    private fun dispatchSessionUpdate(expectedGeneration: Long) {
        synchronized(this) {
            if (!isGenerationActiveLocked(expectedGeneration)) return
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
    }

    private fun clearControllersLocked() {
        callbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }.onFailure(Timber::e)
        }
        callbacks.clear()
        activeControllers.clear()
    }

    private fun unregisterSessionListenerLocked() {
        sessionListener?.let { listener ->
            if (sessionListenerRegistered) {
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(listener) }
                    .onFailure(Timber::e)
            }
        }
        sessionListenerRegistered = false
        sessionListener = null
    }

    private fun isGenerationActiveLocked(expectedGeneration: Long): Boolean =
        isStarted && generation == expectedGeneration

    private fun pickActive(controllers: List<MediaController>): MediaController? {
        val candidates = controllers.filterNot(::isNativeBluetoothController)
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
    }

    private fun isNativeBluetoothController(controller: MediaController): Boolean {
        val packageName = controller.packageName?.lowercase(java.util.Locale.ROOT).orEmpty()
        return packageName == "com.android.bluetooth" ||
            packageName.contains("bluetooth") ||
            packageName.contains("a2dp") ||
            packageName.contains("btservice")
    }
}
