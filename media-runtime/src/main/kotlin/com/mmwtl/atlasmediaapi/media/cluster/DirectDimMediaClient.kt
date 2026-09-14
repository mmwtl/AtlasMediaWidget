package com.mmwtl.atlasmediaapi.media.cluster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Narrow client for the media Binder shipped by DIMInteraction V9.03.
 *
 * The public ECarX facade exposes only the Uri artwork field. The firmware's ONLINE
 * branch, however, is guarded by a second legacy String artwork field. Writing the
 * documented-on-device parcel shape lets us populate both fields without pretending
 * that a USB volume is mounted.
 */
internal class DirectDimMediaClient(context: Context) {
    data class Payload(
        val sourceType: Int,
        val uuid: String,
        val title: String,
        val album: String,
        val artist: String,
        val artworkUri: Uri?,
        val duration: Long,
        val playbackStatus: Int,
        val radioFrequency: String,
        val radioMode: Int,
        val radioStationName: String,
    )

    data class Status(
        val bound: Boolean,
        val sendCount: Int,
        val lastResult: String,
        val lastError: String,
    )

    companion object {
        private const val DIM_PACKAGE = "com.autolink.diminteraction"
        private const val DIM_SERVICE =
            "com.autolink.diminteraction.DIMMediaInteractioncService"
        private const val DESCRIPTOR =
            "com.autolink.adapterbinder.IDimMediaInteractioncService"
        private const val TRANSACTION_SEND_MESSAGE = 1
        private const val RESERVED = 0
        private const val LEGACY_ARTWORK_GATE = "atlas-online-artwork"
    }

    private val appContext = context.applicationContext ?: context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile
    private var binder: IBinder? = null

    @Volatile
    private var binding = false

    @Volatile
    private var pendingPayload: Payload? = null

    @Volatile
    private var transmissionEnabled = true

    private val sendCount = AtomicInteger(0)

    @Volatile
    private var lastResult = "not-bound"

    @Volatile
    private var lastError = ""

    private val deathRecipient = IBinder.DeathRecipient {
        binder = null
        binding = false
        lastResult = "binder-died"
        ensureBound()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            binding = false
            lastResult = "connected"
            lastError = ""
            runCatching { service.linkToDeath(deathRecipient, 0) }
                .onFailure { Timber.w(it, "Unable to monitor direct DIM Binder death") }

            pendingPayload?.takeIf { transmissionEnabled }?.let { payload ->
                scope.launch { sendNow(service, payload) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
            binding = false
            lastResult = "disconnected"
        }

        override fun onBindingDied(name: ComponentName) {
            binder = null
            binding = false
            lastResult = "binding-died"
            ensureBound()
        }

        override fun onNullBinding(name: ComponentName) {
            binder = null
            binding = false
            lastResult = "null-binding"
            lastError = "DIM service returned a null Binder"
        }
    }

    init {
        ensureBound()
    }

    fun status(): Status = Status(
        bound = binder?.isBinderAlive == true,
        sendCount = sendCount.get(),
        lastResult = lastResult,
        lastError = lastError,
    )

    @Synchronized
    fun clearPending() { pendingPayload = null }

    @Synchronized
    fun setTransmissionEnabled(enabled: Boolean) {
        transmissionEnabled = enabled
        if (!enabled) {
            pendingPayload = null
            lastResult = "disabled"
        } else {
            ensureBound()
        }
    }

    /** Returns a short diagnostic result; a disconnected Binder queues the latest payload. */
    @Synchronized
    fun sendOrQueue(payload: Payload): String {
        if (!transmissionEnabled) return "disabled"
        pendingPayload = payload
        val currentBinder = binder
        if (currentBinder == null || !currentBinder.isBinderAlive) {
            ensureBound()
            lastResult = "queued"
            return lastResult
        }
        return sendNow(currentBinder, payload)
    }

    private fun ensureBound() {
        synchronized(lock) {
            if (!transmissionEnabled) return
            if (binder?.isBinderAlive == true || binding) return
            binding = true
            val intent = Intent().setComponent(ComponentName(DIM_PACKAGE, DIM_SERVICE))
            runCatching {
                appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.onSuccess { accepted ->
                if (!accepted) {
                    binding = false
                    lastResult = "bind-rejected"
                    lastError = "bindService returned false"
                } else {
                    lastResult = "binding"
                    lastError = ""
                }
            }.onFailure { error ->
                binding = false
                lastResult = "bind-failed"
                lastError = error.diagnosticMessage()
                Timber.e(error, "Unable to bind direct DIM media service")
            }
        }
    }

    @Synchronized
    private fun sendNow(target: IBinder, payload: Payload): String {
        if (!transmissionEnabled || pendingPayload != payload) return "cancelled"
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(1) // non-null PlayMediaInfo
            writePlayMediaInfo(data, payload)
            data.writeInt(RESERVED)

            check(target.transact(TRANSACTION_SEND_MESSAGE, data, reply, 0)) {
                "DIM Binder rejected transaction $TRANSACTION_SEND_MESSAGE"
            }
            reply.readException()
            // Retain the current packet for replay after Binder reconnection. Source
            // changes and disabled transmission explicitly clear it.
            val currentSendCount = sendCount.incrementAndGet()
            lastError = ""
            lastResult = "sent-$currentSendCount"
            lastResult
        } catch (error: Throwable) {
            if (!target.isBinderAlive) {
                binder = null
                binding = false
                ensureBound()
            }
            lastResult = "send-failed"
            lastError = error.diagnosticMessage()
            Timber.e(error, "Direct DIM media transaction failed")
            "$lastResult:$lastError"
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Exact field order of com.autolink.adapterbinder.PlayMediaInfo on this firmware. */
    private fun writePlayMediaInfo(parcel: Parcel, payload: Payload) {
        parcel.writeInt(payload.sourceType)
        parcel.writeString(payload.uuid)
        parcel.writeInt(0) // mute_state
        parcel.writeString(payload.title)
        parcel.writeString(payload.album)
        parcel.writeString(payload.artist)
        // DIM V9.03 checks only nullness here before converting artworkUrl. Keep this
        // deliberately simple to rule out hidden interpretation of a URI-shaped string.
        parcel.writeString(if (payload.artworkUri != null) LEGACY_ARTWORK_GATE else null)
        parcel.writeParcelable(payload.artworkUri, 0)
        parcel.writeString("") // next_artwork
        parcel.writeString("") // lyric_sentence
        parcel.writeString("") // lyric_content
        parcel.writeString("") // lyric_url
        parcel.writeLong(payload.duration)
        parcel.writeLong(0L) // current_progress
        parcel.writeInt(0) // favorite_state
        parcel.writeInt(0) // loop_mode
        parcel.writeString("") // media_path
        parcel.writeInt(payload.playbackStatus)
        parcel.writeInt(0) // playing_position
        parcel.writeString(payload.radioFrequency)
        parcel.writeInt(payload.radioMode)
        parcel.writeString(payload.radioStationName)
    }

    private fun Throwable.diagnosticMessage(): String {
        val cause = generateSequence(this) { it.cause }.last()
        return "${cause.javaClass.simpleName}: ${cause.message.orEmpty()}"
    }
}
