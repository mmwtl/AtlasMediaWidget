package com.mmwtl.atlasmediawidget;

final class MediaBridgeContract {
    static final String SERVICE_ACTION = "com.mmwtl.atlasmediaapi.media.BIND";
    static final String SERVICE_PACKAGE = BuildConfig.MEDIA_BRIDGE_PACKAGE;
    static final String SERVICE_CLASS = "com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService";
    static final int VERSION = 1;

    static final int REGISTER = 1;
    static final int UNREGISTER = 2;
    static final int GET_SNAPSHOT = 3;
    static final int COMMAND = 4;
    static final int GET_RADIO_STATIONS = 5;
    static final int REGISTERED = 100;
    static final int SNAPSHOT = 101;
    static final int COMMAND_RESULT = 102;
    static final int ERROR = 103;
    static final int RADIO_STATIONS = 104;

    static final long CAP_PLAY = 0x01L;
    static final long CAP_PAUSE = 0x02L;
    static final long CAP_TOGGLE = 0x04L;
    static final long CAP_NEXT = 0x08L;
    static final long CAP_PREVIOUS = 0x10L;
    static final long CAP_SEEK = 0x20L;
    static final long CAP_SET_SOURCE = 0x40L;
    static final long CAP_TUNE_RADIO = 0x80L;

    static final int STATUS_OK = 0;
    static final int STATUS_UNSUPPORTED_VERSION = 2;

    static final String K_VERSION = "protocolVersion";
    static final String K_MIN_VERSION = "minProtocolVersion";
    static final String K_MAX_VERSION = "maxProtocolVersion";
    static final String K_REQUEST_ID = "requestId";
    static final String K_STATUS = "status";
    static final String K_MESSAGE = "message";
    static final String K_GENERATION = "generation";
    static final String K_TIMESTAMP = "timestamp";
    static final String K_BACKEND_CONNECTED = "backendConnected";
    static final String K_BACKEND_ERROR_CODE = "backendErrorCode";
    static final String K_BACKEND_ERROR_MESSAGE = "backendErrorMessage";
    static final String K_AUDIO_SOURCE = "audioSource";
    static final String K_APP_SOURCE = "appSource";
    static final String K_SOURCES = "sources";
    static final String K_SOURCE_ID = "id";
    static final String K_SOURCE_CONNECTED = "connected";
    static final String K_SOURCE_AVAILABLE = "available";
    static final String K_SOURCE_SELECTED = "selected";
    static final String K_SOURCE_CAPABILITIES = "capabilities";
    static final String K_OWNER_PACKAGE = "ownerPackage";
    static final String K_OWNER_APP = "ownerApp";
    static final String K_MEDIA_ID = "mediaId";
    static final String K_TITLE = "title";
    static final String K_ARTIST = "artist";
    static final String K_ALBUM = "album";
    static final String K_DURATION = "duration";
    static final String K_POSITION = "position";
    static final String K_UPDATE_ELAPSED = "updateElapsedRealtime";
    static final String K_SPEED = "speed";
    static final String K_PLAYBACK_STATE = "playbackState";
    static final String K_PLAYBACK_ERROR_CODE = "playbackErrorCode";
    static final String K_PLAYBACK_ERROR_MESSAGE = "playbackErrorMessage";
    static final String K_PLAYBACK_ACTIONS = "playbackActions";
    static final String K_CAPABILITIES = "capabilities";
    static final String K_ARTWORK_URI = "artworkUri";
    static final String K_ARTWORK_REVISION = "artworkRevision";
    static final String K_RADIO_SAVED_STATIONS = "radioSavedStations";
    static final String K_RADIO_FAVORITE_STATIONS = "radioFavoriteStations";
    static final String K_RADIO_STATION_ID = "radioStationId";
    static final String K_RADIO_FREQUENCY_KHZ = "radioFrequencyKHz";
    static final String K_RADIO_FORMATTED_FREQUENCY = "radioFormattedFrequency";
    static final String K_RADIO_BAND = "radioBand";
    static final String K_RADIO_BAND_NAME = "radioBandName";
    static final String K_RADIO_NAME = "radioName";
    static final String K_RADIO_ENSEMBLE_NAME = "radioEnsembleName";
    static final String K_RADIO_SERVICE_NAME = "radioServiceName";
    static final String K_RADIO_GENRE = "radioGenre";
    static final String K_RADIO_ICON_ID = "radioIconId";
    static final String K_RADIO_SIGNAL_QUALITY = "radioSignalQuality";
    static final String K_RADIO_SELECTOR = "radioSelector";
    static final String K_RADIO_FAVORITE = "radioFavorite";
    static final String K_RADIO_ARTWORK_URI = "radioArtworkUri";
    static final String K_COMMAND = "command";
    static final String K_COMMAND_POSITION = "position";
    static final String K_COMMAND_SOURCE = "source";
    static final String K_COMMAND_APP_SOURCE = "appSource";
    static final String K_COMMAND_AUTOPLAY = "autoplay";
    static final String K_UI_SCALE_TENTHS = "uiScaleTenths";

    private MediaBridgeContract() {}
}
