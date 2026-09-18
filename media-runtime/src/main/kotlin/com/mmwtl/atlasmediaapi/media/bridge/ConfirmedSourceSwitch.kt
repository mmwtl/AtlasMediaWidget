package com.mmwtl.atlasmediaapi.media.bridge

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps source selection and autoplay as two separately confirmed operations.
 * OneOS source requests are asynchronous and do not report delivery status.
 */
internal class ConfirmedSourceSwitch(
    private val currentSource: () -> BridgeAudioSource?,
    private val sourceWaitTimeoutMs: Long,
    private val sourcePollDelaysMs: List<Long>,
    private val autoplayConfirmDelaysMs: List<Long>,
) {
    suspend fun requestAndConfirm(
        target: BridgeAudioSource,
        requestTarget: () -> Unit,
    ): Boolean {
        requestTarget()
        return awaitTarget(target)
    }

    suspend fun awaitTarget(target: BridgeAudioSource): Boolean =
        withTimeoutOrNull(sourceWaitTimeoutMs.coerceAtLeast(1L)) {
            var poll = 0
            while (true) {
                if (currentSource() == target) return@withTimeoutOrNull true
                val delayMs = sourcePollDelaysMs
                    .getOrElse(poll) { sourcePollDelaysMs.lastOrNull() ?: 1_000L }
                    .coerceAtLeast(1L)
                delay(delayMs)
                poll++
            }
            false
        } == true

    suspend fun playAndConfirm(
        target: BridgeAudioSource,
        sendPlay: suspend () -> Boolean,
        isPlaying: () -> Boolean,
    ): Boolean {
        autoplayConfirmDelaysMs.forEach { confirmDelayMs ->
            if (currentSource() != target) return false
            try {
                sendPlay()
            } catch (_: Throwable) {
                // A failed command is retried only while the target source remains selected.
            }
            if (isPlaying()) return true
            delay(confirmDelayMs.coerceAtLeast(1L))
            if (currentSource() != target) return false
            if (isPlaying()) return true
        }
        return false
    }

    suspend fun playAndConfirmWithFallback(
        target: BridgeAudioSource,
        sendPlay: suspend () -> Boolean,
        fallbackSendPlay: suspend () -> Boolean,
        isPlaying: () -> Boolean,
    ): Boolean = playAndConfirm(target, sendPlay, isPlaying) ||
        playAndConfirm(target, fallbackSendPlay, isPlaying)
}
