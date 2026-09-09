package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediaBridgeContractTest {
    @Test
    fun `only the advertised protocol version is accepted`() {
        assertEquals(
            MediaBridgeContract.Status.UNSUPPORTED_VERSION,
            MediaBridgeContract.negotiate(MediaBridgeContract.MIN_PROTOCOL_VERSION - 1),
        )
        assertEquals(
            MediaBridgeContract.Status.OK,
            MediaBridgeContract.negotiate(MediaBridgeContract.PROTOCOL_VERSION),
        )
        assertEquals(
            MediaBridgeContract.Status.UNSUPPORTED_VERSION,
            MediaBridgeContract.negotiate(MediaBridgeContract.MAX_PROTOCOL_VERSION + 1),
        )
    }

    @Test
    fun `contract constants match expected values`() {
        assertEquals("com.mmwtl.atlasmediaapi.media.BIND", MediaBridgeContract.SERVICE_ACTION)
        assertEquals("com.mmwtl.atlasmediaapi", MediaBridgeContract.SERVICE_PACKAGE)
        assertEquals("com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService", MediaBridgeContract.SERVICE_CLASS)
        assertEquals(1, MediaBridgeContract.PROTOCOL_VERSION)
        assertEquals(1, MediaBridgeContract.ClientMessage.REGISTER)
        assertEquals(2, MediaBridgeContract.ClientMessage.UNREGISTER)
        assertEquals(3, MediaBridgeContract.ClientMessage.GET_SNAPSHOT)
        assertEquals(4, MediaBridgeContract.ClientMessage.COMMAND)
        assertEquals(5, MediaBridgeContract.ClientMessage.GET_RADIO_STATIONS)
        assertEquals(100, MediaBridgeContract.ServerMessage.REGISTERED)
        assertEquals(101, MediaBridgeContract.ServerMessage.SNAPSHOT)
        assertEquals(102, MediaBridgeContract.ServerMessage.COMMAND_RESULT)
        assertEquals(103, MediaBridgeContract.ServerMessage.ERROR)
        assertEquals(104, MediaBridgeContract.ServerMessage.RADIO_STATIONS)
    }

    @Test
    fun `default media source capabilities include playback controls for CPAA, BT, USB, Radio`() {
        val cpaaCaps = BridgeAudioSource.CPAA.defaultCapabilities()
        val expected = MediaCapabilities.BASIC_PLAYBACK or
                MediaCapabilities.TRACK_NAVIGATION or
                MediaCapabilities.SET_SOURCE
        assertEquals(expected, cpaaCaps)
        assertEquals(expected, BridgeAudioSource.BT.defaultCapabilities())
        assertEquals(expected, BridgeAudioSource.USB.defaultCapabilities())
        assertEquals(
            expected or MediaCapabilities.TUNE_RADIO,
            BridgeAudioSource.RADIO.defaultCapabilities(),
        )
    }
}
