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

import java.util.concurrent.atomic.AtomicLong;

final class MediaBridgeClient {
    enum State { STOPPED, CONNECTING, REGISTERING, CONNECTED, DISCONNECTED, INCOMPATIBLE }

    interface Listener {
        void onBridgeState(State state, String detail);
        void onSnapshot(MediaSnapshot snapshot);
        void onCommandResult(String requestId, int status, String message, long generation);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private HandlerThread ipcThread;
    private final AtomicLong nextRequest = new AtomicLong();
    private final BridgeConnectionState connectionState = new BridgeConnectionState();
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
            handleConnectionLoss("GInputBridge service disconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            handleConnectionLoss("GInputBridge binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            handleConnectionLoss("GInputBridge returned a null binding");
        }
    };

    private final Runnable bindTimeout = () -> {
        if (connectionState.is(BridgeConnectionState.Phase.BINDING)) {
            handleConnectionLoss("Таймаут подключения к GInputBridge");
        }
    };

    private final Runnable registerTimeout = () -> {
        if (connectionState.is(BridgeConnectionState.Phase.REGISTERING)) {
            handleConnectionLoss("Таймаут регистрации GInputBridge mediaapi");
        }
    };

    private final Runnable snapshotTimeout = () -> {
        if (connectionState.is(BridgeConnectionState.Phase.WAITING_SNAPSHOT)) {
            handleConnectionLoss("Таймаут первого snapshot GInputBridge");
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
            if (!started) return;
            started = false;
            removeConnectionCallbacks();
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

    String sendCommand(String command) {
        return sendCommand(command, -1L, null, null, true);
    }

    String seekTo(long position) {
        return sendCommand("SEEK_TO", Math.max(0L, position), null, null, true);
    }

    String setSource(MediaSource.Id source) {
        return sendCommand("SET_SOURCE", -1L, source.name(), null, true);
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
            AppLog.warn("Cannot bind GInputBridge media service", error);
            accepted = false;
        }
        if (!accepted) {
            notifyState(State.DISCONNECTED, "GInputBridge mediaapi не установлен или недоступен");
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
        if (!started || target == null) return;
        target.post(() -> {
            if (!connectionState.canSend() || remote == null) {
                if (what == MediaBridgeContract.COMMAND) {
                    postCommandResult(requestId, 5, "GInputBridge не подключён", 0L);
                }
                return;
            }
            Bundle data = baseData(requestId);
            if (extra != null) data.putAll(extra);
            sendMessage(what, data);
        });
    }

    private Bundle baseData(String requestId) {
        Bundle data = new Bundle();
        data.putInt(MediaBridgeContract.K_VERSION, MediaBridgeContract.VERSION);
        if (requestId != null && !requestId.isEmpty()) {
            data.putString(MediaBridgeContract.K_REQUEST_ID, requestId);
        }
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
            handleConnectionLoss("Ошибка Binder GInputBridge");
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
                case MediaBridgeContract.COMMAND_RESULT, MediaBridgeContract.ERROR ->
                        postCommandResult(
                                data.getString(MediaBridgeContract.K_REQUEST_ID, ""),
                                data.getInt(MediaBridgeContract.K_STATUS, -1),
                                data.getString(MediaBridgeContract.K_MESSAGE, ""),
                                data.getLong(MediaBridgeContract.K_GENERATION));
                default -> AppLog.info("Ignoring unknown Media Bridge message " + message.what);
            }
        } catch (RuntimeException error) {
            AppLog.warn("Invalid Media Bridge payload", error);
        }
        return true;
    }

    private void postCommandResult(String requestId, int status, String message, long generation) {
        main.post(() -> listener.onCommandResult(requestId, status, message, generation));
    }

    private void notifyState(State state, String detail) {
        main.post(() -> listener.onBridgeState(state, detail));
    }

    private void markIncompatible(String detail) {
        if (!started) return;
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
