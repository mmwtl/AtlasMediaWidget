package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps automatic playback away from phone calls.
 *
 * A call handed over to the head unit takes audio focus, so every play retry of an automatic
 * action starts the player and the focus loss stops it again. A call is detected by either of two
 * signals, neither of which needs a permission on Android 11:
 * - the audio mode, which Telecom sets for its calls;
 * - the Telecom call state: on Android 11 [TelephonyManager.getCallState] reads it from Telecom,
 *   which also holds the Bluetooth (HFP client) calls of the head unit, whose logs show them in
 *   Telecom. Android 12 and later require READ_PHONE_STATE for it, and the read then fails.
 *
 * The audio mode alone did not stop the play retries of a head unit started during a call
 * (device report); which of the signals the head unit provides is not yet confirmed.
 */
object CallGuard {
    const val CALL_POLL_MS = 250L

    fun isCallAudioMode(mode: Int): Boolean = when (mode) {
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_CALL_SCREENING -> true
        else -> false
    }

    fun isCallState(state: Int): Boolean =
        state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK

    fun isCallActive(context: Context): Boolean = callSignal(context) != null

    /** Names the signal that reports a call, or null without one; for logs and the guard. */
    fun callSignal(context: Context): String? {
        val mode = runCatching { context.getSystemService(AudioManager::class.java)?.mode }.getOrNull()
        if (mode != null && isCallAudioMode(mode)) return "audio mode $mode"
        @Suppress("DEPRECATION")
        val state = runCatching { context.getSystemService(TelephonyManager::class.java)?.callState }
            .getOrNull()
        if (state != null && isCallState(state)) return "call state $state"
        return null
    }

    /**
     * Runs an automatic media action and cancels it as soon as a call is active.
     *
     * @return the result of [block], or null when a call prevented or interrupted it.
     */
    suspend fun <T : Any> runUnlessCallActive(
        isCallActive: () -> Boolean,
        pollMs: Long = CALL_POLL_MS,
        block: suspend () -> T,
    ): T? {
        if (isCallActive()) return null
        val interrupted = AtomicBoolean(false)
        return coroutineScope {
            val action = async { block() }
            val watcher = launch {
                while (true) {
                    delay(pollMs.coerceAtLeast(1L))
                    if (isCallActive()) {
                        interrupted.set(true)
                        action.cancel()
                        return@launch
                    }
                }
            }
            try {
                action.await()
            } catch (cancelled: CancellationException) {
                if (!interrupted.get()) throw cancelled
                currentCoroutineContext().ensureActive()
                null
            } finally {
                watcher.cancel()
            }
        }
    }
}
