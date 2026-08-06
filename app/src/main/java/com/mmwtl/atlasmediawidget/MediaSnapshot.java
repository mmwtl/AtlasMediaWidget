package com.mmwtl.atlasmediawidget;

import android.os.Bundle;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MediaSnapshot {
    static final int STATE_PLAYING = 3;

    final int protocolVersion;
    final long generation;
    final long timestamp;
    final boolean backendConnected;
    final int backendErrorCode;
    final String backendErrorMessage;
    final MediaSource.Id audioSource;
    final String appSource;
    final List<MediaSource> sources;
    final String ownerPackage;
    final String ownerApp;
    final String mediaId;
    final String title;
    final String artist;
    final String album;
    final long duration;
    final long position;
    final long updateElapsedRealtime;
    final float speed;
    final int playbackState;
    final int playbackErrorCode;
    final String playbackErrorMessage;
    final long playbackActions;
    final long capabilities;
    final String artworkUri;
    final long artworkRevision;

    MediaSnapshot(int protocolVersion, long generation, long timestamp,
            boolean backendConnected, int backendErrorCode, String backendErrorMessage,
            MediaSource.Id audioSource, String appSource, List<MediaSource> sources,
            String ownerPackage, String ownerApp, String mediaId, String title, String artist,
            String album, long duration, long position, long updateElapsedRealtime, float speed,
            int playbackState, int playbackErrorCode, String playbackErrorMessage,
            long playbackActions, long capabilities, String artworkUri, long artworkRevision) {
        this.protocolVersion = protocolVersion;
        this.generation = generation;
        this.timestamp = timestamp;
        this.backendConnected = backendConnected;
        this.backendErrorCode = backendErrorCode;
        this.backendErrorMessage = nonNull(backendErrorMessage);
        this.audioSource = audioSource;
        this.appSource = nonNull(appSource);
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.ownerPackage = nonNull(ownerPackage);
        this.ownerApp = nonNull(ownerApp);
        this.mediaId = nonNull(mediaId);
        this.title = nonNull(title);
        this.artist = nonNull(artist);
        this.album = nonNull(album);
        this.duration = duration;
        this.position = position;
        this.updateElapsedRealtime = updateElapsedRealtime;
        this.speed = speed;
        this.playbackState = playbackState;
        this.playbackErrorCode = playbackErrorCode;
        this.playbackErrorMessage = nonNull(playbackErrorMessage);
        this.playbackActions = playbackActions;
        this.capabilities = capabilities;
        this.artworkUri = nonNull(artworkUri);
        this.artworkRevision = artworkRevision;
    }

    static MediaSnapshot fromBundle(Bundle bundle) {
        int version = bundle.getInt(MediaBridgeContract.K_VERSION, -1);
        if (version != MediaBridgeContract.VERSION) {
            throw new IllegalArgumentException("Unsupported snapshot protocolVersion=" + version);
        }
        ArrayList<Bundle> rawSources = Build.VERSION.SDK_INT >= 33
                ? bundle.getParcelableArrayList(MediaBridgeContract.K_SOURCES, Bundle.class)
                : legacySources(bundle);
        List<MediaSource> sources = new ArrayList<>();
        if (rawSources != null) {
            for (Bundle source : rawSources) {
                if (source != null) sources.add(MediaSource.fromBundle(source));
            }
        }
        return new MediaSnapshot(
                version,
                bundle.getLong(MediaBridgeContract.K_GENERATION),
                bundle.getLong(MediaBridgeContract.K_TIMESTAMP),
                bundle.getBoolean(MediaBridgeContract.K_BACKEND_CONNECTED),
                bundle.getInt(MediaBridgeContract.K_BACKEND_ERROR_CODE),
                bundle.getString(MediaBridgeContract.K_BACKEND_ERROR_MESSAGE),
                MediaSource.Id.fromWire(bundle.getString(MediaBridgeContract.K_AUDIO_SOURCE)),
                bundle.getString(MediaBridgeContract.K_APP_SOURCE),
                sources,
                bundle.getString(MediaBridgeContract.K_OWNER_PACKAGE),
                bundle.getString(MediaBridgeContract.K_OWNER_APP),
                bundle.getString(MediaBridgeContract.K_MEDIA_ID),
                bundle.getString(MediaBridgeContract.K_TITLE),
                bundle.getString(MediaBridgeContract.K_ARTIST),
                bundle.getString(MediaBridgeContract.K_ALBUM),
                bundle.getLong(MediaBridgeContract.K_DURATION, -1L),
                bundle.getLong(MediaBridgeContract.K_POSITION, -1L),
                bundle.getLong(MediaBridgeContract.K_UPDATE_ELAPSED),
                bundle.getFloat(MediaBridgeContract.K_SPEED),
                bundle.getInt(MediaBridgeContract.K_PLAYBACK_STATE),
                bundle.getInt(MediaBridgeContract.K_PLAYBACK_ERROR_CODE),
                bundle.getString(MediaBridgeContract.K_PLAYBACK_ERROR_MESSAGE),
                bundle.getLong(MediaBridgeContract.K_PLAYBACK_ACTIONS),
                bundle.getLong(MediaBridgeContract.K_CAPABILITIES),
                bundle.getString(MediaBridgeContract.K_ARTWORK_URI),
                bundle.getLong(MediaBridgeContract.K_ARTWORK_REVISION)
        );
    }

    boolean isPlaying() {
        return playbackState == STATE_PLAYING;
    }

    boolean supports(long capability) {
        return (capabilities & capability) != 0L;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<Bundle> legacySources(Bundle bundle) {
        return bundle.getParcelableArrayList(MediaBridgeContract.K_SOURCES);
    }
}
