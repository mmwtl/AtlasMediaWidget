package com.mmwtl.atlasmediawidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.AccelerateInterpolator;

public final class OverlayService extends Service
        implements MediaBridgeClient.Listener, MediaCardView.Listener, ArtworkLoader.Listener {
    static final String ACTION_START = "com.mmwtl.atlasmediawidget.action.START";
    static final String ACTION_STOP = "com.mmwtl.atlasmediawidget.action.STOP";
    static final String ACTION_REFRESH_STYLE = "com.mmwtl.atlasmediawidget.action.REFRESH_STYLE";
    private static final String CHANNEL_ID = "atlas_media_widget_service";
    private static final int NOTIFICATION_ID = 2407;
    private static final int POLL_VISIBLE_MS = 1_000;
    private static final int POLL_HIDDEN_MS = 1_500;
    private static final int PROGRESS_TICK_MS = 250;
    private static final long DISAPPEAR_ANIMATION_MS = 180L;
    private static final float HIDDEN_SCALE = 0.97f;
    private static final int HIDDEN_OFFSET_DP = 12;
    private static volatile boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SnapshotReducer reducer = new SnapshotReducer();
    private Prefs prefs;
    private WindowManager windowManager;
    private ForegroundAppDetector foregroundDetector;
    private MediaBridgeClient bridge;
    private ArtworkLoader artworkLoader;
    private RadioCatalog radioCatalog;
    private MediaSourceLauncher mediaSourceLauncher;
    private MediaCardView card;
    private WindowManager.LayoutParams cardParams;
    private MediaBridgeClient.State bridgeState = MediaBridgeClient.State.CONNECTING;
    private long loadedArtworkRevision = Long.MIN_VALUE;
    private String loadedArtworkKey = "";
    private long createdAt;
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;
    private int notificationState = -1;
    private boolean cardHiding;

    private final Runnable foregroundPoll = new Runnable() {
        @Override public void run() {
            if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                stopSelf();
                return;
            }
            if (!Settings.canDrawOverlays(OverlayService.this)
                    || !ForegroundAppDetector.hasUsageAccess(OverlayService.this)) {
                hideCard();
                updateNotification(2);
                main.postDelayed(this, POLL_HIDDEN_MS);
            } else if (foregroundDetector().isHomeForeground()) {
                showCard();
                updateNotification(1);
                main.postDelayed(this, POLL_VISIBLE_MS);
            } else {
                hideCard();
                updateNotification(0);
                main.postDelayed(this, POLL_HIDDEN_MS);
            }
        }
    };

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
        radioCatalog = RadioCatalog.loadBuiltIn(this);
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
        bridge.start();
        AppLog.info("Overlay service created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_REFRESH_STYLE.equals(intent.getAction())) {
            hideCardImmediately();
            main.removeCallbacks(foregroundPoll);
            main.post(foregroundPoll);
            return START_STICKY;
        }
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
        main.removeCallbacks(foregroundPoll);
        main.post(foregroundPoll);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        hideCardImmediately();
        if (bridge != null) bridge.stop();
        if (artworkLoader != null) artworkLoader.shutdown();
        stopForeground(STOP_FOREGROUND_REMOVE);
        running = false;
        AppLog.info("Overlay service destroyed");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onBridgeState(MediaBridgeClient.State state, String detail) {
        bridgeState = state;
        if (state == MediaBridgeClient.State.CONNECTED) {
            reducer.onConnected(SystemClock.elapsedRealtime());
            bridge.requestSnapshot();
        } else if (state == MediaBridgeClient.State.DISCONNECTED
                || state == MediaBridgeClient.State.INCOMPATIBLE) {
            reducer.onDisconnected(SystemClock.elapsedRealtime());
        }
        renderCurrent();
        if (card != null && !detail.isBlank()) {
            card.showTransientStatus(detail, state != MediaBridgeClient.State.CONNECTED);
        }
    }

    @Override public void onSnapshot(MediaSnapshot snapshot) {
        if (!reducer.accept(snapshot)) return;
        renderCurrent();
        loadArtwork(snapshot);
    }

    @Override public void onCommandResult(String requestId, int status, String message,
            long generation) {
        if (card == null) return;
        if (status == MediaBridgeContract.STATUS_OK) {
            card.showTransientStatus("Команда отправлена", false);
            main.postDelayed(this::renderCurrent, 3_000L);
        } else {
            String detail = message == null || message.isBlank()
                    ? "Команда отклонена: " + status : message;
            card.showTransientStatus(detail, true);
        }
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
                prefs.putInt(Prefs.KEY_POSITION_X, cardParams.x);
                prefs.putInt(Prefs.KEY_POSITION_Y, cardParams.y);
                return true;
            }
            default -> { return false; }
        }
    }

    @Override public void onCommand(String command) {
        bridge.sendCommand(command);
    }

    @Override public void onSeek(long positionMs) {
        bridge.seekTo(positionMs);
    }

    @Override public void onSource(MediaSource.Id source) {
        bridge.setSource(source);
    }

    @Override public void onOpenSource() {
        MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
        if (!mediaSourceLauncher.open(visible) && card != null) {
            card.showTransientStatus("Не удалось открыть источник", true);
        }
    }

    @Override public void onArtwork(long token, android.graphics.Bitmap bitmap) {
        if (card != null && card.isAttachedToWindow()) card.setArtwork(bitmap);
    }

    private void showCard() {
        if (card != null && card.isAttachedToWindow()) {
            if (cardHiding) {
                cardHiding = false;
                showCardImmediately(card);
                main.removeCallbacks(progressTick);
                main.post(progressTick);
            }
            return;
        }
        card = null;
        cardParams = null;
        cardHiding = false;
        loadedArtworkRevision = Long.MIN_VALUE;
        loadedArtworkKey = "";
        Rect bounds = availableBounds();
        CardStyle style = CardStyle.fromPreference(
                prefs.getInt(Prefs.KEY_CARD_STYLE, CardStyle.DEFAULT.preferenceValue));
        int maxWidth = Math.max(1, bounds.width() - Ui.dp(this, 32));
        int maxHeight = Math.max(1, bounds.height() - Ui.dp(this, 32));
        MediaCardView candidate = new MediaCardView(this,
                prefs.cardWidthDp(style), prefs.cardHeightDp(style),
                maxWidth, maxHeight, style, prefs.appearance(style), this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                candidate.cardWidth(),
                candidate.cardHeight(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        int storedX = prefs.getInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
        int storedY = prefs.getInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
        params.x = storedX == Prefs.POSITION_UNSET
                ? bounds.left + Math.max(0, (bounds.width() - candidate.cardWidth()) / 2) : storedX;
        params.y = storedY == Prefs.POSITION_UNSET
                ? bounds.top + Math.max(0, Math.round(bounds.height() * 0.62f)) : storedY;
        clampPosition(params, candidate, bounds);
        try {
            windowManager.addView(candidate, params);
            card = candidate;
            cardParams = params;
            AppLog.info("Overlay card attached t+"
                    + (SystemClock.elapsedRealtime() - createdAt) + " ms after service creation");
            renderCurrent();
            MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
            if (visible != null) loadArtwork(visible);
            bridge.requestSnapshot();
            main.removeCallbacks(progressTick);
            main.post(progressTick);
        } catch (SecurityException | WindowManager.BadTokenException error) {
            card = null;
            cardParams = null;
            AppLog.warn("Cannot attach media overlay", error);
        }
    }

    private void hideCard() {
        main.removeCallbacks(progressTick);
        if (card == null || windowManager == null || cardHiding) return;
        if (!card.isAttachedToWindow()) {
            card = null;
            cardParams = null;
            return;
        }
        MediaCardView target = card;
        target.animate().cancel();
        cardHiding = true;
        target.animate()
                .alpha(0f)
                .scaleX(HIDDEN_SCALE)
                .scaleY(HIDDEN_SCALE)
                .translationY(Ui.dp(this, HIDDEN_OFFSET_DP))
                .setDuration(DISAPPEAR_ANIMATION_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (card != target || !cardHiding) return;
                    removeCardView(target);
                    card = null;
                    cardParams = null;
                    cardHiding = false;
                })
                .start();
    }

    private void showCardImmediately(MediaCardView target) {
        target.animate().cancel();
        target.setAlpha(1f);
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setTranslationY(0f);
    }

    private void hideCardImmediately() {
        main.removeCallbacks(progressTick);
        if (card != null) {
            cardHiding = false;
            card.animate().cancel();
            removeCardView(card);
        }
        card = null;
        cardParams = null;
    }

    private void removeCardView(MediaCardView target) {
        if (windowManager == null) return;
        try {
            windowManager.removeViewImmediate(target);
        } catch (IllegalArgumentException ignored) {
            // OEM launcher may already have detached the overlay.
        }
    }

    private void renderCurrent() {
        if (card == null) return;
        MediaSnapshot visible = reducer.visibleSnapshot(SystemClock.elapsedRealtime());
        if (visible == null) card.renderDisconnected(stateDetail());
        else card.renderSnapshot(visible, reducer.isConnected(), radioCatalog.display(visible));
    }

    private String stateDetail() {
        return switch (bridgeState) {
            case CONNECTING, REGISTERING -> getString(R.string.bridge_connecting);
            case INCOMPATIBLE -> "Несовместимая версия GInputBridge mediaapi";
            default -> getString(R.string.bridge_disconnected);
        };
    }

    private void loadArtwork(MediaSnapshot snapshot) {
        RadioDisplay radioDisplay = radioCatalog.display(snapshot);
        ArtworkRef artwork = radioDisplay == null
                ? ArtworkRef.contentUri(snapshot.artworkUri)
                : prefs.getBoolean(Prefs.KEY_SHOW_RADIO_COVERS, true)
                        ? radioDisplay.artwork : ArtworkRef.NONE;
        String artworkKey = artwork.cacheKey();
        if (snapshot.artworkRevision == loadedArtworkRevision
                && artworkKey.equals(loadedArtworkKey)) return;
        loadedArtworkRevision = snapshot.artworkRevision;
        loadedArtworkKey = artworkKey;
        if (card != null) card.setArtwork(null);
        artworkLoader.load(artwork, snapshot.generation, snapshot.artworkRevision);
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
        params.x = Math.max(bounds.left,
                Math.min(params.x, Math.max(bounds.left, bounds.right - target.cardWidth())));
        int height = target.cardHeight();
        params.y = Math.max(bounds.top,
                Math.min(params.y, Math.max(bounds.top, bounds.bottom - height)));
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
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(text))
                .setContentIntent(settings)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, getString(R.string.stop), stop).build())
                .build();
    }

    private void updateNotification(int state) {
        if (state == notificationState) return;
        notificationState = state;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(state));
    }
}
