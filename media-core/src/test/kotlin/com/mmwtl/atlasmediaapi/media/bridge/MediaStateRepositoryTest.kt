package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStateRepositoryTest {
    private class FakeClock(
        var wall: Long = 1_000L,
        var elapsed: Long = 100L,
    ) : MediaBridgeClock {
        override fun currentTimeMillis(): Long = wall
        override fun elapsedRealtime(): Long = elapsed
    }

    @Test
    fun `published mutation increments generation and keeps one atomic snapshot`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)

        clock.wall = 2_000L
        val result = repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.BT.name,
                sources = it.sources.map { source ->
                    source.copy(selected = source.id == BridgeAudioSource.BT.name)
                },
            )
        }

        assertEquals(1L, result.generation)
        assertEquals(2_000L, result.timestamp)
        assertTrue(result.backendConnected)
        assertEquals(BridgeAudioSource.BT.name, result.audioSource)
        assertEquals(
            listOf(BridgeAudioSource.BT.name),
            result.sources.filter(MediaSourceSnapshot::selected).map(MediaSourceSnapshot::id),
        )
        assertSame(result, repository.snapshot())
        assertSame(result, repository.snapshots.value)
    }

    @Test
    fun `identical mutation does not create a generation`() {
        val repository = MediaStateRepository(FakeClock())
        val before = repository.snapshot()

        val after = repository.update { it.copy() }

        assertSame(before, after)
        assertEquals(0L, after.generation)
    }

    @Test
    fun `position tick refreshes query baseline without publishing each second`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)
        val lastPublished = repository.update { playing(it, position = 41_600L, at = 100L) }

        clock.elapsed = 555L
        repository.updateProgress(position = 42_000L, duration = 180_000L, speed = 1f)

        assertSame(lastPublished, repository.snapshots.value)
        assertEquals(1L, repository.snapshot().generation)
        assertEquals(42_000L, repository.snapshot().position)
        assertEquals(555L, repository.snapshot().updateElapsedRealtime)

        clock.wall = 3_000L
        val published = repository.update { it.copy(title = "Next track") }
        assertEquals(2L, published.generation)
        assertEquals(42_000L, published.position)
        assertFalse(published.title.isBlank())
    }

    @Test
    fun `position tick that contradicts the published position is published`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)
        repository.update { playing(it, position = 100_000L, at = 100L) }

        clock.elapsed = 1_100L
        repository.updateProgress(position = 1_000L, duration = 180_000L, speed = 1f)

        assertEquals(2L, repository.snapshots.value.generation)
        assertEquals(1_000L, repository.snapshots.value.position)
        assertEquals(1_100L, repository.snapshots.value.updateElapsedRealtime)
    }

    @Test
    fun `first known position and a new duration are published`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)
        repository.update { it.copy(title = "Track", duration = 180_000L) }

        repository.updateProgress(position = 5_000L, duration = 180_000L, speed = 0f)
        assertEquals(5_000L, repository.snapshots.value.position)

        repository.updateProgress(position = 5_000L, duration = 200_000L, speed = 0f)
        assertEquals(200_000L, repository.snapshots.value.duration)
    }

    private fun playing(snapshot: MediaSnapshot, position: Long, at: Long) = snapshot.copy(
        title = "Track",
        position = position,
        duration = 180_000L,
        speed = 1f,
        playbackState = android.media.session.PlaybackState.STATE_PLAYING,
        updateElapsedRealtime = at,
    )

    @Test
    fun `custom actions are dropped once another owner takes the snapshot`() {
        val repository = MediaStateRepository(FakeClock())
        val like = MediaCustomAction("LIKE", "Like", 0x7f010001, "com.player")

        val published = repository.update {
            it.copy(ownerPackage = "com.player", customActions = listOf(like))
        }
        assertEquals(listOf(like), published.customActions)

        val switched = repository.update { it.copy(ownerPackage = "com.geely.usb") }
        assertEquals(emptyList<MediaCustomAction>(), switched.customActions)
    }
}
