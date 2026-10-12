package com.mmwtl.atlasmediaapi.media.bridge

import com.geely.lib.oneosapi.mediacenter.MediaCenterManager
import com.geely.lib.oneosapi.mediacenter.bean.DeviceInfo
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import com.geely.lib.oneosapi.mediacenter.bean.MediaData
import com.geely.lib.oneosapi.mediacenter.bean.MusicFileData
import com.geely.lib.oneosapi.mediacenter.bean.OnlineUserInfo
import com.geely.lib.oneosapi.mediacenter.bean.SearchResult
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import com.geely.lib.oneosapi.mediacenter.listener.DeviceStateListener
import com.geely.lib.oneosapi.mediacenter.listener.IRadioStateListener
import com.geely.lib.oneosapi.mediacenter.listener.MusicStateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class OneOsMediaBridgeAdapter(
    private val hub: MediaStateHub,
    private val scope: CoroutineScope,
    private val onOnlineSourceConfirmed: () -> Unit = {},
    /**
     * OneOS posts listener callbacks to the main thread, and handling them reads Binder state,
     * queries MediaStore and decodes artwork. One serial thread keeps their order off main.
     */
    private val callbackExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "atlas-oneos-callbacks")
    },
) {
    private val attachLock = Any()

    @Volatile
    private var manager: MediaCenterManager? = null
    private var radioStateListener: IRadioStateListener? = null
    private val deviceListeners = mutableMapOf<MediaCenterConstant.AudioSource, DeviceStateListener>()
    private val playStateGenerations = ConcurrentHashMap<MediaCenterConstant.AudioSource, AtomicLong>()
    private val sourceCallbackGeneration = AtomicLong()

    private val musicStateListener = object : MusicStateListener {
        override fun onMediaDataChanged(
            source: MediaCenterConstant.AudioSource,
            mediaData: MediaData?,
        ) = dispatch { hub.onOneOsMediaData(source, mediaData) }

        override fun onPlayPositionChanged(
            source: MediaCenterConstant.AudioSource,
            current: Long,
            total: Long,
        ) = dispatch { hub.onOneOsProgress(source, current, total) }

        override fun onPlayStateChanged(
            source: MediaCenterConstant.AudioSource,
            state: MediaCenterConstant.PlayState,
        ) {
            if (state == MediaCenterConstant.PlayState.MUSIC_STATE_PLAY) {
                playStateGenerations.computeIfAbsent(source) { AtomicLong() }.incrementAndGet()
            }
            dispatch {
                hub.onOneOsPlayState(source, state)
                refreshCurrentMedia(expectedSource = source, includePlayState = false)
            }
        }

        override fun onPlayListChanged(
            source: MediaCenterConstant.AudioSource,
            list: MutableList<MediaData>?,
        ) = Unit

        override fun onFavorStateChanged(
            source: MediaCenterConstant.AudioSource,
            mediaData: MediaData?,
        ) = Unit

        override fun onLrcLoad(
            source: MediaCenterConstant.AudioSource,
            lrc: String?,
            time: Long,
        ) = Unit

        override fun onPlayModeChange(
            source: MediaCenterConstant.AudioSource,
            mode: MediaCenterConstant.PlayMode,
        ) = Unit
    }

    private val sourceStateListener = object : com.geely.lib.oneosapi.mediacenter.listener.SourceStateListener {
        override fun onSourceChanged(
            source: MediaCenterConstant.AudioSource,
            appSource: MediaCenterConstant.AppSource,
        ) {
            dispatch { this@OneOsMediaBridgeAdapter.onSourceChanged(source, appSource) }
        }

        override fun onWeCarFlowTabChanged(
            source: MediaCenterConstant.AudioSource,
            appSource: MediaCenterConstant.AppSource,
        ) {
            dispatch { this@OneOsMediaBridgeAdapter.onSourceChanged(source, appSource) }
        }
    }

    /** Runs a callback on the callback thread unless the adapter detached in the meantime. */
    private fun dispatch(block: () -> Unit) {
        val owner = manager ?: return
        runCatching {
            callbackExecutor.execute {
                if (manager === owner) runCatching(block).onFailure(Timber::e)
            }
        }.onFailure(Timber::e)
    }

    /** Registers listeners and publishes the current state; performs Binder calls, call off main. */
    fun attach(mediaCenterManager: MediaCenterManager) = synchronized(attachLock) {
        detach(notify = false)
        manager = mediaCenterManager
        val listener = createRadioStateListener(mediaCenterManager)
        radioStateListener = listener
        runCatching { mediaCenterManager.addSourceStateListener(sourceStateListener) }
            .onFailure(Timber::e)
        runCatching { mediaCenterManager.musicAdapterManager.addMusicStateListener(musicStateListener) }
            .onFailure(Timber::e)
        runCatching { mediaCenterManager.radioManager.openRadioAsync(listener) }
            .onFailure(Timber::e)

        mediaCenterManager.musicManagerMap.forEach { (source, musicManager) ->
            val dListener = createDeviceListener(source)
            deviceListeners[source] = dListener
            runCatching { musicManager.addDeviceStateListener(dListener) }.onFailure(Timber::e)
        }

        val currentSource = runCatching { mediaCenterManager.currentAudioSource }
            .getOrDefault(MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN)
        val currentApp = runCatching { mediaCenterManager.currentAppSource }
            .getOrDefault(MediaCenterConstant.AppSource.UNKNOWN)
        hub.onBackendConnected(currentSource, currentApp, queryAvailability(mediaCenterManager))
        if (currentSource == MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE) {
            onOnlineSourceConfirmed()
        }
        refreshCurrentMedia()
    }

    fun detach(notify: Boolean = true) = synchronized(attachLock) {
        val currentManager = manager
        val currentRadioListener = radioStateListener
        // Invalidate radio callbacks before unregistering: BaseRadioManager keeps listeners in a
        // local list and a Binder callback already in flight may finish after closeRadio().
        manager = null
        sourceCallbackGeneration.incrementAndGet()
        radioStateListener = null
        if (currentManager != null) {
            runCatching {
                currentManager.removeSourceStateListener(sourceStateListener)
            }.onFailure(Timber::e)
            runCatching {
                currentManager.musicAdapterManager.removeMusicStateListener(musicStateListener)
            }.onFailure(Timber::e)
            if (currentRadioListener != null) {
                runCatching { currentManager.radioManager.closeRadio(currentRadioListener) }
                    .onFailure(Timber::e)
            }
            deviceListeners.forEach { (source, listener) ->
                runCatching {
                    currentManager.musicManagerMap[source]?.removeDeviceStateListener(listener)
                }.onFailure(Timber::e)
            }
        }
        deviceListeners.clear()
        if (notify) hub.onBackendDisconnected("OneOS MediaCenter disconnected")
    }

    fun onSourceChanged(
        source: MediaCenterConstant.AudioSource,
        appSource: MediaCenterConstant.AppSource,
    ) {
        val currentManager = manager ?: return
        val generation = sourceCallbackGeneration.incrementAndGet()
        val currentSource = runCatching { currentManager.currentAudioSource }.getOrNull()
        if (isKnownSource(currentSource)) {
            if (isCurrentSourceCallback(source, currentSource)) {
                publishConfirmedSource(currentManager, source, appSource)
            } else {
                Timber.d(
                    "Ignoring stale OneOS source callback: callback=%s current=%s",
                    source,
                    currentSource,
                )
                publishCurrentSource(currentManager)
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            val actualSource = awaitKnownCurrentSource(
                currentSource = { runCatching { currentManager.currentAudioSource }.getOrNull() },
            )
            if (sourceCallbackGeneration.get() != generation || manager !== currentManager) return@launch
            if (actualSource == null) {
                Timber.d(
                    "Ignoring unconfirmed OneOS source callback: callback=%s current=%s",
                    source,
                    actualSource,
                )
                return@launch
            }
            if (actualSource != source) {
                Timber.d(
                    "Reconciling stale OneOS source callback: callback=%s current=%s",
                    source,
                    actualSource,
                )
            }
            val confirmedAppSource = if (actualSource == source) {
                appSource
            } else {
                runCatching { currentManager.currentAppSource }
                    .getOrDefault(MediaCenterConstant.AppSource.UNKNOWN)
            }
            publishConfirmedSource(currentManager, actualSource, confirmedAppSource)
        }
    }

    /**
     * Re-reads the current source and media. Source callbacks are filtered while a switch is
     * pending and can be lost, so this repairs state after a failed switch and during
     * reconciliation.
     */
    fun resync() {
        val currentManager = manager ?: return
        val source = runCatching { currentManager.currentAudioSource }.getOrNull()
            ?.takeIf(::isKnownSource) ?: return
        if (hub.selectedSourceName() != source.toBridgeSource().name) {
            publishCurrentSource(currentManager)
        } else {
            refreshCurrentMedia(expectedSource = source)
        }
    }

    private fun publishCurrentSource(owner: MediaCenterManager) {
        val actualSource = runCatching { owner.currentAudioSource }.getOrNull()
            ?.takeIf(::isKnownSource) ?: return
        val actualAppSource = runCatching { owner.currentAppSource }
            .getOrDefault(MediaCenterConstant.AppSource.UNKNOWN)
        publishConfirmedSource(owner, actualSource, actualAppSource)
    }

    private fun publishConfirmedSource(
        owner: MediaCenterManager,
        source: MediaCenterConstant.AudioSource,
        appSource: MediaCenterConstant.AppSource,
    ) {
        if (manager !== owner || runCatching { owner.currentAudioSource }.getOrNull() != source) {
            Timber.d(
                "Ignoring stale OneOS source callback: callback=%s current=%s",
                source,
                runCatching { owner.currentAudioSource }.getOrNull(),
            )
            return
        }
        hub.onSourceChanged(source, appSource)
        if (source == MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE) {
            onOnlineSourceConfirmed()
        }
        refreshCurrentMedia()
    }

    internal fun playStateGeneration(source: MediaCenterConstant.AudioSource): Long =
        playStateGenerations[source]?.get() ?: 0L

    private fun refreshCurrentMedia(
        expectedSource: MediaCenterConstant.AudioSource? = null,
        includePlayState: Boolean = true,
    ) {
        val currentManager = manager ?: return
        runCatching {
            val source = currentManager.currentAudioSource
            if (expectedSource != null && source != expectedSource) return@runCatching
            if (source == MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO) {
                val radio = currentManager.radioManager
                val frequency = radio.getCurrentFrequency(radio.band)
                publishRadioIfActive(
                    owner = currentManager,
                    frequency = frequency,
                    playing = isRadioPlaying(radio.radioStatus),
                )
                return@runCatching
            }
            val adapter = currentManager.musicAdapterManager
            hub.onOneOsMediaData(source, adapter.currentMediaData)
            if (includePlayState) {
                adapter.currentPlayState?.let { hub.onOneOsPlayState(source, it) }
            }
            val data = adapter.currentMediaData
            hub.onOneOsProgress(source, adapter.currentPosition, data?.duration ?: -1L)
        }.onFailure(Timber::e)
    }

    private fun createRadioStateListener(
        owner: MediaCenterManager,
    ): IRadioStateListener = object : IRadioStateListener.Default() {
        override fun onCurrentFrequency(frequency: Frequency?) = dispatch { refreshRadio(owner, frequency) }

        override fun onStationFrequency(frequency: Frequency?) = dispatch { refreshRadio(owner, frequency) }

        override fun onRadioStatusChanged(status: Int) {
            dispatch { refreshRadio(owner, frequency = null, playing = isRadioPlaying(status)) }
        }
    }

    private fun refreshRadio(
        owner: MediaCenterManager,
        frequency: Frequency?,
        playing: Boolean? = null,
    ) {
        if (manager !== owner) return
        if (runCatching { owner.currentAudioSource }.getOrNull() !=
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO
        ) return
        val currentPlaying = playing ?: runCatching {
            isRadioPlaying(owner.radioManager.radioStatus)
        }.getOrDefault(false)
        publishRadioIfActive(owner, frequency, currentPlaying)
    }

    private fun publishRadioIfActive(
        owner: MediaCenterManager,
        frequency: Frequency?,
        playing: Boolean,
    ) {
        if (manager !== owner) return
        hub.onOneOsRadioState(frequency, playing)
    }

    private fun queryAvailability(
        mediaCenterManager: MediaCenterManager,
    ): Map<BridgeAudioSource, Pair<Boolean, Boolean>> {
        val selected = runCatching { mediaCenterManager.currentAudioSource }.getOrNull()
        val result = mutableMapOf<BridgeAudioSource, Pair<Boolean, Boolean>>()
        mediaCenterManager.musicManagerMap.forEach { (source, musicManager) ->
            val available = runCatching { musicManager.isAlive }.getOrDefault(false)
            val connected = when (source) {
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE,
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_YUNTING -> available

                else -> runCatching { !musicManager.devicesInfo.isNullOrEmpty() }.getOrDefault(false)
            } || source == selected
            result[source.toBridgeSource()] = connected to available
        }
        val radioAvailable = runCatching { mediaCenterManager.radioManager.isAlive }.getOrDefault(false)
        result[BridgeAudioSource.RADIO] = ((selected == MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO) to radioAvailable)
        result[BridgeAudioSource.OTHER] = false to false
        result[BridgeAudioSource.UNKNOWN] = false to false
        return result
    }

    private fun createDeviceListener(
        registeredSource: MediaCenterConstant.AudioSource,
    ): DeviceStateListener = object : DeviceStateListener {
        override fun onDeviceStateChanged(
            source: MediaCenterConstant.AudioSource,
            state: MediaCenterConstant.DeviceState,
            info: DeviceInfo?,
        ) {
            val connected = when (state) {
                MediaCenterConstant.DeviceState.DEVICE_USB_MOUNT,
                MediaCenterConstant.DeviceState.DEVICE_USB_SCAN_COMPLETE,
                MediaCenterConstant.DeviceState.DEVICE_BT_CONNECTED,
                MediaCenterConstant.DeviceState.DEVICE_CPAA_CONNECTED_CP,
                MediaCenterConstant.DeviceState.DEVICE_CPAA_CONNECTED_AA -> true

                MediaCenterConstant.DeviceState.DEVICE_USB_UNMOUNT,
                MediaCenterConstant.DeviceState.DEVICE_BT_DISCONNECTED,
                MediaCenterConstant.DeviceState.DEVICE_BT_UNBOUND,
                MediaCenterConstant.DeviceState.DEVICE_BT_OFF,
                MediaCenterConstant.DeviceState.DEVICE_CPAA_DISCONNECTED -> false

                else -> return
            }
            dispatch { hub.onSourceAvailability(source, connected = connected, available = true) }
        }

        override fun onDeviceError(
            source: MediaCenterConstant.AudioSource,
            error: Int,
            errorMsg: String?,
        ) = dispatch { hub.onOneOsError(source, "device error $error: ${errorMsg.orEmpty()}") }

        override fun onBluetoothDeviceChange(
            source: MediaCenterConstant.AudioSource,
            deviceInfoList: MutableList<DeviceInfo>?,
        ) {
            val connected = !deviceInfoList.isNullOrEmpty()
            dispatch { hub.onSourceAvailability(source, connected = connected, available = true) }
        }

        override fun onAppExistStateChanged(
            source: MediaCenterConstant.AudioSource,
            appSource: MediaCenterConstant.AppSource,
            existed: Boolean,
        ) = dispatch { hub.onSourceAvailability(source, connected = existed, available = existed) }

        override fun onAppDied(appSource: MediaCenterConstant.AppSource) {
            dispatch { hub.onSourceAvailability(registeredSource, connected = false, available = false) }
        }

        override fun onScanPathFinish(
            source: MediaCenterConstant.AudioSource,
            musicFileDataList: MutableList<MusicFileData>?,
        ) = Unit

        @Suppress("DEPRECATION")
        override fun onSearchSongResult(
            source: MediaCenterConstant.AudioSource,
            appSource: MediaCenterConstant.AppSource,
            searchResults: MutableList<SearchResult>?,
        ) = Unit

        @Suppress("DEPRECATION")
        override fun onUserInfoResult(
            source: MediaCenterConstant.AudioSource,
            appSource: MediaCenterConstant.AppSource,
            userInfo: OnlineUserInfo?,
        ) = Unit
    }
}

internal fun isCurrentSourceCallback(
    callbackSource: MediaCenterConstant.AudioSource,
    currentSource: MediaCenterConstant.AudioSource?,
): Boolean = isKnownSource(currentSource) && callbackSource == currentSource

private fun isKnownSource(source: MediaCenterConstant.AudioSource?): Boolean =
    source != null && source != MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN

internal suspend fun awaitKnownCurrentSource(
    currentSource: () -> MediaCenterConstant.AudioSource?,
    // A dropped callback leaves the card on the old source; a newer callback supersedes the wait.
    pollDelaysMs: List<Long> = listOf(0L, 50L, 100L, 200L, 400L, 800L, 1_600L),
    wait: suspend (Long) -> Unit = { delay(it) },
): MediaCenterConstant.AudioSource? {
    pollDelaysMs.forEach { pollDelayMs ->
        if (pollDelayMs > 0L) wait(pollDelayMs)
        val actualSource = currentSource()
        if (isKnownSource(actualSource)) return actualSource
    }
    return null
}
