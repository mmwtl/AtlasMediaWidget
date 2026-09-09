package com.mmwtl.atlasmediaapi.media.cluster;

import android.net.Uri;

import com.ecarx.xui.adaptapi.diminteraction.IMediaInteraction;

/**
 * App-owned implementation of the public AdaptAPI playback contract.
 *
 * This class deliberately has a unique package/name and implements only the
 * public IMediaInteraction interface. OneOS loads com.ecarx.xui classes from
 * /system/framework/ecarx.adaptapi.jar before classes bundled in an APK, so an
 * app must not instantiate or extend MediaInteraction's package-private
 * AdaptPlayInfo implementation.
 */
public final class ClusterRadioPlaybackInfo implements IMediaInteraction.IPlaybackInfo {
    private final String uuid;
    private final int sourceType;
    private final String radioFrequency;
    private final String radioStationName;
    private final String title;
    private final String artist;
    private final String album;
    private final long duration;
    private final int playbackStatus;
    private final int radioMode;
    private final Uri artwork;

    public ClusterRadioPlaybackInfo(
            String uuid,
            int sourceType,
            String radioFrequency,
            String radioStationName,
            String title,
            String artist,
            String album,
            long duration,
            int playbackStatus,
            int radioMode,
            Uri artwork) {
        this.uuid = uuid;
        this.sourceType = sourceType;
        this.radioFrequency = radioFrequency;
        this.radioStationName = radioStationName;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.playbackStatus = playbackStatus;
        this.radioMode = radioMode;
        this.artwork = artwork;
    }

    @Override public String getUUID() { return uuid; }
    @Override public String getTitle() { return title; }
    @Override public String getArtist() { return artist; }
    @Override public String getAlbum() { return album; }
    @Override public String getRadioFrequency() { return radioFrequency; }
    @Override public String getRadioStationName() { return radioStationName; }
    @Override public long getDuration() { return duration; }
    @Override public int getPlayingItemPositionInQueue() { return 0; }
    @Override public int getSourceType() { return sourceType; }
    @Override public Uri getMediaPath() { return null; }
    @Override public int getPlaybackStatus() { return playbackStatus; }
    @Override public Uri getLyric() { return null; }
    @Override public String getLyricContent() { return ""; }
    @Override public String getCurrentLyricSentence() { return ""; }
    @Override public Uri getPreviousArtwork() { return null; }
    @Override public Uri getArtwork() { return artwork; }
    @Override public Uri getNextArtwork() { return null; }
    @Override public int getLoopMode() { return IMediaInteraction.IPlaybackInfo.LOOP_MODE_ALL; }
    @Override public int getRadioMode() { return radioMode; }
    @Override public int getFavoriteState() { return 0; }
}
