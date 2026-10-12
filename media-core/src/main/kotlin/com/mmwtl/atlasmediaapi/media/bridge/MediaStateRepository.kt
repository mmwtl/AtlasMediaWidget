package com.mmwtl.atlasmediaapi.media.bridge

import android.media.session.PlaybackState
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
 * Position-only OneOS ticks update the query baseline without emitting subscriber events unless
 * they contradict the published position (seek, new track, missed pause).
 */
class MediaStateRepository(
    private val clock: MediaBridgeClock = AndroidMediaBridgeClock,
) {
    companion object {
        /** Tick jitter subscribers absorb by extrapolating the published position. */
        const val PROGRESS_JUMP_MS = 2_000L
    }

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
        val candidate = transform(before).withOwnedCustomActions()
        if (candidate.contentEquals(before)) return before
        publish(before, candidate)
    }

    fun updateProgress(position: Long, duration: Long, speed: Float) = synchronized(lock) {
        val before = current.get()
        val now = clock.elapsedRealtime()
        val next = before.copy(
            position = position,
            duration = duration.takeIf { it >= 0L } ?: before.duration,
            speed = speed,
            updateElapsedRealtime = now,
        )
        if (contradictsPublished(next, now)) publish(before, next) else current.set(next)
    }

    private fun publish(before: MediaSnapshot, candidate: MediaSnapshot): MediaSnapshot {
        val next = candidate.copy(
            protocolVersion = MediaBridgeContract.PROTOCOL_VERSION,
            generation = before.generation + 1L,
            timestamp = clock.currentTimeMillis(),
        )
        current.set(next)
        mutableSnapshots.value = next
        return next
    }

    private fun contradictsPublished(next: MediaSnapshot, now: Long): Boolean {
        if (next.position < 0L) return false
        val published = mutableSnapshots.value
        if (published.position < 0L || published.duration != next.duration) return true
        return kotlin.math.abs(next.position - published.positionAt(now)) > PROGRESS_JUMP_MS
    }

    /** Custom actions belong to one player; any owner change makes them stale. */
    private fun MediaSnapshot.withOwnedCustomActions(): MediaSnapshot =
        if (customActions.all { it.ownerPackage == ownerPackage }) this
        else copy(customActions = customActions.filter { it.ownerPackage == ownerPackage })

    private fun MediaSnapshot.contentEquals(other: MediaSnapshot): Boolean =
        copy(generation = 0L, timestamp = 0L) == other.copy(generation = 0L, timestamp = 0L)
}

/** Position reached by [elapsedRealtime] while playing; otherwise the stored position. */
fun MediaSnapshot.positionAt(elapsedRealtime: Long): Long {
    if (position < 0L || playbackState != PlaybackState.STATE_PLAYING || speed <= 0f ||
        updateElapsedRealtime <= 0L || elapsedRealtime <= updateElapsedRealtime
    ) return position
    val reached = position + ((elapsedRealtime - updateElapsedRealtime) * speed).toLong()
    return if (duration > 0L) reached.coerceAtMost(duration) else reached
}
