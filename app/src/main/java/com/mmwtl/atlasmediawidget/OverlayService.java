package com.mmwtl.atlasmediawidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class OverlayService extends Service
        implements MediaBridgeClient.Listener, MediaCardView.Listener, ArtworkLoader.Listener,
        RadioArtworkLoader.Listener {
    static final String ACTION_START = "com.mmwtl.atlasmediawidget.action.START";
    static final String ACTION_STOP = "com.mmwtl.atlasmediawidget.action.STOP";
    static final String ACTION_REFRESH_STYLE = "com.mmwtl.atlasmediawidget.action.REFRESH_STYLE";
    static final String ACTION_WIDGET_REFRESH = "com.mmwtl.atlasmediawidget.WIDGET_REFRESH";
    private final java.util.Map<Integer, AtlasMediaWidgetProvider.Frame> widgetFrames = new java.util.HashMap<>();
    private final java.util.Set<Integer> openSourceWidgetIds = new java.util.HashSet<>();
    private android.graphics.Bitmap currentArtwork;
    private long lastWidgetSnapshotAt;
    private java.util.List<?> widgetRenderKey;
    private String pendingWidgetCommand;
    private long pendingWidgetCommandAt;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener widgetPreferences =
            (preferences, key) -> {
                if (!Prefs.KEY_SERVICE_ENABLED.equals(key)) {
                    this.main.removeCallbacks(this.refreshWidgets);
                    this.main.postDelayed(this.refreshWidgets, 100L);
                }
            };
    private final Runnable refreshWidgets = () -> {
        widgetRenderKey = null;
        syncDisplayMode();
    };
    private final Runnable widgetTick = new Runnable() {
        @Override public void run() {
            if (destroyed || !prefs.isWidgetMode()) return;
            if (getSystemService(android.os.PowerManager.class).isInteractive()) {
                renderWidgets(false);
                if (bridgeState == MediaBridgeClient.State.CONNECTED) bridge.requestSnapshot();
            }
            main.postDelayed(this, 5_000L);
        }
    };

    static OverlayService current() { return instance; }

    private static final String CHANNEL_ID = "atlas_media_widget_service";
    private static final int NOTIFICATION_ID = 2407;
    private static final int PROGRESS_TICK_MS = 250;
    private static final long SNAPSHOT_RECONCILIATION_MS = 5_000L;
    private static volatile boolean running;
    private static volatile OverlayService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService foregroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "atlas-home-detector");
        thread.setDaemon(true);
        return thread;
    });
    private final SnapshotReducer reducer = new SnapshotReducer();
    private final TransportSnapshotGuard transportSnapshotGuard = new TransportSnapshotGuard();
    private final OnlineSnapshotStabilizer onlineSnapshotStabilizer =
            new OnlineSnapshotStabilizer();
    private final CardSuppressionPolicy cardSuppression = new CardSuppressionPolicy();
    private final PendingRadioNavigation pendingRadioNavigation =
            new PendingRadioNavigation();
    private Prefs prefs;
    private WindowManager windowManager;
    private ForegroundAppDetector foregroundDetector;
    private MediaBridgeClient bridge;
    private ArtworkLoader artworkLoader;
    private RadioArtworkLoader radioArtworkLoader;
    private MediaSourceLauncher mediaSourceLauncher;
    private MediaCardView card;
    private WindowManager.LayoutParams cardParams;
    private MediaBridgeClient.State bridgeState = MediaBridgeClient.State.CONNECTING;
    private long loadedArtworkRevision = Long.MIN_VALUE;
    private String loadedArtworkKey = "";
    private long expectedArtworkToken = Long.MIN_VALUE;
    private long createdAt;
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;
    private int notificationState = -1;
    private boolean foregroundQueryInFlight;
    private boolean visibilityCheckPending;
    private boolean visibilityCheckPendingFast;
    private long visibilityRequestGeneration;
    private boolean deviceWasReady;
    private boolean visibilityReceiverRegistered;
    private boolean destroyed;
    private long fastProbeUntil;
    private RadioStationLists radioStations = RadioStationLists.EMPTY;
    private boolean radioStationsRequestInFlight;

    private final Runnable transportReconcile = () -> {
        if (bridgeState == MediaBridgeClient.State.CONNECTED) {
            AppLog.info("Requesting post-command reconciliation snapshot");
            bridge.requestSnapshot();
        }
    };

    private final Runnable snapshotReconcile = new Runnable() {
        @Override public void run() {
            if (destroyed || bridgeState != MediaBridgeClient.State.CONNECTED
                    || card == null || !card.isAttachedToWindow()) return;
            MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
            if (visible == null || !visible.isPlaying()) return;
            AppLog.info("Requesting low-frequency playback reconciliation snapshot");
            bridge.requestSnapshot();
            main.postDelayed(this, SNAPSHOT_RECONCILIATION_MS);
        }
    };

    private final BroadcastReceiver visibilityWakeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                deviceWasReady = false;
                hideCard();
                scheduleForegroundPoll(ForegroundPollPolicy.HIDDEN_DELAY_MS);
                return;
            }
            AppLog.info("Immediate HOME check requested by " + action);
            requestImmediateVisibilityCheck();
        }
    };

    private final Runnable foregroundPoll = () -> runVisibilityQuery(false);
    private final Runnable accessibilityFastPoll = () -> runVisibilityQuery(true);

    private void runVisibilityQuery(boolean accessibilityFast) {
        if (destroyed || prefs.isWidgetMode()) return;
        if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            stopSelf();
            return;
        }
        if (!Settings.canDrawOverlays(OverlayService.this)
                || !ForegroundAppDetector.hasUsageAccess(OverlayService.this)
                || !AccessibilityWindowState.isEnabled(OverlayService.this)) {
            hideCard();
            updateNotification(2);
            scheduleForegroundPoll(ForegroundPollPolicy.HIDDEN_DELAY_MS);
            return;
        }
        ForegroundAppDetector detector = foregroundDetector();
        boolean deviceReady = detector.isDeviceReady();
        long now = SystemClock.elapsedRealtime();
        if (deviceReady && !deviceWasReady) {
            fastProbeUntil = now + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        }
        deviceWasReady = deviceReady;
        if (!deviceReady) {
            hideCard();
            updateNotification(0);
            scheduleForegroundPoll(ForegroundPollPolicy.nextDelay(
                    false, false, now, fastProbeUntil));
            return;
        }
        if (foregroundQueryInFlight) {
            if (!visibilityCheckPending) {
                visibilityCheckPendingFast = accessibilityFast;
            } else {
                visibilityCheckPendingFast &= accessibilityFast;
            }
            visibilityCheckPending = true;
            return;
        }
        foregroundQueryInFlight = true;
        long queryGeneration = visibilityRequestGeneration;
        try {
            foregroundExecutor.execute(() -> {
                boolean homeVisible = false;
                try {
                    if (accessibilityFast) {
                        Boolean fastResult = detector.isHomeVisibleFromAccessibility();
                        homeVisible = fastResult != null
                                ? fastResult
                                : detector.isHomeVisible();
                    } else {
                        homeVisible = detector.isHomeVisible();
                    }
                } catch (RuntimeException error) {
                    AppLog.warn("HOME visibility query failed", error);
                }
                boolean result = homeVisible;
                main.post(() -> applyForegroundResult(result, queryGeneration));
            });
        } catch (RejectedExecutionException ignored) {
            foregroundQueryInFlight = false;
        }
    }

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (card != null && card.isAttachedToWindow()) {
                long now = SystemClock.elapsedRealtime();
                MediaSnapshot visible = reducer.visibleSnapshot(now);
                if (visible == null) {
                    card.renderDisconnected(stateDetail());
                } else {
                    card.tick(now);
                }
                main.postDelayed(this, PROGRESS_TICK_MS);
            }
        }
    };

    static void start(android.content.Context context) {
        context.startForegroundService(new Intent(context, OverlayService.class).setAction(ACTION_START));
    }

    static void stop(android.content.Context context) {
        context.startService(new Intent(context, OverlayService.class).setAction(ACTION_STOP));
    }

    static void refreshStyle(android.content.Context context) {
        context.startService(new Intent(context, OverlayService.class).setAction(ACTION_REFRESH_STYLE));
    }

    static boolean isRunning() {
        return running;
    }

    @Override public void onCreate() {
        super.onCreate();
        createdAt = SystemClock.elapsedRealtime();
        prefs = new Prefs(this);
        windowManager = getSystemService(WindowManager.class);
        artworkLoader = new ArtworkLoader(this, this);
        radioArtworkLoader = new RadioArtworkLoader(this, this);
        mediaSourceLauncher = new MediaSourceLauncher(this);
        bridge = new MediaBridgeClient(this, this);
        createNotificationChannel();
        Notification notification = buildNotification(0);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        notificationState = 0;
        running = true;
        instance = this;
        fastProbeUntil = createdAt + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        if (!prefs.isWidgetMode()) registerVisibilityWakeReceiver();
        prefs.observe(widgetPreferences);
        bridge.start();
        AppLog.info("Overlay service created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            if (!prefs.isWidgetMode()) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        if (ACTION_START.equals(action)) prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
        if (ACTION_REFRESH_STYLE.equals(action) || ACTION_WIDGET_REFRESH.equals(action)) {
            hideCardImmediately();
            widgetRenderKey = null;
        }
        syncDisplayMode();
        if (AtlasMediaWidgetProvider.ACTION_COMMAND.equals(action) && prefs.isWidgetMode()
                && AtlasMediaWidgetProvider.owns(this, intent.getIntExtra(
                        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0))) {
            int id = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            String control = intent.getStringExtra(AtlasMediaWidgetProvider.EXTRA_CONTROL);
            if ("sources".equals(control) || "dismiss_sources".equals(control)
                    || control != null && control.startsWith("source_")) {
                executeWidgetSourceAction(id, control);
            } else {
                pendingWidgetCommand = control;
                pendingWidgetCommandAt = SystemClock.elapsedRealtime();
                executeWidgetCommand();
                if (pendingWidgetCommand != null) bridge.requestSnapshot();
            }
        }
        return START_STICKY;
    }

    private void syncDisplayMode() {
        if (destroyed) return;
        main.removeCallbacks(widgetTick);
        if (prefs.isWidgetMode()) {
            unregisterVisibilityWakeReceiver();
            visibilityRequestGeneration++;
            visibilityCheckPending = false;
            main.removeCallbacks(foregroundPoll);
            main.removeCallbacks(accessibilityFastPoll);
            hideCardImmediately();
            if (AtlasMediaWidgetProvider.ids(this).length == 0) {
                stopSelf();
                return;
            }
            renderWidgets(true);
            main.postDelayed(widgetTick, 5_000L);
            updateNotification(3);
        } else {
            widgetFrames.clear();
            openSourceWidgetIds.clear();
            AtlasMediaWidgetProvider.showInactive(this,
                    "Неактивен: выбран режим «Оверлей»\nНажмите для выбора режима");
            if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                stopSelf();
                return;
            }
            if (!visibilityReceiverRegistered) registerVisibilityWakeReceiver();
            requestImmediateVisibilityCheck();
        }
    }

    private void executeWidgetCommand() {
        if (pendingWidgetCommand == null) return;
        MediaSnapshot snapshot = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
        if (bridgeState != MediaBridgeClient.State.CONNECTED || snapshot == null
                || SystemClock.elapsedRealtime() - lastWidgetSnapshotAt > 20_000L) return;
        String command = pendingWidgetCommand;
        pendingWidgetCommand = null;
        if (SystemClock.elapsedRealtime() - pendingWidgetCommandAt > 5_000L) return;
        if ("PLAY_PAUSE".equals(command)) command = PlayPauseActionPolicy.command(
                snapshot.audioSource, snapshot.isPlaying(), snapshot.capabilities);
        long capability = switch (command) {
            case "PLAY" -> MediaBridgeContract.CAP_PLAY;
            case "PAUSE" -> MediaBridgeContract.CAP_PAUSE;
            case "TOGGLE" -> MediaBridgeContract.CAP_TOGGLE;
            case "PREVIOUS", "NEXT" -> prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false)
                    && visibleSource() == MediaSource.Id.RADIO ? MediaBridgeContract.CAP_TUNE_RADIO
                    : "NEXT".equals(command) ? MediaBridgeContract.CAP_NEXT : MediaBridgeContract.CAP_PREVIOUS;
            default -> 0L;
        };
        if (capability != 0 && snapshot.supports(capability)) onCommand(command);
    }

    private void executeWidgetSourceAction(int widgetId, String control) {
        if ("dismiss_sources".equals(control)) {
            if (openSourceWidgetIds.remove(widgetId)) renderWidgets(true);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        MediaSnapshot snapshot = reducer.visibleSnapshot(now);
        if (bridgeState != MediaBridgeClient.State.CONNECTED || snapshot == null
                || now - lastWidgetSnapshotAt > 20_000L) {
            openSourceWidgetIds.remove(widgetId);
            renderWidgets(true);
            bridge.requestSnapshot();
            return;
        }
        if ("sources".equals(control)) {
            if (!openSourceWidgetIds.add(widgetId)) openSourceWidgetIds.remove(widgetId);
            renderWidgets(true);
            return;
        }
        if (!openSourceWidgetIds.contains(widgetId)) return;
        MediaSource.Id selected = MediaSource.Id.fromWire(control.substring("source_".length()));
        if (!selected.isWidgetChoice()
                || selected == MediaSource.selectedId(snapshot.audioSource, snapshot.sources).displayId()) return;
        MediaSource choice = null;
        for (MediaSource source : snapshot.sources) {
            if (source.id.displayId() == selected
                    && (choice == null || source.id == selected)) choice = source;
        }
        if (choice != null && choice.available
                && (choice.capabilities & MediaBridgeContract.CAP_SET_SOURCE) != 0L) {
            openSourceWidgetIds.remove(widgetId);
            renderWidgets(true);
            onSource(selected);
        }
    }

    private void renderWidgets(boolean force) {
        if (!prefs.isWidgetMode()) return;
        long now = SystemClock.elapsedRealtime();
        MediaSnapshot snapshot = reducer.isConnected() && now - lastWidgetSnapshotAt <= 20_000L
                ? reducer.visibleSnapshot(now) : null;
        boolean connected = reducer.isConnected() && (lastWidgetSnapshotAt == 0
                || now - lastWidgetSnapshotAt <= 20_000L);
        java.util.List<?> key = snapshot == null ? java.util.List.of("empty", connected)
                : java.util.Arrays.asList(
                snapshot.title, snapshot.artist, snapshot.album, snapshot.mediaId, snapshot.audioSource,
                snapshot.appSource, snapshot.ownerPackage, snapshot.duration, snapshot.playbackState,
                snapshot.capabilities, snapshot.backendConnected, snapshot.sources.stream()
                        .map(source -> java.util.List.of(source.id, source.available, source.connected,
                                source.selected, source.capabilities))
                        .collect(java.util.stream.Collectors.toList()),
                snapshot.artworkUri, snapshot.artworkRevision);
        int[] ids = AtlasMediaWidgetProvider.ids(this);
        java.util.Set<Integer> active = new java.util.HashSet<>();
        for (int id : ids) active.add(id);
        widgetFrames.keySet().retainAll(active);
        openSourceWidgetIds.retainAll(active);
        if (snapshot == null) openSourceWidgetIds.clear();
        var manager = android.appwidget.AppWidgetManager.getInstance(this);
        for (int id : ids) {
            try {
                if (force || !key.equals(widgetRenderKey) || !widgetFrames.containsKey(id)) {
                    var frame = new AtlasMediaWidgetProvider.Frame(this, prefs,
                            manager.getAppWidgetOptions(id), id, snapshot, currentArtwork, connected,
                            openSourceWidgetIds.contains(id), this);
                    manager.updateAppWidget(id, frame.views);
                    widgetFrames.put(id, frame);
                } else if (snapshot != null) {
                    manager.partiallyUpdateAppWidget(id, widgetFrames.get(id).progress(this, snapshot));
                }
            } catch (RuntimeException error) {
                AppLog.warn("Cannot render media widget " + id, error);
            }
        }
        widgetRenderKey = key;
    }

    @Override public void onDestroy() {
        destroyed = true;
        prefs.unobserve(widgetPreferences);
        widgetFrames.clear();
        openSourceWidgetIds.clear();
        main.removeCallbacksAndMessages(null);
        unregisterVisibilityWakeReceiver();
        foregroundExecutor.shutdownNow();
        hideCardImmediately();
        if (bridge != null) bridge.stop();
        if (artworkLoader != null) {
            expectedArtworkToken = artworkLoader.shutdown();
        }
        if (radioArtworkLoader != null) radioArtworkLoader.shutdown();
        stopForeground(STOP_FOREGROUND_REMOVE);
        running = false;
        if (instance == this) instance = null;
        AppLog.info("Overlay service destroyed");
        super.onDestroy();
    }

    private void applyForegroundResult(boolean homeVisible, long queryGeneration) {
        foregroundQueryInFlight = false;
        if (destroyed || prefs.isWidgetMode()) return;
        if (visibilityCheckPending || queryGeneration != visibilityRequestGeneration) {
            boolean fast = visibilityCheckPendingFast;
            visibilityCheckPending = false;
            visibilityCheckPendingFast = false;
            main.post(fast ? accessibilityFastPoll : foregroundPoll);
            return;
        }
        if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            stopSelf();
            return;
        }
        boolean deviceReady = foregroundDetector().isDeviceReady();
        long now = SystemClock.elapsedRealtime();
        cardSuppression.onVisibility(homeVisible, now);
        boolean cardAllowed = cardSuppression.isCardAllowed(now);
        if (homeVisible && deviceReady && cardAllowed) {
            showCard();
            updateNotification(1);
        } else if (homeVisible && deviceReady) {
            // Keep a visible card in place while the launched activity is still taking over.
            // Remove it only after a non-HOME snapshot is confirmed.
            updateNotification(card != null && card.isAttachedToWindow() ? 1 : 0);
        } else {
            hideCard();
            updateNotification(0);
        }
        scheduleForegroundPoll(ForegroundPollPolicy.nextDelay(
                homeVisible && cardAllowed, deviceReady, now, fastProbeUntil));
        if (!cardAllowed && homeVisible) {
            long remaining = Math.max(1L, cardSuppression.deadline() - now);
            scheduleForegroundPoll(Math.min(
                    remaining, ForegroundPollPolicy.VISIBLE_DELAY_MS));
        }
    }

    private void requestImmediateVisibilityCheck() {
        requestImmediateVisibilityCheck(false);
    }

    private void requestImmediateVisibilityCheck(boolean accessibilityEvent) {
        if (prefs.isWidgetMode()) return;
        visibilityRequestGeneration++;
        fastProbeUntil = SystemClock.elapsedRealtime()
                + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        main.removeCallbacks(foregroundPoll);
        main.removeCallbacks(accessibilityFastPoll);
        if (foregroundQueryInFlight) {
            if (!visibilityCheckPending) {
                visibilityCheckPendingFast = accessibilityEvent;
            } else {
                visibilityCheckPendingFast &= accessibilityEvent;
            }
            visibilityCheckPending = true;
        } else {
            main.post(accessibilityEvent ? accessibilityFastPoll : foregroundPoll);
        }
    }

    private void scheduleForegroundPoll(long delayMs) {
        if (destroyed) return;
        main.removeCallbacks(foregroundPoll);
        main.removeCallbacks(accessibilityFastPoll);
        main.postDelayed(foregroundPoll, delayMs);
    }

    @SuppressWarnings("deprecation")
    private void registerVisibilityWakeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(visibilityWakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(visibilityWakeReceiver, filter);
        }
        visibilityReceiverRegistered = true;
    }

    private void unregisterVisibilityWakeReceiver() {
        if (!visibilityReceiverRegistered) return;
        try {
            unregisterReceiver(visibilityWakeReceiver);
        } catch (IllegalArgumentException ignored) {
            // The process may have already discarded receiver registration.
        }
        visibilityReceiverRegistered = false;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    static void onAccessibilityWindowsChanged() {
        OverlayService service = instance;
        if (service != null && !service.destroyed) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                service.requestImmediateVisibilityCheck(true);
            } else {
                service.main.post(() -> service.requestImmediateVisibilityCheck(true));
            }
        }
    }

    @Override public void onBridgeState(MediaBridgeClient.State state, String detail) {
        bridgeState = state;
        if (state == MediaBridgeClient.State.CONNECTED) {
            reducer.onConnected(SystemClock.elapsedRealtime());
            bridge.requestSnapshot();
            requestRadioStations();
        } else if (state == MediaBridgeClient.State.DISCONNECTED
                || state == MediaBridgeClient.State.INCOMPATIBLE) {
            if (artworkLoader != null) expectedArtworkToken = artworkLoader.clear();
            transportSnapshotGuard.clear();
            onlineSnapshotStabilizer.clear();
            main.removeCallbacks(transportReconcile);
            main.removeCallbacks(snapshotReconcile);
            reducer.onDisconnected(SystemClock.elapsedRealtime());
            loadedArtworkRevision = Long.MIN_VALUE;
            loadedArtworkKey = "";
            currentArtwork = null;
            radioStations = RadioStationLists.EMPTY;
            radioStationsRequestInFlight = false;
            pendingRadioNavigation.clear();
            if (radioArtworkLoader != null) radioArtworkLoader.clear();
            if (card != null) card.setRadioStations(RadioStationLists.EMPTY);
        }
        renderCurrent();
        scheduleSnapshotReconcile();
        if (card != null && !detail.isBlank()) {
            card.showTransientStatus(detail, state != MediaBridgeClient.State.CONNECTED);
        }
    }

    @Override public void onSnapshot(MediaSnapshot snapshot) {
        long now = SystemClock.elapsedRealtime();
        snapshot = onlineSnapshotStabilizer.stabilize(snapshot, now);
        if (transportSnapshotGuard.shouldDefer(snapshot, now)) {
            AppLog.info("Holding transient empty ONLINE snapshot generation="
                    + snapshot.generation + " during transport reconciliation");
            return;
        }
        if (!reducer.accept(snapshot)) return;
        lastWidgetSnapshotAt = now;
        executeWidgetCommand();
        pendingRadioNavigation.cancelIfSourceChanged(visibleSource());
        loadArtwork(snapshot);
        renderCurrent();
        scheduleSnapshotReconcile();
    }

    @Override public void onCommandResult(String requestId, int status, String message,
            long generation) {
        AppLog.info("Media command result request=" + requestId + " status=" + status
                + " generation=" + generation + " message=" + message);
        if (status != MediaBridgeContract.STATUS_OK) {
            transportSnapshotGuard.clear();
            main.removeCallbacks(transportReconcile);
            bridge.requestSnapshot();
        }
        if (card == null) return;
        card.onTransportResult(status == MediaBridgeContract.STATUS_OK);
        if (status == MediaBridgeContract.STATUS_OK) {
            card.showTransientStatus("Команда отправлена", false);
            main.postDelayed(this::renderCurrent, 3_000L);
        } else {
            String detail = message == null || message.isBlank()
                    ? "Команда отклонена: " + status : message;
            card.showTransientStatus(detail, true);
        }
    }

    @Override public void onRadioStations(RadioStationLists lists) {
        radioStationsRequestInFlight = false;
        radioStations = lists == null ? RadioStationLists.EMPTY : lists;
        AppLog.info("Radio station lists received: saved=" + radioStations.saved.size()
                + " favorites=" + radioStations.favorites.size()
                + " generation=" + radioStations.generation);
        if (card != null) card.setRadioStations(radioStations);
        int direction = pendingRadioNavigation.consume(visibleSource());
        if (direction != 0) {
            MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
            List<RadioStation> navigationStations = radioNavigationStations();
            RadioStation target = RadioStationNavigator.adjacent(
                    navigationStations, visible, direction);
            if (target == null) {
                if (card != null) card.showTransientStatus(navigationStations.isEmpty()
                        ? emptyRadioNavigationMessage()
                        : noOtherRadioNavigationMessage(), true);
            } else {
                tuneRadio(target);
            }
        }
    }

    @Override public void onRadioStationsError(int status, String message) {
        radioStationsRequestInFlight = false;
        pendingRadioNavigation.clear();
        radioStations = RadioStationLists.EMPTY;
        String detail = switch (status) {
            case 5 -> "Радиосервис недоступен";
            case 6 -> "Списки радиостанций не поддерживаются";
            default -> message == null || message.isBlank()
                    ? "Список радиостанций недоступен (" + status + ')' : message;
        };
        AppLog.info("Radio station list request failed: status=" + status
                + " message=" + detail);
        if (card != null) card.setRadioStationsError(detail);
    }

    @Override public boolean onDragTouch(View view, MotionEvent event) {
        if (card == null || cardParams == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartX = cardParams.x;
                dragStartY = cardParams.y;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                cardParams.x = dragStartX + Math.round(event.getRawX() - dragStartRawX);
                cardParams.y = dragStartY + Math.round(event.getRawY() - dragStartRawY);
                clampPosition(cardParams, card, availableBounds());
                try {
                    windowManager.updateViewLayout(card, cardParams);
                } catch (IllegalArgumentException error) {
                    AppLog.warn("Cannot move media overlay", error);
                }
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                Rect bounds = availableBounds();
                OverlayCorner corner = storedCorner();
                OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(corner,
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        card.cardWidth(), card.cardHeight(),
                        cardParams.x + bounds.left, cardParams.y + bounds.top);
                prefs.putPosition(corner, offsets.x(), offsets.y());
                return true;
            }
            default -> { return false; }
        }
    }

    @Override public void onCommand(String command) {
        if (prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false)
                && visibleSource() == MediaSource.Id.RADIO
                && ("PREVIOUS".equals(command) || "NEXT".equals(command))) {
            MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
            List<RadioStation> navigationStations = radioNavigationStations();
            RadioStation target = RadioStationNavigator.adjacent(
                    navigationStations, visible, "NEXT".equals(command) ? 1 : -1);
            if (target == null) {
                if (!navigationStations.isEmpty()) {
                    if (card != null) card.showTransientStatus(
                            noOtherRadioNavigationMessage(), false);
                    return;
                }
                pendingRadioNavigation.schedule("NEXT".equals(command) ? 1 : -1);
                requestRadioStations();
                if (card != null) card.showTransientStatus(
                        loadingRadioNavigationMessage(), false);
                return;
            }
            tuneRadio(target);
            return;
        }
        beginTransportReconciliation();
        String requestId = bridge.sendCommand(command);
        AppLog.info("Sending media command request=" + requestId + " command=" + command
                + " source=" + visibleSource());
    }

    private List<RadioStation> radioNavigationStations() {
        return radioStations.navigationStations(
                prefs.getBoolean(Prefs.KEY_RADIO_FAVORITES_NAVIGATION, false));
    }

    private boolean navigatesRadioFavorites() {
        return prefs.getBoolean(Prefs.KEY_RADIO_FAVORITES_NAVIGATION, false);
    }

    private String emptyRadioNavigationMessage() {
        return navigatesRadioFavorites()
                ? "Нет избранных станций" : "Нет сохранённых станций";
    }

    private String noOtherRadioNavigationMessage() {
        return navigatesRadioFavorites()
                ? "Нет другой избранной станции" : "Нет другой сохранённой станции";
    }

    private String loadingRadioNavigationMessage() {
        return navigatesRadioFavorites()
                ? "Загрузка избранных станций…" : "Загрузка сохранённых станций…";
    }

    @Override public void onSeek(long positionMs) {
        beginTransportReconciliation();
        String requestId = bridge.seekTo(positionMs);
        AppLog.info("Sending media command request=" + requestId + " command=SEEK_TO"
                + " position=" + positionMs + " source=" + visibleSource());
    }

    @Override public void onSource(MediaSource.Id source) {
        transportSnapshotGuard.clear();
        main.removeCallbacks(transportReconcile);
        pendingRadioNavigation.cancelIfSourceChanged(source);
        String requestId = bridge.setSource(source);
        AppLog.info("Sending media command request=" + requestId + " command=SET_SOURCE"
                + " source=" + source);
    }

    @Override public void onRadioStationsRequested() {
        requestRadioStations();
    }

    @Override public void onRadioStation(RadioStation station) {
        tuneRadio(station);
    }

    @Override public void onRadioArtworkRequested(RadioStation station) {
        if (radioArtworkLoader != null) radioArtworkLoader.load(station);
    }

    @Override public void onRadioArtwork(String key, android.graphics.Bitmap bitmap) {
        if (card != null) card.setRadioArtwork(key, bitmap);
    }

    private void requestRadioStations() {
        if (radioStationsRequestInFlight
                || bridgeState != MediaBridgeClient.State.CONNECTED) return;
        radioStationsRequestInFlight = true;
        bridge.requestRadioStations();
    }

    private void tuneRadio(RadioStation station) {
        MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
        if (station == null || visible == null
                || !visible.supports(MediaBridgeContract.CAP_TUNE_RADIO)) {
            if (card != null) card.showTransientStatus(
                    "Прямое переключение радио недоступно", true);
            return;
        }
        beginTransportReconciliation();
        String requestId = bridge.tuneRadio(station);
        AppLog.info("Sending radio tune request=" + requestId + " station=" + station.id
                + " frequencyKHz=" + station.frequencyKHz + " band=" + station.band);
    }

    private void beginTransportReconciliation() {
        long now = SystemClock.elapsedRealtime();
        transportSnapshotGuard.begin(reducer.visibleSnapshot(now), now);
        main.removeCallbacks(transportReconcile);
        main.postDelayed(transportReconcile, TransportSnapshotGuard.RECONCILIATION_MS);
    }

    private MediaSource.Id visibleSource() {
        MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
        return visible == null ? MediaSource.Id.UNKNOWN
                : MediaSource.selectedId(visible.audioSource, visible.sources).displayId();
    }

    @Override public void onOpenSource() {
        MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
        cardSuppression.suppress(SystemClock.elapsedRealtime(), 1_500L);
        if (!mediaSourceLauncher.open(visible) && card != null) {
            card.showTransientStatus("Не удалось открыть источник", true);
        }
    }

    @Override public void onArtwork(long token, android.graphics.Bitmap bitmap) {
        if (token != expectedArtworkToken) return;
        currentArtwork = bitmap;
        widgetRenderKey = null;
        renderWidgets(true);
        if (card != null) card.setArtwork(bitmap);
    }

    private void showCard() {
        if (windowManager == null) {
            windowManager = getSystemService(WindowManager.class);
        }
        if (card != null) {
            if (card.isAttachedToWindow()) {
                card.setVisibility(View.VISIBLE);
                return;
            }
            if (cardParams != null) {
                Rect bounds = availableBounds();
                migrateLegacyPosition(bounds, card.cardWidth(), card.cardHeight());
                applyStoredPosition(cardParams, card.cardWidth(), card.cardHeight(), bounds);
                try {
                    card.setVisibility(View.VISIBLE);
                    windowManager.addView(card, cardParams);
                    AppLog.info("Overlay card reattached in "
                            + (SystemClock.elapsedRealtime() - createdAt) + " ms");
                    renderCurrent();
                    card.setRadioStations(radioStations);
                    MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
                    if (visible != null) loadArtwork(visible);
                    main.removeCallbacks(progressTick);
                    main.post(progressTick);
                    scheduleSnapshotReconcile();
                    return;
                } catch (SecurityException | WindowManager.BadTokenException
                         | IllegalArgumentException | IllegalStateException error) {
                    AppLog.warn("Cannot reattach cached overlay window", error);
                    discardCard();
                }
            } else {
                discardCard();
            }
        }
        loadedArtworkRevision = Long.MIN_VALUE;
        loadedArtworkKey = "";
        Rect bounds = availableBounds();
        CardStyle style = CardStyle.fromPreference(prefs.getInt(Prefs.KEY_CARD_STYLE,
                CardStyle.DEFAULT.preferenceValue));
        int maxWidth = Math.max(1, bounds.width() - Ui.dp(this, 32));
        int maxHeight = Math.max(1, bounds.height() - Ui.dp(this, 32));
        MediaCardView candidate = new MediaCardView(this,
                prefs.cardWidthPx(), prefs.cardHeightPx(),
                maxWidth, maxHeight, style, prefs.appearance(style),
                prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false),
                prefs.getBoolean(Prefs.KEY_DRAG_HANDLE_VISIBLE, true),
                prefs.radioFavoritesColumns(), prefs.radioFavoritesRows(), this);
        migrateLegacyPosition(bounds, candidate.cardWidth(), candidate.cardHeight());
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                candidate.cardWidth(),
                candidate.cardHeight(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        applyStoredPosition(params, candidate.cardWidth(), candidate.cardHeight(), bounds);
        try {
            Trace.beginSection("AtlasOverlay.addView");
            try {
                windowManager.addView(candidate, params);
            } finally {
                Trace.endSection();
            }
            card = candidate;
            card.setArtwork(currentArtwork);
            cardParams = params;
            AppLog.info("Overlay card attached t+"
                    + (SystemClock.elapsedRealtime() - createdAt) + " ms after service creation");
            renderCurrent();
            card.setRadioStations(radioStations);
            MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
            if (visible != null) loadArtwork(visible);
            bridge.requestSnapshot();
            main.removeCallbacks(progressTick);
            main.post(progressTick);
            scheduleSnapshotReconcile();
        } catch (SecurityException | WindowManager.BadTokenException error) {
            discardCard();
            AppLog.warn("Cannot attach media overlay", error);
        }
    }

    private void hideCard() {
        main.removeCallbacks(progressTick);
        main.removeCallbacks(snapshotReconcile);
        if (card == null || windowManager == null) return;
        MediaCardView target = card;
        target.animate().cancel();
        target.setAlpha(1f);
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setTranslationY(0f);
        // Keep the view reference and its LayoutParams. During a WindowManager traversal
        // isAttachedToWindow() can briefly be false while the window is still registered;
        // discarding the reference here can leave an orphaned window and add a second card on
        // the next HOME check. This mirrors the working AtlasAppWidget behavior.
        target.setVisibility(View.GONE);
        removeCardView(target);
    }

    private void hideCardImmediately() {
        main.removeCallbacks(progressTick);
        main.removeCallbacks(snapshotReconcile);
        discardCard();
    }

    private void discardCard() {
        if (card != null) {
            card.animate().cancel();
            removeCardView(card);
        }
        card = null;
        cardParams = null;
    }

    private void removeCardView(MediaCardView target) {
        if (windowManager == null) return;
        try {
            Trace.beginSection("AtlasOverlay.removeView");
            try {
                windowManager.removeViewImmediate(target);
            } finally {
                Trace.endSection();
            }
        } catch (IllegalArgumentException ignored) {
            // OEM launcher may already have detached the overlay.
        }
    }

    private void renderCurrent() {
        renderWidgets(false);
        if (card == null) return;
        MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
        if (visible == null) card.renderDisconnected(stateDetail());
        else card.renderSnapshot(visible, reducer.isConnected());
    }

    private void scheduleSnapshotReconcile() {
        main.removeCallbacks(snapshotReconcile);
        if (destroyed || bridgeState != MediaBridgeClient.State.CONNECTED
                || card == null || !card.isAttachedToWindow()) return;
        MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
        if (visible != null && visible.isPlaying()) {
            main.postDelayed(snapshotReconcile, SNAPSHOT_RECONCILIATION_MS);
        }
    }

    private String stateDetail() {
        return switch (bridgeState) {
            case CONNECTING, REGISTERING -> getString(R.string.bridge_connecting);
            case INCOMPATIBLE -> "Несовместимая версия Atlas Media API";
            default -> getString(R.string.bridge_disconnected);
        };
    }

    private void loadArtwork(MediaSnapshot snapshot) {
        ArtworkRef artwork = !snapshot.artworkUri.isBlank()
                ? ArtworkRef.mediaUri(snapshot.artworkUri)
                : ArtworkRef.NONE;
        String artworkKey = snapshot.audioSource + ":" + snapshot.mediaId + ":"
                + snapshot.title + ":" + artwork.cacheKey();
        if (snapshot.artworkRevision == loadedArtworkRevision
                && artworkKey.equals(loadedArtworkKey)) return;
        currentArtwork = null;
        widgetRenderKey = null;
        renderWidgets(true);
        loadedArtworkRevision = snapshot.artworkRevision;
        loadedArtworkKey = artworkKey;
        // Keep the current bitmap visible until the replacement has decoded. ArtworkLoader
        // still reports null for an empty or failed load, so genuinely unavailable art clears.
        expectedArtworkToken = artworkLoader.load(
                artwork, snapshot.generation, snapshot.artworkRevision);
    }

    private ForegroundAppDetector foregroundDetector() {
        if (foregroundDetector == null) foregroundDetector = new ForegroundAppDetector(this);
        return foregroundDetector;
    }

    private Rect availableBounds() {
        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        Rect full = metrics.getBounds();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect safe = new Rect(full.left + insets.left, full.top + insets.top,
                full.right - insets.right, full.bottom - insets.bottom);
        return safe.width() > 0 && safe.height() > 0 ? safe : new Rect(full);
    }

    private void clampPosition(WindowManager.LayoutParams params, MediaCardView target, Rect bounds) {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(OverlayCorner.TOP_START,
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                target.cardWidth(), target.cardHeight(), params.x, params.y);
        params.x = position.x() - bounds.left;
        params.y = position.y() - bounds.top;
    }

    private OverlayCorner storedCorner() {
        OverlayCorner corner = OverlayCorner.fromPreference(
                prefs.getString(Prefs.KEY_POSITION_CORNER, null));
        return corner == null ? OverlayCorner.TOP_START : corner;
    }

    private void migrateLegacyPosition(Rect bounds, int cardWidth, int cardHeight) {
        OverlayPositionMigration.migrate(prefs, bounds, cardWidth, cardHeight);
    }

    private void applyStoredPosition(WindowManager.LayoutParams params, int cardWidth,
            int cardHeight, Rect bounds) {
        OverlayCorner corner = storedCorner();
        int offsetX = Math.max(0, prefs.getInt(Prefs.KEY_POSITION_X, 0));
        int offsetY = Math.max(0, prefs.getInt(Prefs.KEY_POSITION_Y, 0));
        OverlayGeometry.Position position = OverlayGeometry.positionFor(corner,
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                cardWidth, cardHeight, offsetX, offsetY);
        params.x = position.x() - bounds.left;
        params.y = position.y() - bounds.top;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(int state) {
        int text = state == 1 ? R.string.notification_visible
                : state == 2 ? R.string.notification_permission_error
                : R.string.notification_hidden;
        PendingIntent settings = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(state == 3 ? "Медиа для системных виджетов" : getString(text))
                .setContentIntent(settings)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (!prefs.isWidgetMode()) builder.addAction(new Notification.Action.Builder(null,
                getString(R.string.stop), stop).build());
        return builder.build();
    }

    private void updateNotification(int state) {
        if (state == notificationState) return;
        notificationState = state;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(state));
    }
}
