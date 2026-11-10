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
import com.mmwtl.atlasmediaapi.media.bridge.PlayerSessionSelector
import com.mmwtl.atlasmediaapi.media.bridge.ShownPlayerHold
import com.mmwtl.atlasmediaapi.media.bridge.playerSessionFacts
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

    // Sessions reached through a player's media browser before the player made them active; each
    // is dropped once its package publishes an active session or the session is destroyed.
    private val preparedSessions = CopyOnWriteArrayList<PreparedSession>()
    private val preparedCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var sessionListenerRegistered = false
    private var isStarted = false
    private var generation = 0L
    private var playingPackages = emptySet<String>()
    private val stoppedPlayingAt = mutableMapOf<String, Long>()
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
            val registeredPrepared = preparedCallbacks.toMap()
            preparedCallbacks.clear()
            preparedSessions.clear()

            cbHandler?.removeCallbacksAndMessages(null)
            cbHandler?.post {
                listener?.let {
                    runCatching { sessionManager.removeOnActiveSessionsChangedListener(it) }
                        .onFailure(Timber::e)
                }
                (registeredCallbacks + registeredPrepared).forEach { (controller, callback) ->
                    runCatching { controller.unregisterCallback(callback) }.onFailure(Timber::e)
                }
            }
            cbThread?.quitSafely()
            cbThread = null
            cbHandler = null

            hub.onSessionObservationLost()
            hub.onCpaaMediaController(null)
        }
    }

    /** Active sessions, then prepared sessions of packages that have no active one. */
    fun getActiveControllers(): List<MediaController> {
        val active = activeControllers.toList()
        val activePackages = active.mapTo(HashSet()) { it.packageName }
        return active + preparedSessions.map { it.controller }.filter { it.packageName !in activePackages }
    }

    private fun preparedSession(controller: MediaController): PreparedSession? =
        preparedSessions.firstOrNull { it.controller === controller }

    /** Metadata the card shows for [controller]; see [PreparedSession.metadata]. */
    fun metadataOf(controller: MediaController): MediaMetadata? =
        preparedSession(controller)?.metadata() ?: controller.metadata

    /** Playback state the card and commands use for [controller]; see [PreparedSession]. */
    fun playbackStateOf(controller: MediaController): PlaybackState? =
        preparedSession(controller)?.playbackState() ?: controller.playbackState

    private fun factsOf(controller: MediaController) =
        controller.playerSessionFacts(metadataOf(controller), playbackStateOf(controller))

    private fun publish(controller: MediaController) {
        hub.onMediaController(controller, metadataOf(controller), playbackStateOf(controller))
    }

    /**
     * Observes a player session reached through its media browser that may not be active yet, so
     * the card can show the track the player would resume and commands can reach it.
     */
    fun addPreparedSession(session: PreparedSession) {
        synchronized(this) {
            if (!isStarted) return
            val handler = cbHandler ?: return
            val controller = session.controller
            val packageName = controller.packageName
            if (activeControllers.any { it.packageName == packageName }) {
                dispatchSessionUpdate(generation)
                return
            }
            preparedSessions.filter { it.controller.packageName == packageName }
                .forEach { removePreparedLocked(it.controller) }
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handler.post(::dispatchCurrentSessions)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    handler.post(::dispatchCurrentSessions)
                }

                override fun onSessionDestroyed() {
                    handler.post {
                        synchronized(this@MediaSessionObserver) {
                            removePreparedLocked(controller)
                            dispatchSessionUpdate(generation)
                        }
                    }
                }
            }
            runCatching { controller.registerCallback(callback, handler) }
                .onSuccess { preparedCallbacks[controller] = callback }
                .onFailure(Timber::e)
            preparedSessions += session
            Timber.i("Observing the prepared session of %s", packageName)
            dispatchSessionUpdate(generation)
        }
    }

    private fun dispatchCurrentSessions() {
        synchronized(this) { dispatchSessionUpdate(generation) }
    }

    private fun removePreparedLocked(controller: MediaController) {
        preparedCallbacks.remove(controller)?.let { callback ->
            runCatching { controller.unregisterCallback(callback) }.onFailure(Timber::e)
        }
        preparedSessions.removeAll { it.controller === controller }
    }

    /** Elapsed realtime the player stopped playing while it had a session, or null. */
    fun stoppedPlayingAt(packageName: String): Long? =
        synchronized(this) { stoppedPlayingAt[packageName] }

    fun refreshActiveController() {
        synchronized(this) {
            if (!isStarted) return
            // A refresh only republishes; session removal is reported by session callbacks.
            selectActive(getActiveControllers().filterNot(::isNativeBluetoothController), generation)
                ?.let(::publish)
        }
    }

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
            hub.onSessionObservationLost()
        } catch (e: Exception) {
            unregisterSessionListenerLocked()
            Timber.e(e, "Failed to initialize MediaSessionObserver")
            hub.onSessionObservationLost()
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
                        hub.onSessionObservationLost()
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
            val activePackages = filtered.mapTo(HashSet()) { it.packageName }
            preparedSessions.filter { it.controller.packageName in activePackages }
                .forEach { removePreparedLocked(it.controller) }
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
            val controllers = getActiveControllers()
            publishActive(controllers, expectedGeneration)

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

    /**
     * Facts are read once per pass: each read is a Binder call and a player changing tracks can
     * answer differently within one pass.
     */
    private fun selectActive(
        candidates: List<MediaController>,
        expectedGeneration: Long,
    ): MediaController? {
        val facts = candidates.associateWith(::factsOf)
        val now = android.os.SystemClock.elapsedRealtime()
        val playing = facts.values.filter { it.playing }.map { it.packageName }.toSet()
        (playingPackages - playing).forEach { stoppedPlayingAt[it] = now }
        playing.forEach(stoppedPlayingAt::remove)
        playingPackages = playing
        val shownPackage = hub.displayedOwnerPackage()
        val selected = PlayerSessionSelector.select(
            sessions = candidates,
            facts = facts::getValue,
            displayedPackage = shownPackage,
            requireContent = true,
        )
        val shown = candidates.firstOrNull { it.packageName == shownPackage }
        val holdMs = ShownPlayerHold.remainingMs(
            selected = selected?.packageName,
            shown = shown?.packageName,
            shownStoppedPlayingAt = stoppedPlayingAt[shownPackage],
            now = now,
        )
        if (holdMs == 0L) return selected
        cbHandler?.postDelayed({ dispatchSessionUpdate(expectedGeneration) }, holdMs)
        return shown
    }

    private fun publishActive(controllers: List<MediaController>, expectedGeneration: Long) {
        val candidates = controllers.filterNot(::isNativeBluetoothController)
        val active = selectActive(candidates, expectedGeneration)
        when {
            active != null -> publish(active)
            candidates.isEmpty() -> hub.onMediaController(null)
            else -> hub.onPlayerContentGone()
        }
    }

    private fun isNativeBluetoothController(controller: MediaController): Boolean {
        val packageName = controller.packageName?.lowercase(java.util.Locale.ROOT).orEmpty()
        return packageName == "com.android.bluetooth" ||
            packageName.contains("bluetooth") ||
            packageName.contains("a2dp") ||
            packageName.contains("btservice")
    }
}
