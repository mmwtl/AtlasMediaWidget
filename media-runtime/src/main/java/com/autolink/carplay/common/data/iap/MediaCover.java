package com.autolink.carplay.common.data.iap;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class MediaCover implements Parcelable {
    private String mMediaItemArtworkFilePath;
    private int mArtworkSize;
    private byte[] mArtworkData;
    private Bitmap bitmap;

    public MediaCover() {
    }

    protected MediaCover(Parcel in) {
        this.mMediaItemArtworkFilePath = in.readString();
        this.mArtworkSize = in.readInt();
        this.mArtworkData = in.createByteArray();
        this.bitmap = (Bitmap) in.readParcelable(Bitmap.class.getClassLoader());
    }

    public static final Creator<MediaCover> CREATOR = new Creator<MediaCover>() {
        @Override
        public MediaCover createFromParcel(Parcel in) {
            return new MediaCover(in);
        }

        @Override
        public MediaCover[] newArray(int size) {
            return new MediaCover[size];
        }
    };

    public String getMediaItemArtworkFilePath() {
        return this.mMediaItemArtworkFilePath;
    }

    public void setMediaItemArtworkFilePath(String filePath) {
        this.mMediaItemArtworkFilePath = filePath;
    }

    public int getArtworkSize() {
        return this.mArtworkSize;
    }

    public void setArtworkSize(int size) {
        this.mArtworkSize = size;
    }

    public byte[] getArtworkData() {
        return this.mArtworkData;
    }

    public void setArtworkData(byte[] data) {
        this.mArtworkData = data;
    }

    public Bitmap getBitmap() {
        return this.bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mMediaItemArtworkFilePath);
        dest.writeInt(this.mArtworkSize);
        dest.writeByteArray(this.mArtworkData);
        dest.writeParcelable(this.bitmap, flags);
    }

    @Override
    public String toString() {
        return "MediaCover{" +
                "mMediaItemArtworkFilePath='" + mMediaItemArtworkFilePath + '\'' +
                ", mArtworkSize=" + mArtworkSize +
                ", bitmapIsNull=" + (bitmap == null) +
                '}';
    }
}
