package com.mmwtl.atlasmediaapi.media.bridge

import android.os.Bundle
import android.media.session.PlaybackState
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DemoMediaBackendTest {
    private val repository = MediaStateRepository()
    private val backend = DemoMediaBackend(
        context = RuntimeEnvironment.getApplication(),
        repository = repository,
        artworkRepository = ArtworkNormalizer { _, callback -> callback(NormalizedArtwork("demo", "content://demo/cover.jpg")) },
        resourceArtworkLoader = { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) },
    )

    @Test
    fun `start publishes a connected radio snapshot with artwork and stations`() {
        backend.start()

        val snapshot = repository.snapshot()
        assertTrue(snapshot.backendConnected)
        assertEquals(BridgeAudioSource.RADIO.name, snapshot.audioSource)
        assertEquals("Atlas FM", snapshot.title)
        assertEquals("101.7 MHz", snapshot.artist)
        assertTrue(snapshot.artworkUri.isNotBlank())
        assertEquals(4, backend.radioStationLists().saved.size)
        assertEquals(2, backend.radioStationLists().favorites.size)
    }

    @Test
    fun `source and track commands produce normal widget snapshots`() {
        backend.start()
        assertEquals(MediaBridgeContract.Status.OK, backend.execute(command(MediaCommand.SET_SOURCE) {
            putString(MediaBridgeContract.Key.COMMAND_SOURCE, BridgeAudioSource.ONLINE.name)
            putBoolean(MediaBridgeContract.Key.COMMAND_AUTOPLAY, true)
        }).status)
        val online = repository.snapshot()
        assertEquals(BridgeAudioSource.ONLINE.name, online.audioSource)
        assertEquals(PlaybackState.STATE_PLAYING, online.playbackState)
        assertEquals("Rain on the Windshield, Lights on the Water", online.title)

        backend.execute(command(MediaCommand.NEXT))
        assertEquals("The Last Train Never Leaves on Time", repository.snapshot().title)

        backend.execute(command(MediaCommand.SEEK_TO) {
            putLong(MediaBridgeContract.Key.COMMAND_POSITION, 90_000L)
        })
        assertEquals(90_000L, repository.snapshot().position)
    }

    @Test
    fun `radio tune accepts a client station and rejects seek`() {
        backend.start()
        val result = backend.execute(command(MediaCommand.TUNE_RADIO) {
            putInt(MediaBridgeContract.Key.RADIO_FREQUENCY_KHZ, 104_300)
            putInt(MediaBridgeContract.Key.RADIO_BAND, 1)
            putString(MediaBridgeContract.Key.RADIO_SERVICE_NAME, "City Wave")
        })

        assertEquals(MediaBridgeContract.Status.OK, result.status)
        assertEquals("City Wave", repository.snapshot().title)
        assertEquals(
            MediaBridgeContract.Status.NOT_SUPPORTED,
            backend.execute(command(MediaCommand.SEEK_TO) {
                putLong(MediaBridgeContract.Key.COMMAND_POSITION, 10L)
            }).status,
        )
    }

    private fun command(command: MediaCommand, block: Bundle.() -> Unit = {}): MediaCommandRequest {
        val bundle = Bundle().apply {
            putString(MediaBridgeContract.Key.REQUEST_ID, "demo-test-${command.name}")
            putString(MediaBridgeContract.Key.COMMAND, command.name)
            block()
        }
        return requireNotNull(bundle.toMediaCommandRequest())
    }
}
