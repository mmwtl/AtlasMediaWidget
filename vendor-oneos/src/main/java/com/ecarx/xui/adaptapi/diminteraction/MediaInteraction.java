package com.ecarx.xui.adaptapi.diminteraction;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.ecarx.xui.adaptapi.AbsCarSignal;
import ecarx.fw.api.ECarXAPI;
import ecarx.fw.api.diminteraction.EcarxMediaInteraction;

import java.util.List;

public class MediaInteraction extends AbsCarSignal implements IMediaInteraction {
    private static final String TAG = "AtlasDimMedia";

    private final Context appContext;
    private volatile EcarxMediaInteraction mMediaDIMInteractionManager;
    private volatile Throwable initializationError;

    public MediaInteraction(Context context) {
        super(context);
        Context applicationContext = context.getApplicationContext();
        this.appContext = applicationContext != null ? applicationContext : context;
    }

    /**
     * The DIM service may not be ready when the application process starts, so
     * creation is retried on demand. Failures are retained for diagnostics
     * instead of being silently swallowed.
     */
    public synchronized boolean ensureAvailable() {
        if (mMediaDIMInteractionManager != null) {
            return true;
        }
        try {
            this.mMediaDIMInteractionManager =
                    ECarXAPI.creator(EcarxMediaInteraction.class).create(appContext);
            this.initializationError = null;
            Log.i(TAG, "EcarxMediaInteraction created");
            return true;
        } catch (Throwable error) {
            this.initializationError = error;
            Log.e(TAG, "EcarxMediaInteraction creation failed", error);
            return false;
        }
    }

    public boolean isAvailable() {
        return mMediaDIMInteractionManager != null;
    }

    public Throwable getInitializationError() {
        return initializationError;
    }

    private EcarxMediaInteraction requireManager() {
        if (!ensureAvailable()) {
            throw new IllegalStateException(
                    "EcarxMediaInteraction is unavailable", initializationError);
        }
        return mMediaDIMInteractionManager;
    }

    @Override
    public void updateMediaSourceTypeList(int[] sourceTypes) {
        requireManager().updateMediaSourceTypeList(sourceTypes);
    }

    @Override
    public void updateCurrentSourceType(int sourceType) {
        requireManager().updateCurrentSourceType(sourceType);
    }

    @Override
    public void updatePlaylist(int sourceType, List<IMedia> list) {
    }

    @Override
    public void updatePlaybackInfo(IPlaybackInfo iPlaybackInfo) {
        if (iPlaybackInfo == null) {
            throw new IllegalArgumentException("playbackInfo == null");
        }
        AdaptPlayInfo adaptPlayInfo = new AdaptPlayInfo();
        adaptPlayInfo.setUUID(iPlaybackInfo.getUUID());
        adaptPlayInfo.setTitle(iPlaybackInfo.getTitle());
        adaptPlayInfo.setArtist(iPlaybackInfo.getArtist());
        adaptPlayInfo.setAlbum(iPlaybackInfo.getAlbum());
        adaptPlayInfo.setRadioFrequency(iPlaybackInfo.getRadioFrequency());
        adaptPlayInfo.setRadioStationName(iPlaybackInfo.getRadioStationName());
        adaptPlayInfo.setDuration(iPlaybackInfo.getDuration());
        adaptPlayInfo.setPlayingItemPositionInQueue(iPlaybackInfo.getPlayingItemPositionInQueue());
        adaptPlayInfo.setSourceType(iPlaybackInfo.getSourceType());
        adaptPlayInfo.setMediaPath(iPlaybackInfo.getMediaPath());
        adaptPlayInfo.setPlaybackStatus(iPlaybackInfo.getPlaybackStatus());
        adaptPlayInfo.setLyricContent(iPlaybackInfo.getLyricContent());
        adaptPlayInfo.setLyric(iPlaybackInfo.getLyric());
        adaptPlayInfo.setCurrentLyricSentence(iPlaybackInfo.getCurrentLyricSentence());
        adaptPlayInfo.setPreviousArtwork(iPlaybackInfo.getPreviousArtwork());
        adaptPlayInfo.setArtwork(iPlaybackInfo.getArtwork());
        adaptPlayInfo.setNextArtwork(iPlaybackInfo.getNextArtwork());
        adaptPlayInfo.setLoopMode(iPlaybackInfo.getLoopMode());
        adaptPlayInfo.setRadioMode(iPlaybackInfo.getRadioMode());
        adaptPlayInfo.setFavoriteState(iPlaybackInfo.getFavoriteState());
        requireManager().updatePlaybackInfo(adaptPlayInfo);
    }

    @Override
    public void updateCurrentProgress(long progress) {
        requireManager().updateCurrentProgress(progress);
    }

    @Override
    public void updateMediaMuteState(int muteState) {
        requireManager().updateMediaMuteState(muteState);
    }

    @Override
    public void setMediaInteractionCallback(IMediaInteractionCallback callback) {
    }

    @Override
    public void unsetMediaInteractionCallback(IMediaInteractionCallback callback) {
    }

    @Override
    public void updateExtensionInfo(Bundle bundle) {
    }

    static class AdaptPlayInfo implements IMediaInteraction.IPlaybackInfo, EcarxMediaInteraction.EcarxPlaybackInfo {
        private String mAlbumName = "";
        private String mArtistName = "";
        private Uri mArtwork;
        private String mCurrentLyricSentence = "";
        private long mDuration = 0L;
        private int mFavoriteState = 0;
        private int mLoopMode = 0;
        private Uri mLyric;
        private String mLyricContent = "";
        private Uri mMediaPath;
        private Uri mNextArtwork;
        private int mPlaybackStatus = 0;
        private int mPlayingItemPositionInQueue = 0;
        private Uri mPreviousArtwork;
        private String mRadioFrequency = "";
        private int mRadioMode = 0;
        private String mRadioStationName = "";
        private int mSourceType = 0;
        private String mTitle = "";
        private String mUUid = "";

        AdaptPlayInfo() {
        }

        @Override public void setUUID(String str) { this.mUUid = str; }
        @Override public String getUUID() { return this.mUUid; }

        @Override public void setTitle(String str) { this.mTitle = str; }
        @Override public String getTitle() { return this.mTitle; }

        @Override public void setArtist(String str) { this.mArtistName = str; }
        @Override public String getArtist() { return this.mArtistName; }

        @Override public void setAlbum(String str) { this.mAlbumName = str; }
        @Override public String getAlbum() { return this.mAlbumName; }

        @Override public void setRadioFrequency(String str) { this.mRadioFrequency = str; }
        @Override public String getRadioFrequency() { return this.mRadioFrequency; }

        @Override public void setRadioStationName(String str) { this.mRadioStationName = str; }
        @Override public String getRadioStationName() { return this.mRadioStationName; }

        @Override public void setDuration(long j) { this.mDuration = j; }
        @Override public long getDuration() { return this.mDuration; }

        @Override public void setPlayingItemPositionInQueue(int i) { this.mPlayingItemPositionInQueue = i; }
        @Override public int getPlayingItemPositionInQueue() { return this.mPlayingItemPositionInQueue; }

        @Override public void setSourceType(int i) { this.mSourceType = i; }
        @Override public int getSourceType() { return this.mSourceType; }

        @Override public void setMediaPath(Uri uri) { this.mMediaPath = uri; }
        @Override public Uri getMediaPath() { return this.mMediaPath; }

        @Override public void setPlaybackStatus(int i) { this.mPlaybackStatus = i; }
        @Override public int getPlaybackStatus() { return this.mPlaybackStatus; }

        @Override public void setLyric(Uri uri) { this.mLyric = uri; }
        @Override public Uri getLyric() { return this.mLyric; }

        @Override public void setLyricContent(String str) { this.mLyricContent = str; }
        @Override public String getLyricContent() { return this.mLyricContent; }

        @Override public void setCurrentLyricSentence(String str) { this.mCurrentLyricSentence = str; }
        @Override public String getCurrentLyricSentence() { return this.mCurrentLyricSentence; }

        @Override public void setPreviousArtwork(Uri uri) { this.mPreviousArtwork = uri; }
        @Override public Uri getPreviousArtwork() { return this.mPreviousArtwork; }

        @Override public void setArtwork(Uri uri) { this.mArtwork = uri; }
        @Override public Uri getArtwork() { return this.mArtwork; }

        @Override public void setNextArtwork(Uri uri) { this.mNextArtwork = uri; }
        @Override public Uri getNextArtwork() { return this.mNextArtwork; }

        @Override public void setLoopMode(int i) { this.mLoopMode = i; }
        @Override public int getLoopMode() { return this.mLoopMode; }

        @Override public void setRadioMode(int i) { this.mRadioMode = i; }
        @Override public int getRadioMode() { return this.mRadioMode; }

        @Override public void setFavoriteState(int i) { this.mFavoriteState = i; }
        @Override public int getFavoriteState() { return this.mFavoriteState; }
    }
}
