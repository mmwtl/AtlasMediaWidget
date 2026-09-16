package com.mmwtl.atlasmediaapi.media.bridge

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MediaStateHubDisconnectTest {
    @Test
    fun `radio state received before source callback is restored after confirmation`() {
        val fixture = fixture()
        fixture.hub.onBackendConnected(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            MediaCenterConstant.AppSource.UNKNOWN,
            emptyMap(),
        )

        fixture.hub.onOneOsRadioState(frequency = null, playing = true)
        fixture.hub.onSourceChanged(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO,
            MediaCenterConstant.AppSource.UNKNOWN,
        )

        val snapshot = fixture.repository.snapshot()
        assertEquals(BridgeAudioSource.RADIO.name, snapshot.audioSource)
        assertEquals(PlaybackState.STATE_PLAYING, snapshot.playbackState)
    }

    @Test
    fun `radio status without frequency preserves cached station`() {
        val fixture = fixture()
        fixture.hub.onBackendConnected(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            MediaCenterConstant.AppSource.UNKNOWN,
            emptyMap(),
        )
        fixture.hub.onOneOsRadioState(radioFrequency(), playing = true)
        fixture.hub.onOneOsRadioState(frequency = null, playing = false)

        val field = MediaStateHub::class.java.getDeclaredField("lastRadioFrequency")
        field.isAccessible = true
        assertEquals(100_100, (field.get(fixture.hub) as Frequency).frequency)
    }

    @Test
    fun `OneOS disconnect preserves selected meaningful Android online session`() {
        val fixture = fixture()
        val session = createAndroidOnlineSession(fixture)
        fixture.hub.onMediaController(session.controller)
        fixture.hub.onBackendDisconnected("OneOS unavailable")

        val snapshot = fixture.repository.snapshot()
        assertFalse(snapshot.backendConnected)
        assertEquals(BridgeAudioSource.ONLINE.name, snapshot.audioSource)
        assertEquals("VLC track", snapshot.title)
        assertEquals(session.controller.packageName, snapshot.ownerPackage)
        session.session.release()
    }

    @Test
    fun `session removal clears preserved online playback`() {
        val fixture = fixture()
        val session = createAndroidOnlineSession(fixture)
        fixture.hub.onMediaController(session.controller)
        fixture.hub.onBackendDisconnected("OneOS unavailable")
        fixture.hub.onMediaController(null)

        val snapshot = fixture.repository.snapshot()
        assertEquals("", snapshot.ownerPackage)
        assertEquals("", snapshot.title)
        assertEquals(PlaybackState.STATE_NONE, snapshot.playbackState)
        session.session.release()
    }

    @Test
    fun `meaningful Android session replaces stale disconnected native source`() {
        val fixture = fixture()
        fixture.repository.update {
            it.copy(
                backendConnected = false,
                audioSource = BridgeAudioSource.RADIO.name,
                title = "stale radio",
            )
        }
        val session = createAndroidOnlineSession(fixture)

        fixture.hub.onMediaController(session.controller)

        val snapshot = fixture.repository.snapshot()
        assertEquals(BridgeAudioSource.ONLINE.name, snapshot.audioSource)
        assertEquals("VLC track", snapshot.title)
        session.session.release()
    }

    @Test
    fun `OneOS online metadata is cleared when no Android session owns it`() {
        val fixture = fixture()
        fixture.repository.update {
            it.copy(
                audioSource = BridgeAudioSource.ONLINE.name,
                ownerPackage = "",
                ownerApp = "ONLINE",
                title = "Native online track",
                playbackState = PlaybackState.STATE_PLAYING,
            )
        }

        fixture.hub.onBackendDisconnected("OneOS unavailable")

        val snapshot = fixture.repository.snapshot()
        assertEquals("", snapshot.title)
        assertEquals("", snapshot.ownerPackage)
        assertEquals(PlaybackState.STATE_NONE, snapshot.playbackState)
        assertTrue(snapshot.sources.none { it.selected })
    }

    private data class Fixture(
        val context: android.content.Context,
        val repository: MediaStateRepository,
        val hub: MediaStateHub,
    )

    private fun fixture(): Fixture {
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
        return Fixture(context, repository, hub)
    }

    private fun radioFrequency(): Frequency = Frequency(
        100_100,
        1,
        "",
        "Radio 7",
        "",
        0,
        80,
        "",
    )

    private data class SessionFixture(
        val session: MediaSession,
        val controller: MediaController,
    )

    private fun createAndroidOnlineSession(fixture: Fixture): SessionFixture {
        val session = MediaSession(fixture.context, "media-state-hub-test")
        session.isActive = true
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "VLC track")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "VLC artist")
            .build()
        session.setMetadata(metadata)
        val playbackState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 120L, 1f)
            .build()
        session.setPlaybackState(playbackState)
        val controller = MediaController(fixture.context, session.sessionToken)
        shadowOf(controller).setPackageName("com.example.vlc")
        shadowOf(controller).setMetadata(metadata)
        shadowOf(controller).setPlaybackState(playbackState)
        return SessionFixture(session, controller)
    }
}
