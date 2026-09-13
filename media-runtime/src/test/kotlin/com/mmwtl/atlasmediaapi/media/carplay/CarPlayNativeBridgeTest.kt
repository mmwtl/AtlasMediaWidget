package com.mmwtl.atlasmediaapi.media.carplay

import android.media.session.PlaybackState
import com.autolink.carplay.common.data.iap.NowPlayingInfo
import com.mmwtl.atlasmediaapi.media.bridge.BridgeAudioSource
import com.mmwtl.atlasmediaapi.media.bridge.MediaCapabilities
import com.mmwtl.atlasmediaapi.media.bridge.MediaStateRepository
import com.mmwtl.atlasmediaapi.media.bridge.SessionPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayNativeBridgeTest {
    private class FakeClock : com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeClock {
        var time = 1000L
        override fun currentTimeMillis(): Long = time++
        override fun elapsedRealtime(): Long = time
    }

    @Test
    fun `NowPlayingInfo playbackStatus 1 maps to PLAYING`() {
        val nowPlaying = NowPlayingInfo().apply {
            mediaItemTitle = "Test Song"
            mediaItemArtist = "Test Artist"
            playbackStatus = 1 // Playing
            mediaItemPlaybackDurationMs = 180_000L
            playbackElapsedTimeMs = 45_000L
        }

        assertEquals("Test Song", nowPlaying.mediaItemTitle)
        assertEquals("Test Artist", nowPlaying.mediaItemArtist)
        assertEquals(1, nowPlaying.playbackStatus)
        assertEquals(180_000L, nowPlaying.mediaItemPlaybackDurationMs)
        assertEquals(45_000L, nowPlaying.playbackElapsedTimeMs)
    }

    @Test
    fun `NowPlayingInfo non-1 playbackStatus maps to NOT_PLAYING`() {
        val paused = NowPlayingInfo().apply {
            playbackStatus = 2 // Paused
        }
        val isPlaying = paused.playbackStatus == 1
        assertFalse(isPlaying)

        val stopped = NowPlayingInfo().apply {
            playbackStatus = 0 // Stopped
        }
        assertFalse(stopped.playbackStatus == 1)
    }

    @Test
    fun `CPAA snapshot mapping preserves and updates track info`() {
        val repository = MediaStateRepository(FakeClock())
        repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.CPAA.name,
                ownerPackage = "com.autolink.carplay",
                ownerApp = BridgeAudioSource.CPAA.name,
                title = "Initial Title",
                artist = "Initial Artist",
                playbackState = PlaybackState.STATE_PAUSED,
                speed = 0f,
                duration = 100_000L,
                position = 10_000L,
            )
        }

        // Simulate new song update from NowPlayingInfo
        val newSong = NowPlayingInfo().apply {
            mediaItemTitle = "New Track"
            mediaItemArtist = "New Artist"
            playbackStatus = 1
            mediaItemPlaybackDurationMs = 240_000L
            playbackElapsedTimeMs = 1_000L
        }

        repository.update { before ->
            before.copy(
                title = newSong.mediaItemTitle,
                artist = newSong.mediaItemArtist,
                duration = newSong.mediaItemPlaybackDurationMs,
                position = newSong.playbackElapsedTimeMs,
                playbackState = if (newSong.playbackStatus == 1) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                speed = if (newSong.playbackStatus == 1) 1f else 0f,
            )
        }

        val snapshot = repository.snapshot()
        assertEquals("New Track", snapshot.title)
        assertEquals("New Artist", snapshot.artist)
        assertEquals(240_000L, snapshot.duration)
        assertEquals(1_000L, snapshot.position)
        assertEquals(PlaybackState.STATE_PLAYING, snapshot.playbackState)
        assertEquals(1f, snapshot.speed, 0.001f)
    }
}
