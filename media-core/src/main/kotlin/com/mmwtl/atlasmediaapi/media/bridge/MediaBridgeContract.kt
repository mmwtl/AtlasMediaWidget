package com.mmwtl.atlasmediaapi.media.bridge

import android.os.Bundle

/** Public Messenger/Bundle contract. Keep key values stable across protocol versions. */
object MediaBridgeContract {
    const val SERVICE_ACTION = "com.mmwtl.atlasmediaapi.media.BIND"
    const val SERVICE_PACKAGE = "com.mmwtl.atlasmediaapi"
    const val SERVICE_CLASS = "com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService"

    const val PROTOCOL_VERSION = 1
    const val MIN_PROTOCOL_VERSION = 1
    const val MAX_PROTOCOL_VERSION = 1

    const val MAX_REQUEST_ID_LENGTH = 128
    const val MAX_MESSAGE_LENGTH = 512
    const val MAX_RADIO_METADATA_LENGTH = 128

    object ClientMessage {
        const val REGISTER = 1
        const val UNREGISTER = 2
        const val GET_SNAPSHOT = 3
        const val COMMAND = 4
        const val GET_RADIO_STATIONS = 5
        const val GET_SETTINGS = 6
        const val UPDATE_SETTINGS = 7
        const val EXPORT_MEDIA_BACKUP = 8
        const val PREPARE_MEDIA_IMPORT = 9
        const val COMMIT_MEDIA_IMPORT = 10
        const val GET_IMPORT_STATUS = 11
        const val ABORT_MEDIA_IMPORT = 12
        const val RESTORE_DEFAULT_CATALOG = 13
        const val EXPORT_RADIO_CATALOG = 14
        const val IMPORT_RADIO_CATALOG = 15
    }

    object ServerMessage {
        const val REGISTERED = 100
        const val SNAPSHOT = 101
        const val COMMAND_RESULT = 102
        const val ERROR = 103
        const val RADIO_STATIONS = 104
        const val SETTINGS = 105
        const val SETTINGS_UPDATED = 106
        const val MEDIA_BACKUP_EXPORTED = 107
        const val MEDIA_IMPORT_PREPARED = 108
        const val MEDIA_IMPORT_COMMITTED = 109
        const val MEDIA_IMPORT_STATUS = 110
        const val MEDIA_IMPORT_ABORTED = 111
        const val DEFAULT_CATALOG_RESTORED = 112
        const val RADIO_CATALOG_EXPORTED = 114
        const val RADIO_CATALOG_IMPORTED = 115
    }

    object Key {
        const val PROTOCOL_VERSION = "protocolVersion"
        const val MIN_PROTOCOL_VERSION = "minProtocolVersion"
        const val MAX_PROTOCOL_VERSION = "maxProtocolVersion"
        const val REQUEST_ID = "requestId"
        const val STATUS = "status"
        const val MESSAGE = "message"

        const val GENERATION = "generation"
        const val TIMESTAMP = "timestamp"
        const val BACKEND_CONNECTED = "backendConnected"
        const val BACKEND_ERROR_CODE = "backendErrorCode"
        const val BACKEND_ERROR_MESSAGE = "backendErrorMessage"
        const val AUDIO_SOURCE = "audioSource"
        const val APP_SOURCE = "appSource"
        const val SOURCES = "sources"
        const val SOURCE_ID = "id"
        const val SOURCE_CONNECTED = "connected"
        const val SOURCE_AVAILABLE = "available"
        const val SOURCE_SELECTED = "selected"
        const val SOURCE_CAPABILITIES = "capabilities"

        const val OWNER_PACKAGE = "ownerPackage"
        const val OWNER_APP = "ownerApp"
        const val MEDIA_ID = "mediaId"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val DURATION = "duration"
        const val POSITION = "position"
        const val UPDATE_ELAPSED_REALTIME = "updateElapsedRealtime"
        const val SPEED = "speed"
        const val PLAYBACK_STATE = "playbackState"
        const val PLAYBACK_ERROR_CODE = "playbackErrorCode"
        const val PLAYBACK_ERROR_MESSAGE = "playbackErrorMessage"
        const val PLAYBACK_ACTIONS = "playbackActions"
        const val CAPABILITIES = "capabilities"
        const val ARTWORK_URI = "artworkUri"
        const val ARTWORK_REVISION = "artworkRevision"

        const val RADIO_SAVED_STATIONS = "radioSavedStations"
        const val RADIO_FAVORITE_STATIONS = "radioFavoriteStations"
        const val RADIO_STATION_ID = "radioStationId"
        const val RADIO_FREQUENCY_KHZ = "radioFrequencyKHz"
        const val RADIO_FORMATTED_FREQUENCY = "radioFormattedFrequency"
        const val RADIO_BAND = "radioBand"
        const val RADIO_BAND_NAME = "radioBandName"
        const val RADIO_NAME = "radioName"
        const val RADIO_ENSEMBLE_NAME = "radioEnsembleName"
        const val RADIO_SERVICE_NAME = "radioServiceName"
        const val RADIO_GENRE = "radioGenre"
        const val RADIO_ICON_ID = "radioIconId"
        const val RADIO_SIGNAL_QUALITY = "radioSignalQuality"
        const val RADIO_SELECTOR = "radioSelector"
        const val RADIO_FAVORITE = "radioFavorite"
        const val RADIO_ARTWORK_URI = "radioArtworkUri"

        const val COMMAND = "command"
        const val COMMAND_POSITION = "position"
        const val COMMAND_SOURCE = "source"
        const val COMMAND_APP_SOURCE = "appSource"
        const val COMMAND_AUTOPLAY = "autoplay"
        const val UI_SCALE_TENTHS = "uiScaleTenths"

        const val SETTINGS_PROTOCOL_VERSION = "settingsProtocolVersion"
        const val SETTINGS_REVISION = "settingsRevision"
        const val EXPECTED_REVISION = "expectedRevision"
        const val OPERATION_ID = "operationId"
        const val STAGING_TOKEN = "stagingToken"
        const val IMPORT_STATUS = "importStatus"
        const val FILE_DESCRIPTOR = "fileDescriptor"
        const val IMPORT_PREVIEW = "importPreview"

        const val DEFAULT_AUDIO_SOURCE = "defaultAudioSource"
        const val DEFAULT_AUDIO_SOURCE_DELAY_SEC = "defaultAudioSourceDelaySec"
        const val DEFAULT_AUDIO_SOURCE_AUTOPLAY = "defaultAudioSourceAutoplay"
        const val AUTO_SWITCH_TO_DEFAULT = "autoSwitchToDefault"
        const val AUTO_SWITCH_TO_DEFAULT_AUTOPLAY = "autoSwitchToDefaultAutoplay"
        const val DEFAULT_MEDIA_PACKAGE = "defaultMediaPackage"
        const val SWITCH_TO_ONLINE_BEFORE_SESSION_PLAY = "switchToOnlineBeforeSessionPlay"
        const val RADIO_WIDGET_BROADCAST_ENABLED = "radioWidgetBroadcastEnabled"
        const val CLUSTER_COVERS_ENABLED = "clusterCoversEnabled"
        const val CLUSTER_ONLINE_ENABLED = "clusterOnlineEnabled"
        const val CLUSTER_WATCHDOG_INTERVAL_MS = "clusterWatchdogIntervalMs"
        const val CLUSTER_REASSERT_BURST_INTERVAL_MS = "clusterReassertBurstIntervalMs"
        const val CATALOG_TYPE = "catalogType"
        const val CATALOG_STATION_COUNT = "catalogStationCount"
        const val CATALOG_DESCRIPTION = "catalogDescription"
    }

    object Status {
        const val OK = 0
        const val INVALID_REQUEST = 1
        const val UNSUPPORTED_VERSION = 2
        const val UNAUTHORIZED = 3
        const val UNKNOWN_COMMAND = 4
        const val BACKEND_UNAVAILABLE = 5
        const val NOT_SUPPORTED = 6
        const val FAILED = 7
        const val NOT_REGISTERED = 8
        const val VALIDATION_ERROR = 9
        const val CONFLICT = 10
        const val IO_ERROR = 11
    }

    object BackendError {
        const val NONE = 0
        const val CONNECTING = 1
        const val ONE_OS_DISCONNECTED = 2
        const val ONE_OS_ERROR = 3
    }

    fun negotiate(clientVersion: Int): Int = when {
        clientVersion < MIN_PROTOCOL_VERSION || clientVersion > MAX_PROTOCOL_VERSION ->
            Status.UNSUPPORTED_VERSION

        else -> Status.OK
    }
}

fun MediaSnapshot.toBundle(): Bundle = Bundle().apply {
    putInt(MediaBridgeContract.Key.PROTOCOL_VERSION, protocolVersion)
    putLong(MediaBridgeContract.Key.GENERATION, generation)
    putLong(MediaBridgeContract.Key.TIMESTAMP, timestamp)
    putBoolean(MediaBridgeContract.Key.BACKEND_CONNECTED, backendConnected)
    putInt(MediaBridgeContract.Key.BACKEND_ERROR_CODE, backendErrorCode)
    putString(MediaBridgeContract.Key.BACKEND_ERROR_MESSAGE, backendErrorMessage)
    putString(MediaBridgeContract.Key.AUDIO_SOURCE, audioSource)
    putString(MediaBridgeContract.Key.APP_SOURCE, appSource)
    putParcelableArrayList(
        MediaBridgeContract.Key.SOURCES,
        ArrayList(sources.map(MediaSourceSnapshot::toBundle)),
    )
    putString(MediaBridgeContract.Key.OWNER_PACKAGE, ownerPackage)
    putString(MediaBridgeContract.Key.OWNER_APP, ownerApp)
    putString(MediaBridgeContract.Key.MEDIA_ID, mediaId)
    putString(MediaBridgeContract.Key.TITLE, title)
    putString(MediaBridgeContract.Key.ARTIST, artist)
    putString(MediaBridgeContract.Key.ALBUM, album)
    putLong(MediaBridgeContract.Key.DURATION, duration)
    putLong(MediaBridgeContract.Key.POSITION, position)
    putLong(MediaBridgeContract.Key.UPDATE_ELAPSED_REALTIME, updateElapsedRealtime)
    putFloat(MediaBridgeContract.Key.SPEED, speed)
    putInt(MediaBridgeContract.Key.PLAYBACK_STATE, playbackState)
    putInt(MediaBridgeContract.Key.PLAYBACK_ERROR_CODE, playbackErrorCode)
    putString(MediaBridgeContract.Key.PLAYBACK_ERROR_MESSAGE, playbackErrorMessage)
    putLong(MediaBridgeContract.Key.PLAYBACK_ACTIONS, playbackActions)
    putLong(MediaBridgeContract.Key.CAPABILITIES, capabilities)
    putString(MediaBridgeContract.Key.ARTWORK_URI, artworkUri)
    putLong(MediaBridgeContract.Key.ARTWORK_REVISION, artworkRevision)
}

private fun MediaSourceSnapshot.toBundle(): Bundle = Bundle().apply {
    putString(MediaBridgeContract.Key.SOURCE_ID, id)
    putBoolean(MediaBridgeContract.Key.SOURCE_CONNECTED, connected)
    putBoolean(MediaBridgeContract.Key.SOURCE_AVAILABLE, available)
    putBoolean(MediaBridgeContract.Key.SOURCE_SELECTED, selected)
    putLong(MediaBridgeContract.Key.SOURCE_CAPABILITIES, capabilities)
}

fun Bundle.toMediaCommandRequest(): MediaCommandRequest? {
    val rawRequestId = getString(MediaBridgeContract.Key.REQUEST_ID)?.takeIf(String::isNotBlank)
        ?: return null
    if (rawRequestId.length > MediaBridgeContract.MAX_REQUEST_ID_LENGTH) return null

    val command = getString(MediaBridgeContract.Key.COMMAND)
        ?.let { runCatching { MediaCommand.valueOf(it) }.getOrNull() }
        ?: return null

    return when (command) {
        MediaCommand.SEEK_TO -> {
            if (!containsKey(MediaBridgeContract.Key.COMMAND_POSITION)) return null
            val position = getLong(MediaBridgeContract.Key.COMMAND_POSITION)
            if (position < 0L) return null
            MediaCommandRequest(rawRequestId, command, position = position)
        }

        MediaCommand.SET_SOURCE -> {
            val source = getString(MediaBridgeContract.Key.COMMAND_SOURCE)
                ?.let { runCatching { BridgeAudioSource.valueOf(it) }.getOrNull() }
                ?: return null
            val appSource = getString(MediaBridgeContract.Key.COMMAND_APP_SOURCE)
                ?.takeIf { it.length <= 64 }
            MediaCommandRequest(
                requestId = rawRequestId,
                command = command,
                source = source,
                appSource = appSource,
                autoplay = getBoolean(MediaBridgeContract.Key.COMMAND_AUTOPLAY, true),
            )
        }

        MediaCommand.TUNE_RADIO -> {
            if (!containsKey(MediaBridgeContract.Key.RADIO_FREQUENCY_KHZ) ||
                !containsKey(MediaBridgeContract.Key.RADIO_BAND)
            ) return null
            val frequencyKHz = getInt(MediaBridgeContract.Key.RADIO_FREQUENCY_KHZ)
            val band = getInt(MediaBridgeContract.Key.RADIO_BAND)
            if (!isSupportedRadioFrequency(frequencyKHz, band)) return null
            val target = RadioStationTarget(
                frequencyKHz = frequencyKHz,
                band = band,
                ensembleName = radioString(MediaBridgeContract.Key.RADIO_ENSEMBLE_NAME) ?: return null,
                serviceName = radioString(MediaBridgeContract.Key.RADIO_SERVICE_NAME) ?: return null,
                genre = radioString(MediaBridgeContract.Key.RADIO_GENRE) ?: return null,
                iconId = getInt(MediaBridgeContract.Key.RADIO_ICON_ID),
                signalQuality = getInt(MediaBridgeContract.Key.RADIO_SIGNAL_QUALITY),
                selector = radioString(MediaBridgeContract.Key.RADIO_SELECTOR) ?: return null,
            )
            MediaCommandRequest(
                requestId = rawRequestId,
                command = command,
                autoplay = getBoolean(MediaBridgeContract.Key.COMMAND_AUTOPLAY, true),
                radioStation = target,
            )
        }

        else -> MediaCommandRequest(rawRequestId, command)
    }
}

fun RadioStationLists.toBundle(): Bundle = Bundle().apply {
    putInt(MediaBridgeContract.Key.PROTOCOL_VERSION, MediaBridgeContract.PROTOCOL_VERSION)
    putParcelableArrayList(
        MediaBridgeContract.Key.RADIO_SAVED_STATIONS,
        saved.toSupportedRadioBundles(),
    )
    putParcelableArrayList(
        MediaBridgeContract.Key.RADIO_FAVORITE_STATIONS,
        favorites.toSupportedRadioBundles(),
    )
}

fun RadioStationSnapshot.toBundle(): Bundle = Bundle().apply {
    putString(MediaBridgeContract.Key.RADIO_STATION_ID, id)
    putInt(MediaBridgeContract.Key.RADIO_FREQUENCY_KHZ, frequencyKHz)
    putString(MediaBridgeContract.Key.RADIO_FORMATTED_FREQUENCY, formattedFrequency)
    putInt(MediaBridgeContract.Key.RADIO_BAND, band)
    putString(MediaBridgeContract.Key.RADIO_BAND_NAME, bandName)
    putString(MediaBridgeContract.Key.RADIO_NAME, name)
    putString(MediaBridgeContract.Key.RADIO_ENSEMBLE_NAME, ensembleName)
    putString(MediaBridgeContract.Key.RADIO_SERVICE_NAME, serviceName)
    putString(MediaBridgeContract.Key.RADIO_GENRE, genre)
    putInt(MediaBridgeContract.Key.RADIO_ICON_ID, iconId)
    putInt(MediaBridgeContract.Key.RADIO_SIGNAL_QUALITY, signalQuality)
    putString(MediaBridgeContract.Key.RADIO_SELECTOR, selector)
    putBoolean(MediaBridgeContract.Key.RADIO_FAVORITE, favorite)
    putString(MediaBridgeContract.Key.RADIO_ARTWORK_URI, artworkUri)
}

private fun Bundle.radioString(key: String): String? {
    val value = getString(key).orEmpty().trim()
    return value.takeIf { it.length <= MediaBridgeContract.MAX_RADIO_METADATA_LENGTH }
}

private fun List<RadioStationSnapshot>.toSupportedRadioBundles(): ArrayList<Bundle> =
    filter { isSupportedRadioFrequency(it.frequencyKHz, it.band) }
        .mapTo(ArrayList(), RadioStationSnapshot::toBundle)
