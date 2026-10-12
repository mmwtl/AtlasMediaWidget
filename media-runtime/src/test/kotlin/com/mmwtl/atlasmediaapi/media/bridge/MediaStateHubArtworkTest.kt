package com.mmwtl.atlasmediaapi.media.bridge

import android.graphics.Bitmap
import android.graphics.Color
import com.geely.lib.oneosapi.mediacenter.bean.MediaData
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaStateHubArtworkTest {
    private val bt = MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT

    @Test
    fun `same artwork input is normalized again after a failed read`() {
        val fixture = fixture()
        fixture.hub.onOneOsMediaData(bt, track(cover = "content://covers/1"))
        assertEquals(1, fixture.requests.size)

        fixture.requests[0].complete("")
        fixture.hub.onOneOsMediaData(bt, track(cover = "content://covers/1"))
        assertEquals(2, fixture.requests.size)

        fixture.requests[1].complete("content://artwork/1.jpg")
        assertEquals("content://artwork/1.jpg", fixture.repository.snapshot().artworkUri)
        fixture.hub.onOneOsMediaData(bt, track(cover = "content://covers/1"))
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun `failed read of replaced input keeps the newer request deduplicated`() {
        val fixture = fixture()
        fixture.hub.onOneOsMediaData(bt, track(cover = "content://covers/old"))
        fixture.hub.onOneOsMediaData(bt, track(cover = "content://covers/new"))
        assertEquals(2, fixture.requests.size)

        fixture.requests[0].complete("")
        fixture.hub.onOneOsMediaData(bt, track(cover = "content://covers/new"))
        assertEquals(2, fixture.requests.size)

        fixture.requests[1].complete("content://artwork/new.jpg")
        assertEquals("content://artwork/new.jpg", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `same-size artwork with other pixels replaces the published artwork`() {
        val fixture = fixture()
        fixture.hub.onOneOsMediaData(bt, track(bitmap = solid(Color.BLACK)))
        fixture.requests[0].complete("content://artwork/logo.jpg")

        fixture.hub.onOneOsMediaData(bt, track(bitmap = solid(Color.BLACK)))
        assertEquals(1, fixture.requests.size)

        fixture.hub.onOneOsMediaData(bt, track(bitmap = solid(Color.RED)))
        assertEquals(2, fixture.requests.size)
        fixture.requests[1].complete("content://artwork/cover.jpg")
        assertEquals("content://artwork/cover.jpg", fixture.repository.snapshot().artworkUri)
    }

    private fun track(cover: String = "", bitmap: Bitmap? = null) = MediaData().apply {
        id = "1"
        name = "Song"
        artist = "Artist"
        albumCoverUri = cover
        albumCover = bitmap
    }

    private fun solid(color: Int): Bitmap =
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private class Request(val onResult: (NormalizedArtwork) -> Unit) {
        fun complete(uri: String) = onResult(NormalizedArtwork(token = uri, uri = uri))
    }

    private class Fixture(
        val repository: MediaStateRepository,
        val hub: MediaStateHub,
        val requests: List<Request>,
    )

    private fun fixture(): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val requests = mutableListOf<Request>()
        val hub = MediaStateHub(
            context = context,
            repository = repository,
            artworkRepository = ArtworkRepository(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
            trackArtworkNormalizer = { _, onResult -> requests += Request(onResult) },
        )
        hub.onBackendConnected(bt, MediaCenterConstant.AppSource.UNKNOWN, emptyMap())
        return Fixture(repository, hub, requests)
    }
}
