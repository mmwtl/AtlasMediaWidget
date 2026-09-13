package com.mmwtl.atlasmediaapi.media.carplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.os.IBinder
import android.os.Parcel
import com.autolink.carplay.common.aidl.INowPlayingUpdateCallback
import com.autolink.carplay.common.data.iap.MediaCover
import com.autolink.carplay.common.data.iap.NowPlayingInfo
import com.mmwtl.atlasmediaapi.media.bridge.ArtworkInput
import com.mmwtl.atlasmediaapi.media.bridge.ArtworkRepository
import com.mmwtl.atlasmediaapi.media.bridge.NormalizedArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CarPlayNativeBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val artworkRepository: ArtworkRepository,
    private val onArtworkUpdated: (NormalizedArtwork, NowPlayingInfo?) -> Unit,
    private val onNowPlayingUpdated: (NowPlayingInfo) -> Unit = {},
) {
    companion object {
        const val CARPLAY_PACKAGE = "com.autolink.carplay"
        const val CARPLAY_SERVICE_ACTION = "com.autolink.carplay.action.CarPlayService"
        private const val DESCRIPTOR = "com.autolink.carplay.common.aidl.ICarPlayService"
        private const val TRANSACTION_START_NOW_PLAYING_UPDATES = 9
        private const val TRANSACTION_STOP_NOW_PLAYING_UPDATES = 10
        private const val TRANSACTION_SEND_HID_EVENT_OVER_IAP = 27
        private const val TRANSACTION_SEND_HID_EVENT_OVER_CARPLAY = 28

        // CarPlayHidKeyCode
        const val CARPLAY_KEY_PLAY = 32
        const val CARPLAY_KEY_PAUSE = 33
        const val CARPLAY_KEY_PLAYPAUSE = 34
        const val CARPLAY_KEY_NEXTTRACK = 35
        const val CARPLAY_KEY_PREVTRACK = 36

        // IapHidKeyCode
        const val IAP_HID_PLAYBACK_PLAY = 1
        const val IAP_HID_PLAYBACK_PAUSE = 2
        const val IAP_HID_PLAYBACK_NEXT = 4
        const val IAP_HID_PLAYBACK_PREV = 8
        const val IAP_HID_PLAYBACK_PLAY_PAUSE = 64
    }

    private val isStarted = AtomicBoolean(false)
    private var isBound = false
    private var remoteBinder: IBinder? = null
    private var reconnectJob: Job? = null

    var isConnected: Boolean = false
        private set
    var lastCoverTimestamp: Long = 0L
        private set
    var lastNowPlaying: NowPlayingInfo? = null
        private set
    var currentArtwork: NormalizedArtwork = NormalizedArtwork("", "")
        private set

    val isPlaying: Boolean
        get() = lastNowPlaying?.playbackStatus == 1

    fun getCachedArtwork(): NormalizedArtwork? = currentArtwork.takeIf { it.uri.isNotBlank() }

    fun play(): Boolean = sendMediaCommand(CARPLAY_KEY_PLAY, IAP_HID_PLAYBACK_PLAY)

    fun pause(): Boolean = sendMediaCommand(CARPLAY_KEY_PAUSE, IAP_HID_PLAYBACK_PAUSE)

    fun toggle(): Boolean = sendMediaCommand(CARPLAY_KEY_PLAYPAUSE, IAP_HID_PLAYBACK_PLAY_PAUSE)

    fun next(): Boolean = sendMediaCommand(CARPLAY_KEY_NEXTTRACK, IAP_HID_PLAYBACK_NEXT)

    fun previous(): Boolean = sendMediaCommand(CARPLAY_KEY_PREVTRACK, IAP_HID_PLAYBACK_PREV)

    fun sendMediaCommand(carPlayKeyCode: Int, iapKeyCode: Int): Boolean {
        val binder = remoteBinder ?: return false
        return runCatching {
            // Send Key Down (action 0) and Key Up (action 1) over both CarPlay and IAP channels
            sendHidEventTransaction(binder, TRANSACTION_SEND_HID_EVENT_OVER_CARPLAY, carPlayKeyCode, 0)
            sendHidEventTransaction(binder, TRANSACTION_SEND_HID_EVENT_OVER_CARPLAY, carPlayKeyCode, 1)
            sendHidEventTransaction(binder, TRANSACTION_SEND_HID_EVENT_OVER_IAP, iapKeyCode, 0)
            sendHidEventTransaction(binder, TRANSACTION_SEND_HID_EVENT_OVER_IAP, iapKeyCode, 1)
            Timber.tag("CarPlayNativeBridge").i("sendMediaCommand succeeded for carPlayKey=%d, iapKey=%d", carPlayKeyCode, iapKeyCode)
            true
        }.onFailure { e ->
            Timber.tag("CarPlayNativeBridge").e(e, "sendMediaCommand failed for carPlayKey=%d", carPlayKeyCode)
        }.getOrDefault(false)
    }

    private fun sendHidEventTransaction(
        binder: IBinder,
        transactionCode: Int,
        keyCode: Int,
        keyAction: Int,
    ) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(keyCode)
            data.writeInt(keyAction)
            binder.transact(transactionCode, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        Timber.tag("CarPlayNativeBridge").w("CarPlay service binder died, scheduling reconnect")
        remoteBinder = null
        isConnected = false
        scheduleReconnect()
    }

    private val callback = object : INowPlayingUpdateCallback.Stub() {
        override fun onNowPlayingUpdate(nowPlayingInfo: NowPlayingInfo?) {
            Timber.tag("CarPlayNativeBridge").d("onNowPlayingUpdate: %s", nowPlayingInfo)
            lastNowPlaying = nowPlayingInfo
            if (nowPlayingInfo != null) {
                lastCoverTimestamp = System.currentTimeMillis()
                onNowPlayingUpdated(nowPlayingInfo)
            }
        }

        override fun onMediaCoverUpdate(mediaCover: MediaCover?) {
            Timber.tag("CarPlayNativeBridge").d("onMediaCoverUpdate: %s", mediaCover)
            if (mediaCover != null) {
                lastCoverTimestamp = System.currentTimeMillis()
                processMediaCover(mediaCover)
            }
        }

        override fun onPlaybackListUpdate(
            mediaItemCount: Int,
            mediaItemStartIndex: Int,
            mediaItemList: List<*>?,
        ) {
            // No-op for now
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.tag("CarPlayNativeBridge").i("Connected to CarPlayService: %s", name)
            remoteBinder = service
            isConnected = true
            service?.linkToDeath(deathRecipient, 0)
            registerNowPlayingUpdates(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.tag("CarPlayNativeBridge").w("Disconnected from CarPlayService: %s", name)
            remoteBinder = null
            isConnected = false
            scheduleReconnect()
        }
    }

    fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        Timber.tag("CarPlayNativeBridge").i("Starting CarPlayNativeBridge")
        bindCarPlayService()
    }

    fun stop() {
        if (!isStarted.compareAndSet(true, false)) return
        Timber.tag("CarPlayNativeBridge").i("Stopping CarPlayNativeBridge")
        reconnectJob?.cancel()
        reconnectJob = null
        unregisterNowPlayingUpdates(remoteBinder)
        if (isBound) {
            runCatching { context.unbindService(serviceConnection) }.onFailure(Timber::e)
            isBound = false
        }
        remoteBinder = null
        isConnected = false
    }

    private fun bindCarPlayService() {
        if (!isStarted.get() || isBound) return
        val intent = Intent(CARPLAY_SERVICE_ACTION).apply {
            setPackage(CARPLAY_PACKAGE)
        }
        runCatching {
            isBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Timber.tag("CarPlayNativeBridge").d("bindService result: %s", isBound)
            if (!isBound) {
                scheduleReconnect()
            }
        }.onFailure { e ->
            Timber.tag("CarPlayNativeBridge").e(e, "Error binding CarPlayService")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!isStarted.get() || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(5000)
            if (isActive && isStarted.get() && !isConnected) {
                if (isBound) {
                    runCatching { context.unbindService(serviceConnection) }
                    isBound = false
                }
                bindCarPlayService()
            }
        }
    }

    private fun registerNowPlayingUpdates(binder: IBinder?) {
        binder ?: return
        scope.launch(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeStrongBinder(callback.asBinder())
                binder.transact(TRANSACTION_START_NOW_PLAYING_UPDATES, data, reply, 0)
                reply.readException()
                Timber.tag("CarPlayNativeBridge").i("startNowPlayingUpdates succeeded")
            } catch (e: Exception) {
                Timber.tag("CarPlayNativeBridge").e(e, "Failed to call startNowPlayingUpdates")
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    private fun unregisterNowPlayingUpdates(binder: IBinder?) {
        binder ?: return
        runCatching {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeStrongBinder(callback.asBinder())
                binder.transact(TRANSACTION_STOP_NOW_PLAYING_UPDATES, data, reply, 0)
                reply.readException()
                Timber.tag("CarPlayNativeBridge").d("stopNowPlayingUpdates called")
            } finally {
                data.recycle()
                reply.recycle()
            }
        }.onFailure(Timber::e)
    }

    private fun processMediaCover(cover: MediaCover) {
        val input = when {
            cover.bitmap != null -> ArtworkInput(bitmap = cover.bitmap)
            cover.artworkData != null && cover.artworkData.isNotEmpty() -> {
                val bmp = runCatching {
                    BitmapFactory.decodeByteArray(cover.artworkData, 0, cover.artworkData.size)
                }.getOrNull()
                if (bmp != null) ArtworkInput(bitmap = bmp) else null
            }
            !cover.mediaItemArtworkFilePath.isNullOrBlank() -> {
                val file = File(cover.mediaItemArtworkFilePath)
                if (file.exists() && file.length() > 0) {
                    ArtworkInput(sourceUri = file.absolutePath)
                } else null
            }
            else -> null
        }

        if (input != null) {
            artworkRepository.normalize(input) { normalized ->
                currentArtwork = normalized
                onArtworkUpdated(normalized, lastNowPlaying)
            }
        }
    }
}
