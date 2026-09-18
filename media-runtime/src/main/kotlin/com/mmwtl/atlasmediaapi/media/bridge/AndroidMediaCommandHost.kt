package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.geely.lib.oneosapi.OneOSApiManager
import com.geely.lib.oneosapi.mediacenter.MediaCenterManager
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import com.mmwtl.atlasmediaapi.media.carplay.CarPlayNativeBridge
import com.mmwtl.atlasmediaapi.media.session.MediaSessionObserver
import com.mmwtl.atlasmediaapi.settings.AtlasPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class AndroidMediaCommandHost(
    private val context: Context,
    private val apiManager: OneOSApiManager,
    private val sessionObserver: MediaSessionObserver,
    private val preferences: AtlasPreferences,
    private val radioCatalogRepository: RadioCatalogRepository,
    private val carPlayBridge: CarPlayNativeBridge? = null,
    private val onUserAction: (() -> Unit)? = null,
    private val stateHub: MediaStateHub? = null,
    internal val sessionWaitTimeoutMs: Long = SESSION_WAIT_TIMEOUT_MS,
    internal val sessionPollDelaysMs: List<Long> = SESSION_POLL_DELAYS_MS,
    internal val sourceWaitTimeoutMs: Long = SOURCE_WAIT_TIMEOUT_MS,
    internal val sourcePollDelaysMs: List<Long> = SOURCE_POLL_DELAYS_MS,
    internal val autoplayConfirmDelaysMs: List<Long> = AUTOPLAY_CONFIRM_DELAYS_MS,
    internal val autoplayMediaKeyConfirmDelayMs: Long = AUTOPLAY_MEDIA_KEY_CONFIRM_DELAY_MS,
    private val launchPackage: ((String) -> Boolean)? = null,
) : MediaCommandHost {
    companion object {
        private const val MAX_RADIO_STATIONS_PER_LIST = 256
        const val SESSION_WAIT_TIMEOUT_MS = 10_000L
        val SESSION_POLL_DELAYS_MS = listOf(100L, 200L, 400L, 800L, 1_000L)
        const val SOURCE_WAIT_TIMEOUT_MS = 5_000L
        val SOURCE_POLL_DELAYS_MS = listOf(100L, 200L, 400L, 800L, 1_000L)
        val AUTOPLAY_CONFIRM_DELAYS_MS = listOf(300L, 700L, 1_200L)
        const val AUTOPLAY_MEDIA_KEY_CONFIRM_DELAY_MS = 1_000L
    }

    private val currentMediaPackageRef = AtomicReference("")
    private val lastOnlineMediaPackageRef = AtomicReference("")

    private fun mediaCenter(): MediaCenterManager? =
        apiManager.getMediaCenterManager()?.takeIf { it.isAlive }

    override fun backendAvailable(): Boolean =
        mediaCenter()?.isAlive == true ||
                sessionObserver.getActiveControllers().isNotEmpty() ||
                defaultMediaPackage().isNotBlank()

    override fun blocksMediaCommands(): Boolean = false

    override fun ownedSession(): MediaSessionCommandTarget? {
        if (mediaCenter()?.currentAudioSource == MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA) {
            val session = sessionObserver.getActiveControllers()
                .firstOrNull { it.packageName in CARPLAY_MEDIA_SESSION_PACKAGES }
                ?.let(::AndroidMediaSessionTarget)
            return session ?: carPlayBridge?.let(::CarPlayMediaSessionTarget)
        }
        return null
    }

    override suspend fun executeNative(request: MediaCommandRequest): MediaCommandResult? {
        onUserAction?.invoke()
        val center = mediaCenter() ?: return null
        val source = runCatching { center.currentAudioSource }.getOrNull() ?: return null

        val supportsNative = when (source) {
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO,
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB,
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA -> true

            MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE,
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_YUNTING ->
                center.musicManagerMap[source]?.isAlive == true && !hasAndroidSession()

            else -> false
        }
        if (!supportsNative) return null

        return runCatching {
            val handled = when (source) {
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO -> executeRadio(center, request)
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA -> executeCarPlay(request)
                else -> executeMusicAdapter(center, request)
            }
            if (handled) {
                MediaCommandResult(MediaBridgeContract.Status.OK)
            } else {
                MediaCommandResult(
                    MediaBridgeContract.Status.NOT_SUPPORTED,
                    "native source does not support ${request.command}",
                )
            }
        }.onFailure(Timber::e).getOrElse {
            MediaCommandResult(MediaBridgeContract.Status.FAILED, it.message.orEmpty())
        }
    }
    private fun hasAndroidSession(): Boolean {
        val currentPkg = currentMediaPackage()
        val controllers = sessionObserver.getActiveControllers()
        if (currentPkg.isNotBlank() && controllers.any { it.packageName == currentPkg }) {
            return true
        }
        return controllers.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }

    override fun preferredSession(): MediaSessionCommandTarget? {
        if (mediaCenter()?.currentAudioSource == MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA) {
            val session = sessionObserver.getActiveControllers()
                .firstOrNull { it.packageName in CARPLAY_MEDIA_SESSION_PACKAGES }
                ?.let(::AndroidMediaSessionTarget)
            return session ?: carPlayBridge?.let(::CarPlayMediaSessionTarget)
        }
        val controllers = sessionObserver.getActiveControllers()
        val active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
        return active?.let(::AndroidMediaSessionTarget)
    }

    override fun sessions(): List<MediaSessionCommandTarget> =
        sessionObserver.getActiveControllers().map(::AndroidMediaSessionTarget)

    override fun currentVisiblePackage(): String = ""

    override fun currentMediaPackage(): String = currentMediaPackageRef.get()

    override fun defaultMediaPackage(): String = preferences.defaultMediaPackage

    override fun setCurrentMediaPackage(packageName: String) {
        currentMediaPackageRef.set(packageName)
        val currentSource = runCatching { mediaCenter()?.currentAudioSource }.getOrNull()
        if (currentSource == null || currentSource == MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE) {
            rememberOnlineMediaPackage(packageName)
        }
    }

    override fun beforeSessionPlay() {
        if (preferences.switchToOnlineBeforeSessionPlay) {
            runCatching {
                mediaCenter()?.requestAudioSource(MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE)
            }.onFailure(Timber::e)
        }
    }

    override suspend fun sendFallback(packageName: String, command: MediaCommand): Boolean {
        onUserAction?.invoke()
        val controller = sessionObserver.getActiveControllers().firstOrNull { it.packageName == packageName }
        if (controller != null) {
            val target = AndroidMediaSessionTarget(controller)
            return when (command) {
                MediaCommand.PLAY -> target.play()
                MediaCommand.PAUSE -> target.pause()
                MediaCommand.TOGGLE -> target.toggle()
                MediaCommand.NEXT -> target.next()
                MediaCommand.PREVIOUS -> target.previous()
                MediaCommand.SEEK_TO -> false
                MediaCommand.SET_SOURCE -> false
                MediaCommand.TUNE_RADIO -> false
            }
        }
        return false
    }

    override suspend fun startDefaultAndPlay(packageName: String): Boolean =
        launchPackageAndMaybePlay(
            packageName = packageName,
            autoplay = true,
            returnHomeAfterLaunch = false,
        )

    suspend fun setDefaultSource(source: BridgeAudioSource, autoplay: Boolean): Boolean =
        setSourceInternal(
            source = source,
            appSource = null,
            autoplay = autoplay,
            launchConfiguredOnlinePackage = true,
        )

    suspend fun autoplayCurrentSource(): Boolean {
        val request = MediaCommandRequest(
            requestId = "startup-autoplay",
            command = MediaCommand.PLAY,
        )
        executeNative(request)?.let { nativeResult ->
            if (nativeResult.status == MediaBridgeContract.Status.OK) return true
            if (nativeResult.status != MediaBridgeContract.Status.NOT_SUPPORTED) return false
        }

        val session = preferredSession()
        if (session != null) {
            return session.play().also { played ->
                if (played) setCurrentMediaPackage(session.packageName)
            }
        }
        return false
    }

    private suspend fun launchPackageAndMaybePlay(
        packageName: String,
        autoplay: Boolean,
        returnHomeAfterLaunch: Boolean,
    ): Boolean {
        onUserAction?.invoke()
        val launched = launchPackage?.let { callback ->
            runCatching { callback(packageName) }
                .onFailure { Timber.w(it, "Could not launch configured media package $packageName") }
                .getOrDefault(false)
        } ?: launchConfiguredPackage(packageName)
        if (!launched) return false
        if (!autoplay) {
            if (returnHomeAfterLaunch) returnHomeScreen()
            return true
        }

        val sessionFound = awaitSession(packageName)
        val result = if (sessionFound != null) {
            playAndConfirm(packageName, sessionFound)
        } else {
            Timber.w("Configured media package %s did not publish a MediaSession", packageName)
            false
        }
        if (returnHomeAfterLaunch) returnHomeScreen()
        return result
    }

    private suspend fun playAndConfirm(
        packageName: String,
        initialController: MediaController,
    ): Boolean {
        var controller = initialController
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            setCurrentMediaPackage(packageName)
            return true
        }

        autoplayConfirmDelaysMs.forEachIndexed { attempt, confirmDelayMs ->
            runCatching { controller.transportControls.play() }
                .onFailure {
                    Timber.w(it, "Could not request playback from $packageName (attempt ${attempt + 1})")
                }
            delay(confirmDelayMs.coerceAtLeast(1L))
            controller = configuredController(packageName) ?: controller
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                setCurrentMediaPackage(packageName)
                return true
            }
        }

        runCatching {
            val eventTime = android.os.SystemClock.uptimeMillis()
            val downSent = controller.dispatchMediaButtonEvent(
                KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0),
            )
            val upSent = controller.dispatchMediaButtonEvent(
                KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0),
            )
            downSent || upSent
        }.onFailure {
            Timber.w(it, "Could not send MEDIA_PLAY to configured media package $packageName")
        }
        delay(autoplayMediaKeyConfirmDelayMs.coerceAtLeast(1L))
        controller = configuredController(packageName) ?: controller
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            setCurrentMediaPackage(packageName)
            return true
        }

        Timber.w("Configured media package %s stayed paused after autoplay requests", packageName)
        return false
    }

    private fun configuredController(packageName: String): MediaController? =
        sessionObserver.getActiveControllers().firstOrNull { it.packageName == packageName }

    private fun launchConfiguredPackage(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: run {
                Timber.w("No launch intent for configured media package %s", packageName)
                return false
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(launchIntent) }
            .onFailure { Timber.w(it, "Could not launch configured media package $packageName") }
            .isSuccess
    }

    private suspend fun awaitSession(packageName: String): MediaController? =
        withTimeoutOrNull(sessionWaitTimeoutMs.coerceAtLeast(1L)) {
            var poll = 0
            while (true) {
                sessionObserver.getActiveControllers()
                    .firstOrNull { it.packageName == packageName }
                    ?.let { return@withTimeoutOrNull it }
                val delayMs = sessionPollDelaysMs
                    .getOrElse(poll) { sessionPollDelaysMs.lastOrNull() ?: 1_000L }
                    .coerceAtLeast(1L)
                delay(delayMs)
                poll++
            }
            null
        }

    private fun returnHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(homeIntent) }
            .onFailure { Timber.w(it, "Could not return to HOME after starting media package") }
    }

    override suspend fun setSource(
        source: BridgeAudioSource,
        appSource: String?,
        autoplay: Boolean,
    ): Boolean = setSourceInternal(
        source = source,
        appSource = appSource,
        autoplay = autoplay,
        launchConfiguredOnlinePackage = false,
    )

    private suspend fun setSourceInternal(
        source: BridgeAudioSource,
        appSource: String?,
        autoplay: Boolean,
        launchConfiguredOnlinePackage: Boolean,
    ): Boolean {
        onUserAction?.invoke()
        val center = mediaCenter()
        if (center == null) {
            if (source != BridgeAudioSource.ONLINE) return false
            Timber.i("OneOS MediaCenter unavailable; applying Android ONLINE source path")
            stateHub?.onSourceChanged(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE,
                MediaCenterConstant.AppSource.UNKNOWN,
            )
            val configuredOnlinePackage = defaultMediaPackage().takeIf(String::isNotBlank)
            if (launchConfiguredOnlinePackage && configuredOnlinePackage != null) {
                return launchPackageAndMaybePlay(
                    packageName = configuredOnlinePackage,
                    autoplay = autoplay,
                    returnHomeAfterLaunch = preferences.minimizeOnlinePlayerAfterAutostart,
                )
            }
            if (!autoplay) return true

            val sessionPackage = preferredOnlineSession()?.packageName
            val controller = sessionPackage?.let(::configuredController)
            if (controller != null) {
                return playAndConfirm(controller.packageName, controller)
            }
            return if (configuredOnlinePackage != null) {
                launchPackageAndMaybePlay(
                    packageName = configuredOnlinePackage,
                    autoplay = true,
                    returnHomeAfterLaunch = preferences.minimizeOnlinePlayerAfterAutostart,
                )
            } else {
                true
            }
        }
        val oneOsSource = source.toOneOsSource()
        val oneOsApp = appSource?.let { runCatching { MediaCenterConstant.AppSource.valueOf(it) }.getOrNull() }
            ?: MediaCenterConstant.AppSource.UNKNOWN
        val sourceSwitch = ConfirmedSourceSwitch(
            currentSource = {
                runCatching { center.currentAudioSource.toBridgeSource() }.getOrNull()
            },
            sourceWaitTimeoutMs = sourceWaitTimeoutMs,
            sourcePollDelaysMs = sourcePollDelaysMs,
            autoplayConfirmDelaysMs = autoplayConfirmDelaysMs,
        )
        val transitionGeneration = stateHub?.beginSourceTransition(source)

        return runCatching {
            rememberOnlineSessionBeforeLeaving(center, oneOsSource)
            val sourceConfirmed = sourceSwitch.requestAndConfirm(
                target = source,
                requestTarget = {
                    if (oneOsSource == MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE &&
                        oneOsApp == MediaCenterConstant.AppSource.UNKNOWN
                    ) {
                        center.requestAudioSource(oneOsSource, MediaCenterConstant.AppSource.WECARFLOW)
                    } else if (oneOsApp != MediaCenterConstant.AppSource.UNKNOWN) {
                        center.requestAudioSource(oneOsSource, oneOsApp)
                    } else {
                        center.requestAudioSource(oneOsSource)
                    }
                },
            )
            if (!sourceConfirmed) {
                Timber.w("OneOS did not confirm source %s within timeout", source.name)
                return@runCatching false
            }

            // Publish only the confirmed target. Intermediate source callbacks are filtered by
            // MediaStateHub while this transition generation is active.
            stateHub?.onSourceChanged(oneOsSource, oneOsApp)

            val configuredOnlinePackage = if (
                launchConfiguredOnlinePackage &&
                oneOsSource == MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE
            ) {
                defaultMediaPackage().takeIf(String::isNotBlank)
            } else {
                null
            }
            if (configuredOnlinePackage != null) {
                return@runCatching launchPackageAndMaybePlay(
                    packageName = configuredOnlinePackage,
                    autoplay = autoplay,
                    returnHomeAfterLaunch = preferences.minimizeOnlinePlayerAfterAutostart,
                )
            } else if (!autoplay) {
                true
            } else {
                autoplayConfirmedSource(center, sourceSwitch, oneOsSource)
            }
        }.onFailure(Timber::e).getOrDefault(false)
            .also { succeeded ->
                if (!succeeded && transitionGeneration != null) {
                    stateHub?.cancelSourceTransition(transitionGeneration)
                }
            }
    }

    private suspend fun autoplayConfirmedSource(
        center: MediaCenterManager,
        sourceSwitch: ConfirmedSourceSwitch,
        target: MediaCenterConstant.AudioSource,
    ): Boolean {
        val targetSource = target.toBridgeSource()
        return when (target) {
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO -> {
                val radio = center.radioManager
                sourceSwitch.playAndConfirm(
                    target = targetSource,
                    sendPlay = {
                        radio.requestAudioSource()
                        delay(300L)
                        radio.play()
                    },
                    isPlaying = { isRadioPlaying(radio.radioStatus) },
                )
            }

            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT -> sourceSwitch.playAndConfirm(
                target = targetSource,
                sendPlay = { playBluetooth(center) },
                isPlaying = {
                    center.musicAdapterManager.currentPlayState ==
                        MediaCenterConstant.PlayState.MUSIC_STATE_PLAY
                },
            )

            MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB -> sourceSwitch.playAndConfirm(
                target = targetSource,
                sendPlay = { playMusicAdapter(center, target) },
                isPlaying = {
                    center.musicAdapterManager.currentPlayState ==
                        MediaCenterConstant.PlayState.MUSIC_STATE_PLAY
                },
            )

            MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA -> {
                launchCarPlayActivity()
                sourceSwitch.playAndConfirm(
                    target = targetSource,
                    sendPlay = { carPlayBridge?.play() == true },
                    isPlaying = { carPlayBridge?.isPlaying == true },
                )
            }

            MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE -> {
                val sessionPackage = preferredOnlineSession()?.packageName
                val controller = sessionPackage?.let(::configuredController)
                if (controller != null) {
                    playAndConfirm(controller.packageName, controller)
                } else {
                    val defaultPkg = defaultMediaPackage()
                    defaultPkg.isNotBlank() && launchPackageAndMaybePlay(
                        packageName = defaultPkg,
                        autoplay = true,
                        returnHomeAfterLaunch = preferences.minimizeOnlinePlayerAfterAutostart,
                    )
                }
            }

            else -> false
        }
    }

    private fun rememberOnlineSessionBeforeLeaving(
        center: MediaCenterManager,
        targetSource: MediaCenterConstant.AudioSource,
    ) {
        if (targetSource == MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE) return
        val currentSource = runCatching { center.currentAudioSource }.getOrNull()
        if (currentSource != MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE) return

        val controllers = sessionObserver.getActiveControllers()
        val currentPackage = currentMediaPackage()
        val onlineController = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            currentPackage.isNotBlank() && it.packageName == currentPackage
        } ?: controllers.maxByOrNull {
            it.playbackState?.lastPositionUpdateTime ?: 0L
        }
        rememberOnlineMediaPackage(onlineController?.packageName.orEmpty())
    }

    internal fun preferredOnlineSession(): MediaSessionCommandTarget? {
        val controllers = sessionObserver.getActiveControllers()
        val rememberedPackage = lastOnlineMediaPackageRef.get()
        if (rememberedPackage.isNotBlank()) {
            controllers
                .firstOrNull { it.packageName == rememberedPackage }
                ?.let { return AndroidMediaSessionTarget(it) }
        }
        val configuredPackage = defaultMediaPackage()
        if (configuredPackage.isNotBlank()) {
            controllers
                .firstOrNull { it.packageName == configuredPackage }
                ?.let { return AndroidMediaSessionTarget(it) }
        }
        return null
    }

    private fun rememberOnlineMediaPackage(packageName: String) {
        if (packageName.isNotBlank()) lastOnlineMediaPackageRef.set(packageName)
    }

    override suspend fun tuneRadio(target: RadioStationTarget, autoplay: Boolean): Boolean {
        onUserAction?.invoke()
        val center = mediaCenter() ?: return false
        if (!setSource(BridgeAudioSource.RADIO, appSource = null, autoplay = false)) return false

        delay(250L)
        val radio = center.radioManager
        radio.requestAudioSource()
        if (runCatching { radio.band }.getOrNull() != target.band) {
            runCatching { radio.setBandAsync(target.band) }.onFailure(Timber::e)
            delay(150L)
        }
        val tuned = runCatching {
            radio.tuneFrequency(target.toOneOsFrequency()) || radio.setFrequency(target.frequencyKHz)
        }.onFailure(Timber::e).getOrDefault(false)
        if (!tuned) return false
        if (autoplay) {
            delay(150L)
            radio.play()
        }
        return true
    }

    fun radioStationLists(): RadioStationLists? {
        val center = mediaCenter() ?: return null
        val radio = center.radioManager.takeIf { it.isAlive } ?: return null
        val favorites = mergeRadioStations(
            runCatching { radio.collectionStationsList }.getOrNull().orEmpty(),
        ).take(MAX_RADIO_STATIONS_PER_LIST)
        val saved = mergeRadioStations(
            buildList {
                addAll(runCatching { radio.scanStationsList }.getOrNull().orEmpty())
                addAll(runCatching { radio.radioStationsNameList }.getOrNull().orEmpty())
            },
        ).take(MAX_RADIO_STATIONS_PER_LIST)

        fun mapStation(frequency: Frequency, favorite: Boolean): RadioStationSnapshot {
            val catalogStation = radioCatalogRepository.lookup(frequency.frequency)
            return frequency.toRadioStationSnapshot(
                catalogStation = catalogStation,
                favorite = favorite,
                artworkUri = catalogStation?.let(radioCatalogRepository::artworkUri).orEmpty(),
            )
        }

        return RadioStationLists(
            saved = saved.map { station ->
                mapStation(station, favorites.any { sameRadioStation(station, it) })
            },
            favorites = favorites.map { mapStation(it, favorite = true) },
        )
    }

    private fun playBluetooth(center: MediaCenterManager): Boolean {
        if (runCatching { center.currentAudioSource }
                .getOrNull() != MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT
        ) {
            return false
        }
        val adapterResult = runCatching { center.musicAdapterManager.play() }.getOrDefault(0)
        if (adapterResult == 1) return true
        if (runCatching { center.currentAudioSource }
                .getOrNull() != MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT
        ) {
            return false
        }
        val btController = sessionObserver.getActiveControllers().firstOrNull { controller ->
            val pkg = controller.packageName?.lowercase(java.util.Locale.ROOT).orEmpty()
            pkg.contains("bluetooth") || pkg.contains("a2dp") || pkg.contains("btservice")
        }
        if (btController != null && btController.playbackState?.state != PlaybackState.STATE_PLAYING) {
            return runCatching {
                btController.transportControls.play()
                true
            }.onFailure(Timber::e).getOrDefault(false)
        }
        return false
    }

    private fun playMusicAdapter(
        center: MediaCenterManager,
        target: MediaCenterConstant.AudioSource,
    ): Boolean {
        if (runCatching { center.currentAudioSource }.getOrNull() != target) return false
        return runCatching { center.musicAdapterManager.play() == 1 }
            .onFailure(Timber::e)
            .getOrDefault(false)
    }

    private fun launchCarPlayActivity() {
        runCatching {
            val intent = Intent().apply {
                setClassName("com.autolink.carplay.app", "com.autolink.carplay.app.ui.display.view.CarPlayDisplayActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(intent)
            Timber.tag("AndroidMediaCommandHost").i("CarPlayDisplayActivity launched")
        }.onFailure { e ->
            Timber.tag("AndroidMediaCommandHost").w(e, "Could not launch CarPlayDisplayActivity directly, trying package launch intent")
            runCatching {
                context.packageManager.getLaunchIntentForPackage("com.autolink.carplay.app")?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }.onFailure(Timber::e)
        }
    }

    private fun executeCarPlay(request: MediaCommandRequest): Boolean {
        val bridge = carPlayBridge ?: return false
        return when (request.command) {
            MediaCommand.PLAY -> bridge.play()
            MediaCommand.PAUSE -> bridge.pause()
            MediaCommand.TOGGLE -> bridge.toggle()
            MediaCommand.NEXT -> bridge.next()
            MediaCommand.PREVIOUS -> bridge.previous()
            MediaCommand.SEEK_TO,
            MediaCommand.SET_SOURCE -> false
            MediaCommand.TUNE_RADIO -> false
        }
    }

    private fun executeRadio(
        mediaCenter: MediaCenterManager,
        request: MediaCommandRequest,
    ): Boolean {
        val radio = mediaCenter.radioManager
        val status = if (request.command == MediaCommand.TOGGLE) radio.radioStatus else 0
        return when (radioPlaybackAction(request.command, status)) {
            RadioPlaybackAction.PLAY -> {
                radio.requestAudioSource()
                radio.play()
            }

            RadioPlaybackAction.PAUSE -> radio.pause()
            null -> when (request.command) {
                MediaCommand.NEXT -> radio.seekAsync(0)
                MediaCommand.PREVIOUS -> radio.seekAsync(1)
                MediaCommand.SEEK_TO,
                MediaCommand.SET_SOURCE,
                MediaCommand.PLAY,
                MediaCommand.PAUSE,
                MediaCommand.TOGGLE -> false
                MediaCommand.TUNE_RADIO -> false
            }
        }
    }

    private fun executeMusicAdapter(
        mediaCenter: MediaCenterManager,
        request: MediaCommandRequest,
    ): Boolean {
        val adapter = mediaCenter.musicAdapterManager
        val result = when (request.command) {
            MediaCommand.PLAY -> adapter.play()
            MediaCommand.PAUSE -> adapter.pause()
            MediaCommand.TOGGLE -> if (
                adapter.currentPlayState == MediaCenterConstant.PlayState.MUSIC_STATE_PLAY
            ) adapter.pause() else adapter.play()

            MediaCommand.NEXT -> adapter.next()
            MediaCommand.PREVIOUS -> adapter.prev()
            MediaCommand.SEEK_TO -> adapter.seekTo(request.position ?: return false)
            MediaCommand.SET_SOURCE -> return false
            MediaCommand.TUNE_RADIO -> return false
        }
        return result == 1
    }
}

class CarPlayMediaSessionTarget(
    private val bridge: CarPlayNativeBridge,
) : MediaSessionCommandTarget {
    override val packageName: String get() = CarPlayNativeBridge.CARPLAY_PACKAGE
    override val lastPositionUpdateTime: Long get() = bridge.lastCoverTimestamp
    override val playbackState: SessionPlaybackState
        get() = if (bridge.isPlaying) SessionPlaybackState.PLAYING else SessionPlaybackState.NOT_PLAYING
    override val capabilities: Long
        get() = MediaCapabilities.BASIC_PLAYBACK or MediaCapabilities.TRACK_NAVIGATION or MediaCapabilities.TOGGLE

    override fun play(): Boolean = bridge.play()
    override fun pause(): Boolean = bridge.pause()
    override fun toggle(): Boolean = bridge.toggle()
    override fun next(): Boolean = bridge.next()
    override fun previous(): Boolean = bridge.previous()
    override fun seekTo(position: Long): Boolean = false
}

fun BridgeAudioSource.toOneOsSource(): MediaCenterConstant.AudioSource = when (this) {
    BridgeAudioSource.USB -> MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB
    BridgeAudioSource.BT -> MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT
    BridgeAudioSource.RADIO -> MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO
    BridgeAudioSource.ONLINE -> MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE
    BridgeAudioSource.OTHER -> MediaCenterConstant.AudioSource.AUDIO_SOURCE_OTHER
    BridgeAudioSource.YUNTING -> MediaCenterConstant.AudioSource.AUDIO_SOURCE_YUNTING
    BridgeAudioSource.CPAA -> MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA
    BridgeAudioSource.UNKNOWN -> MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN
}

class AndroidMediaSessionTarget(
    private val controller: MediaController,
) : MediaSessionCommandTarget {
    override val packageName: String get() = controller.packageName.orEmpty()
    override val lastPositionUpdateTime: Long
        get() = controller.playbackState?.lastPositionUpdateTime ?: 0L

    override val playbackState: SessionPlaybackState
        get() = if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            SessionPlaybackState.PLAYING
        } else SessionPlaybackState.NOT_PLAYING

    override val capabilities: Long
        get() {
            val actions = controller.playbackState?.actions ?: 0L
            var caps = MediaCapabilities.fromPlaybackActions(actions)
            if (caps and MediaCapabilities.PLAY != 0L &&
                caps and MediaCapabilities.PAUSE != 0L
            ) {
                caps = caps or MediaCapabilities.TOGGLE
            }
            if (controller.packageName in CARPLAY_MEDIA_SESSION_PACKAGES &&
                (caps and (MediaCapabilities.BASIC_PLAYBACK or MediaCapabilities.TRACK_NAVIGATION) == 0L)
            ) {
                caps = caps or MediaCapabilities.BASIC_PLAYBACK or MediaCapabilities.TRACK_NAVIGATION
            }
            return caps
        }

    override fun play(): Boolean = runCatching {
        controller.transportControls.play()
        true
    }.getOrDefault(false)

    override fun pause(): Boolean = runCatching {
        controller.transportControls.pause()
        true
    }.getOrDefault(false)

    override fun next(): Boolean = runCatching {
        controller.transportControls.skipToNext()
        true
    }.getOrDefault(false)

    override fun previous(): Boolean = runCatching {
        controller.transportControls.skipToPrevious()
        true
    }.getOrDefault(false)

    override fun seekTo(position: Long): Boolean = runCatching {
        controller.transportControls.seekTo(position)
        true
    }.getOrDefault(false)
}
