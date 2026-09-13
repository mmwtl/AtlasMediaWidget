package com.mmwtl.atlasmediawidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

final class MediaBridgeClient {
    enum State { STOPPED, CONNECTING, REGISTERING, CONNECTED, DISCONNECTED, INCOMPATIBLE }

    interface Listener {
        void onBridgeState(State state, String detail);
        void onSnapshot(MediaSnapshot snapshot);
        void onCommandResult(String requestId, int status, String message, long generation);
        void onRadioStations(RadioStationLists lists);
        void onRadioStationsError(int status, String message);
    }

    interface SettingsCallback {
        void onSettings(MediaSettingsSnapshot snapshot);
        void onError(int status, String message);
    }

    interface UpdateSettingsCallback {
        void onSettingsUpdated(MediaSettingsSnapshot snapshot);
        void onError(int status, String message);
    }

    interface BackupCallback {
        void onBackupExported();
        void onError(int status, String message);
    }

    interface RadioCatalogExportCallback {
        void onCatalogExported(int stationCount);
        void onError(int status, String message);
    }

    interface RadioCatalogImportCallback {
        void onCatalogImported(int stationCount);
        void onError(int status, String message);
    }

    interface PrepareImportCallback {
        void onImportPrepared(String stagingToken, String catalogMode, int stationCount, java.util.List<String> warnings);
        void onError(int status, String message);
    }

    interface CommitImportCallback {
        void onImportCommitted(MediaSettingsSnapshot snapshot);
        void onError(int status, String message);
    }

    interface ImportStatusCallback {
        void onStatus(String status);
        void onError(int status, String message);
    }

    interface RestoreCatalogCallback {
        void onCatalogRestored(MediaSettingsSnapshot snapshot);
        void onError(int status, String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private HandlerThread ipcThread;
    private final AtomicLong nextRequest = new AtomicLong();
    private final BridgeConnectionState connectionState = new BridgeConnectionState();
    private final java.util.Map<String, Object> pendingCallbacks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Runnable> pendingTimeouts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long REQUEST_TIMEOUT_MS = 15_000L;
    private volatile boolean settingsSupported;
    private Handler ipc;
    private Messenger incoming;
    private Messenger remote;
    private volatile boolean started;
    private long startedAt;
    private long bindStartedAt;
    private long registerStartedAt;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!started || !connectionState.onServiceConnected()) return;
            main.removeCallbacks(bindTimeout);
            registerStartedAt = SystemClock.elapsedRealtime();
            AppLog.info("Media Bridge service connected after "
                    + elapsedSince(bindStartedAt) + " ms (t+" + elapsedSince(startedAt) + " ms)");
            notifyState(State.REGISTERING, "");
            main.postDelayed(registerTimeout, ReconnectPolicy.REGISTER_TIMEOUT_MS);
            ipc.post(() -> {
                remote = new Messenger(service);
                sendRegister();
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            handleConnectionLoss("Atlas Media API service disconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            handleConnectionLoss("Atlas Media API binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            handleConnectionLoss("Atlas Media API returned a null binding");
        }
    };

    private final Runnable bindTimeout = () -> {
        if (connectionState.is(BridgeConnectionState.Phase.BINDING)) {
            handleConnectionLoss("Таймаут подключения к Atlas Media API");
        }
    };

    private final Runnable registerTimeout = () -> {
        if (connectionState.is(BridgeConnectionState.Phase.REGISTERING)) {
            handleConnectionLoss("Таймаут регистрации Atlas Media API");
        }
    };

    private final Runnable snapshotTimeout = () -> {
        if (connectionState.is(BridgeConnectionState.Phase.WAITING_SNAPSHOT)) {
            handleConnectionLoss("Таймаут первого snapshot Atlas Media API");
        }
    };

    MediaBridgeClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        main.post(() -> {
            if (started) return;
            started = true;
            startedAt = SystemClock.elapsedRealtime();
            HandlerThread thread = new HandlerThread("atlas-media-bridge");
            ipcThread = thread;
            thread.start();
            android.os.Looper looper = thread.getLooper();
            ipc = new Handler(looper);
            incoming = new Messenger(new Handler(looper, this::handleIncoming));
            bindNow();
        });
    }

    void stop() {
        main.post(() -> {
            if (!started) {
                settingsSupported = false;
                failPendingCallbacks(MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                        "Atlas Media API остановлен");
                return;
            }
            started = false;
            removeConnectionCallbacks();
            settingsSupported = false;
            failPendingCallbacks(MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                    "Atlas Media API остановлен");
            boolean hadBinding = connectionState.hasBinding();
            connectionState.onStopped();
            Handler oldIpc = ipc;
            HandlerThread oldThread = ipcThread;
            Messenger oldRemote = remote;
            Messenger oldIncoming = incoming;
            ipc = null;
            ipcThread = null;
            remote = null;
            incoming = null;
            if (oldIpc != null) {
                oldIpc.post(() -> sendUnregister(oldRemote, oldIncoming));
            }
            if (hadBinding) {
                try {
                    context.unbindService(connection);
                } catch (IllegalArgumentException ignored) {
                    // A concurrent Binder death may already have removed the connection.
                }
            }
            if (oldThread != null) oldThread.quitSafely();
            notifyState(State.STOPPED, "");
        });
    }

    void requestSnapshot() {
        sendSimple(MediaBridgeContract.GET_SNAPSHOT, requestId("snapshot"), null);
    }

    void requestRadioStations() {
        sendSimple(MediaBridgeContract.GET_RADIO_STATIONS,
                requestId("radio-stations"), null);
    }

    String sendCommand(String command) {
        return sendCommand(command, -1L, null, null, true);
    }

    String seekTo(long position) {
        return sendCommand("SEEK_TO", Math.max(0L, position), null, null, true);
    }

    String setSource(MediaSource.Id source) {
        return sendCommand("SET_SOURCE", -1L, source.name(), null, true);
    }

    String tuneRadio(RadioStation station) {
        String requestId = requestId("command-radio");
        Bundle extra = new Bundle();
        extra.putString(MediaBridgeContract.K_COMMAND, "TUNE_RADIO");
        extra.putInt(MediaBridgeContract.K_RADIO_FREQUENCY_KHZ, station.frequencyKHz);
        extra.putInt(MediaBridgeContract.K_RADIO_BAND, station.band);
        extra.putString(MediaBridgeContract.K_RADIO_ENSEMBLE_NAME, station.ensembleName);
        extra.putString(MediaBridgeContract.K_RADIO_SERVICE_NAME, station.serviceName);
        extra.putString(MediaBridgeContract.K_RADIO_GENRE, station.genre);
        extra.putInt(MediaBridgeContract.K_RADIO_ICON_ID, station.iconId);
        extra.putInt(MediaBridgeContract.K_RADIO_SIGNAL_QUALITY, station.signalQuality);
        extra.putString(MediaBridgeContract.K_RADIO_SELECTOR, station.selector);
        extra.putBoolean(MediaBridgeContract.K_COMMAND_AUTOPLAY, true);
        sendSimple(MediaBridgeContract.COMMAND, requestId, extra);
        return requestId;
    }

    private String sendCommand(String command, long position, String source,
            String appSource, boolean autoplay) {
        String requestId = requestId("command");
        Bundle extra = new Bundle();
        extra.putString(MediaBridgeContract.K_COMMAND, command);
        if (position >= 0L) extra.putLong(MediaBridgeContract.K_COMMAND_POSITION, position);
        if (source != null) {
            extra.putString(MediaBridgeContract.K_COMMAND_SOURCE, source);
            if (appSource != null) {
                extra.putString(MediaBridgeContract.K_COMMAND_APP_SOURCE, appSource);
            }
            extra.putBoolean(MediaBridgeContract.K_COMMAND_AUTOPLAY, autoplay);
        }
        sendSimple(MediaBridgeContract.COMMAND, requestId, extra);
        return requestId;
    }

    private void bindNow() {
        if (!started || !connectionState.startBinding()) return;
        bindStartedAt = SystemClock.elapsedRealtime();
        AppLog.info("Media Bridge bind requested at t+" + elapsedSince(startedAt) + " ms");
        notifyState(State.CONNECTING, "");
        Intent intent = new Intent(MediaBridgeContract.SERVICE_ACTION).setComponent(
                new ComponentName(MediaBridgeContract.SERVICE_PACKAGE,
                        MediaBridgeContract.SERVICE_CLASS));
        boolean accepted;
        try {
            accepted = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot bind Atlas Media API service", error);
            accepted = false;
        }
        if (!accepted) {
            notifyState(State.DISCONNECTED, "Atlas Media API не установлен или недоступен");
            scheduleRebind(connectionState.onDisconnected());
        } else {
            main.postDelayed(bindTimeout, ReconnectPolicy.BIND_TIMEOUT_MS);
        }
    }

    private final Runnable rebind = this::bindNow;

    private void scheduleRebind(long delayMs) {
        if (!started || delayMs < 0L) return;
        main.removeCallbacks(rebind);
        AppLog.info("Media Bridge reconnect scheduled in " + delayMs + " ms");
        main.postDelayed(rebind, delayMs);
    }

    private void handleConnectionLoss(String detail) {
        main.post(() -> {
            if (!started) return;
            boolean hadBinding = connectionState.hasBinding();
            long delayMs = connectionState.onDisconnected();
            if (delayMs < 0L) return;
            removeConnectionCallbacks();
            if (hadBinding) {
                try {
                    context.unbindService(connection);
                } catch (IllegalArgumentException ignored) {
                    // Already unbound by the framework.
                }
            }
            if (ipc != null) ipc.post(() -> remote = null);
            failPendingCallbacks(MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                    "Atlas Media API отключён");
            AppLog.info(detail + " at t+" + elapsedSince(startedAt) + " ms");
            notifyState(State.DISCONNECTED, detail);
            scheduleRebind(delayMs);
        });
    }

    private void sendRegister() {
        Bundle data = baseData(requestId("register"));
        sendMessage(MediaBridgeContract.REGISTER, data);
    }

    private void sendUnregister(Messenger target, Messenger replyTo) {
        if (target == null || replyTo == null) return;
        Message message = Message.obtain(null, MediaBridgeContract.UNREGISTER);
        message.replyTo = replyTo;
        message.setData(baseData(""));
        try {
            target.send(message);
        } catch (RemoteException error) {
            AppLog.warn("Media Bridge unregister failed", error);
        }
    }

    private void sendSimple(int what, String requestId, Bundle extra) {
        Handler target = ipc;
        if (!started || target == null) {
            closeFileDescriptor(extra);
            if (what == MediaBridgeContract.COMMAND) {
                postCommandResult(requestId, MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                        "Atlas Media API не подключён", 0L);
            } else {
                dispatchError(requestId, MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                        "Atlas Media API не подключён");
            }
            return;
        }
        boolean posted = target.post(() -> {
            if (!started || !connectionState.canSend() || remote == null) {
                closeFileDescriptor(extra);
                if (what == MediaBridgeContract.COMMAND) {
                    postCommandResult(requestId, MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                            "Atlas Media API не подключён", 0L);
                } else {
                    dispatchError(requestId, MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                            "Atlas Media API не подключён");
                }
                return;
            }
            Bundle data = baseData(requestId);
            if (extra != null) data.putAll(extra);
            try {
                sendMessage(what, data);
            } finally {
                closeFileDescriptor(data);
            }
        });
        if (!posted) {
            closeFileDescriptor(extra);
            if (what == MediaBridgeContract.COMMAND) {
                postCommandResult(requestId, MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                        "Atlas Media API не подключён", 0L);
            } else {
                dispatchError(requestId, MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE,
                        "Atlas Media API не подключён");
            }
        }
    }

    private Bundle baseData(String requestId) {
        Bundle data = new Bundle();
        data.putInt(MediaBridgeContract.K_VERSION, MediaBridgeContract.VERSION);
        if (requestId != null && !requestId.isEmpty()) {
            data.putString(MediaBridgeContract.K_REQUEST_ID, requestId);
        }
        data.putInt(MediaBridgeContract.K_UI_SCALE_TENTHS,
                ScaledActivity.configuredScaleTenths(context));
        return data;
    }

    private void sendMessage(int what, Bundle data) {
        Messenger target = remote;
        if (target == null || incoming == null) return;
        Message message = Message.obtain(null, what);
        message.replyTo = incoming;
        message.setData(data);
        try {
            target.send(message);
        } catch (RemoteException error) {
            AppLog.warn("Media Bridge send failed", error);
            handleConnectionLoss("Ошибка Binder Atlas Media API");
        }
    }

    private boolean handleIncoming(Message message) {
        Bundle data = message.getData();
        int version = data.getInt(MediaBridgeContract.K_VERSION, -1);
        if (version != MediaBridgeContract.VERSION) {
            markIncompatible("Несовместимая версия mediaapi: " + version);
            return true;
        }
        try {
            switch (message.what) {
                case MediaBridgeContract.REGISTERED -> {
                    int status = data.getInt(MediaBridgeContract.K_STATUS, -1);
                    if (status == MediaBridgeContract.STATUS_OK) {
                        settingsSupported = data.getInt(
                                MediaBridgeContract.K_SETTINGS_PROTOCOL_VERSION, -1) == 1;
                        if (!connectionState.onRegistered()) return true;
                        main.post(() -> {
                            main.removeCallbacks(registerTimeout);
                            AppLog.info("Media Bridge registered after "
                                    + elapsedSince(registerStartedAt) + " ms (t+"
                                    + elapsedSince(startedAt) + " ms)");
                            listener.onBridgeState(State.CONNECTED, "");
                            main.postDelayed(snapshotTimeout, ReconnectPolicy.SNAPSHOT_TIMEOUT_MS);
                        });
                    } else {
                        markIncompatible("Регистрация отклонена: " + status);
                    }
                }
                case MediaBridgeContract.SNAPSHOT -> {
                    MediaSnapshot snapshot = MediaSnapshot.fromBundle(data);
                    BridgeConnectionState.SnapshotResult result = connectionState.onSnapshot();
                    if (result == BridgeConnectionState.SnapshotResult.IGNORED) return true;
                    main.post(() -> {
                        if (result == BridgeConnectionState.SnapshotResult.FIRST) {
                            main.removeCallbacks(snapshotTimeout);
                            AppLog.info("Media Bridge first snapshot received at t+"
                                    + elapsedSince(startedAt) + " ms");
                        }
                        listener.onSnapshot(snapshot);
                    });
                }
                case MediaBridgeContract.RADIO_STATIONS -> {
                    int status = data.getInt(MediaBridgeContract.K_STATUS, -1);
                    if (status == MediaBridgeContract.STATUS_OK) {
                        RadioStationLists lists = RadioStationLists.fromBundle(data);
                        main.post(() -> listener.onRadioStations(lists));
                    } else {
                        postRadioStationsError(status,
                                data.getString(MediaBridgeContract.K_MESSAGE, ""));
                    }
                }
                case MediaBridgeContract.SETTINGS -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    MediaSettingsSnapshot snapshot = MediaSettingsSnapshot.fromBundle(data);
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof SettingsCallback scb) {
                        main.post(() -> scb.onSettings(snapshot));
                    }
                }
                case MediaBridgeContract.SETTINGS_UPDATED -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    MediaSettingsSnapshot snapshot = MediaSettingsSnapshot.fromBundle(data);
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof UpdateSettingsCallback ucb) {
                        main.post(() -> ucb.onSettingsUpdated(snapshot));
                    }
                }
                case MediaBridgeContract.DEFAULT_CATALOG_RESTORED -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    MediaSettingsSnapshot snapshot = MediaSettingsSnapshot.fromBundle(data);
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof RestoreCatalogCallback rcb) {
                        main.post(() -> rcb.onCatalogRestored(snapshot));
                    }
                }
                case MediaBridgeContract.MEDIA_BACKUP_EXPORTED -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof BackupCallback bcb) {
                        main.post(bcb::onBackupExported);
                    }
                }
                case MediaBridgeContract.RADIO_CATALOG_EXPORTED -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    int status = data.getInt(MediaBridgeContract.K_STATUS, -1);
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof RadioCatalogExportCallback rcb) {
                        if (status == MediaBridgeContract.STATUS_OK) {
                            int stationCount = data.getInt(
                                    MediaBridgeContract.K_CATALOG_STATION_COUNT, 0);
                            main.post(() -> rcb.onCatalogExported(stationCount));
                        } else {
                            dispatchError(rcb, status,
                                    data.getString(MediaBridgeContract.K_MESSAGE, ""));
                        }
                    }
                }
                case MediaBridgeContract.RADIO_CATALOG_IMPORTED -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    int status = data.getInt(MediaBridgeContract.K_STATUS, -1);
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof RadioCatalogImportCallback rcb) {
                        if (status == MediaBridgeContract.STATUS_OK) {
                            int stationCount = data.getInt(
                                    MediaBridgeContract.K_CATALOG_STATION_COUNT, 0);
                            main.post(() -> rcb.onCatalogImported(stationCount));
                        } else {
                            dispatchError(rcb, status,
                                    data.getString(MediaBridgeContract.K_MESSAGE, ""));
                        }
                    }
                }
                case MediaBridgeContract.MEDIA_IMPORT_PREPARED -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof PrepareImportCallback pcb) {
                        String token = data.getString(MediaBridgeContract.K_STAGING_TOKEN, "");
                        String catalogMode = data.getString(MediaBridgeContract.K_CATALOG_TYPE, "");
                        int stationCount = data.getInt(MediaBridgeContract.K_CATALOG_STATION_COUNT, 0);
                        java.util.ArrayList<String> warnings = data.getStringArrayList(MediaBridgeContract.K_IMPORT_PREVIEW);
                        main.post(() -> pcb.onImportPrepared(token, catalogMode, stationCount,
                                warnings != null ? warnings : java.util.Collections.emptyList()));
                    }
                }
                case MediaBridgeContract.MEDIA_IMPORT_COMMITTED -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    MediaSettingsSnapshot snapshot = MediaSettingsSnapshot.fromBundle(data);
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof CommitImportCallback ccb) {
                        main.post(() -> ccb.onImportCommitted(snapshot));
                    }
                }
                case MediaBridgeContract.MEDIA_IMPORT_STATUS -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    Object cb = takePendingCallback(requestId);
                    if (cb instanceof ImportStatusCallback scb) {
                        String status = data.getString(MediaBridgeContract.K_IMPORT_STATUS, "IDLE");
                        main.post(() -> scb.onStatus(status));
                    }
                }
                case MediaBridgeContract.ERROR -> {
                    String requestId = data.getString(MediaBridgeContract.K_REQUEST_ID, "");
                    int status = data.getInt(MediaBridgeContract.K_STATUS, -1);
                    String detail = data.getString(MediaBridgeContract.K_MESSAGE, "");
                    boolean handled = dispatchError(requestId, status, detail);
                    if (!handled && requestId.startsWith("radio-stations-")) {
                        postRadioStationsError(status, detail);
                    } else if (!handled && !requestId.startsWith("radio-stations-")) {
                        postCommandResult(requestId, status, detail,
                                data.getLong(MediaBridgeContract.K_GENERATION));
                    }
                }
                case MediaBridgeContract.COMMAND_RESULT ->
                        postCommandResult(
                                data.getString(MediaBridgeContract.K_REQUEST_ID, ""),
                                data.getInt(MediaBridgeContract.K_STATUS, -1),
                                data.getString(MediaBridgeContract.K_MESSAGE, ""),
                                data.getLong(MediaBridgeContract.K_GENERATION));
                default -> AppLog.info("Ignoring unknown Media Bridge message " + message.what);
            }
        } catch (RuntimeException error) {
            AppLog.warn("Invalid Media Bridge payload", error);
            dispatchError(data.getString(MediaBridgeContract.K_REQUEST_ID, ""),
                    MediaBridgeContract.STATUS_FAILED, "Некорректный ответ медиасервиса");
            if (message.what == MediaBridgeContract.RADIO_STATIONS) {
                postRadioStationsError(1, "Некорректный список радиостанций");
            }
        }
        return true;
    }

    boolean isSettingsSupported() {
        return settingsSupported;
    }

    void getSettings(SettingsCallback callback) {
        String reqId = requestId("get-settings");
        if (callback != null) addPendingCallback(reqId, callback);
        sendSimple(MediaBridgeContract.GET_SETTINGS, reqId, null);
    }

    void updateSettings(Long expectedRevision, Bundle changes, UpdateSettingsCallback callback) {
        String reqId = requestId("update-settings");
        if (callback != null) addPendingCallback(reqId, callback);
        Bundle extra = changes != null ? new Bundle(changes) : new Bundle();
        if (expectedRevision != null) {
            extra.putLong(MediaBridgeContract.K_EXPECTED_REVISION, expectedRevision);
        }
        sendSimple(MediaBridgeContract.UPDATE_SETTINGS, reqId, extra);
    }

    void restoreDefaultCatalog(RestoreCatalogCallback callback) {
        String reqId = requestId("restore-catalog");
        if (callback != null) addPendingCallback(reqId, callback);
        sendSimple(MediaBridgeContract.RESTORE_DEFAULT_CATALOG, reqId, null);
    }

    void exportMediaBackup(File destinationFile, BackupCallback callback) {
        try {
            android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(
                    destinationFile,
                    android.os.ParcelFileDescriptor.MODE_WRITE_ONLY
                            | android.os.ParcelFileDescriptor.MODE_CREATE
                            | android.os.ParcelFileDescriptor.MODE_TRUNCATE);
            exportMediaBackup(pfd, callback);
        } catch (Exception e) {
            if (callback != null) {
                main.post(() -> callback.onError(MediaBridgeContract.STATUS_IO_ERROR,
                        e.getMessage()));
            }
        }
    }

    /** The descriptor overload takes ownership and closes the descriptor after send or drop. */
    void exportMediaBackup(android.os.ParcelFileDescriptor pfd, BackupCallback callback) {
        String reqId = requestId("export-media-backup");
        if (callback != null) addPendingCallback(reqId, callback);
        Bundle extra = new Bundle();
        extra.putParcelable(MediaBridgeContract.K_FILE_DESCRIPTOR, pfd);
        sendSimple(MediaBridgeContract.EXPORT_MEDIA_BACKUP, reqId, extra);
    }

    void exportRadioCatalog(File destinationFile, RadioCatalogExportCallback callback) {
        try {
            android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(
                    destinationFile,
                    android.os.ParcelFileDescriptor.MODE_WRITE_ONLY
                            | android.os.ParcelFileDescriptor.MODE_CREATE
                            | android.os.ParcelFileDescriptor.MODE_TRUNCATE);
            exportRadioCatalog(pfd, callback);
        } catch (Exception e) {
            if (callback != null) {
                main.post(() -> callback.onError(MediaBridgeContract.STATUS_IO_ERROR,
                        e.getMessage()));
            }
        }
    }

    /** The descriptor overload takes ownership and closes the descriptor after send or drop. */
    void exportRadioCatalog(android.os.ParcelFileDescriptor pfd,
            RadioCatalogExportCallback callback) {
        String reqId = requestId("export-radio-catalog");
        if (callback != null) addPendingCallback(reqId, callback);
        Bundle extra = new Bundle();
        extra.putParcelable(MediaBridgeContract.K_FILE_DESCRIPTOR, pfd);
        sendSimple(MediaBridgeContract.EXPORT_RADIO_CATALOG, reqId, extra);
    }

    void importRadioCatalog(File zipFile, RadioCatalogImportCallback callback) {
        try {
            android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(
                    zipFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
            importRadioCatalog(pfd, callback);
        } catch (Exception e) {
            if (callback != null) {
                main.post(() -> callback.onError(MediaBridgeContract.STATUS_IO_ERROR,
                        e.getMessage()));
            }
        }
    }

    /** The descriptor overload takes ownership and closes the descriptor after send or drop. */
    void importRadioCatalog(android.os.ParcelFileDescriptor pfd,
            RadioCatalogImportCallback callback) {
        String reqId = requestId("import-radio-catalog");
        if (callback != null) addPendingCallback(reqId, callback);
        Bundle extra = new Bundle();
        extra.putParcelable(MediaBridgeContract.K_FILE_DESCRIPTOR, pfd);
        sendSimple(MediaBridgeContract.IMPORT_RADIO_CATALOG, reqId, extra);
    }

    void prepareMediaImport(String operationId, File zipFile, PrepareImportCallback callback) {
        try {
            android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(
                    zipFile,
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY);
            prepareMediaImport(operationId, pfd, callback);
        } catch (Exception e) {
            if (callback != null) {
                main.post(() -> callback.onError(MediaBridgeContract.STATUS_IO_ERROR,
                        e.getMessage()));
            }
        }
    }

    /** The descriptor overload takes ownership and closes the descriptor after send or drop. */
    void prepareMediaImport(String operationId, android.os.ParcelFileDescriptor pfd, PrepareImportCallback callback) {
        String reqId = requestId("prepare-media-import");
        if (callback != null) addPendingCallback(reqId, callback);
        Bundle extra = new Bundle();
        extra.putString(MediaBridgeContract.K_OPERATION_ID, operationId);
        extra.putParcelable(MediaBridgeContract.K_FILE_DESCRIPTOR, pfd);
        sendSimple(MediaBridgeContract.PREPARE_MEDIA_IMPORT, reqId, extra);
    }

    void commitMediaImport(String operationId, String stagingToken, CommitImportCallback callback) {
        String reqId = requestId("commit-media-import");
        if (callback != null) addPendingCallback(reqId, callback);
        Bundle extra = new Bundle();
        extra.putString(MediaBridgeContract.K_OPERATION_ID, operationId);
        extra.putString(MediaBridgeContract.K_STAGING_TOKEN, stagingToken);
        sendSimple(MediaBridgeContract.COMMIT_MEDIA_IMPORT, reqId, extra);
    }

    void getImportStatus(String operationId, ImportStatusCallback callback) {
        String reqId = requestId("get-import-status");
        if (callback != null) addPendingCallback(reqId, callback);
        Bundle extra = new Bundle();
        extra.putString(MediaBridgeContract.K_OPERATION_ID, operationId);
        sendSimple(MediaBridgeContract.GET_IMPORT_STATUS, reqId, extra);
    }

    void abortMediaImport(String operationId) {
        String reqId = requestId("abort-media-import");
        Bundle extra = new Bundle();
        extra.putString(MediaBridgeContract.K_OPERATION_ID, operationId);
        sendSimple(MediaBridgeContract.ABORT_MEDIA_IMPORT, reqId, extra);
    }

    private void postCommandResult(String requestId, int status, String message, long generation) {
        main.post(() -> listener.onCommandResult(requestId, status, message, generation));
    }

    private void postRadioStationsError(int status, String message) {
        main.post(() -> listener.onRadioStationsError(status, message));
    }

    private void addPendingCallback(String requestId, Object callback) {
        pendingCallbacks.put(requestId, callback);
        Runnable timeout = () -> {
            if (pendingCallbacks.remove(requestId, callback)) {
                pendingTimeouts.remove(requestId);
                dispatchError(callback, MediaBridgeContract.STATUS_FAILED,
                        "Таймаут запроса к Atlas Media API");
            }
        };
        pendingTimeouts.put(requestId, timeout);
        main.postDelayed(timeout, REQUEST_TIMEOUT_MS);
    }

    private Object takePendingCallback(String requestId) {
        Object callback = pendingCallbacks.remove(requestId);
        if (callback != null) {
            Runnable timeout = pendingTimeouts.remove(requestId);
            if (timeout != null) main.removeCallbacks(timeout);
        }
        return callback;
    }

    private boolean dispatchError(String requestId, int status, String message) {
        Object callback = takePendingCallback(requestId);
        if (callback == null) return false;
        dispatchError(callback, status, message);
        return true;
    }

    private void dispatchError(Object callback, int status, String message) {
        main.post(() -> {
            if (callback instanceof SettingsCallback cb) {
                cb.onError(status, message);
            } else if (callback instanceof UpdateSettingsCallback cb) {
                cb.onError(status, message);
            } else if (callback instanceof BackupCallback cb) {
                cb.onError(status, message);
            } else if (callback instanceof RadioCatalogExportCallback cb) {
                cb.onError(status, message);
            } else if (callback instanceof RadioCatalogImportCallback cb) {
                cb.onError(status, message);
            } else if (callback instanceof PrepareImportCallback cb) {
                cb.onError(status, message);
            } else if (callback instanceof CommitImportCallback cb) {
                cb.onError(status, message);
            } else if (callback instanceof ImportStatusCallback cb) {
                cb.onError(status, message);
            } else if (callback instanceof RestoreCatalogCallback cb) {
                cb.onError(status, message);
            }
        });
    }

    private void failPendingCallbacks(int status, String message) {
        for (java.util.Map.Entry<String, Object> entry : pendingCallbacks.entrySet()) {
            if (pendingCallbacks.remove(entry.getKey(), entry.getValue())) {
                Runnable timeout = pendingTimeouts.remove(entry.getKey());
                if (timeout != null) main.removeCallbacks(timeout);
                dispatchError(entry.getValue(), status, message);
            }
        }
    }

    private static void closeFileDescriptor(Bundle data) {
        if (data == null) return;
        android.os.Parcelable value = data.getParcelable(MediaBridgeContract.K_FILE_DESCRIPTOR);
        if (!(value instanceof android.os.ParcelFileDescriptor pfd)) return;
        try {
            pfd.close();
        } catch (java.io.IOException error) {
            AppLog.warn("Cannot close Media Bridge file descriptor", error);
        }
    }

    private void notifyState(State state, String detail) {
        main.post(() -> listener.onBridgeState(state, detail));
    }

    private void markIncompatible(String detail) {
        if (!started) return;
        settingsSupported = false;
        failPendingCallbacks(MediaBridgeContract.STATUS_UNSUPPORTED_VERSION, detail);
        connectionState.onIncompatible();
        main.post(() -> {
            removeConnectionTimeouts();
            AppLog.info(detail + " at t+" + elapsedSince(startedAt) + " ms");
            listener.onBridgeState(State.INCOMPATIBLE, detail);
        });
    }

    private void removeConnectionCallbacks() {
        main.removeCallbacks(rebind);
        removeConnectionTimeouts();
    }

    private void removeConnectionTimeouts() {
        main.removeCallbacks(bindTimeout);
        main.removeCallbacks(registerTimeout);
        main.removeCallbacks(snapshotTimeout);
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, SystemClock.elapsedRealtime() - started);
    }

    private String requestId(String prefix) {
        return prefix + '-' + Long.toUnsignedString(nextRequest.incrementAndGet(), 36);
    }
}
