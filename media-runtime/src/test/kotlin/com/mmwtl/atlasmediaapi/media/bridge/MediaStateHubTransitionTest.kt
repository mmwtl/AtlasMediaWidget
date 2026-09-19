package com.mmwtl.atlasmediaapi.media.bridge

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaStateHubTransitionTest {
    @Test
    fun `intermediate source callbacks do not replace a pending target`() {
        val fixture = fixture()
        fixture.repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.ONLINE.name,
            )
        }

        fixture.hub.beginSourceTransition(BridgeAudioSource.USB)
        fixture.hub.onSourceChanged(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            MediaCenterConstant.AppSource.UNKNOWN,
        )
        assertEquals(BridgeAudioSource.ONLINE.name, fixture.repository.snapshot().audioSource)

        fixture.hub.onSourceChanged(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB,
            MediaCenterConstant.AppSource.UNKNOWN,
        )
        assertEquals(BridgeAudioSource.USB.name, fixture.repository.snapshot().audioSource)
    }

    @Test
    fun `active online session does not replace state during bluetooth transition`() {
        val fixture = fixture()
        fixture.repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.UNKNOWN.name,
            )
        }
        val session = MediaSession(RuntimeEnvironment.getApplication(), "online-during-bt-transition").apply {
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Online title")
                    .build(),
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build(),
            )
        }

        fixture.hub.beginSourceTransition(BridgeAudioSource.BT)
        fixture.hub.onMediaController(session.controller)

        assertEquals(BridgeAudioSource.UNKNOWN.name, fixture.repository.snapshot().audioSource)
        assertEquals("", fixture.repository.snapshot().title)

        fixture.hub.onSourceChanged(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            MediaCenterConstant.AppSource.UNKNOWN,
        )
        fixture.hub.onMediaController(session.controller)

        assertEquals(BridgeAudioSource.BT.name, fixture.repository.snapshot().audioSource)
        assertEquals("", fixture.repository.snapshot().title)
        session.release()
    }

    @Test
    fun `repeated source loss callback is emitted once until recovery`() {
        val events = mutableListOf<BridgeAudioSource>()
        val fixture = fixture { source, _ -> events += source }
        fixture.repository.update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.BT.name,
                playbackState = PlaybackState.STATE_PLAYING,
            )
        }

        fixture.hub.onSourceAvailability(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            connected = false,
            available = false,
        )
        fixture.hub.onSourceAvailability(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            connected = false,
            available = false,
        )
        assertEquals(listOf(BridgeAudioSource.BT), events)

        fixture.hub.onSourceAvailability(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            connected = true,
            available = true,
        )
        fixture.hub.onSourceAvailability(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            connected = false,
            available = false,
        )
        assertEquals(listOf(BridgeAudioSource.BT, BridgeAudioSource.BT), events)
    }

    @Test
    fun `source callback is accepted only when it matches a known OneOS source`() {
        assertFalse(
            isCurrentSourceCallback(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB,
            ),
        )
        assertTrue(
            isCurrentSourceCallback(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB,
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB,
            ),
        )
        assertFalse(
            isCurrentSourceCallback(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN,
            ),
        )
    }

    @Test
    fun `late online callback is rejected after unknown resolves to bluetooth`() = runBlocking {
        val actualSources = ArrayDeque(
            listOf(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN,
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN,
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            ),
        )
        val waits = mutableListOf<Long>()

        val actualSource = awaitKnownCurrentSource(
            currentSource = { actualSources.removeFirst() },
            pollDelaysMs = listOf(0L, 50L, 100L),
            wait = { waits += it },
        )

        assertEquals(MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT, actualSource)
        assertEquals(listOf(50L, 100L), waits)
    }

    @Test
    fun `manual bluetooth callback is published after actual source confirmation`() = runBlocking {
        val actualSources = ArrayDeque(
            listOf(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN,
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            ),
        )

        val actualSource = awaitKnownCurrentSource(
            currentSource = { actualSources.removeFirst() },
            pollDelaysMs = listOf(0L, 50L),
            wait = {},
        )

        assertEquals(MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT, actualSource)
    }

    private data class Fixture(
        val repository: MediaStateRepository,
        val hub: MediaStateHub,
    )

    private fun fixture(
        onActiveSourceLost: (BridgeAudioSource, Boolean) -> Unit = { _, _ -> },
    ): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = MediaStateHub(
            context = context,
            repository = repository,
            artworkRepository = ArtworkRepository(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
            onActiveSourceLost = onActiveSourceLost,
        )
        return Fixture(repository, hub)
    }
}
