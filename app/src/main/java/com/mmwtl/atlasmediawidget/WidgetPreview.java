package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.Arrays;

/** Demo-track previews shared by the settings screen and the widget setup dialog. */
final class WidgetPreview {
    static final MediaCardView.Listener INERT = new MediaCardView.Listener() {
        @Override public boolean onDragTouch(View view, MotionEvent event) { return true; }
        @Override public void onCommand(String command) {}
        @Override public void onSeek(long positionMs) {}
        @Override public void onSource(MediaSource.Id source) {}
        @Override public void onOpenSource() {}
        @Override public void onRadioStationsRequested() {}
        @Override public void onRadioStation(RadioStation station) {}
        @Override public void onRadioArtworkRequested(RadioStation station) {}
        @Override public void onCustomAction(String action) {}
    };

    private static Bitmap demoArtwork;

    private WidgetPreview() {}

    static MediaSnapshot demoSnapshot() {
        long capabilities = MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_PAUSE
                | MediaBridgeContract.CAP_TOGGLE | MediaBridgeContract.CAP_NEXT
                | MediaBridgeContract.CAP_PREVIOUS | MediaBridgeContract.CAP_SEEK
                | MediaBridgeContract.CAP_SET_SOURCE | MediaBridgeContract.CAP_TUNE_RADIO;
        return new MediaSnapshot(
                MediaBridgeContract.VERSION, 1L, System.currentTimeMillis(), true, 0, "",
                MediaSource.Id.USB, "DEMO",
                Arrays.asList(
                        new MediaSource(MediaSource.Id.BT, true, true, false, capabilities),
                        new MediaSource(MediaSource.Id.RADIO, true, true, false, capabilities),
                        new MediaSource(MediaSource.Id.USB, true, true, true, capabilities),
                        new MediaSource(MediaSource.Id.ONLINE, true, true, false, capabilities)),
                "com.mmwtl.atlasmediaapi.demo.usb", "Atlas demo USB", "demo:USB:0",
                "Liminal Hours (Extended Night Drive Version)",
                "Northern Signal Department feat. Elena Markova", "The Roads We Leave Behind",
                286_000L, 47_000L,
                SystemClock.elapsedRealtime(), 1f, MediaSnapshot.STATE_PLAYING,
                0, "", 0L, capabilities, "", 0L);
    }

    static Bitmap demoArtwork(Context context) {
        if (demoArtwork == null) demoArtwork = BitmapFactory.decodeResource(
                context.getResources(), com.mmwtl.atlasmediaapi.R.drawable.demo_neon_drive);
        return demoArtwork;
    }

    /** Applies the real AppWidget RemoteViews for the demo track, scaled to fit the host. */
    static void renderWidget(Context context, FrameLayout host, Prefs prefs, Bundle options,
            int maxHeightPx) {
        int width = Ui.dp(context, AtlasMediaWidgetProvider.widthDp(context, options));
        int height = Ui.dp(context, AtlasMediaWidgetProvider.heightDp(context, options));
        var frame = new AtlasMediaWidgetProvider.Frame(context, prefs, options, 0,
                demoSnapshot(), demoArtwork(context), true, INERT);
        View preview = frame.views.apply(context, host);
        int available = host.getWidth() - host.getPaddingLeft() - host.getPaddingRight();
        float scale = Math.min(1f, Math.min(available / (float) width,
                maxHeightPx / (float) height));
        preview.setScaleX(scale);
        preview.setScaleY(scale);
        host.removeAllViews();
        host.addView(preview, new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
        View blocker = new View(context);
        blocker.setClickable(true);
        host.addView(blocker, new FrameLayout.LayoutParams(-1, -1));
        var params = host.getLayoutParams();
        params.height = Math.round(height * scale) + host.getPaddingTop()
                + host.getPaddingBottom();
        host.setLayoutParams(params);
    }
}
