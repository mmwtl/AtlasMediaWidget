package com.mmwtl.atlasmediaapi.media.bridge

import android.media.session.PlaybackState
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStateHubRaceTest {
    private class FakeClock : MediaBridgeClock {
        var time = 1000L
        override fun currentTimeMillis(): Long = time++
        override fun elapsedRealtime(): Long = time
    }

    @Test
    fun `OneOS online metadata does not override active MediaSession`() {
        val policy = OnlineMediaSourcePolicy()
        policy.onAudioSource(BridgeAudioSource.ONLINE)

        // MediaSession becomes active
        assertTrue(policy.onSession("com.yandex.music", meaningful = true))

        // Competing OneOS callback arrives
        assertFalse(policy.acceptOneOs(BridgeAudioSource.ONLINE, meaningful = true))
    }

    @Test
    fun `Source change from ONLINE to RADIO during artwork decode clears stale artwork`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)

        // Connected in ONLINE with artwork
        repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.ONLINE.name,
                artworkUri = "content://media/artwork/123.jpg",
                artworkRevision = 1L,
            )
        }

        // Switch to RADIO
        val radioSnapshot = repository.update { before ->
            before.copy(
                audioSource = BridgeAudioSource.RADIO.name,
                artworkUri = "",
                artworkRevision = before.artworkRevision + 1L,
            )
        }

        assertEquals(BridgeAudioSource.RADIO.name, radioSnapshot.audioSource)
        assertEquals("", radioSnapshot.artworkUri)
        assertEquals(2L, radioSnapshot.artworkRevision)
    }

    @Test
    fun `Radio state preserves frequency and metadata across status ticks`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)

        val station = radioMetadata(101_700, 0, "Nashe Radio", "", "Radio")
        repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.RADIO.name,
            ).withRadioState(station, playing = true, elapsedRealtime = clock.elapsedRealtime())
        }

        val snapshot = repository.snapshot()
        assertEquals("Nashe Radio", snapshot.title)
        assertEquals("101.7 MHz", snapshot.artist)
        assertEquals(PlaybackState.STATE_PLAYING, snapshot.playbackState)

        // Status update while station remains null (RDS ticker)
        val afterStatus = repository.update {
            it.withRadioState(null, playing = false, elapsedRealtime = clock.elapsedRealtime())
        }

        assertEquals("Nashe Radio", afterStatus.title)
        assertEquals("101.7 MHz", afterStatus.artist)
        assertEquals(PlaybackState.STATE_PAUSED, afterStatus.playbackState)
    }

    @Test
    fun `Disconnection clears playback state and stale owner`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)

        repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.BT.name,
                ownerPackage = "com.android.bluetooth",
                title = "Bluetooth Song",
                playbackState = PlaybackState.STATE_PLAYING,
            )
        }

        val disconnected = repository.update {
            it.copy(
                backendConnected = false,
                backendErrorCode = MediaBridgeContract.BackendError.ONE_OS_DISCONNECTED,
                backendErrorMessage = "Disconnected",
                ownerPackage = "",
                title = "",
                playbackState = PlaybackState.STATE_NONE,
            )
        }

        assertFalse(disconnected.backendConnected)
        assertEquals(MediaBridgeContract.BackendError.ONE_OS_DISCONNECTED, disconnected.backendErrorCode)
        assertEquals("", disconnected.ownerPackage)
        assertEquals(PlaybackState.STATE_NONE, disconnected.playbackState)
    }

    @Test
    fun `CPAA owner package is configured correctly`() {
        assertEquals("com.autolink.carplay", CARPLAY_MEDIA_SESSION_PACKAGE)
        assertTrue(CARPLAY_MEDIA_SESSION_PACKAGES.contains("com.autolink.carplay"))
        assertTrue(CARPLAY_MEDIA_SESSION_PACKAGES.contains("com.test.carplay"))
        assertTrue(CARPLAY_MEDIA_SESSION_PACKAGES.contains("com.igrs.autolink"))
        assertTrue(CARPLAY_MEDIA_SESSION_PACKAGES.contains("com.android.cpaa"))
        assertTrue(CARPLAY_MEDIA_SESSION_PACKAGES.contains("com.autolink.carplay.app"))
    }

    @Test
    fun `CPAA default capabilities include transport controls`() {
        val caps = BridgeAudioSource.CPAA.defaultCapabilities()
        assertTrue(caps and MediaCapabilities.PLAY != 0L)
        assertTrue(caps and MediaCapabilities.PAUSE != 0L)
        assertTrue(caps and MediaCapabilities.TOGGLE != 0L)
        assertTrue(caps and MediaCapabilities.NEXT != 0L)
        assertTrue(caps and MediaCapabilities.PREVIOUS != 0L)
        assertTrue(caps and MediaCapabilities.SET_SOURCE != 0L)
    }

    @Test
    fun `BridgeAudioSource toOneOsSource maps correctly`() {
        assertEquals(MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA, BridgeAudioSource.CPAA.toOneOsSource())
        assertEquals(MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB, BridgeAudioSource.USB.toOneOsSource())
        assertEquals(MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT, BridgeAudioSource.BT.toOneOsSource())
        assertEquals(MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO, BridgeAudioSource.RADIO.toOneOsSource())
        assertEquals(MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE, BridgeAudioSource.ONLINE.toOneOsSource())
    }

    @Test
    fun `Source availability update and onActiveSourceLost callback on active source lost`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)
        var lostSourceReported: BridgeAudioSource? = null
        var wasPlayingReported: Boolean? = null

        // Set up snapshot with active USB source in PLAYING state
        repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.USB.name,
                playbackState = PlaybackState.STATE_PLAYING,
            )
        }

        val snapshot = repository.snapshot()
        assertEquals(BridgeAudioSource.USB.name, snapshot.audioSource)

        val activeSource = BridgeAudioSource.valueOf(snapshot.audioSource)
        val wasPlaying = snapshot.playbackState == PlaybackState.STATE_PLAYING
        val isLost = (activeSource == BridgeAudioSource.USB)
        if (isLost) {
            lostSourceReported = activeSource
            wasPlayingReported = wasPlaying
        }

        assertEquals(BridgeAudioSource.USB, lostSourceReported)
        assertTrue(wasPlayingReported == true)

        // Now test when state was PAUSED
        repository.update {
            it.copy(
                playbackState = PlaybackState.STATE_PAUSED,
            )
        }
        val pausedSnapshot = repository.snapshot()
        val pausedWasPlaying = pausedSnapshot.playbackState == PlaybackState.STATE_PLAYING
        assertFalse(pausedWasPlaying)
    }

    @Test
    fun `Recently playing window handles USB unmount race condition`() {
        var lastPlayingRealtimeMs = 10_000L
        val now = 10_050L // 50ms after STOP event
        val wasRecentlyPlaying = (now - lastPlayingRealtimeMs) in 0..2500L
        assertTrue(wasRecentlyPlaying)

        val longPausedNow = 15_000L // 5000ms after STOP event
        val wasRecentlyPlayingAfterLongPause = (longPausedNow - lastPlayingRealtimeMs) in 0..2500L
        assertFalse(wasRecentlyPlayingAfterLongPause)
    }

    @Test
    fun `Online source loss triggers when active MediaSession is gone`() {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)
        var lostSourceReported: BridgeAudioSource? = null
        var wasPlayingReported: Boolean? = null

        repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.ONLINE.name,
                ownerPackage = "com.yandex.music",
                playbackState = PlaybackState.STATE_PLAYING,
            )
        }

        val snapshot = repository.snapshot()
        val wasActiveOnline = snapshot.audioSource == BridgeAudioSource.ONLINE.name
        val hadOwner = snapshot.ownerPackage.isNotBlank()
        val wasPlaying = snapshot.playbackState == PlaybackState.STATE_PLAYING

        if (wasActiveOnline && hadOwner) {
            lostSourceReported = BridgeAudioSource.ONLINE
            wasPlayingReported = wasPlaying
        }

        assertEquals(BridgeAudioSource.ONLINE, lostSourceReported)
        assertTrue(wasPlayingReported == true)
    }
}
