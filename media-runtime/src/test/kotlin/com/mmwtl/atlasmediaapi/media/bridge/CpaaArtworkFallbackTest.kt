package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpaaArtworkFallbackTest {
    private class FakeClock : MediaBridgeClock {
        private var time = 0L
        override fun currentTimeMillis(): Long = ++time
        override fun elapsedRealtime(): Long = time
    }

    private class DeferredNormalizer : ArtworkNormalizer {
        data class Request(
            val input: ArtworkInput,
            val callback: (NormalizedArtwork) -> Unit,
        )

        val requests = mutableListOf<Request>()

        override fun normalize(
            input: ArtworkInput,
            onResult: (NormalizedArtwork) -> Unit,
        ) {
            requests += Request(input, onResult)
        }

        fun complete(index: Int, uri: String) {
            requests[index].callback(NormalizedArtwork("token-$index", uri))
        }
    }

    private class Fixture(
        val repository: MediaStateRepository,
        val normalizer: DeferredNormalizer,
        val fallback: CpaaArtworkFallback,
    )

    private fun fixture(track: ArtworkTrackIdentity): Fixture {
        val clock = FakeClock()
        val repository = MediaStateRepository(clock)
        repository.setTrack(track)
        val normalizer = DeferredNormalizer()
        val fallback = CpaaArtworkFallback(repository, normalizer)
        return Fixture(repository, normalizer, fallback)
    }

    private fun MediaStateRepository.setTrack(track: ArtworkTrackIdentity) {
        update {
            it.copy(
                backendConnected = true,
                audioSource = BridgeAudioSource.CPAA.name,
                ownerPackage = CARPLAY_MEDIA_SESSION_PACKAGE,
                mediaId = track.mediaId,
                title = track.title,
                artist = track.artist,
            )
        }
    }

    private fun track(mediaId: String, title: String, artist: String) =
        ArtworkTrackIdentity(mediaId = mediaId, title = title, artist = artist)

    private fun candidate(
        track: ArtworkTrackIdentity,
        artwork: String,
        packageName: String = CARPLAY_MEDIA_SESSION_PACKAGE,
    ) = SessionArtworkCandidate(
        packageName = packageName,
        track = track,
        artwork = ArtworkInput(sourceUri = artwork),
    )

    @Test
    fun `matching CarPlay session fills missing CPAA artwork`() {
        val fixture = fixture(track("oneos-id", "Song", "Artist"))

        fixture.fallback.onOneOsTrack(ArtworkInput())
        fixture.fallback.onMediaSession(
            candidate(track("oneos-id", "ignored", "ignored"), "session-art"),
        )
        fixture.normalizer.complete(0, "content://atlasmedia/fallback")

        val snapshot = fixture.repository.snapshot()
        assertEquals("content://atlasmedia/fallback", snapshot.artworkUri)
        assertEquals("Song", snapshot.title)
        assertEquals("Artist", snapshot.artist)
        assertEquals(CARPLAY_MEDIA_SESSION_PACKAGE, snapshot.ownerPackage)
    }

    @Test
    fun `OneOS artwork wins over pending MediaSession fallback`() {
        val fixture = fixture(track("track-1", "Song", "Artist"))
        fixture.fallback.onOneOsTrack(ArtworkInput())
        fixture.fallback.onMediaSession(candidate(track("track-1", "Song", "Artist"), "fallback"))

        fixture.fallback.onOneOsTrack(ArtworkInput(sourceUri = "oneos-art"))
        fixture.normalizer.complete(0, "content://atlasmedia/stale-fallback")
        fixture.normalizer.complete(1, "content://atlasmedia/oneos")

        assertEquals("content://atlasmedia/oneos", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `failed OneOS artwork keeps matching MediaSession fallback available`() {
        val fixture = fixture(track("track-1", "Song", "Artist"))
        fixture.fallback.onOneOsTrack(ArtworkInput(sourceUri = "unreadable-art"))
        fixture.fallback.onMediaSession(candidate(track("track-1", "Song", "Artist"), "fallback"))

        fixture.normalizer.complete(0, "")
        fixture.normalizer.complete(1, "content://atlasmedia/fallback")

        assertEquals("content://atlasmedia/fallback", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `controller from another package is ignored`() {
        val fixture = fixture(track("track-1", "Song", "Artist"))
        fixture.fallback.onOneOsTrack(ArtworkInput())

        fixture.fallback.onMediaSession(
            candidate(
                track("track-1", "Song", "Artist"),
                artwork = "foreign-art",
                packageName = "com.example.player",
            ),
        )

        assertTrue(fixture.normalizer.requests.isEmpty())
        assertEquals("", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `mismatched track is ignored`() {
        val fixture = fixture(track("oneos-id", "Song", "Artist"))
        fixture.fallback.onOneOsTrack(ArtworkInput())

        fixture.fallback.onMediaSession(
            candidate(track("session-id", "Different", "Performer"), "foreign-track-art"),
        )

        assertTrue(fixture.normalizer.requests.isEmpty())
        assertEquals("", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `late callback from previous track is ignored`() {
        val fixture = fixture(track("track-a", "First", "Artist"))
        fixture.fallback.onOneOsTrack(ArtworkInput())
        fixture.fallback.onMediaSession(candidate(track("track-a", "First", "Artist"), "art-a"))

        fixture.repository.setTrack(track("track-b", "Second", "Artist"))
        fixture.fallback.onOneOsTrack(ArtworkInput())
        fixture.fallback.onMediaSession(candidate(track("track-b", "Second", "Artist"), "art-b"))
        fixture.normalizer.complete(0, "content://atlasmedia/stale-a")
        fixture.normalizer.complete(1, "content://atlasmedia/current-b")

        assertEquals("content://atlasmedia/current-b", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `leaving CPAA clears fallback artwork`() {
        val fixture = fixture(track("track-1", "Song", "Artist"))
        fixture.fallback.onOneOsTrack(ArtworkInput())
        fixture.fallback.onMediaSession(candidate(track("track-1", "Song", "Artist"), "fallback"))
        fixture.normalizer.complete(0, "content://atlasmedia/fallback")

        fixture.repository.update { it.copy(audioSource = BridgeAudioSource.BT.name) }
        fixture.fallback.onBridgeStateChanged()

        assertEquals("", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `CarPlay session without artwork keeps snapshot empty`() {
        val fixture = fixture(track("track-1", "Song", "Artist"))
        fixture.fallback.onOneOsTrack(ArtworkInput())

        fixture.fallback.onMediaSession(
            SessionArtworkCandidate(
                packageName = CARPLAY_MEDIA_SESSION_PACKAGE,
                track = track("track-1", "Song", "Artist"),
                artwork = ArtworkInput(),
            ),
        )

        assertTrue(fixture.normalizer.requests.isEmpty())
        assertEquals("", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `all supported CarPlay packages are accepted for fallback`() {
        CARPLAY_MEDIA_SESSION_PACKAGES.forEachIndexed { index, pkg ->
            val fixture = fixture(track("track-$index", "Song $index", "Artist $index"))
            fixture.fallback.onOneOsTrack(ArtworkInput())
            fixture.fallback.onMediaSession(
                candidate(
                    track = track("track-$index", "Song $index", "Artist $index"),
                    artwork = "art-$index",
                    packageName = pkg,
                ),
            )
            fixture.normalizer.complete(0, "content://atlasmedia/fallback-$index")
            assertEquals("content://atlasmedia/fallback-$index", fixture.repository.snapshot().artworkUri)
        }
    }

    @Test
    fun `fuzzy track title matching handles feat and remastered suffixes`() {
        val fixture = fixture(track("oneos-id", "Song Name", "Artist Name"))
        fixture.fallback.onOneOsTrack(ArtworkInput())
        fixture.fallback.onMediaSession(
            candidate(
                track = track("session-id", "Song Name (feat. Guest Artist) [Remastered]", "Artist Name"),
                artwork = "feat-art",
            ),
        )
        fixture.normalizer.complete(0, "content://atlasmedia/feat-art")
        assertEquals("content://atlasmedia/feat-art", fixture.repository.snapshot().artworkUri)
    }

    @Test
    fun `fuzzy track matching handles empty or unknown oneos metadata`() {
        val fixture = fixture(track("oneos-id", "", ""))
        fixture.fallback.onOneOsTrack(ArtworkInput())
        fixture.fallback.onMediaSession(
            candidate(
                track = track("session-id", "Actual Song", "Actual Artist"),
                artwork = "actual-art",
            ),
        )
        fixture.normalizer.complete(0, "content://atlasmedia/actual-art")
        assertEquals("content://atlasmedia/actual-art", fixture.repository.snapshot().artworkUri)
    }
}
