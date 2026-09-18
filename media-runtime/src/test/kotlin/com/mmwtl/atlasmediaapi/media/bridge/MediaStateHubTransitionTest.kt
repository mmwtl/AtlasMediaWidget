package com.mmwtl.atlasmediaapi.media.bridge

import android.media.session.PlaybackState
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
    fun `stale callback is accepted only when it matches actual OneOS source`() {
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
        assertTrue(
            isCurrentSourceCallback(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN,
            ),
        )
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
