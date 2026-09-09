package com.mmwtl.atlasmediaapi.media.bridge

import android.media.session.PlaybackState

data class RadioMetadata(
    val mediaId: String,
    val title: String,
    val subtitle: String,
)

enum class RadioPlaybackAction {
    PLAY,
    PAUSE,
}

const val RADIO_STATUS_PLAYING = 0x1001
const val ONE_OS_MEDIA_FUNCTION_PLAY = 0x1000
const val ONE_OS_MEDIA_FUNCTION_PAUSE = 0x1001

fun isRadioPlaying(status: Int): Boolean = status == RADIO_STATUS_PLAYING

fun radioPlaybackAction(
    command: MediaCommand,
    status: Int,
): RadioPlaybackAction? = when (command) {
    MediaCommand.PLAY -> RadioPlaybackAction.PLAY
    MediaCommand.PAUSE -> RadioPlaybackAction.PAUSE
    MediaCommand.TOGGLE -> if (isRadioPlaying(status)) {
        RadioPlaybackAction.PAUSE
    } else {
        RadioPlaybackAction.PLAY
    }

    MediaCommand.NEXT,
    MediaCommand.PREVIOUS,
    MediaCommand.SEEK_TO,
    MediaCommand.SET_SOURCE -> null
    MediaCommand.TUNE_RADIO -> null
}

fun oneOsPlayPauseCommand(function: Int, forceToggle: Boolean): MediaCommand = when {
    forceToggle -> MediaCommand.TOGGLE
    function == ONE_OS_MEDIA_FUNCTION_PLAY -> MediaCommand.PLAY
    function == ONE_OS_MEDIA_FUNCTION_PAUSE -> MediaCommand.PAUSE
    else -> MediaCommand.TOGGLE
}

fun radioMetadata(
    frequency: Int,
    band: Int,
    serviceName: String,
    ensembleName: String,
    fallbackTitle: String,
): RadioMetadata {
    val formattedFrequency = formatRadioFrequency(frequency)
    val title = serviceName.trim().ifBlank {
        formattedFrequency.ifBlank { fallbackTitle }
    }
    val subtitle = ensembleName.trim().ifBlank {
        if (title == formattedFrequency) fallbackTitle else formattedFrequency
    }
    return RadioMetadata(
        mediaId = "radio:$band:$frequency:${serviceName.trim()}",
        title = title,
        subtitle = subtitle,
    )
}

fun resolveRadioServiceName(
    oneOsServiceName: String?,
    catalogServiceName: String,
    catalogBroadcastEnabled: Boolean,
): String = oneOsServiceName
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?: catalogServiceName.trim().takeIf { catalogBroadcastEnabled }.orEmpty()

fun MediaSnapshot.withRadioState(
    station: RadioMetadata?,
    playing: Boolean,
    elapsedRealtime: Long,
    artworkUri: String? = null,
): MediaSnapshot {
    if (!backendConnected || audioSource != BridgeAudioSource.RADIO.name) return this

    val nextPlaybackState = if (playing) {
        PlaybackState.STATE_PLAYING
    } else {
        PlaybackState.STATE_PAUSED
    }
    val nextSpeed = if (playing) 1f else 0f
    val playbackChanged = playbackState != nextPlaybackState || speed != nextSpeed
    val stationChanged = station != null && (station.mediaId != mediaId || station.title != title)
    val nextArtworkUri = when {
        artworkUri != null -> artworkUri
        stationChanged -> ""
        else -> this.artworkUri
    }
    val artworkChanged = this.artworkUri != nextArtworkUri
    return copy(
        ownerPackage = RADIO_OWNER_PACKAGE,
        ownerApp = BridgeAudioSource.RADIO.name,
        mediaId = station?.mediaId ?: mediaId,
        title = station?.title ?: title,
        artist = station?.subtitle ?: artist,
        album = if (station != null) "" else album,
        duration = -1L,
        position = -1L,
        updateElapsedRealtime = if (playbackChanged) elapsedRealtime else updateElapsedRealtime,
        speed = nextSpeed,
        playbackState = nextPlaybackState,
        playbackErrorCode = 0,
        playbackErrorMessage = "",
        playbackActions = 0L,
        capabilities = BridgeAudioSource.RADIO.defaultCapabilities(),
        artworkUri = nextArtworkUri,
        artworkRevision = if (artworkChanged) {
            artworkRevision + 1L
        } else {
            artworkRevision
        },
    )
}

const val RADIO_OWNER_PACKAGE = "com.geely.radio.service"
