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
) : MediaCommandHost {
    companion object {
        private const val MAX_RADIO_STATIONS_PER_LIST = 256
    }

    private val currentMediaPackageRef = AtomicReference("")

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
                center.musicManagerMap[source]?.isAlive == true

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

    override suspend fun startDefaultAndPlay(packageName: String): Boolean {
        onUserAction?.invoke()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launchIntent) }.onFailure { return false }

        // Wait up to 3 seconds for session to appear
        val sessionFound = withTimeoutOrNull(3000L) {
            while (true) {
                val controller = sessionObserver.getActiveControllers().firstOrNull { it.packageName == packageName }
                if (controller != null) {
                    return@withTimeoutOrNull controller
                }
                delay(100L)
            }
            null
        }

        return if (sessionFound != null) {
            runCatching {
                sessionFound.transportControls.play()
                true
            }.getOrDefault(false)
        } else {
            true
        }
    }

    override suspend fun setSource(
        source: BridgeAudioSource,
        appSource: String?,
        autoplay: Boolean,
    ): Boolean {
        onUserAction?.invoke()
        val center = mediaCenter() ?: return false
        val oneOsSource = source.toOneOsSource()
        val oneOsApp = appSource?.let { runCatching { MediaCenterConstant.AppSource.valueOf(it) }.getOrNull() }
            ?: MediaCenterConstant.AppSource.UNKNOWN

        return runCatching {
            pauseCurrentPlayback(center)

            if (oneOsSource == MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE &&
                oneOsApp == MediaCenterConstant.AppSource.UNKNOWN
            ) {
                center.requestAudioSource(oneOsSource, MediaCenterConstant.AppSource.WECARFLOW)
            } else if (oneOsApp != MediaCenterConstant.AppSource.UNKNOWN) {
                center.requestAudioSource(oneOsSource, oneOsApp)
            } else {
                center.requestAudioSource(oneOsSource)
            }

            // Immediately update stateHub to avoid stale UI state if OneOS skips onSourceChanged
            // (e.g. when OneOS was already on this native source while an Android MediaSession was active)
            stateHub?.onSourceChanged(oneOsSource, oneOsApp)

            if (autoplay) {
                delay(500L)
                when (oneOsSource) {
                    MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO -> {
                        val radio = center.radioManager
                        radio.requestAudioSource()
                        delay(300L)
                        radio.play()
                        delay(600L)
                        if (!isRadioPlaying(radio.radioStatus) && radio.radioStatus != 1) {
                            Timber.i("Radio not playing yet (status=0x%X), retrying play", radio.radioStatus)
                            radio.play()
                        }
                    }
                    MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT -> {
                        playBluetooth(center)
                    }
                    MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB -> {
                        center.musicAdapterManager.play()
                    }
                    MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA -> {
                        carPlayBridge?.play()
                        launchCarPlayActivity()
                    }
                    MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE -> {
                        val session = preferredSession()
                        if (session != null) {
                            session.play()
                        } else {
                            val defaultPkg = defaultMediaPackage()
                            if (defaultPkg.isNotBlank()) {
                                startDefaultAndPlay(defaultPkg)
                            }
                        }
                    }
                    else -> {}
                }
            }
            true
        }.onFailure(Timber::e).getOrDefault(false)
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

    private fun playBluetooth(center: MediaCenterManager) {
        val adapterResult = runCatching { center.musicAdapterManager.play() }.getOrDefault(0)
        if (adapterResult != 1) {
            val btController = sessionObserver.getActiveControllers().firstOrNull { controller ->
                val pkg = controller.packageName?.lowercase(java.util.Locale.ROOT).orEmpty()
                pkg.contains("bluetooth") || pkg.contains("a2dp") || pkg.contains("btservice")
            }
            if (btController != null && btController.playbackState?.state != PlaybackState.STATE_PLAYING) {
                runCatching { btController.transportControls.play() }.onFailure(Timber::e)
            }
        }
    }

    private fun pauseCurrentPlayback(center: MediaCenterManager) {
        runCatching { center.radioManager.pause() }.onFailure(Timber::e)
        runCatching { center.musicAdapterManager.pause() }.onFailure(Timber::e)
        carPlayBridge?.pause()
        sessionObserver.getActiveControllers().forEach { controller ->
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                runCatching { controller.transportControls.pause() }.onFailure(Timber::e)
            }
        }
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }.onFailure(Timber::e)
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

    override fun next(): Boolean = dispatchSkip(KeyEvent.KEYCODE_MEDIA_NEXT) {
        controller.transportControls.skipToNext()
    }

    override fun previous(): Boolean = dispatchSkip(KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
        controller.transportControls.skipToPrevious()
    }

    override fun seekTo(position: Long): Boolean = runCatching {
        controller.transportControls.seekTo(position)
        true
    }.getOrDefault(false)

    private fun dispatchSkip(keyCode: Int, fallback: () -> Unit): Boolean = runCatching {
        val down = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val up = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        if (!down && !up) fallback()
        true
    }.getOrDefault(false)
}
