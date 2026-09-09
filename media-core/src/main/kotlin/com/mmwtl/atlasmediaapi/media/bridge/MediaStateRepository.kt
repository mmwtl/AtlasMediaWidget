package com.mmwtl.atlasmediaapi.media.bridge

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

interface MediaBridgeClock {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
}

object AndroidMediaBridgeClock : MediaBridgeClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

/**
 * Single-writer-style atomic media state. Each published change is one complete immutable snapshot.
 * Position-only OneOS ticks update the query baseline without emitting subscriber events.
 */
class MediaStateRepository(
    private val clock: MediaBridgeClock = AndroidMediaBridgeClock,
) {
    private val lock = Any()
    private val initial = MediaSnapshot(
        timestamp = clock.currentTimeMillis(),
        updateElapsedRealtime = clock.elapsedRealtime(),
    )
    private val current = AtomicReference(initial)
    private val mutableSnapshots = MutableStateFlow(initial)

    val snapshots: StateFlow<MediaSnapshot> = mutableSnapshots.asStateFlow()

    fun snapshot(): MediaSnapshot = current.get()

    fun update(transform: (MediaSnapshot) -> MediaSnapshot): MediaSnapshot = synchronized(lock) {
        val before = current.get()
        val candidate = transform(before)
        if (candidate.contentEquals(before)) return before

        val next = candidate.copy(
            protocolVersion = MediaBridgeContract.PROTOCOL_VERSION,
            generation = before.generation + 1L,
            timestamp = clock.currentTimeMillis(),
        )
        current.set(next)
        mutableSnapshots.value = next
        next
    }

    fun updateProgress(position: Long, duration: Long, speed: Float) = synchronized(lock) {
        val before = current.get()
        current.set(
            before.copy(
                position = position,
                duration = duration.takeIf { it >= 0L } ?: before.duration,
                speed = speed,
                updateElapsedRealtime = clock.elapsedRealtime(),
            )
        )
    }

    private fun MediaSnapshot.contentEquals(other: MediaSnapshot): Boolean =
        copy(generation = 0L, timestamp = 0L) == other.copy(generation = 0L, timestamp = 0L)
}
