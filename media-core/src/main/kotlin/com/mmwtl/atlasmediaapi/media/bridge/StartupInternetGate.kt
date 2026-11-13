package com.mmwtl.atlasmediaapi.media.bridge

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Holds the startup media action until the head unit has internet. The check is a cheap
 * ConnectivityManager read, so a short poll is simpler than tracking network callbacks across
 * Bridge reconnects.
 */
object StartupInternetGate {
    const val POLL_INTERVAL_MS = 1_000L

    /** True once [hasInternet] holds; false when [timeoutMs] passes first. */
    suspend fun await(
        timeoutMs: Long,
        pollIntervalMs: Long = POLL_INTERVAL_MS,
        hasInternet: () -> Boolean,
    ): Boolean {
        if (hasInternet()) return true
        return withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
            while (true) {
                delay(pollIntervalMs.coerceAtLeast(1L))
                if (hasInternet()) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } == true
    }
}
