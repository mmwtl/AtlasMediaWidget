package com.mmwtl.atlasmediaapi.media.cluster

import android.net.Uri
import com.ecarx.xui.adaptapi.diminteraction.IMediaInteraction
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClusterRadioPlaybackInfoTest {

    @Test
    fun `online playback info mirrors artwork into nextArtwork`() {
        val artwork = Uri.parse("file:///storage/emulated/0/Documents/.atlas_dim/online_cover_1.jpg")
        val playInfo = ClusterRadioPlaybackInfo(
            "atlas-online:player:1",
            IMediaInteraction.SOURCE_TYPE_ONLINE,
            "",
            "",
            "Title",
            "Artist",
            "Album",
            1_000L,
            IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PLAYING,
            0,
            artwork,
            artwork,
        )

        assertSame(artwork, playInfo.artwork)
        assertSame(artwork, playInfo.nextArtwork)
        assertNull(playInfo.previousArtwork)
    }
}
