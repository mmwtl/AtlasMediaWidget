package com.mmwtl.atlasmediawidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Shows text in the left pill of the OEM status bar through the OneOS API "scene mode" service
 * (type 13). The G636 SystemUI plugin (1.0.20250623G(312)) reads this service, the stock provider
 * is not installed, and OneOSApiService.addService accepts any binder and announces it to clients.
 * Transaction codes come from the plugin's decompiled oneosapi library; they are firmware-specific.
 *
 * <p>OneOS API does not watch our binder's death, so a scene left open by a dead process stays in
 * the status bar until the head unit reboots. Only the foreground OverlayService owns this.
 */
final class OneOsStatusBarScene {
    private static final String API_PACKAGE = "com.geely.service.oneosapi";
    private static final String API_CLASS = "com.geely.service.oneosapi.OneOSApiService";
    private static final String SERVICE_MANAGER = "com.geely.lib.oneosapi.IServiceManager";
    private static final String SCENE_SERVICE = "com.geely.lib.oneosapi.scenemode.ISceneModeService";
    private static final String SCENE_LISTENER =
            "com.geely.lib.oneosapi.scenemode.ISceneModeServiceChangedListener";
    private static final int SCENE_TYPE = 13;
    private static final int ADD_SERVICE = 1;
    private static final int GET_SERVICE = 2;
    private static final int ON_OPEN_STATE_CHANGED = 1;
    // Track changes arrive as several snapshots; one reopen per change avoids a double blink.
    private static final long SETTLE_MS = 500L;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "AtlasStatusBarScene"));
    private final StatusBarScene scene = new StatusBarScene();
    private final List<IBinder> listeners = new ArrayList<>();
    private final Binder service = new SceneService();
    private volatile String sceneName = "";
    private volatile boolean sceneOpen;
    // Executor thread only.
    private IBinder manager;
    private boolean bound;
    private boolean shutdown;
    // Main thread only.
    private String pendingText;
    private final Runnable applyPending = () -> submit(pendingText);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName component, IBinder binder) {
            run(() -> {
                manager = binder;
                register();
            });
        }

        @Override public void onServiceDisconnected(ComponentName component) {
            // Android rebinds when OneOSApiService restarts; onServiceConnected registers again.
            AppLog.info("Status bar: OneOS API service disconnected");
            run(() -> manager = null);
        }

        @Override public void onNullBinding(ComponentName component) {
            AppLog.info("Status bar: OneOS API service returned no binder");
        }
    };

    OneOsStatusBarScene(Context context) {
        this.context = context.getApplicationContext();
        service.attachInterface(null, SCENE_SERVICE);
    }

    /** Main thread. Shows this text, or closes the scene for null, once the media settles. */
    void update(String text) {
        pendingText = text;
        main.removeCallbacks(applyPending);
        main.postDelayed(applyPending, SETTLE_MS);
    }

    /** Main thread. Closes the scene without waiting, e.g. when the setting is turned off. */
    void close() {
        pendingText = null;
        main.removeCallbacks(applyPending);
        submit(null);
    }

    /** Main thread. Closes the scene and releases OneOS API; the registration stays closed. */
    void shutdown() {
        close();
        run(() -> {
            shutdown = true;
            if (bound) context.unbindService(connection);
            bound = false;
            manager = null;
        });
        executor.shutdown();
    }

    private void submit(String text) {
        run(() -> apply(text));
    }

    private void run(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Shut down with the service; the scene was closed before that.
        }
    }

    private void apply(String text) {
        if (shutdown) return;
        int[] states = scene.show(text);
        if (text != null) sceneName = text;
        for (int open : states) {
            sceneOpen = open != 0;
            notifyOpenState(open);
        }
        if (states.length > 0) AppLog.info("Status bar scene " + (text == null ? "closed" : "shown"));
        if (text != null) bind();
    }

    private void bind() {
        if (bound) return;
        Intent intent = new Intent().setClassName(API_PACKAGE, API_CLASS);
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException error) {
            AppLog.warn("Status bar: cannot bind OneOS API", error);
        }
        if (!bound) {
            AppLog.info("Status bar: OneOS API service is not available");
            // Do not leave the bindService reservation behind; a later text retries.
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // The failed bind did not register the connection.
            }
        }
    }

    /** Adds our scene service unless another live provider already owns type 13. */
    private void register() {
        IBinder current = manager;
        if (current == null || shutdown) return;
        try {
            IBinder registered = getService(current);
            if (registered == service) return;
            if (registered != null && registered.pingBinder()) {
                AppLog.info("Status bar: scene service belongs to another app, not replacing it");
                return;
            }
            // Empty, or the dead binder of our previous process.
            addService(current);
            AppLog.info("Status bar: scene service registered in OneOS API");
        } catch (RemoteException | RuntimeException error) {
            AppLog.warn("Status bar: OneOS API registration failed", error);
        }
    }

    private static IBinder getService(IBinder manager) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_MANAGER);
            data.writeInt(SCENE_TYPE);
            manager.transact(GET_SERVICE, data, reply, 0);
            reply.readException();
            return reply.readStrongBinder();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void addService(IBinder manager) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_MANAGER);
            data.writeInt(SCENE_TYPE);
            data.writeStrongBinder(service);
            manager.transact(ADD_SERVICE, data, reply, 0);
            reply.readException();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** ISceneModeServiceChangedListener.onOpenStateChanged; synchronous, off the main thread. */
    private void notifyOpenState(int open) {
        List<IBinder> targets;
        synchronized (listeners) {
            targets = new ArrayList<>(listeners);
        }
        for (IBinder listener : targets) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SCENE_LISTENER);
                data.writeInt(open);
                listener.transact(ON_OPEN_STATE_CHANGED, data, reply, 0);
                reply.readException();
            } catch (RemoteException | RuntimeException error) {
                AppLog.warn("Status bar: scene listener failed, dropping it", error);
                synchronized (listeners) {
                    listeners.remove(listener);
                }
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }

    /** ISceneModeService: the getters and listener registration the status bar plugin uses. */
    private final class SceneService extends Binder {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code < 1 || code > 8) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(SCENE_SERVICE);
            IBinder listener = code == 7 || code == 8 ? data.readStrongBinder() : null;
            if (code == 7 && listener != null) {
                synchronized (listeners) {
                    if (!listeners.contains(listener)) listeners.add(listener);
                }
            } else if (code == 8) {
                synchronized (listeners) {
                    listeners.remove(listener);
                }
            }
            if (reply == null) return true;
            reply.writeNoException();
            switch (code) {
                case 2 -> reply.writeInt(0); // executeSceneModeById
                case 4 -> reply.writeInt(sceneOpen ? 1 : 0); // getSceneModeOpenState
                case 5 -> reply.writeInt(0); // getSceneModeFrontState
                case 6 -> reply.writeString(sceneName); // getSceneModeName
                default -> { } // executeModeById, enterIntoModeById, (un)register listener
            }
            return true;
        }
    }
}
