package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

final class MediaCardView extends LinearLayout {
    interface Listener {
        boolean onDragTouch(View view, MotionEvent event);
        void onCommand(String command);
        void onSeek(long positionMs);
        void onSource(MediaSource.Id source);
    }

    private static final int PROGRESS_MAX = 10_000;
    private final Listener listener;
    private final int cardWidth;
    private final TextView header;
    private final ImageView artwork;
    private final TextView title;
    private final TextView subtitle;
    private final TextView status;
    private final SeekBar progress;
    private final TextView elapsed;
    private final TextView duration;
    private final Button previous;
    private final Button playPause;
    private final Button next;
    private final LinearLayout sources;
    private MediaSnapshot snapshot;
    private boolean seeking;

    MediaCardView(Context context, int availableWidth, Listener listener) {
        super(context);
        this.listener = listener;
        cardWidth = Math.min(availableWidth, Ui.dp(context, 620));
        setOrientation(VERTICAL);
        setPadding(Ui.dp(context, 18), Ui.dp(context, 12),
                Ui.dp(context, 18), Ui.dp(context, 18));
        setBackground(Ui.background(Ui.CARD, 24, context));
        setElevation(Ui.dp(context, 10));

        header = text("⋮⋮   MEDIA", 13, Ui.ACCENT, Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(context, 4), Ui.dp(context, 4), 0, Ui.dp(context, 8));
        header.setOnTouchListener(listener::onDragTouch);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 40)));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setImageDrawable(Ui.background(Ui.NESTED, 18, context));
        int artSize = Ui.dp(context, 142);
        content.addView(artwork, new LayoutParams(artSize, artSize));

        LinearLayout metadata = new LinearLayout(context);
        metadata.setOrientation(VERTICAL);
        metadata.setPadding(Ui.dp(context, 18), 0, 0, 0);
        content.addView(metadata, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        title = text(getResources().getString(R.string.unknown_track), 23, Ui.PRIMARY, Typeface.BOLD);
        title.setMaxLines(2);
        metadata.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        subtitle = text("", 17, Ui.SECONDARY, Typeface.NORMAL);
        subtitle.setMaxLines(2);
        LayoutParams subtitleParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Ui.dp(context, 7);
        metadata.addView(subtitle, subtitleParams);
        status = text(getResources().getString(R.string.bridge_connecting), 13, Ui.ACCENT, Typeface.NORMAL);
        LayoutParams statusParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Ui.dp(context, 12);
        metadata.addView(status, statusParams);

        progress = new SeekBar(context);
        progress.setMax(PROGRESS_MAX);
        progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        progress.setThumbTintList(ColorStateList.valueOf(Ui.PRIMARY));
        LayoutParams progressParams = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 38));
        progressParams.topMargin = Ui.dp(context, 10);
        addView(progress, progressParams);

        LinearLayout times = new LinearLayout(context);
        times.setOrientation(HORIZONTAL);
        elapsed = text("–:––", 12, Ui.SECONDARY, Typeface.NORMAL);
        duration = text("–:––", 12, Ui.SECONDARY, Typeface.NORMAL);
        duration.setGravity(Gravity.END);
        times.addView(elapsed, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        times.addView(duration, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(times, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser && snapshot != null && snapshot.duration > 0L) {
                    elapsed.setText(formatTime(snapshot.duration * value / PROGRESS_MAX));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                seeking = false;
                if (snapshot != null && snapshot.duration > 0L
                        && snapshot.supports(MediaBridgeContract.CAP_SEEK)) {
                    listener.onSeek(snapshot.duration * seekBar.getProgress() / PROGRESS_MAX);
                }
            }
        });

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 10));
        previous = controlButton("◀◀");
        playPause = controlButton("▶");
        next = controlButton("▶▶");
        controls.addView(previous, new LayoutParams(Ui.dp(context, 92), Ui.dp(context, 62)));
        LayoutParams playParams = new LayoutParams(Ui.dp(context, 108), Ui.dp(context, 68));
        playParams.setMargins(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        controls.addView(playPause, playParams);
        controls.addView(next, new LayoutParams(Ui.dp(context, 92), Ui.dp(context, 62)));
        addView(controls, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        previous.setOnClickListener(v -> listener.onCommand("PREVIOUS"));
        next.setOnClickListener(v -> listener.onCommand("NEXT"));
        playPause.setOnClickListener(v -> {
            if (snapshot == null) return;
            if (snapshot.supports(MediaBridgeContract.CAP_TOGGLE)) listener.onCommand("TOGGLE");
            else listener.onCommand(snapshot.isPlaying() ? "PAUSE" : "PLAY");
        });

        HorizontalScrollView sourceScroll = new HorizontalScrollView(context);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sources = new LinearLayout(context);
        sources.setOrientation(HORIZONTAL);
        sourceScroll.addView(sources, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(sourceScroll, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 52)));
        renderDisconnected(getResources().getString(R.string.bridge_connecting));
    }

    int cardWidth() {
        return cardWidth;
    }

    void renderSnapshot(MediaSnapshot value, boolean bridgeConnected) {
        snapshot = value;
        if (value == null) {
            renderDisconnected(getResources().getString(R.string.bridge_disconnected));
            return;
        }
        title.setText(value.title.isBlank() ? getResources().getString(R.string.unknown_track) : value.title);
        String detail = value.artist;
        if (!value.album.isBlank()) detail = detail.isBlank() ? value.album : detail + " • " + value.album;
        subtitle.setText(detail);
        subtitle.setVisibility(detail.isBlank() ? GONE : VISIBLE);
        String source = value.audioSource.label();
        if (!value.ownerApp.isBlank()) source += " • " + value.ownerApp;
        status.setText(bridgeConnected && value.backendConnected ? source
                : source + " • backend недоступен");
        status.setTextColor(bridgeConnected && value.backendConnected ? Ui.ACCENT : Ui.ERROR);
        previous.setEnabled(value.supports(MediaBridgeContract.CAP_PREVIOUS));
        next.setEnabled(value.supports(MediaBridgeContract.CAP_NEXT));
        boolean toggle = value.supports(MediaBridgeContract.CAP_TOGGLE)
                || value.isPlaying() && value.supports(MediaBridgeContract.CAP_PAUSE)
                || !value.isPlaying() && value.supports(MediaBridgeContract.CAP_PLAY);
        playPause.setEnabled(toggle);
        playPause.setText(value.isPlaying() ? "❚❚" : "▶");
        progress.setEnabled(value.duration > 0L && value.supports(MediaBridgeContract.CAP_SEEK));
        duration.setText(formatTime(value.duration));
        renderSources(value);
        tick(SystemClock.elapsedRealtime());
    }

    void renderDisconnected(String detail) {
        snapshot = null;
        title.setText(R.string.unknown_track);
        subtitle.setVisibility(GONE);
        status.setText(detail);
        status.setTextColor(Ui.ERROR);
        previous.setEnabled(false);
        playPause.setEnabled(false);
        next.setEnabled(false);
        progress.setEnabled(false);
        progress.setProgress(0);
        elapsed.setText("–:––");
        duration.setText("–:––");
        sources.removeAllViews();
    }

    void showTransientStatus(String message, boolean error) {
        status.setText(message);
        status.setTextColor(error ? Ui.ERROR : Ui.ACCENT);
    }

    void setArtwork(Bitmap bitmap) {
        if (bitmap == null) {
            artwork.setImageDrawable(Ui.background(Ui.NESTED, 18, getContext()));
        } else {
            artwork.setImageBitmap(bitmap);
        }
    }

    void tick(long nowElapsedRealtime) {
        if (snapshot == null || seeking) return;
        long value = ProgressEstimator.estimate(snapshot.position, snapshot.duration,
                snapshot.updateElapsedRealtime, snapshot.speed, snapshot.playbackState,
                nowElapsedRealtime);
        elapsed.setText(formatTime(value));
        if (value >= 0L && snapshot.duration > 0L) {
            progress.setProgress((int) Math.min(PROGRESS_MAX,
                    value * PROGRESS_MAX / snapshot.duration));
        } else {
            progress.setProgress(0);
        }
    }

    private void renderSources(MediaSnapshot value) {
        sources.removeAllViews();
        for (MediaSource source : value.sources) {
            if (source.id == MediaSource.Id.UNKNOWN) continue;
            Button button = new Button(getContext());
            button.setAllCaps(false);
            button.setText(source.id.label());
            button.setTextSize(13);
            button.setTextColor(source.selected ? Ui.BACKGROUND : Ui.PRIMARY);
            button.setBackgroundTintList(ColorStateList.valueOf(
                    source.selected ? Ui.ACCENT : Ui.NESTED));
            boolean canSelect = source.selected || source.available
                    && (source.capabilities & MediaBridgeContract.CAP_SET_SOURCE) != 0L;
            button.setEnabled(canSelect && !source.selected);
            button.setAlpha(source.available || source.connected || source.selected ? 1f : 0.42f);
            button.setOnClickListener(v -> listener.onSource(source.id));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, Ui.dp(getContext(), 46));
            params.rightMargin = Ui.dp(getContext(), 8);
            sources.addView(button, params);
        }
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private Button controlButton(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setTextSize(19);
        button.setTextColor(Ui.PRIMARY);
        button.setBackgroundTintList(ColorStateList.valueOf(Ui.NESTED));
        return button;
    }

    private static String formatTime(long milliseconds) {
        if (milliseconds < 0L) return "–:––";
        long seconds = milliseconds / 1000L;
        long hours = seconds / 3600L;
        long minutes = seconds / 60L % 60L;
        long remainder = seconds % 60L;
        return hours > 0L
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.US, "%d:%02d", minutes, remainder);
    }
}
