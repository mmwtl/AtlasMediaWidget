package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import com.geely.lib.oneosapi.OneOSApiManager
import com.geely.lib.oneosapi.listener.ServiceConnectionListener
import com.mmwtl.atlasmediaapi.media.session.AndroidBackgroundPlayerStarter
import com.mmwtl.atlasmediaapi.media.session.MediaSessionObserver
import com.mmwtl.atlasmediaapi.settings.AtlasPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class MediaBackendCoordinator(
    private val context: Context,
    val preferences: AtlasPreferences = AtlasPreferences(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    companion object {
        const val GRACE_PERIOD_MS = 30_000L
        const val RECONCILE_INTERVAL_MS = 10_000L
        val RECONNECT_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L, 30_000L)
        val DEFAULT_SOURCE_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L)
    }

    val stateRepository: MediaStateRepository = MediaStateRepository()
    val artworkRepository: ArtworkRepository = ArtworkRepository(context, scope)
    val radioCatalogRepository: RadioCatalogRepository = RadioCatalogRepository(context)
    val clusterMediaBridge: com.mmwtl.atlasmediaapi.media.cluster.ClusterMediaBridge =
        com.mmwtl.atlasmediaapi.media.cluster.ClusterMediaBridge(context)
    lateinit var carPlayBridge: com.mmwtl.atlasmediaapi.media.carplay.CarPlayNativeBridge
    val stateHub: MediaStateHub = MediaStateHub(
        context = context,
        repository = stateRepository,
        artworkRepository = artworkRepository,
        radioCatalogRepository = radioCatalogRepository,
        clusterMediaBridge = clusterMediaBridge,
        carPlayArtworkProvider = { if (::carPlayBridge.isInitialized) carPlayBridge.getCachedArtwork() else null },
        onActiveSourceLost = ::handleActiveSourceLost,
        canStartOnlinePlayer = { preferences.defaultMediaPackage.isNotBlank() },
    )
    val sessionObserver: MediaSessionObserver = MediaSessionObserver(context, stateHub)
    val oneOsAdapter: OneOsMediaBridgeAdapter = OneOsMediaBridgeAdapter(
        hub = stateHub,
        scope = scope,
        onOnlineSourceConfirmed = sessionObserver::refreshActiveController,
    )

    init {
        carPlayBridge = com.mmwtl.atlasmediaapi.media.carplay.CarPlayNativeBridge(
            context = context,
            scope = scope,
            artworkRepository = artworkRepository,
            onArtworkUpdated = stateHub::onCarPlayNativeArtwork,
            onNowPlayingUpdated = stateHub::onCarPlayNowPlaying,
        )
    }

    val commandHost: AndroidMediaCommandHost = AndroidMediaCommandHost(
        context = context,
        apiManager = OneOSApiManager.getInstance(context),
        sessionObserver = sessionObserver,
        preferences = preferences,
        radioCatalogRepository = radioCatalogRepository,
        carPlayBridge = carPlayBridge,
        onUserAction = ::cancelDefaultSourceSwitch,
        stateHub = stateHub,
        oneOsPlayStateGeneration = oneOsAdapter::playStateGeneration,
        backgroundPlayerStarter = AndroidBackgroundPlayerStarter(context),
        displayedMediaPackage = { stateRepository.snapshot().ownerPackage },
        onSourceSwitchFailed = ::resyncMediaState,
    )
    val commandRouter: MediaCommandRouter = MediaCommandRouter(commandHost)
    val demoBackend: DemoMediaBackend = DemoMediaBackend(
        context = context,
        repository = stateRepository,
        artworkRepository = artworkRepository,
        radioCatalogRepository = radioCatalogRepository,
    )
    val settingsController: com.mmwtl.atlasmediaapi.settings.MediaSettingsController =
        com.mmwtl.atlasmediaapi.settings.MediaSettingsController(
            context = context,
            preferences = preferences,
            radioCatalogRepository = radioCatalogRepository,
            clusterMediaBridge = clusterMediaBridge,
            onSettingsChanged = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        stateHub.refreshRadioState()
                        publishOnlineToCluster(stateRepository.snapshot())
                    }
                    cancelDefaultSourceSwitch()
                }
            },
        )
    val callerAccessPolicy: CallerAccessPolicy = OpenCallerAccessPolicy()
    val commandGate: MediaCommandGate = MediaCommandGate()

    private val apiManager = OneOSApiManager.getInstance(context)
    private val clientCount = AtomicInteger(0)
    private val lastReconcileRealtime = AtomicLong(Long.MIN_VALUE / 2)
    private var onlineClusterJob: Job? = null
    private var graceJob: Job? = null
    private var reconnectJob: Job? = null
    private var mediaCenterRetryJob: Job? = null
    private var defaultSourceJob: Job? = null
    private var activeSourceLossJob: Job? = null
    private var hasAppliedDefaultSource = false
    private var isBackendStarted = false
    private var activeBackendIsDemo = false
    private var connectionGeneration = 0L
    private var oneOsConnected = false

    private val serviceConnectionListener = object : ServiceConnectionListener {
        override fun onServiceBinderUpdated(binderType: Int) {
            // At cold boot OneOS ServiceManager can connect before MediaCenter registers its
            // binder; this callback is the notification that it is now available.
            if (binderType != OneOSApiManager.SERVICE_MEDIA_CENTER) return
            scope.launch {
                Timber.i("OneOS MediaCenter binder updated")
                handleConnectionChanged(true)
            }
        }

        override fun onServiceConnectionChanged(connected: Boolean) {
            scope.launch {
                handleConnectionChanged(connected)
            }
        }
    }

    fun clientRegistered() {
        val count = clientCount.incrementAndGet()
        Timber.i("Client registered. Active clients: %d", count)
        graceJob?.cancel()
        graceJob = null
        if (!isBackendStarted) {
            startBackend()
        }
    }

    fun clientUnregistered() {
        val count = clientCount.decrementAndGet().coerceAtLeast(0)
        Timber.i("Client unregistered. Active clients: %d", count)
        if (count == 0 && isBackendStarted) {
            graceJob?.cancel()
            graceJob = scope.launch {
                delay(GRACE_PERIOD_MS)
                if (clientCount.get() == 0) {
                    Timber.i("Grace period expired with 0 clients. Stopping backend.")
                    stopBackend()
                }
            }
        }
    }

    fun activeClientCount(): Int = clientCount.get()

    fun isBackendRunning(): Boolean = isBackendStarted

    fun isDemoMode(): Boolean = preferences.demoModeEnabled

    fun setDemoMode(enabled: Boolean) {
        if (preferences.demoModeEnabled == enabled) return
        if (isBackendStarted) {
            stopBackend()
            preferences.demoModeEnabled = enabled
            startBackend()
        } else {
            preferences.demoModeEnabled = enabled
        }
    }

    fun startBackend() {
        if (isBackendStarted) return
        isBackendStarted = true
        activeBackendIsDemo = isDemoMode()
        Timber.i("Starting media backend coordinator")
        if (activeBackendIsDemo) {
            Timber.i("Starting synthetic demo media backend")
            demoBackend.start()
            return
        }
        onlineClusterJob = scope.launch(Dispatchers.IO) {
            stateRepository.snapshots.collect { snapshot ->
                runCatching { publishOnlineToCluster(snapshot) }.onFailure { Timber.w(it, "Online DIM update failed") }
            }
        }
        stateHub.onBackendConnecting()

        apiManager.registerServiceConnectionListener(serviceConnectionListener)
        apiManager.init()

        sessionObserver.start()
        carPlayBridge.start()
        if (preferences.defaultAudioSource == BridgeAudioSource.ONLINE.name) {
            scheduleDefaultSourceSwitch()
        }
    }

    fun stopBackend() {
        if (!isBackendStarted) return
        isBackendStarted = false
        onlineClusterJob?.cancel()
        onlineClusterJob = null
        clusterMediaBridge.setActiveSource(null)
        Timber.i("Stopping media backend coordinator")
        reconnectJob?.cancel()
        reconnectJob = null
        mediaCenterRetryJob?.cancel()
        mediaCenterRetryJob = null
        defaultSourceJob?.cancel()
        defaultSourceJob = null
        activeSourceLossJob?.cancel()
        activeSourceLossJob = null
        hasAppliedDefaultSource = false

        val wasDemoBackend = activeBackendIsDemo
        activeBackendIsDemo = false
        if (wasDemoBackend) {
            demoBackend.stop()
            return
        }

        apiManager.unregisterServiceConnectionListener(serviceConnectionListener)
        oneOsAdapter.detach(notify = true)
        sessionObserver.stop()
        carPlayBridge.stop()
        apiManager.release()
    }

    /**
     * Re-reads OneOS and session state at most every [RECONCILE_INTERVAL_MS]. Clients ask for a
     * snapshot only while the card is visible and playing, so this supplements callbacks then.
     */
    fun requestReconcile() {
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastReconcileRealtime.get()
        if (now - last < RECONCILE_INTERVAL_MS || !lastReconcileRealtime.compareAndSet(last, now)) return
        scope.launch {
            if (!isBackendStarted || activeBackendIsDemo) return@launch
            withContext(Dispatchers.IO) { resyncMediaState() }
        }
    }

    private fun resyncMediaState() {
        runCatching {
            oneOsAdapter.resync()
            sessionObserver.refreshActiveController()
        }.onFailure { Timber.w(it, "Media state resync failed") }
    }

    private fun publishOnlineToCluster(snapshot: MediaSnapshot) {
        val token = snapshot.artworkUri.substringAfterLast("/").substringBeforeLast(".")
        val file = token.takeIf { it.isNotBlank() }?.let(artworkRepository::getCacheFile)
        clusterMediaBridge.updateOnlinePlayback(snapshot, file)
    }

    private suspend fun handleConnectionChanged(connected: Boolean) {
        if (!isBackendStarted) return
        if (connected) {
            reconnectJob?.cancel()
            reconnectJob = null
            connectionGeneration++
            oneOsConnected = true
            val currentGen = connectionGeneration
            Timber.i("OneOS connected, generation=%d", currentGen)

            val center = withContext(Dispatchers.IO) {
                apiManager.getMediaCenterManager()?.takeIf { it.isAlive }
            }
            if (connectionGeneration != currentGen || !isBackendStarted) return
            if (center == null) {
                stateHub.onBackendDisconnected("OneOS MediaCenter unavailable")
                scheduleMediaCenterRetry(currentGen)
                return
            }
            withContext(Dispatchers.IO) { oneOsAdapter.attach(center) }
            if (connectionGeneration != currentGen || !isBackendStarted) {
                // The backend stopped or OneOS disconnected while attaching; a newer connection
                // attaches again by itself.
                if (!isBackendStarted || !oneOsConnected) {
                    withContext(Dispatchers.IO) { oneOsAdapter.detach(notify = true) }
                }
                return
            }
            if (!hasAppliedDefaultSource && defaultSourceJob?.isActive != true) {
                scheduleDefaultSourceSwitch()
            }
        } else {
            Timber.w("OneOS disconnected")
            connectionGeneration++
            oneOsConnected = false
            mediaCenterRetryJob?.cancel()
            mediaCenterRetryJob = null
            if (preferences.defaultAudioSource != BridgeAudioSource.ONLINE.name) {
                defaultSourceJob?.cancel()
                defaultSourceJob = null
            }
            withContext(Dispatchers.IO) { oneOsAdapter.detach(notify = true) }
            if (clientCount.get() > 0 && isBackendStarted) {
                scheduleReconnect()
            }
        }
    }

    @Volatile
    private var isApplyingDefaultSource = false

    fun scheduleDefaultSourceSwitch() {
        defaultSourceJob?.cancel()
        defaultSourceJob = null

        val targetSourceStr = preferences.defaultAudioSource
        val autoplay = preferences.defaultAudioSourceAutoplayOnStartup
        val targetSource = if (targetSourceStr.isBlank()) {
            null
        } else {
            runCatching { BridgeAudioSource.valueOf(targetSourceStr) }.getOrNull() ?: return
        }
        if (targetSource == BridgeAudioSource.UNKNOWN || targetSource == BridgeAudioSource.OTHER) return
        if (targetSource == null && !autoplay) return

        val delayMs = if (targetSource != null) {
            preferences.defaultAudioSourceDelaySec * 1000L
        } else {
            0L
        }
        val targetDescription = targetSource?.name ?: "current source"
        Timber.i(
            "Scheduling startup media action for %s in %d ms (autoplay=%b)",
            targetDescription,
            delayMs,
            autoplay,
        )

        defaultSourceJob = scope.launch {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            if (!isBackendStarted) return@launch
            var attempt = 0
            while (isBackendStarted && !hasAppliedDefaultSource) {
                isApplyingDefaultSource = true
                val applied = try {
                    Timber.i(
                        "Applying startup media action for %s (autoplay=%b, attempt=%d)",
                        targetDescription,
                        autoplay,
                        attempt + 1,
                    )
                    withContext(Dispatchers.IO) {
                        commandGate.run(
                            interruptible = true,
                            onPreempted = ::onStartupActionPreempted,
                        ) {
                            if (targetSource != null) {
                                commandHost.setDefaultSource(
                                    source = targetSource,
                                    autoplay = autoplay,
                                )
                            } else {
                                commandHost.autoplayCurrentSource()
                            }
                        }
                    }
                } finally {
                    isApplyingDefaultSource = false
                }
                if (applied) {
                    hasAppliedDefaultSource = true
                    Timber.i("Startup media action for %s applied", targetDescription)
                    return@launch
                }
                val retryDelay = DEFAULT_SOURCE_RETRY_DELAYS_MS.getOrNull(attempt)
                if (retryDelay == null) {
                    Timber.w(
                        "Startup media action for %s was not applied after %d attempts",
                        targetDescription,
                        attempt + 1,
                    )
                    return@launch
                }
                attempt++
                if (!isBackendStarted || hasAppliedDefaultSource) return@launch
                Timber.w(
                    "Startup media action for %s was not applied; retrying in %d ms",
                    targetDescription,
                    retryDelay,
                )
                delay(retryDelay)
            }
        }
    }

    /** A user command replaced the startup action; it must not run again after a reconnect. */
    private fun onStartupActionPreempted() {
        scope.launch {
            Timber.i("Startup media action cancelled by a newer user command")
            hasAppliedDefaultSource = true
        }
    }

    fun cancelDefaultSourceSwitch() {
        if (isApplyingDefaultSource) return
        if (defaultSourceJob?.isActive == true) {
            Timber.i("Cancelling pending default source switch due to explicit user action")
            defaultSourceJob?.cancel()
            defaultSourceJob = null
            hasAppliedDefaultSource = true
        }
    }

    private fun handleActiveSourceLost(lostSource: BridgeAudioSource, wasPlaying: Boolean) {
        // Loss callbacks arrive on session and Binder threads; job state belongs to the scope.
        scope.launch { switchAfterSourceLoss(lostSource, wasPlaying) }
    }

    private fun switchAfterSourceLoss(lostSource: BridgeAudioSource, wasPlaying: Boolean) {
        // Callbacks still in flight while the backend stops must not switch the head unit.
        if (!isBackendStarted || activeBackendIsDemo) return
        if (!preferences.autoSwitchToDefaultOnSourceLost) return
        val targetSourceStr = preferences.defaultAudioSource
        if (targetSourceStr.isBlank()) return
        val targetSource = runCatching { BridgeAudioSource.valueOf(targetSourceStr) }.getOrNull() ?: return
        if (targetSource == BridgeAudioSource.UNKNOWN || targetSource == BridgeAudioSource.OTHER || targetSource == lostSource) return
        if (activeSourceLossJob?.isActive == true) {
            Timber.i("Ignoring duplicate loss of %s while default source switch is active", lostSource.name)
            return
        }

        val autoplay = preferences.autoSwitchToDefaultAutoplayOnSourceLost && wasPlaying
        Timber.i(
            "Active source %s lost (wasPlaying=%b), auto-switching to default source %s (autoplay=%b)",
            lostSource.name,
            wasPlaying,
            targetSource.name,
            autoplay,
        )
        activeSourceLossJob = scope.launch {
            isApplyingDefaultSource = true
            try {
                withContext(Dispatchers.IO) {
                    commandGate.run(interruptible = true) {
                        commandHost.setSource(
                            source = targetSource,
                            appSource = null,
                            autoplay = autoplay,
                        )
                    }
                }
            } finally {
                isApplyingDefaultSource = false
            }
        }
    }

    private fun scheduleMediaCenterRetry(generation: Long) {
        if (mediaCenterRetryJob?.isActive == true) return
        mediaCenterRetryJob = scope.launch {
            for (delayMs in RECONNECT_DELAYS_MS) {
                Timber.i("OneOS MediaCenter unavailable; retrying in %d ms", delayMs)
                delay(delayMs)
                if (!isBackendStarted || connectionGeneration != generation) return@launch
                val alive = withContext(Dispatchers.IO) {
                    apiManager.getMediaCenterManager()?.isAlive == true
                }
                if (!isBackendStarted || connectionGeneration != generation) return@launch
                if (alive) {
                    mediaCenterRetryJob = null
                    handleConnectionChanged(true)
                    return@launch
                }
            }
            Timber.w("OneOS MediaCenter still unavailable after %d retries", RECONNECT_DELAYS_MS.size)
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var attempt = 0
            while (clientCount.get() > 0 && isBackendStarted) {
                val delayMs = RECONNECT_DELAYS_MS.getOrElse(attempt) { RECONNECT_DELAYS_MS.last() }
                Timber.i("Scheduling OneOS reconnect in %d ms (attempt %d)", delayMs, attempt + 1)
                delay(delayMs)
                if (clientCount.get() > 0 && isBackendStarted) {
                    stateHub.onBackendConnecting()
                    apiManager.init()
                }
                attempt++
            }
        }
    }
}
