package com.ecarx.xui.adaptapi.diminteraction;

import android.net.Uri;
import android.os.Bundle;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

public interface IMediaInteraction {
    int DATA_TYPE_GET_SOURCE_TYPE_LIST = 0;
    int DATA_TYPE_GET_CURRENT_SOURCE_TYPE = 1;
    int DATA_TYPE_GET_PLAYLIST = 2;
    int DATA_TYPE_GET_PLAYBACK_INFO = 3;
    int DATA_TYPE_GET_CURRENT_PROGRESS = 4;
    int DATA_TYPE_OP_NEXT = 5;
    int DATA_TYPE_OP_PREVIEW = 6;
    int DATA_TYPE_OP_PLAY = 7;
    int DATA_TYPE_OP_PAUSE = 8;
    int DATA_TYPE_OP_FAST_FORWARD = 9;
    int DATA_TYPE_OP_FAST_BACKWARD = 10;
    int DATA_TYPE_OP_STOP = 11;
    int DATA_TYPE_OP_SEEK_NEXT = 12;
    int DATA_TYPE_OP_SEEK_PREV = 13;
    int DATA_TYPE_OP_SEEK_STOP = 14;
    int DATA_TYPE_OP_MUTE = 15;
    int DATA_TYPE_OP_UNMUTE = 16;
    int DATA_TYPE_OP_ADD_FAVORITE = 17;
    int DATA_TYPE_OP_RM_FAVORITE = 18;
    int DATA_TYPE_OP_ADD_SUBSCRIPTION = 19;
    int DATA_TYPE_OP_RM_SUBSCRIPTION = 20;
    int DATA_TYPE_OP_RADIO_SCAN = 21;
    int DATA_TYPE_OP_LOOP_MODE_NEXT = 22;
    int DATA_TYPE_OP_LOOP_MODE_ALL = 23;
    int DATA_TYPE_OP_LOOP_MODE_SINGLE = 24;
    int DATA_TYPE_OP_LOOP_MODE_SHUFFLE = 25;

    int SOURCE_TYPE_LOCAL = 0;
    int SOURCE_TYPE_USB = 1;
    int SOURCE_TYPE_BT = 2;
    int SOURCE_TYPE_FM = 3;
    int SOURCE_TYPE_AM = 4;
    int SOURCE_TYPE_AUX = 5;
    int SOURCE_TYPE_ONLINE = 6;
    int SOURCE_TYPE_USB2 = 7;
    int SOURCE_TYPE_STATION = 8;
    int SOURCE_TYPE_NET_NEWS = 9;
    int SOURCE_TYPE_NET_VIDEO = 10;
    int SOURCE_TYPE_DAB = 11;

    interface IMedia {
        String getUUID();
        String getTitle();
        String getArtist();
        String getAlbum();
        String getRadioFrequency();
        String getRadioStationName();
        long getDuration();
        int getPlayingItemPositionInQueue();
        int getSourceType();
        Uri getMediaPath();
        Uri getLyric();
        String getLyricContent();
        Uri getArtwork();
        int getFavoriteState();
    }

    interface IMediaInteractionCallback {
        void onMediaHighlighted(IMedia iMedia);
        void onMediaSelected(IMedia iMedia);
        void onSourceSelected(int source);
        void onUpdateMediaStatusRequest(int status);
    }

    interface IPlaybackInfo {
        int LOOP_MODE_ALL = 0;
        int LOOP_MODE_SINGLE = 1;
        int LOOP_MODE_SHUFFLE = 2;

        int PLAYBACK_STATUS_PAUSED = 0;
        int PLAYBACK_STATUS_PLAYING = 1;

        int RADIO_MODE_PLAYING = 0;
        int RADIO_MODE_CAROUSEL = 1;
        int RADIO_MODE_SEEK_PREV = 2;
        int RADIO_MODE_SEEK_NEXT = 3;
        int RADIO_MODE_SCAN = 4;

        String getUUID();
        String getTitle();
        String getArtist();
        String getAlbum();
        String getRadioFrequency();
        String getRadioStationName();
        long getDuration();
        int getPlayingItemPositionInQueue();
        int getSourceType();
        Uri getMediaPath();
        int getPlaybackStatus();
        Uri getLyric();
        String getLyricContent();
        String getCurrentLyricSentence();
        Uri getPreviousArtwork();
        Uri getArtwork();
        Uri getNextArtwork();
        int getLoopMode();
        int getRadioMode();
        int getFavoriteState();
    }

    void updateMediaSourceTypeList(int[] sourceTypes);
    void updateCurrentSourceType(int sourceType);
    void updatePlaylist(int sourceType, List<IMedia> list);
    void updatePlaybackInfo(IPlaybackInfo iPlaybackInfo);
    void updateCurrentProgress(long progress);
    void updateMediaMuteState(int muteState);
    void setMediaInteractionCallback(IMediaInteractionCallback callback);
    void unsetMediaInteractionCallback(IMediaInteractionCallback callback);
    void updateExtensionInfo(Bundle bundle);
}
