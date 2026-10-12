package com.mmwtl.atlasmediaapi.media.bridge

import android.media.session.PlaybackState
import android.os.SystemClock
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class MediaStateHubProgressTest {
    private val usb = MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB

    @Test
    fun `repeated OneOS play state keeps the progress baseline`() {
        val fixture = usbFixture()
        fixture.hub.onOneOsPlayState(usb, MediaCenterConstant.PlayState.MUSIC_STATE_PLAY)
        fixture.hub.onOneOsProgress(usb, position = 10_000L, duration = 200_000L)
        val before = fixture.repository.snapshot()

        ShadowSystemClock.advanceBy(Duration.ofMillis(800L))
        fixture.hub.onOneOsPlayState(usb, MediaCenterConstant.PlayState.MUSIC_STATE_PLAY)

        val after = fixture.repository.snapshot()
        assertEquals(before.generation, after.generation)
        assertEquals(10_000L, after.position)
        assertEquals(before.updateElapsedRealtime, after.updateElapsedRealtime)
    }

    @Test
    fun `pause keeps the position reached since the last progress tick`() {
        val fixture = usbFixture()
        fixture.hub.onOneOsPlayState(usb, MediaCenterConstant.PlayState.MUSIC_STATE_PLAY)
        fixture.hub.onOneOsProgress(usb, position = 10_000L, duration = 200_000L)

        ShadowSystemClock.advanceBy(Duration.ofMillis(700L))
        fixture.hub.onOneOsPlayState(usb, MediaCenterConstant.PlayState.MUSIC_STATE_PAUSE)

        val snapshot = fixture.repository.snapshot()
        assertEquals(PlaybackState.STATE_PAUSED, snapshot.playbackState)
        assertEquals(10_700L, snapshot.position)
        assertEquals(SystemClock.elapsedRealtime(), snapshot.updateElapsedRealtime)
        assertEquals(0f, snapshot.speed)
    }

    private data class Fixture(
        val repository: MediaStateRepository,
        val hub: MediaStateHub,
    )

    private fun usbFixture(): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = MediaStateHub(
            context = context,
            repository = repository,
            artworkRepository = ArtworkRepository(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
        )
        hub.onBackendConnected(usb, MediaCenterConstant.AppSource.UNKNOWN, emptyMap())
        return Fixture(repository, hub)
    }
}
