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
    private final HandlerThread ipcThread = new HandlerThread("atlas-media-bridge");
    private final AtomicLong nextRequest = new AtomicLong();
    private Handler ipc;
    private Messenger incoming;
    private Messenger remote;
    private boolean registered;
    private boolean started;
    private boolean bound;
    private boolean binding;
    private int reconnectAttempt;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binding = false;
            bound = true;
            reconnectAttempt = 0;
            notifyState(State.REGISTERING, "");
            ipc.post(() -> {
                remote = new Messenger(service);
                registered = false;
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

    MediaBridgeClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        main.post(() -> {
            if (started) return;
            started = true;
            ipcThread.start();
            ipc = new Handler(ipcThread.getLooper());
            incoming = new Messenger(new Handler(ipcThread.getLooper(), this::handleIncoming));
            bindNow();
        });
    }

    void stop() {
        main.post(() -> {
            if (!started) return;
            started = false;
            main.removeCallbacks(rebind);
            if (ipc != null) ipc.post(this::sendUnregister);
            if (bound || binding) {
                try {
                    context.unbindService(connection);
                } catch (IllegalArgumentException ignored) {
                    // A concurrent Binder death may already have removed the connection.
                }
            }
            bound = false;
            binding = false;
            remote = null;
            registered = false;
            ipcThread.quitSafely();
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
        if (!started || bound || binding) return;
        binding = true;
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
            binding = false;
            notifyState(State.DISCONNECTED, "GInputBridge mediaapi не установлен или недоступен");
            scheduleRebind();
        }
    }

    private final Runnable rebind = this::bindNow;

    private void scheduleRebind() {
        if (!started) return;
        main.removeCallbacks(rebind);
        main.postDelayed(rebind, ReconnectPolicy.delayMs(reconnectAttempt++));
    }

    private void handleConnectionLoss(String detail) {
        main.post(() -> {
            if (!started) return;
            if (bound || binding) {
                try {
                    context.unbindService(connection);
                } catch (IllegalArgumentException ignored) {
                    // Already unbound by the framework.
                }
            }
            bound = false;
            binding = false;
            if (ipc != null) ipc.post(() -> {
                remote = null;
                registered = false;
            });
            notifyState(State.DISCONNECTED, detail);
            scheduleRebind();
        });
    }

    private void sendRegister() {
        Bundle data = baseData(requestId("register"));
        sendMessage(MediaBridgeContract.REGISTER, data);
    }

    private void sendUnregister() {
        if (remote == null || incoming == null) return;
        sendMessage(MediaBridgeContract.UNREGISTER, baseData(""));
    }

    private void sendSimple(int what, String requestId, Bundle extra) {
        Handler target = ipc;
        if (!started || target == null) return;
        target.post(() -> {
            if (!registered || remote == null) {
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
            notifyState(State.INCOMPATIBLE, "Несовместимая версия mediaapi: " + version);
            return true;
        }
        try {
            switch (message.what) {
                case MediaBridgeContract.REGISTERED -> {
                    int status = data.getInt(MediaBridgeContract.K_STATUS, -1);
                    if (status == MediaBridgeContract.STATUS_OK) {
                        registered = true;
                        notifyState(State.CONNECTED, "");
                    } else {
                        notifyState(State.INCOMPATIBLE, "Регистрация отклонена: " + status);
                    }
                }
                case MediaBridgeContract.SNAPSHOT -> {
                    MediaSnapshot snapshot = MediaSnapshot.fromBundle(data);
                    main.post(() -> listener.onSnapshot(snapshot));
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

    private String requestId(String prefix) {
        return prefix + '-' + Long.toUnsignedString(nextRequest.incrementAndGet(), 36);
    }
}
