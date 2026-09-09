package com.autolink.carplay.common.data.iap;

import android.os.Parcel;
import android.os.Parcelable;

public class NowPlayingInfo implements Parcelable {
    private String mMediaItemTitle;
    private String mMediaItemArtist;
    private long mMediaItemPlaybackDurationMs;
    private int mPlaybackStatus;
    private long mPlaybackElapsedTimeMs;
    private int mPlaybackShuffleMode;
    private int mPlaybackRepeatMode;

    public NowPlayingInfo() {
    }

    protected NowPlayingInfo(Parcel in) {
        this.mMediaItemTitle = in.readString();
        this.mMediaItemArtist = in.readString();
        this.mMediaItemPlaybackDurationMs = in.readLong();
        this.mPlaybackStatus = in.readInt();
        this.mPlaybackElapsedTimeMs = in.readLong();
        this.mPlaybackShuffleMode = in.readInt();
        this.mPlaybackRepeatMode = in.readInt();
    }

    public static final Creator<NowPlayingInfo> CREATOR = new Creator<NowPlayingInfo>() {
        @Override
        public NowPlayingInfo createFromParcel(Parcel in) {
            return new NowPlayingInfo(in);
        }

        @Override
        public NowPlayingInfo[] newArray(int size) {
            return new NowPlayingInfo[size];
        }
    };

    public String getMediaItemTitle() {
        return this.mMediaItemTitle;
    }

    public void setMediaItemTitle(String title) {
        this.mMediaItemTitle = title;
    }

    public String getMediaItemArtist() {
        return this.mMediaItemArtist;
    }

    public void setMediaItemArtist(String artist) {
        this.mMediaItemArtist = artist;
    }

    public long getMediaItemPlaybackDurationMs() {
        return this.mMediaItemPlaybackDurationMs;
    }

    public void setMediaItemPlaybackDurationMs(long durationMs) {
        this.mMediaItemPlaybackDurationMs = durationMs;
    }

    public int getPlaybackStatus() {
        return this.mPlaybackStatus;
    }

    public void setPlaybackStatus(int status) {
        this.mPlaybackStatus = status;
    }

    public long getPlaybackElapsedTimeMs() {
        return this.mPlaybackElapsedTimeMs;
    }

    public void setPlaybackElapsedTimeMs(long elapsedTimeMs) {
        this.mPlaybackElapsedTimeMs = elapsedTimeMs;
    }

    public int getPlaybackShuffleMode() {
        return this.mPlaybackShuffleMode;
    }

    public void setPlaybackShuffleMode(int shuffleMode) {
        this.mPlaybackShuffleMode = shuffleMode;
    }

    public int getPlaybackRepeatMode() {
        return this.mPlaybackRepeatMode;
    }

    public void setPlaybackRepeatMode(int repeatMode) {
        this.mPlaybackRepeatMode = repeatMode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mMediaItemTitle);
        dest.writeString(this.mMediaItemArtist);
        dest.writeLong(this.mMediaItemPlaybackDurationMs);
        dest.writeInt(this.mPlaybackStatus);
        dest.writeLong(this.mPlaybackElapsedTimeMs);
        dest.writeInt(this.mPlaybackShuffleMode);
        dest.writeInt(this.mPlaybackRepeatMode);
    }

    @Override
    public String toString() {
        return "NowPlayingInfo{" +
                "mMediaItemTitle='" + mMediaItemTitle + '\'' +
                ", mMediaItemArtist='" + mMediaItemArtist + '\'' +
                ", mMediaItemPlaybackDurationMs=" + mMediaItemPlaybackDurationMs +
                ", mPlaybackStatus=" + mPlaybackStatus +
                ", mPlaybackElapsedTimeMs=" + mPlaybackElapsedTimeMs +
                ", mPlaybackShuffleMode=" + mPlaybackShuffleMode +
                ", mPlaybackRepeatMode=" + mPlaybackRepeatMode +
                '}';
    }
}
