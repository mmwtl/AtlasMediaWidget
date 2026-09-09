package com.mmwtl.atlasmediaapi.media.bridge

import java.util.Locale

val CARPLAY_MEDIA_SESSION_PACKAGES = setOf(
    "com.autolink.carplay",
    "com.autolink.carplay.app",
    "com.test.carplay",
    "com.igrs.autolink",
    "com.android.cpaa",
    "com.ecarx.cpaa",
    "com.ecarx.carplay",
    "com.geely.cpaa",
    "com.geely.carplay",
    "com.autolink.carlink",
    "com.suding.speedplay",
    "com.zjinnova.zlink",
    "cn.manstep.phonemirrorbox",
    "com.carplay",
    "com.carplay.service",
)
const val CARPLAY_MEDIA_SESSION_PACKAGE = "com.autolink.carplay"

data class ArtworkTrackIdentity(
    val mediaId: String,
    val title: String,
    val artist: String,
) {
    val normalizedTitle = title.normalizeTrackText()
    val normalizedArtist = artist.normalizeTrackText()
    private val strippedTitle = normalizedTitle.stripTrackSuffixes()

    fun matchesSession(other: ArtworkTrackIdentity): Boolean {
        if (mediaId.isNotBlank() && other.mediaId.isNotBlank() && mediaId == other.mediaId) {
            return true
        }
        val otherStripped = other.normalizedTitle.stripTrackSuffixes()
        val titleMatches = when {
            normalizedTitle.isBlank() || other.normalizedTitle.isBlank() -> true
            normalizedTitle == other.normalizedTitle -> true
            strippedTitle.isNotBlank() && otherStripped.isNotBlank() && strippedTitle == otherStripped -> true
            strippedTitle.isNotBlank() && otherStripped.isNotBlank() &&
                (strippedTitle.startsWith(otherStripped) || otherStripped.startsWith(strippedTitle)) -> true
            normalizedTitle.contains(other.normalizedTitle) || other.normalizedTitle.contains(normalizedTitle) -> true
            else -> false
        }
        val artistMatches = when {
            normalizedArtist.isBlank() || other.normalizedArtist.isBlank() -> true
            normalizedArtist == other.normalizedArtist -> true
            normalizedArtist.contains(other.normalizedArtist) || other.normalizedArtist.contains(normalizedArtist) -> true
            else -> false
        }
        return titleMatches && artistMatches
    }

    fun isSameOneOsTrack(other: ArtworkTrackIdentity): Boolean =
        if (mediaId.isNotBlank() || other.mediaId.isNotBlank()) {
            mediaId.isNotBlank() && mediaId == other.mediaId
        } else {
            normalizedTitle.isNotBlank() &&
                normalizedTitle == other.normalizedTitle &&
                normalizedArtist == other.normalizedArtist
        }
}

data class SessionArtworkCandidate(
    val packageName: String,
    val track: ArtworkTrackIdentity,
    val artwork: ArtworkInput,
)

/** Keeps CPAA metadata OneOS-owned while filling only a missing artwork URI from CarPlay. */
class CpaaArtworkFallback(
    private val repository: MediaStateRepository,
    private val normalizer: ArtworkNormalizer,
) {
    private enum class Origin { ONE_OS, MEDIA_SESSION }

    private data class PendingRequest(
        val sequence: Long,
        val track: ArtworkTrackIdentity,
        val input: ArtworkInput,
        val origin: Origin,
    )

    private val lock = Any()
    private var sequence = 0L
    private var currentTrack: ArtworkTrackIdentity? = null
    private var sessionCandidate: SessionArtworkCandidate? = null
    private var oneOsArtworkAvailable = false
    private var fallbackApplied = false

    fun onBridgeStateChanged() {
        val shouldClear = synchronized(lock) {
            val snapshot = repository.snapshot()
            if (snapshot.backendConnected && snapshot.audioSource == BridgeAudioSource.CPAA.name) {
                false
            } else {
                sequence++
                currentTrack = null
                oneOsArtworkAvailable = false
                val clear = fallbackApplied || snapshot.artworkUri.isNotBlank()
                fallbackApplied = false
                clear
            }
        }
        if (shouldClear) publishArtwork("")
    }

    fun onOneOsTrack(artwork: ArtworkInput) {
        var shouldClear = false
        val request = synchronized(lock) {
            val snapshot = repository.snapshot()
            if (!snapshot.backendConnected || snapshot.audioSource != BridgeAudioSource.CPAA.name) {
                sequence++
                currentTrack = null
                oneOsArtworkAvailable = false
                fallbackApplied = false
                null
            } else {
                val track = snapshot.artworkTrack()
                val trackChanged = currentTrack?.isSameOneOsTrack(track) != true
                if (trackChanged) {
                    sequence++
                    currentTrack = track
                    oneOsArtworkAvailable = false
                    fallbackApplied = false
                    shouldClear = snapshot.artworkUri.isNotBlank()
                }

                if (artwork.isPresent()) {
                    newRequest(track, artwork, Origin.ONE_OS)
                } else if (!oneOsArtworkAvailable) {
                    sessionCandidate
                        ?.takeIf { it.track.matchesSession(track) && it.artwork.isPresent() }
                        ?.let { newRequest(track, it.artwork, Origin.MEDIA_SESSION) }
                } else {
                    null
                }
            }
        }
        if (shouldClear) publishArtwork("")
        request?.start()
    }

    fun onMediaSession(candidate: SessionArtworkCandidate?) {
        if (candidate != null && candidate.packageName !in CARPLAY_MEDIA_SESSION_PACKAGES) return

        var shouldClear = false
        val request = synchronized(lock) {
            sessionCandidate = candidate
            val snapshot = repository.snapshot()
            val track = currentTrack
            if (!snapshot.backendConnected || snapshot.audioSource != BridgeAudioSource.CPAA.name ||
                track == null || oneOsArtworkAvailable
            ) {
                null
            } else if (candidate == null) {
                sequence++
                shouldClear = fallbackApplied
                fallbackApplied = false
                null
            } else if (!candidate.track.matchesSession(track)) {
                null
            } else if (!candidate.artwork.isPresent()) {
                sequence++
                shouldClear = fallbackApplied || snapshot.artworkUri.isNotBlank()
                fallbackApplied = false
                null
            } else {
                newRequest(track, candidate.artwork, Origin.MEDIA_SESSION)
            }
        }
        if (shouldClear) publishArtwork("")
        request?.start()
    }

    private fun newRequest(
        track: ArtworkTrackIdentity,
        input: ArtworkInput,
        origin: Origin,
    ): PendingRequest {
        val nextSequence = ++sequence
        return PendingRequest(nextSequence, track, input, origin)
    }

    private fun PendingRequest.start() {
        normalizer.normalize(input) { normalized ->
            val uriToPublish = synchronized(lock) {
                if (sequence != this.sequence) return@normalize
                val snapshot = repository.snapshot()
                if (!snapshot.backendConnected || snapshot.audioSource != BridgeAudioSource.CPAA.name) {
                    return@normalize
                }
                val current = currentTrack ?: return@normalize
                if (!current.isSameOneOsTrack(track)) return@normalize

                when (origin) {
                    Origin.ONE_OS -> {
                        if (normalized.uri.isNotBlank()) {
                            oneOsArtworkAvailable = true
                            fallbackApplied = false
                            normalized.uri
                        } else {
                            oneOsArtworkAvailable = false
                            val fallback = sessionCandidate
                                ?.takeIf { it.track.matchesSession(current) && it.artwork.isPresent() }
                                ?.let { newRequest(current, it.artwork, Origin.MEDIA_SESSION) }
                            fallback?.start()
                            null
                        }
                    }

                    Origin.MEDIA_SESSION -> {
                        if (oneOsArtworkAvailable) return@normalize
                        if (normalized.uri.isNotBlank()) {
                            fallbackApplied = true
                            normalized.uri
                        } else {
                            val clear = fallbackApplied || snapshot.artworkUri.isNotBlank()
                            fallbackApplied = false
                            if (clear) "" else null
                        }
                    }
                }
            }
            if (uriToPublish != null) publishArtwork(uriToPublish)
        }
    }

    private fun publishArtwork(uri: String) {
        repository.update {
            if (it.artworkUri == uri) it else it.copy(
                artworkUri = uri,
                artworkRevision = it.artworkRevision + 1L,
            )
        }
    }
}

private fun MediaSnapshot.artworkTrack() = ArtworkTrackIdentity(
    mediaId = mediaId,
    title = title,
    artist = artist,
)

private fun ArtworkInput.isPresent(): Boolean = bitmap != null || sourceUri.isNotBlank()

private fun String.normalizeTrackText(): String = lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
    .trim()

private fun String.stripTrackSuffixes(): String =
    replace(Regex("\\b(feat|ft|featuring|remix|remastered|deluxe|version|single|edit|live|mix)\\b.*"), "").trim()
