package com.mmwtl.atlasmediaapi.media.bridge

import android.media.session.PlaybackState

object MediaCapabilities {
    const val PLAY = 1L shl 0
    const val PAUSE = 1L shl 1
    const val TOGGLE = 1L shl 2
    const val NEXT = 1L shl 3
    const val PREVIOUS = 1L shl 4
    const val SEEK_TO = 1L shl 5
    const val SET_SOURCE = 1L shl 6
    const val TUNE_RADIO = 1L shl 7

    const val BASIC_PLAYBACK = PLAY or PAUSE or TOGGLE
    const val TRACK_NAVIGATION = NEXT or PREVIOUS
    const val ALL = BASIC_PLAYBACK or TRACK_NAVIGATION or SEEK_TO or SET_SOURCE

    fun fromPlaybackActions(actions: Long): Long {
        var result = SET_SOURCE
        if (actions and PlaybackState.ACTION_PLAY != 0L) result = result or PLAY
        if (actions and PlaybackState.ACTION_PAUSE != 0L) result = result or PAUSE
        if (actions and PlaybackState.ACTION_PLAY_PAUSE != 0L) {
            result = result or PLAY or PAUSE or TOGGLE
        }
        if (result and PLAY != 0L && result and PAUSE != 0L) result = result or TOGGLE
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) result = result or NEXT
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) result = result or PREVIOUS
        if (actions and PlaybackState.ACTION_SEEK_TO != 0L) result = result or SEEK_TO
        return result
    }
}

enum class BridgeAudioSource {
    UNKNOWN,
    USB,
    BT,
    RADIO,
    ONLINE,
    OTHER,
    YUNTING,
    CPAA,
}

data class MediaSourceSnapshot(
    val id: String,
    val connected: Boolean = false,
    val available: Boolean = false,
    val selected: Boolean = false,
    val capabilities: Long = MediaCapabilities.SET_SOURCE,
)

data class MediaSnapshot(
    val protocolVersion: Int = MediaBridgeContract.PROTOCOL_VERSION,
    val generation: Long = 0L,
    val timestamp: Long = 0L,
    val backendConnected: Boolean = false,
    val backendErrorCode: Int = MediaBridgeContract.BackendError.NONE,
    val backendErrorMessage: String = "",
    val audioSource: String = BridgeAudioSource.UNKNOWN.name,
    val appSource: String = "UNKNOWN",
    val sources: List<MediaSourceSnapshot> = defaultMediaSources(),
    val ownerPackage: String = "",
    val ownerApp: String = "",
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = -1L,
    val position: Long = -1L,
    val updateElapsedRealtime: Long = 0L,
    val speed: Float = 0f,
    val playbackState: Int = PlaybackState.STATE_NONE,
    val playbackErrorCode: Int = 0,
    val playbackErrorMessage: String = "",
    val playbackActions: Long = 0L,
    val capabilities: Long = MediaCapabilities.SET_SOURCE,
    val artworkUri: String = "",
    val artworkRevision: Long = 0L,
)

fun defaultMediaSources(): List<MediaSourceSnapshot> =
    BridgeAudioSource.entries.map { source ->
        MediaSourceSnapshot(
            id = source.name,
            capabilities = when (source) {
                BridgeAudioSource.RADIO ->
                    MediaCapabilities.BASIC_PLAYBACK or
                            MediaCapabilities.TRACK_NAVIGATION or
                            MediaCapabilities.TUNE_RADIO or
                            MediaCapabilities.SET_SOURCE

                BridgeAudioSource.BT,
                BridgeAudioSource.USB,
                BridgeAudioSource.CPAA ->
                    MediaCapabilities.BASIC_PLAYBACK or
                            MediaCapabilities.TRACK_NAVIGATION or
                            MediaCapabilities.SET_SOURCE

                BridgeAudioSource.ONLINE -> MediaCapabilities.ALL
                BridgeAudioSource.YUNTING ->
                    MediaCapabilities.BASIC_PLAYBACK or
                            MediaCapabilities.TRACK_NAVIGATION or
                            MediaCapabilities.SET_SOURCE

                BridgeAudioSource.UNKNOWN,
                BridgeAudioSource.OTHER -> MediaCapabilities.SET_SOURCE
            },
        )
    }

fun BridgeAudioSource.defaultCapabilities(): Long =
    defaultMediaSources().first { it.id == name }.capabilities

enum class MediaCommand {
    PLAY,
    PAUSE,
    TOGGLE,
    NEXT,
    PREVIOUS,
    SEEK_TO,
    SET_SOURCE,
    TUNE_RADIO,
}

data class RadioStationTarget(
    val frequencyKHz: Int,
    val band: Int,
    val ensembleName: String = "",
    val serviceName: String = "",
    val genre: String = "",
    val iconId: Int = 0,
    val signalQuality: Int = 0,
    val selector: String = "",
)

data class RadioStationSnapshot(
    val id: String,
    val frequencyKHz: Int,
    val formattedFrequency: String,
    val band: Int,
    val bandName: String,
    val name: String,
    val ensembleName: String = "",
    val serviceName: String = "",
    val genre: String = "",
    val iconId: Int = 0,
    val signalQuality: Int = 0,
    val selector: String = "",
    val favorite: Boolean = false,
    val artworkUri: String = "",
)

data class RadioStationLists(
    val saved: List<RadioStationSnapshot>,
    val favorites: List<RadioStationSnapshot>,
)

data class MediaCommandRequest(
    val requestId: String,
    val command: MediaCommand,
    val position: Long? = null,
    val source: BridgeAudioSource? = null,
    val appSource: String? = null,
    val autoplay: Boolean = true,
    val radioStation: RadioStationTarget? = null,
)

data class MediaCommandResult(
    val status: Int,
    val message: String = "",
) {
    val succeeded: Boolean get() = status == MediaBridgeContract.Status.OK
}
