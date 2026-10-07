package com.mmwtl.atlasmediawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

/** Opens the current player or the progress scrubber from the widget, apart from the settings task. */
public final class WidgetControlActivity extends Activity implements MediaBridgeClient.Listener {
    private Prefs prefs;
    private MediaBridgeClient mediaBridgeClient;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable openTimeout = () -> failOpen("Нет соединения с медиасервисом");
    private String requestedWidgetControl;
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private MediaCardView scrubber;
    private boolean scrubbing;
    private boolean hidesWidgetStrip;
    private boolean scrubberShown;
    private boolean closingScrubber;
    private final Runnable scrubberIdle = this::closeScrubber;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        requestedWidgetControl = getIntent().getStringExtra(AtlasMediaWidgetProvider.EXTRA_CONTROL);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (!prefs.isWidgetMode() || !AtlasMediaWidgetProvider.owns(this, id)
                || !("open".equals(requestedWidgetControl) || "scrub".equals(requestedWidgetControl))) {
            finish();
            return;
        }
        widgetId = id;
        mediaBridgeClient = new MediaBridgeClient(this, this);
        AtlasMediaWidgetProvider.refresh(this);
        if ("scrub".equals(requestedWidgetControl)) {
            requestedWidgetControl = null;
            showScrubber();
        } else {
            // Keep HOME visible while the bridge supplies the current source. No card or settings UI.
            getWindow().setLayout(1, 1);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (mediaBridgeClient == null || isFinishing()) return;
        if ("open".equals(requestedWidgetControl)) main.postDelayed(openTimeout, 5_000L);
        mediaBridgeClient.start();
    }

    @Override protected void onStop() {
        main.removeCallbacks(openTimeout);
        if (mediaBridgeClient != null) mediaBridgeClient.stop();
        super.onStop();
        if (!isFinishing() && !isChangingConfigurations()) finish();
    }

    /** The window belongs to its own task; skip the task close slide over HOME. */
    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (scrubber != null && event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            closeScrubber();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override protected void onDestroy() {
        setScrubbing(false);
        scrubber = null;
        if (mediaBridgeClient != null) mediaBridgeClient.stop();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onBridgeState(MediaBridgeClient.State state, String detail) {
        if (isFinishing() || isDestroyed()) return;
        if (scrubber != null && (state == MediaBridgeClient.State.DISCONNECTED
                || state == MediaBridgeClient.State.INCOMPATIBLE)) finish();
    }

    @Override public void onSnapshot(MediaSnapshot snapshot) {
        if (isFinishing() || isDestroyed()) return;
        if ("open".equals(requestedWidgetControl)) {
            requestedWidgetControl = null;
            main.removeCallbacks(openTimeout);
            if (!new MediaSourceLauncher(this).open(snapshot)) {
                failOpen("Не удалось открыть текущий плеер");
            } else {
                finish();
            }
            return;
        }
        if (scrubber != null) renderScrubber(snapshot);
    }

    @Override public void onCommandResult(String requestId, int status, String message, long generation) {}
    @Override public void onRadioStations(RadioStationLists lists) {}
    @Override public void onRadioStationsError(int status, String message) {}

    private void failOpen(String message) {
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Places only the live progress row over the widget's strip. The launcher reports the tapped
     * time label's screen bounds; the rest of the geometry comes from the rendered widget frame.
     */
    @SuppressWarnings("deprecation") // The typed getParcelableExtra overload needs API 33.
    private void showScrubber() {
        Rect source = getIntent().getSourceBounds();
        Rect target = getIntent().getParcelableExtra(AtlasMediaWidgetProvider.EXTRA_TARGET_BOUNDS);
        Rect row = getIntent().getParcelableExtra(AtlasMediaWidgetProvider.EXTRA_PROGRESS_BOUNDS);
        if (source == null || target == null || row == null || row.isEmpty()) {
            AppLog.warn("Widget host did not report scrubber bounds: source=" + source, null);
            failOpen("Лаунчер не передал положение виджета");
            return;
        }
        var context = getApplicationContext();
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId);
        int width = Math.min(4096, Ui.dp(context, AtlasMediaWidgetProvider.widthDp(context, options)));
        int height = Math.min(4096, Ui.dp(context, AtlasMediaWidgetProvider.heightDp(context, options)));
        CardStyle style = CardStyle.fromPreference(prefs.getInt(Prefs.KEY_CARD_STYLE, CardStyle.DEFAULT.preferenceValue));
        WidgetAppearance appearance = prefs.appearance(style);
        scrubber = new MediaCardView(context, width, height, width, height, style,
                appearance, prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false),
                false, prefs.radioFavoritesColumns(), prefs.radioFavoritesRows(), scrubberListener);
        scrubber.showProgressOnly();
        // Keep the finger-sized window inside the card so its backdrop is always card pixels.
        int margin = Math.max(0, (Ui.dp(this, 56) - row.height()) / 2);
        int topMargin = Math.min(margin, Math.max(0, row.top));
        int bottomMargin = Math.min(margin, Math.max(0, height - row.bottom));
        Rect area = new Rect(row.left, row.top - topMargin, row.right, row.bottom + bottomMargin);
        int touchY = topMargin + row.height() / 2;
        FrameLayout strip = new FrameLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) main.removeCallbacks(scrubberIdle);
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    main.postDelayed(scrubberIdle, 2_000L);
                }
                // The whole finger-sized strip drives the thin track.
                event.setLocation(event.getX(), touchY);
                return super.dispatchTouchEvent(event);
            }
        };
        strip.setClipChildren(true);
        OverlayService service = OverlayService.current();
        var frame = service == null ? null : service.widgetFrame(widgetId);
        // A translucent backdrop would let the widget's own strip show through the copy.
        if (frame != null && frame.width == width && frame.height == height
                && !appearance.backdrop.translucentFor(style)) {
            // An opaque copy of the card under the strip hides whatever the widget's own strip
            // shows, so it never has to be hidden or restored and is never drawn twice.
            Rect crop = new Rect(Math.round(area.left * frame.cardScale),
                    Math.round(area.top * frame.cardScale), Math.round(area.right * frame.cardScale),
                    Math.round(area.bottom * frame.cardScale));
            if (crop.intersect(0, 0, frame.cardBitmap.getWidth(), frame.cardBitmap.getHeight())) {
                strip.setBackground(new BitmapDrawable(getResources(), Bitmap.createBitmap(
                        frame.cardBitmap, crop.left, crop.top, crop.width(), crop.height())));
            }
        }
        hidesWidgetStrip = strip.getBackground() == null;
        scrubber.setTranslationX(-area.left);
        scrubber.setTranslationY(-area.top);
        strip.addView(scrubber, new FrameLayout.LayoutParams(width, height));
        setContentView(strip);
        var window = getWindow();
        window.setLayout(area.width(), area.height());
        window.setGravity(Gravity.TOP | Gravity.START);
        window.setElevation(0f);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.x = source.left - target.left + area.left;
        attributes.y = source.top - target.top + area.top;
        window.setAttributes(attributes);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        main.postDelayed(scrubberIdle, 4_000L);
        MediaSnapshot snapshot = service == null ? null : service.widgetSnapshot();
        if (snapshot != null) renderScrubber(snapshot);
        String zone = getIntent().getStringExtra(AtlasMediaWidgetProvider.EXTRA_SEEK_ZONE);
        if (zone != null && !isFinishing()) seekZone(service, snapshot, zone);
        main.post(scrubberTick);
    }

    /** A tap on the strip seeks at once; the scrubber then stays for fine dragging. */
    private void seekZone(OverlayService service, MediaSnapshot snapshot, String zone) {
        if (service != null && snapshot != null) {
            long position = AtlasMediaWidgetProvider.seekZonePosition(zone, snapshot.duration);
            if (position < 0L || !snapshot.supports(MediaBridgeContract.CAP_SEEK)) return;
            service.seekFromWidget(position);
            scrubber.projectWidgetSeek(position);
            return;
        }
        // The service validates freshness and capabilities once its snapshot arrives.
        try {
            startService(new Intent(this, OverlayService.class)
                    .setAction(AtlasMediaWidgetProvider.ACTION_COMMAND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra(AtlasMediaWidgetProvider.EXTRA_CONTROL, zone));
        } catch (RuntimeException error) {
            AppLog.warn("Cannot send widget seek", error);
        }
    }

    private void renderScrubber(MediaSnapshot snapshot) {
        scrubber.renderSnapshot(snapshot, true);
        if (!scrubber.isProgressVisible() || snapshot.duration <= 0L
                || !snapshot.supports(MediaBridgeContract.CAP_SEEK)) {
            finish();
            return;
        }
        if (scrubberShown || !hidesWidgetStrip) return;
        scrubberShown = true;
        // Hide the widget's own strip only once this window's strip is on screen; until then
        // both show the same position, so the strip never disappears while the window starts.
        scrubber.invalidate();
        getWindow().getDecorView().getViewTreeObserver().registerFrameCommitCallback(() -> {
            if (!closingScrubber && !isFinishing()) setScrubbing(true);
        });
    }

    /** Restores the widget's strip under the window first, then removes the window. */
    private void closeScrubber() {
        if (closingScrubber || isFinishing()) return;
        closingScrubber = true;
        main.removeCallbacks(scrubberIdle);
        if (!scrubbing) {
            finish();
            return;
        }
        setScrubbing(false);
        main.postDelayed(this::finish, 300L);
    }

    private void setScrubbing(boolean value) {
        if (scrubbing == value) return;
        scrubbing = value;
        OverlayService service = OverlayService.current();
        if (service != null) service.setWidgetScrubbing(widgetId, value);
    }

    private final Runnable scrubberTick = new Runnable() {
        @Override public void run() {
            if (scrubber == null) return;
            scrubber.tick(SystemClock.elapsedRealtime());
            main.postDelayed(this, 1000L);
        }
    };

    private final MediaCardView.Listener scrubberListener = new MediaCardView.Listener() {
        @Override public boolean onDragTouch(View view, MotionEvent event) { return false; }
        @Override public void onCommand(String command) {}
        @Override public void onSeek(long positionMs) {
            OverlayService service = OverlayService.current();
            if (service != null) service.seekFromWidget(positionMs);
        }
        @Override public void onSource(MediaSource.Id source) {}
        @Override public void onOpenSource() {}
        @Override public void onRadioStationsRequested() {}
        @Override public void onRadioStation(RadioStation station) {}
        @Override public void onRadioArtworkRequested(RadioStation station) {}
        @Override public void onCustomAction(String action) {}
    };
}
