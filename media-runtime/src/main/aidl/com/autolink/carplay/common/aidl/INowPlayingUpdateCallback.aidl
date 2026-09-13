package com.autolink.carplay.common.aidl;

import com.autolink.carplay.common.data.iap.NowPlayingInfo;
import com.autolink.carplay.common.data.iap.MediaCover;
import java.util.List;

interface INowPlayingUpdateCallback {
    void onNowPlayingUpdate(in NowPlayingInfo nowPlayingInfo);
    void onMediaCoverUpdate(in MediaCover mediaCover);
    void onPlaybackListUpdate(int mediaItemCount, int mediaItemStartIndex, in List mediaItemList);
}
