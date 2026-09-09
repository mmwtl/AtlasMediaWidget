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
        val lastPublished = repository.snapshots.value

        clock.elapsed = 555L
        repository.updateProgress(position = 42_000L, duration = 180_000L, speed = 1f)

        assertSame(lastPublished, repository.snapshots.value)
        assertEquals(0L, repository.snapshot().generation)
        assertEquals(42_000L, repository.snapshot().position)
        assertEquals(555L, repository.snapshot().updateElapsedRealtime)

        clock.wall = 3_000L
        val published = repository.update { it.copy(title = "Next track") }
        assertEquals(1L, published.generation)
        assertEquals(42_000L, published.position)
        assertFalse(published.title.isBlank())
    }
}
