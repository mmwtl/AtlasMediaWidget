package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.media.AudioManager
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
 * action starts the player and the focus loss stops it again. The call is read from the audio
 * mode, which needs no permission; the Bluetooth phone is assumed to set it like any Android
 * telephony or VoIP call (not yet confirmed on the head unit).
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

    fun isCallActive(context: Context): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        return runCatching { isCallAudioMode(audioManager.mode) }.getOrDefault(false)
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
