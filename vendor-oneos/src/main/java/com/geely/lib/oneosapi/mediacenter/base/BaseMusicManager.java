package com.geely.lib.oneosapi.mediacenter.base;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.util.Log;

import com.geely.lib.oneosapi.mediacenter.IMusicManager;
import com.geely.lib.oneosapi.mediacenter.MediaCenterManager;
import com.geely.lib.oneosapi.mediacenter.bean.DeviceInfo;
import com.geely.lib.oneosapi.mediacenter.bean.MediaData;
import com.geely.lib.oneosapi.mediacenter.bean.MusicFileData;
import com.geely.lib.oneosapi.mediacenter.bean.OnlineUserInfo;
import com.geely.lib.oneosapi.mediacenter.bean.SearchResult;
import com.geely.lib.oneosapi.mediacenter.callback.MusicListCallback;
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant;
import com.geely.lib.oneosapi.mediacenter.listener.DeviceStateListener;
import com.geely.lib.oneosapi.mediacenter.listener.IDeviceStateListener;
import com.geely.lib.oneosapi.mediacenter.listener.IMediaStateListener;
import com.geely.lib.oneosapi.mediacenter.listener.IMusicStateListener;
import com.geely.lib.oneosapi.mediacenter.listener.MediaStateListener;
import com.geely.lib.oneosapi.mediacenter.listener.MusicStateListener;
import com.geely.lib.oneosapi.mediacenter.listener.QueryUsbMediaListener;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/* loaded from: classes.dex */
public abstract class BaseMusicManager {
    protected Context mContext;
    protected final MediaCenterManager mMediaCenterManager;
    protected IMusicManager mService;
    protected final CopyOnWriteArrayList<DeviceStateListener> mDeviceStateListeners = new CopyOnWriteArrayList<>();
    protected final CopyOnWriteArrayList<MusicStateListener> mMusicStateListeners = new CopyOnWriteArrayList<>();
    protected final CopyOnWriteArrayList<MediaStateListener> mMediaStateListeners = new CopyOnWriteArrayList<>();
    protected final CopyOnWriteArrayList<QueryUsbMediaListener> mQueryUsbMediaListener = new CopyOnWriteArrayList<>();
    private final DeviceStateListenerImpl mDeviceStateListenerImpl = new DeviceStateListenerImpl();
    private final MusicStateListenerImpl mMusicStateListenerImpl = new MusicStateListenerImpl(this);
    private final MusicStateListenerImpl mPsdMusicStateListenerImpl = new MusicStateListenerImpl(true);
    private final MediaStateListenerImpl mMediaStateListenerImpl = new MediaStateListenerImpl(this);
    private final MediaStateListenerImpl mPsdMediaStateListenerImpl = new MediaStateListenerImpl(true);
    private final String mTag = getClass().getSimpleName();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public abstract int getAudioSource();

    public BaseMusicManager(Context context, IMusicManager service, MediaCenterManager mediaCenterManager) {
        this.mContext = context;
        initService(service);
        this.mMediaCenterManager = mediaCenterManager;
    }

    public void setService(IMusicManager service) {
        initService(service);
    }

    public boolean isAlive() {
        IMusicManager iMusicManager = this.mService;
        return iMusicManager != null && iMusicManager.asBinder().isBinderAlive();
    }

    protected void initService(IMusicManager service) {
        this.mService = service;
        if (service != null) {
            try {
                service.addDeviceStateListener(getAudioSource(), this.mDeviceStateListenerImpl);
                this.mService.addMusicStateListener(getAudioSource(), this.mMusicStateListenerImpl);
                this.mService.addPsdMusicStateListener(getAudioSource(), this.mPsdMusicStateListenerImpl);
                this.mService.addMediaStateListener(getAudioSource(), this.mMediaStateListenerImpl);
                this.mService.addPsdMediaStateListener(getAudioSource(), this.mPsdMediaStateListenerImpl);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Deprecated
    public void addMusicStateListener(MusicStateListener listener) {
        if (listener == null || this.mMusicStateListeners.contains(listener)) {
            return;
        }
        this.mMusicStateListeners.add(listener);
    }

    public void addMediaStateListener(MediaStateListener listener) {
        if (listener == null || this.mMediaStateListeners.contains(listener)) {
            return;
        }
        this.mMediaStateListeners.add(listener);
    }

    public void addDeviceStateListener(DeviceStateListener listener) {
        if (listener == null || this.mDeviceStateListeners.contains(listener)) {
            return;
        }
        this.mDeviceStateListeners.add(listener);
    }

    @Deprecated
    public void removeMusicStateListener(MusicStateListener listener) {
        if (listener != null) {
            this.mMusicStateListeners.remove(listener);
        }
    }

    public void removeMediaStateListener(MediaStateListener listener) {
        if (listener != null) {
            this.mMediaStateListeners.remove(listener);
        }
    }

    public void removeDeviceStateListener(DeviceStateListener listener) {
        if (listener != null) {
            this.mDeviceStateListeners.remove(listener);
        }
    }

    protected void addUsbMediaQueryListener(QueryUsbMediaListener listener) {
        if (listener == null || this.mQueryUsbMediaListener.contains(listener)) {
            return;
        }
        this.mQueryUsbMediaListener.add(listener);
    }

    public void removeUsbMediaQueryListener(QueryUsbMediaListener listener) {
        if (listener != null) {
            this.mQueryUsbMediaListener.remove(listener);
        }
    }

    public void playItem(MediaData mediaData) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.playItem(getAudioSource(), mediaData);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "playItem: mService is null");
    }

    public void play() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.play(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "play: mService is null");
    }

    public void pause() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.pause(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "pause: mService is null");
    }

    public void prev() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.prev(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "prev: mService is null");
    }

    @Deprecated
    public void pre() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.prev(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "prev: mService is null");
    }

    public void next() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.next(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "next: mService is null");
    }

    public void sendHidEventOverIap() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.sendHidEventOverIap(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "next: mService is null");
    }

    public void setPlaybackShuffleMode() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.setPlaybackShuffleMode(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "next: mService is null");
    }

    public void setPlaybackRepeatMode() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.setPlaybackRepeatMode(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "next: mService is null");
    }

    public void seekTo(long time) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.seekTo(getAudioSource(), time);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "seekTo: mService is null");
    }

    public void fastForward(long time) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.fastForward(getAudioSource(), time);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "fastForward: mService is null");
    }

    public void fastRewind(long time) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.fastRewind(getAudioSource(), time);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "fastRewind: mService is null");
    }

    @Deprecated
    public List<MediaData> getPlayList() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                return iMusicManager.getPlayList(getAudioSource());
            } catch (RemoteException e) {
                e.printStackTrace();
                return null;
            }
        }
        Log.w(this.mTag, "getPlayList: mService is null");
        return null;
    }

    public void getPlayListAsync(MusicListCallback callback) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.getPlayListAsync(getAudioSource(), callback.getCallbackImpl());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "getPlayListAsync: mService is null");
    }

    public void addFavor(MediaData mediaData) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.addFavor(getAudioSource(), mediaData);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "addFavor: mService is null");
    }

    public void cancelFavor(MediaData mediaData) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.cancelFavor(getAudioSource(), mediaData);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "cancelFavor: mService is null");
    }

    public List<MediaData> getFavorList() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                return iMusicManager.getFavorList(getAudioSource());
            } catch (RemoteException e) {
                e.printStackTrace();
                return null;
            }
        }
        Log.w(this.mTag, "getFavorList: mService is null");
        return null;
    }

    public List<DeviceInfo> getDevicesInfo() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                return iMusicManager.getDevicesInfo(getAudioSource());
            } catch (RemoteException e) {
                e.printStackTrace();
                return null;
            }
        }
        Log.w(this.mTag, "getDevicesInfo: mService is null");
        return null;
    }

    public void startApp() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.startApp(getAudioSource());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "startApp: mService is null");
    }

    public void setPlayMode(MediaCenterConstant.PlayMode mode) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.setPlayMode(getAudioSource(), mode.ordinal());
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "setPlayMode: mService is null");
    }

    public MediaCenterConstant.PlayMode getPlayMode() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                return MediaCenterConstant.getPlayModeEnum(iMusicManager.getPlayMode(getAudioSource()));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Log.w(this.mTag, "getPlayMode: mService is null");
        }
        return MediaCenterConstant.PlayMode.PLAY_MODE_QUEUE;
    }

    public MediaData getCurrentMediaData() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                return iMusicManager.getCurrentMediaData(getAudioSource());
            } catch (RemoteException e) {
                e.printStackTrace();
                return null;
            }
        }
        Log.w(this.mTag, "getCurrentMediaData: mService is null");
        return null;
    }

    public MediaCenterConstant.PlayState getCurrentPlayState() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                return MediaCenterConstant.getPlayStateEnum(iMusicManager.getCurrentPlayState(getAudioSource()));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Log.w(this.mTag, "getCurrentPlayState: mService is null");
        }
        return MediaCenterConstant.PlayState.MUSIC_STATE_STOP;
    }

    public long getCurrentPosition() {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                return iMusicManager.getCurrentPosition(getAudioSource());
            } catch (RemoteException e) {
                e.printStackTrace();
                return 0L;
            }
        }
        Log.w(this.mTag, "getCurrentPosition: mService is null");
        return 0L;
    }

    public MediaCenterConstant.AudioSource getCurrentAudioSource() {
        return this.mMediaCenterManager.getCurrentAudioSource();
    }

    public MediaCenterConstant.AppSource getCurrentAppSource() {
        return this.mMediaCenterManager.getCurrentAppSource();
    }

    public void sendCommand(String command, Bundle args, ResultReceiver cb) {
        sendCommand(MediaCenterConstant.AppSource.UNKNOWN, command, args, cb);
    }

    public void sendCommand(MediaCenterConstant.AppSource appSource, String command, Bundle args, ResultReceiver cb) {
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                iMusicManager.sendCommand(getAudioSource(), appSource.ordinal(), command, args, cb);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
                return;
            }
        }
        Log.w(this.mTag, "sendCommand: mService is null");
    }

    public Bundle getCurrentMediaBundle(MediaCenterConstant.AppSource appSource, String key, Bundle args, ResultReceiver cb) {
        if (appSource == null) {
            appSource = MediaCenterConstant.AppSource.UNKNOWN;
        }
        IMusicManager iMusicManager = this.mService;
        if (iMusicManager != null) {
            try {
                return iMusicManager.getCurrentMediaBundle(getAudioSource(), appSource.ordinal(), key, args, cb);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Log.w(this.mTag, "getCurrentMediaBundle: mService is null");
        }
        return Bundle.EMPTY;
    }

    public Bundle getCurrentMediaBundle(String key, Bundle args, ResultReceiver cb) {
        return getCurrentMediaBundle(MediaCenterConstant.AppSource.UNKNOWN, key, args, cb);
    }

    public Bundle getCurrentMediaBundle(String key, ResultReceiver cb) {
        return getCurrentMediaBundle(MediaCenterConstant.AppSource.UNKNOWN, key, null, cb);
    }

    protected class DeviceStateListenerImpl extends IDeviceStateListener.Stub {
        @Override
        public void onDeviceStateChanged(int source, int state, DeviceInfo info) {
            mHandler.post(() -> mDeviceStateListeners.forEach(listener ->
                    listener.onDeviceStateChanged(
                            MediaCenterConstant.getAudioSourceEnum(source),
                            MediaCenterConstant.getDeviceStateEnum(state),
                            info)));
        }

        @Override
        public void onDeviceError(int source, int error, String errorMsg) {
            mHandler.post(() -> mDeviceStateListeners.forEach(listener ->
                    listener.onDeviceError(
                            MediaCenterConstant.getAudioSourceEnum(source), error, errorMsg)));
        }

        @Override
        public void onScanPathFinish(int source, List<MusicFileData> files) {
            mHandler.post(() -> mDeviceStateListeners.forEach(listener ->
                    listener.onScanPathFinish(MediaCenterConstant.getAudioSourceEnum(source), files)));
        }

        @Override
        public void onUserInfoResult(int source, int app, OnlineUserInfo userInfo) {
            mHandler.post(() -> mDeviceStateListeners.forEach(listener ->
                    listener.onUserInfoResult(
                            MediaCenterConstant.getAudioSourceEnum(source),
                            MediaCenterConstant.getAppSourceEnum(app),
                            userInfo)));
        }

        @Override
        public void onSearchSongResult(int source, int app, List<SearchResult> results) {
            mHandler.post(() -> mDeviceStateListeners.forEach(listener ->
                    listener.onSearchSongResult(
                            MediaCenterConstant.getAudioSourceEnum(source),
                            MediaCenterConstant.getAppSourceEnum(app),
                            results)));
        }

        @Override
        public void onBluetoothDeviceChange(int source, List<DeviceInfo> devices) {
            mHandler.post(() -> mDeviceStateListeners.forEach(listener ->
                    listener.onBluetoothDeviceChange(
                            MediaCenterConstant.getAudioSourceEnum(source), devices)));
        }

        @Override
        public void onAppExistStateChanged(int source, int app, boolean existed) {
            mHandler.post(() -> mDeviceStateListeners.forEach(listener ->
                    listener.onAppExistStateChanged(
                            MediaCenterConstant.getAudioSourceEnum(source),
                            MediaCenterConstant.getAppSourceEnum(app),
                            existed)));
        }

        @Override
        public void onAppDied(int app) {
            mHandler.post(() -> mDeviceStateListeners.forEach(listener ->
                    listener.onAppDied(MediaCenterConstant.getAppSourceEnum(app))));
        }

        @Override
        public void onMediaQueryStarted(int source, DeviceInfo info) {
            mHandler.post(() -> mQueryUsbMediaListener.forEach(listener ->
                    listener.onMediaQueryStarted(MediaCenterConstant.getAudioSourceEnum(source), info)));
        }

        @Override
        public void onMediaQueryFinished(int source, DeviceInfo info) {
            mHandler.post(() -> mQueryUsbMediaListener.forEach(listener ->
                    listener.onMediaQueryFinished(MediaCenterConstant.getAudioSourceEnum(source), info)));
        }
    }

    @Deprecated
    private class MusicStateListenerImpl extends IMusicStateListener.Stub {
        private final boolean isPsdCallback;

        public MusicStateListenerImpl(final BaseMusicManager ignored) {
            this(false);
        }

        public MusicStateListenerImpl(boolean isPsdCallback) {
            this.isPsdCallback = isPsdCallback;
        }

        private boolean shouldDispatch() {
            return this.isPsdCallback == BaseMusicManager.this.mMediaCenterManager.isPsdMode();
        }

        @Override
        public void onMediaDataChanged(int source, MediaData mediaData) {
            if (!shouldDispatch()) return;
            mHandler.post(() -> mMusicStateListeners.forEach(listener ->
                    listener.onMediaDataChanged(MediaCenterConstant.getAudioSourceEnum(source), mediaData)));
        }

        @Override
        public void onPlayPositionChanged(int source, long current, long total) {
            if (!shouldDispatch()) return;
            mHandler.post(() -> mMusicStateListeners.forEach(listener ->
                    listener.onPlayPositionChanged(
                            MediaCenterConstant.getAudioSourceEnum(source), current, total)));
        }

        @Override
        public void onPlayStateChanged(int source, int state) {
            if (!shouldDispatch()) return;
            mHandler.post(() -> mMusicStateListeners.forEach(listener ->
                    listener.onPlayStateChanged(
                            MediaCenterConstant.getAudioSourceEnum(source),
                            MediaCenterConstant.getPlayStateEnum(state))));
        }

        @Override
        public void onPlayListChanged(int source, List<MediaData> list) {
            if (!shouldDispatch()) return;
            mHandler.post(() -> mMusicStateListeners.forEach(listener ->
                    listener.onPlayListChanged(MediaCenterConstant.getAudioSourceEnum(source), list)));
        }

        @Override
        public void onFavorStateChanged(int source, MediaData mediaData) {
            if (!shouldDispatch()) return;
            mHandler.post(() -> mMusicStateListeners.forEach(listener ->
                    listener.onFavorStateChanged(MediaCenterConstant.getAudioSourceEnum(source), mediaData)));
        }

        @Override
        public void onLrcLoad(int source, String lrc, long time) {
            if (!shouldDispatch()) return;
            mHandler.post(() -> mMusicStateListeners.forEach(listener ->
                    listener.onLrcLoad(MediaCenterConstant.getAudioSourceEnum(source), lrc, time)));
        }

        @Override
        public void onPlayModeChange(int source, int mode) {
            if (!shouldDispatch()) return;
            mHandler.post(() -> mMusicStateListeners.forEach(listener ->
                    listener.onPlayModeChange(
                            MediaCenterConstant.getAudioSourceEnum(source),
                            MediaCenterConstant.getPlayModeEnum(mode))));
        }
    }

    private class MediaStateListenerImpl extends IMediaStateListener.Stub {
        private final boolean isPsdCallback;

        public MediaStateListenerImpl(final BaseMusicManager this$0) {
            this(false);
        }

        public MediaStateListenerImpl(boolean isPsdCallback) {
            this.isPsdCallback = isPsdCallback;
        }

        @Override // com.geely.lib.oneosapi.mediacenter.listener.IMediaStateListener
        public void onMediaDataChanged(int source, int app, MediaData mediaData) throws RemoteException {

        }

        @Override // com.geely.lib.oneosapi.mediacenter.listener.IMediaStateListener
        public void onPlayPositionChanged(int source, int app, long current, long total) throws RemoteException {

        }

        @Override // com.geely.lib.oneosapi.mediacenter.listener.IMediaStateListener
        public void onPlayStateChanged(int source, int app, int state) throws RemoteException {

        }

        @Override // com.geely.lib.oneosapi.mediacenter.listener.IMediaStateListener
        public void onPlayListChanged(int source, int app, List<MediaData> list) throws RemoteException {

        }

        @Override // com.geely.lib.oneosapi.mediacenter.listener.IMediaStateListener
        public void onFavorStateChanged(int source, int app, MediaData mediaData) throws RemoteException {

        }

        @Override // com.geely.lib.oneosapi.mediacenter.listener.IMediaStateListener
        public void onLrcLoad(int source, int app, String lrc, long time) throws RemoteException {

        }

        @Override // com.geely.lib.oneosapi.mediacenter.listener.IMediaStateListener
        public void onPlayModeChange(int source, int app, int mode) throws RemoteException {

        }
    }
}
