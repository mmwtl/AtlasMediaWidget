package com.mmwtl.atlasmediawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/** Widget interactions isolated from the settings task, like the native fuel dialog. */
public final class WidgetControlActivity extends Activity implements MediaBridgeClient.Listener {
    private Prefs prefs;
    private MediaBridgeClient mediaBridgeClient;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable openTimeout = () -> failOpen("Нет соединения с медиасервисом");
    private MediaCardView widgetControls;
    private ArtworkLoader controlsArtwork;
    private String controlsArtworkKey = "";
    private long controlsArtworkToken;
    private String requestedWidgetControl;
    private final MediaCardView.Listener controlsListener = new MediaCardView.Listener() {
        private OverlayService service() { return OverlayService.current(); }
        @Override public boolean onDragTouch(View view, MotionEvent event) { return false; }
        @Override public void onCommand(String command) { if (service() != null) service().onCommand(command); }
        @Override public void onSeek(long positionMs) { if (service() != null) service().onSeek(positionMs); }
        @Override public void onSource(MediaSource.Id source) { if (service() != null) service().onSource(source); }
        @Override public void onOpenSource() {
            if (service() != null) { service().onOpenSource(); finish(); }
        }
        @Override public void onRadioStationsRequested() { mediaBridgeClient.requestRadioStations(); }
        @Override public void onRadioStation(RadioStation station) { if (service() != null) service().onRadioStation(station); }
        @Override public void onRadioArtworkRequested(RadioStation station) {
            if (controlsRadioArtwork != null) controlsRadioArtwork.load(station);
        }
    };
    private RadioArtworkLoader controlsRadioArtwork;
    private final Runnable controlsTick = new Runnable() {
        @Override public void run() {
            if (widgetControls == null) return;
            widgetControls.tick(SystemClock.elapsedRealtime());
            main.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        requestedWidgetControl = getIntent().getStringExtra(AtlasMediaWidgetProvider.EXTRA_CONTROL);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (!prefs.isWidgetMode() || !AtlasMediaWidgetProvider.owns(this, id)
                || !("open".equals(requestedWidgetControl) || "favorites".equals(requestedWidgetControl)
                || "seek".equals(requestedWidgetControl))) {
            finish();
            return;
        }
        mediaBridgeClient = new MediaBridgeClient(this, this);
        AtlasMediaWidgetProvider.refresh(this);
        if ("open".equals(requestedWidgetControl)) {
            // Keep HOME visible while the bridge supplies the current source. No card or settings UI.
            getWindow().setLayout(1, 1);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        } else {
            showWidgetControls();
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

    @Override protected void onDestroy() {
        if (mediaBridgeClient != null) mediaBridgeClient.stop();
        if (controlsArtwork != null) controlsArtwork.shutdown();
        if (controlsRadioArtwork != null) controlsRadioArtwork.shutdown();
        widgetControls = null;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onBridgeState(MediaBridgeClient.State state, String detail) {
        if (isFinishing() || isDestroyed()) return;
        if (state == MediaBridgeClient.State.CONNECTED) {
            if (widgetControls != null) mediaBridgeClient.requestRadioStations();
        } else if (widgetControls != null) {
            widgetControls.renderDisconnected("Нет соединения");
            controlsArtworkKey = "";
            controlsArtworkToken = controlsArtwork.clear();
        }
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
        if (widgetControls == null) return;
        widgetControls.renderSnapshot(snapshot, true);
        String key = snapshot.audioSource + ":" + snapshot.mediaId + ":" + snapshot.title
                + ":" + snapshot.artworkUri + ":" + snapshot.artworkRevision;
        if (!key.equals(controlsArtworkKey)) {
            controlsArtworkKey = key;
            widgetControls.setArtwork(null);
            controlsArtworkToken = controlsArtwork.load(ArtworkRef.mediaUri(snapshot.artworkUri),
                    snapshot.generation, snapshot.artworkRevision);
        }
        if (requestedWidgetControl != null) {
            widgetControls.openWidgetChooser(requestedWidgetControl);
            requestedWidgetControl = null;
        }
    }

    @Override public void onCommandResult(String requestId, int status, String message, long generation) {}
    @Override public void onRadioStations(RadioStationLists lists) {
        if (widgetControls != null) widgetControls.setRadioStations(lists);
    }
    @Override public void onRadioStationsError(int status, String message) {
        if (widgetControls != null) widgetControls.setRadioStationsError(message);
    }

    private void failOpen(String message) {
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showWidgetControls() {
        if (isFinishing()) return;
        var context = getApplicationContext();
        int width = Math.min(Ui.dp(context, 500), getWindowManager().getCurrentWindowMetrics().getBounds().width() - Ui.dp(this, 32));
        int height = Math.min(Ui.dp(context, 500), getWindowManager().getCurrentWindowMetrics().getBounds().height() - Ui.dp(this, 80));
        CardStyle style = CardStyle.fromPreference(prefs.getInt(Prefs.KEY_CARD_STYLE, CardStyle.DEFAULT.preferenceValue));
        widgetControls = new MediaCardView(context, width, height, width, height, style,
                prefs.appearance(style), prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false),
                false, prefs.radioFavoritesColumns(), prefs.radioFavoritesRows(), controlsListener);
        widgetControls.renderDisconnected("Подключение…");
        controlsArtwork = new ArtworkLoader(this, (token, bitmap) -> {
            if (token == controlsArtworkToken && widgetControls != null) widgetControls.setArtwork(bitmap);
        });
        controlsRadioArtwork = new RadioArtworkLoader(this, (key, bitmap) -> {
            if (widgetControls != null) widgetControls.setRadioArtwork(key, bitmap);
        });
        setContentView(widgetControls);
        getWindow().setLayout(width, height);
        setFinishOnTouchOutside(true);
        main.post(controlsTick);
    }
}
