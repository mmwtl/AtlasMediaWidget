package com.mmwtl.atlasmediaapi.media.bridge

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RadioMetadataTest {
    private fun radioSnapshot() = MediaSnapshot(
        backendConnected = true,
        audioSource = BridgeAudioSource.RADIO.name,
    )

    @Test
    fun `uses station name and formatted FM frequency`() {
        val metadata = radioMetadata(101_700, 0, "Наше Радио", "", "Радио")

        assertEquals("Наше Радио", metadata.title)
        assertEquals("101.7 MHz", metadata.subtitle)
        assertEquals("radio:0:101700:Наше Радио", metadata.mediaId)
    }

    @Test
    fun `uses frequency as title when RDS name is absent`() {
        val metadata = radioMetadata(9_950, 0, "", "", "Радио")

        assertEquals("99.5 MHz", metadata.title)
        assertEquals("Радио", metadata.subtitle)
    }

    @Test
    fun `disabled catalog broadcast keeps OneOS radio metadata stock`() {
        assertEquals(
            "",
            resolveRadioServiceName(null, "Catalog Station", catalogBroadcastEnabled = false),
        )
        assertEquals(
            "RDS Station",
            resolveRadioServiceName(" RDS Station ", "Catalog Station", catalogBroadcastEnabled = false),
        )
    }

    @Test
    fun `enabled catalog broadcast fills missing OneOS station name`() {
        assertEquals(
            "Catalog Station",
            resolveRadioServiceName(null, " Catalog Station ", catalogBroadcastEnabled = true),
        )
    }

    @Test
    fun `keeps radio fallback when vendor frequency is unavailable`() {
        val metadata = radioMetadata(0, 0, "", "", "Радио")

        assertEquals("Радио", metadata.title)
        assertEquals("", metadata.subtitle)
    }

    @Test
    fun `keeps vendor FM precision without floating point rounding`() {
        assertEquals("87.55 MHz", formatRadioFrequency(8_755))
        assertEquals("101.7 MHz", formatRadioFrequency(101_700))
    }

    @Test
    fun `formats AM and DAB frequency ranges`() {
        assertEquals("1017 kHz", formatRadioFrequency(1_017))
        assertEquals("220.352 MHz", formatRadioFrequency(220_352))
    }

    @Test
    fun `formats OIRT and long wave frequencies`() {
        assertEquals("68 MHz", formatRadioFrequency(68_000))
        assertEquals("198 kHz", formatRadioFrequency(198))
    }

    @Test
    fun `rejects non-radio frequency values`() {
        assertEquals("", formatRadioFrequency(0))
        assertEquals("", formatRadioFrequency(-1))
        assertEquals("", formatRadioFrequency(12_345))
    }

    @Test
    fun `status callback preserves station metadata`() {
        val before = radioSnapshot().copy(
            mediaId = "radio:0:101700:Station",
            title = "Station",
            artist = "101.7 MHz",
            album = "old",
            playbackState = PlaybackState.STATE_PLAYING,
            speed = 1f,
            updateElapsedRealtime = 10L,
        )

        val after = before.withRadioState(station = null, playing = false, elapsedRealtime = 20L)

        assertEquals(before.mediaId, after.mediaId)
        assertEquals(before.title, after.title)
        assertEquals(before.artist, after.artist)
        assertEquals(before.album, after.album)
        assertEquals(PlaybackState.STATE_PAUSED, after.playbackState)
        assertEquals(0f, after.speed)
        assertEquals(20L, after.updateElapsedRealtime)
    }

    @Test
    fun `duplicate callback keeps playback baseline stable`() {
        val before = radioSnapshot().copy(
            playbackState = PlaybackState.STATE_PLAYING,
            speed = 1f,
            updateElapsedRealtime = 10L,
        )

        val after = before.withRadioState(station = null, playing = true, elapsedRealtime = 20L)

        assertEquals(10L, after.updateElapsedRealtime)
    }

    @Test
    fun `late callback cannot update disconnected or non-radio snapshot`() {
        val disconnected = radioSnapshot().copy(backendConnected = false)
        val otherSource = radioSnapshot().copy(audioSource = BridgeAudioSource.BT.name)

        assertSame(
            disconnected,
            disconnected.withRadioState(station = null, playing = true, elapsedRealtime = 20L),
        )
        assertSame(
            otherSource,
            otherSource.withRadioState(station = null, playing = true, elapsedRealtime = 20L),
        )
    }

    @Test
    fun `station callback clears stale artwork once`() {
        val before = radioSnapshot().copy(artworkUri = "content://old", artworkRevision = 4L)
        val station = radioMetadata(101_700, 0, "Station", "", "Radio")

        val after = before.withRadioState(station = station, playing = true, elapsedRealtime = 20L)

        assertEquals("", after.artworkUri)
        assertEquals(5L, after.artworkRevision)
    }
}
