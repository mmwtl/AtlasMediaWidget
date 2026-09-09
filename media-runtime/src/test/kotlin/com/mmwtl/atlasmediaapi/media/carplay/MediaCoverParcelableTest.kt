package com.mmwtl.atlasmediaapi.media.carplay

import android.os.Parcel
import com.autolink.carplay.common.data.iap.MediaCover
import com.autolink.carplay.common.data.iap.NowPlayingInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MediaCoverParcelableTest {

    @Test
    fun mediaCover_gettersAndSetters_workCorrectly() {
        val cover = MediaCover().apply {
            mediaItemArtworkFilePath = "/cache/cover1.jpg"
            artworkSize = 1024
            artworkData = byteArrayOf(1, 2, 3, 4, 5)
        }

        assertEquals("/cache/cover1.jpg", cover.mediaItemArtworkFilePath)
        assertEquals(1024, cover.artworkSize)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), cover.artworkData)
        assertEquals(0, cover.describeContents())
        assertNotNull(cover.toString())
    }

    @Test
    fun nowPlayingInfo_gettersAndSetters_workCorrectly() {
        val info = NowPlayingInfo().apply {
            mediaItemTitle = "Test Song"
            mediaItemArtist = "Test Artist"
            mediaItemPlaybackDurationMs = 180000L
            playbackStatus = 1
            playbackElapsedTimeMs = 45000L
            playbackShuffleMode = 2
            playbackRepeatMode = 3
        }

        assertEquals("Test Song", info.mediaItemTitle)
        assertEquals("Test Artist", info.mediaItemArtist)
        assertEquals(180000L, info.mediaItemPlaybackDurationMs)
        assertEquals(1, info.playbackStatus)
        assertEquals(45000L, info.playbackElapsedTimeMs)
        assertEquals(2, info.playbackShuffleMode)
        assertEquals(3, info.playbackRepeatMode)
        assertEquals(0, info.describeContents())
        assertNotNull(info.toString())
    }

    @Test
    fun carPlayConstants_matchExpectedProtocolValues() {
        assertEquals(32, CarPlayNativeBridge.CARPLAY_KEY_PLAY)
        assertEquals(33, CarPlayNativeBridge.CARPLAY_KEY_PAUSE)
        assertEquals(34, CarPlayNativeBridge.CARPLAY_KEY_PLAYPAUSE)
        assertEquals(35, CarPlayNativeBridge.CARPLAY_KEY_NEXTTRACK)
        assertEquals(36, CarPlayNativeBridge.CARPLAY_KEY_PREVTRACK)

        assertEquals(1, CarPlayNativeBridge.IAP_HID_PLAYBACK_PLAY)
        assertEquals(2, CarPlayNativeBridge.IAP_HID_PLAYBACK_PAUSE)
        assertEquals(4, CarPlayNativeBridge.IAP_HID_PLAYBACK_NEXT)
        assertEquals(8, CarPlayNativeBridge.IAP_HID_PLAYBACK_PREV)
        assertEquals(64, CarPlayNativeBridge.IAP_HID_PLAYBACK_PLAY_PAUSE)
    }
}
