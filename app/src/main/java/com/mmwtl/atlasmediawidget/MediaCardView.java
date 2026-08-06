package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

final class MediaCardView extends FrameLayout {
    interface Listener {
        boolean onDragTouch(View view, MotionEvent event);
        void onCommand(String command);
        void onSeek(long positionMs);
        void onSource(MediaSource.Id source);
    }

    private static final int PROGRESS_MAX = 10_000;
    private final Listener listener;
    private final int cardSize;
    private final ImageView artwork;
    private final TextView watermark;
    private final TextView header;
    private final TextView title;
    private final TextView subtitle;
    private final TextView status;
    private final SeekBar progress;
    private final TextView elapsed;
    private final TextView duration;
    private final TransportButton previous;
    private final TransportButton playPause;
    private final TransportButton next;
    private final LinearLayout sources;
    private MediaSnapshot snapshot;
    private boolean seeking;

    MediaCardView(Context context, int availableWidth, Listener listener) {
        super(context);
        this.listener = listener;
        cardSize = Math.min(availableWidth, Ui.dp(context, 500));
        setMinimumWidth(cardSize);
        setMinimumHeight(cardSize);
        setBackground(Ui.background(Ui.CARD, 30, context));
        setClipToOutline(true);
        setElevation(Ui.dp(context, 12));

        artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackgroundColor(Ui.CARD);
        addView(artwork, match());

        watermark = text("♫", 132, Ui.ACCENT, Typeface.BOLD);
        watermark.setAlpha(0.13f);
        watermark.setGravity(Gravity.CENTER);
        addView(watermark, match());

        View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x70000000, 0x14000000, 0xB8191919, 0xF5171717}));
        addView(scrim, match());

        View border = new View(context);
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(Color.TRANSPARENT);
        borderDrawable.setCornerRadius(Ui.dp(context, 30));
        borderDrawable.setStroke(Ui.dp(context, 1), 0x667893A0);
        border.setBackground(borderDrawable);
        addView(border, match());

        header = text("⋮⋮  MEDIA", 12, Ui.ACCENT, Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLetterSpacing(0.08f);
        header.setPadding(Ui.dp(context, 22), 0, Ui.dp(context, 18), 0);
        header.setOnTouchListener(listener::onDragTouch);
        LayoutParams headerParams = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 52));
        headerParams.gravity = Gravity.TOP;
        addView(header, headerParams);

        HorizontalScrollView sourceScroll = new HorizontalScrollView(context);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sourceScroll.setClipToPadding(false);
        sourceScroll.setPadding(Ui.dp(context, 18), 0, Ui.dp(context, 18), 0);
        sources = new LinearLayout(context);
        sources.setOrientation(LinearLayout.HORIZONTAL);
        sourceScroll.addView(sources, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        LayoutParams sourceParams = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 42));
        sourceParams.gravity = Gravity.TOP;
        sourceParams.topMargin = Ui.dp(context, 50);
        addView(sourceScroll, sourceParams);

        LinearLayout lower = new LinearLayout(context);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(Ui.dp(context, 24), 0, Ui.dp(context, 24), Ui.dp(context, 20));
        LayoutParams lowerParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lowerParams.gravity = Gravity.BOTTOM;
        addView(lower, lowerParams);

        title = text(getResources().getString(R.string.unknown_track), 26, Ui.PRIMARY, Typeface.BOLD);
        title.setMaxLines(2);
        title.setShadowLayer(Ui.dp(context, 3), 0, Ui.dp(context, 1), 0xCC000000);
        lower.addView(title, fullWrap());

        subtitle = text("", 16, Ui.SECONDARY, Typeface.NORMAL);
        subtitle.setMaxLines(1);
        subtitle.setShadowLayer(Ui.dp(context, 2), 0, Ui.dp(context, 1), 0xCC000000);
        LinearLayout.LayoutParams subtitleParams = fullWrap();
        subtitleParams.topMargin = Ui.dp(context, 4);
        lower.addView(subtitle, subtitleParams);

        status = text(getResources().getString(R.string.bridge_connecting), 12, Ui.ACCENT, Typeface.BOLD);
        status.setMaxLines(1);
        status.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams statusParams = fullWrap();
        statusParams.topMargin = Ui.dp(context, 8);
        lower.addView(status, statusParams);

        progress = new SeekBar(context);
        progress.setMax(PROGRESS_MAX);
        progress.setPadding(Ui.dp(context, 6), 0, Ui.dp(context, 6), 0);
        progress.setSplitTrack(false);
        configureProgressStyle(context, progress);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, 28));
        progressParams.topMargin = Ui.dp(context, 8);
        lower.addView(progress, progressParams);

        LinearLayout times = new LinearLayout(context);
        elapsed = text("–:––", 11, Ui.SECONDARY, Typeface.BOLD);
        duration = text("–:––", 11, Ui.SECONDARY, Typeface.BOLD);
        duration.setGravity(Gravity.END);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        times.addView(duration, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        lower.addView(times, fullWrap());

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, Ui.dp(context, 12), 0, 0);
        previous = new TransportButton(context, TransportButton.Type.PREVIOUS, false);
        playPause = new TransportButton(context, TransportButton.Type.PLAY_PAUSE, true);
        next = new TransportButton(context, TransportButton.Type.NEXT, false);
        controls.addView(previous, square(context, 54));
        LinearLayout.LayoutParams playParams = square(context, 68);
        playParams.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
        controls.addView(playPause, playParams);
        controls.addView(next, square(context, 54));
        lower.addView(controls, fullWrap());

        previous.setOnClickListener(v -> listener.onCommand("PREVIOUS"));
        next.setOnClickListener(v -> listener.onCommand("NEXT"));
        playPause.setOnClickListener(v -> {
            if (snapshot == null) return;
            if (snapshot.supports(MediaBridgeContract.CAP_TOGGLE)) listener.onCommand("TOGGLE");
            else listener.onCommand(snapshot.isPlaying() ? "PAUSE" : "PLAY");
        });

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
        renderDisconnected(getResources().getString(R.string.bridge_connecting));
    }

    int cardWidth() { return cardSize; }
    int cardHeight() { return cardSize; }

    void renderSnapshot(MediaSnapshot value, boolean bridgeConnected) {
        snapshot = value;
        if (value == null) {
            renderDisconnected(getResources().getString(R.string.bridge_disconnected));
            return;
        }
        header.setText("⋮⋮  MEDIA  •  " + value.audioSource.label().toUpperCase(Locale.ROOT));
        title.setText(value.title.isBlank() ? getResources().getString(R.string.unknown_track) : value.title);
        String detail = value.artist;
        if (!value.album.isBlank()) detail = detail.isBlank() ? value.album : detail + "  •  " + value.album;
        subtitle.setText(detail);
        subtitle.setVisibility(detail.isBlank() ? GONE : VISIBLE);
        String owner = value.ownerApp.isBlank() ? value.audioSource.label() : value.ownerApp;
        status.setText(bridgeConnected && value.backendConnected
                ? owner.toUpperCase(Locale.ROOT) : owner + "  •  BACKEND НЕДОСТУПЕН");
        status.setTextColor(bridgeConnected && value.backendConnected ? Ui.ACCENT : Ui.ERROR);
        previous.setEnabled(value.supports(MediaBridgeContract.CAP_PREVIOUS));
        next.setEnabled(value.supports(MediaBridgeContract.CAP_NEXT));
        boolean toggle = value.supports(MediaBridgeContract.CAP_TOGGLE)
                || value.isPlaying() && value.supports(MediaBridgeContract.CAP_PAUSE)
                || !value.isPlaying() && value.supports(MediaBridgeContract.CAP_PLAY);
        playPause.setEnabled(toggle);
        playPause.setPlaying(value.isPlaying());
        progress.setEnabled(value.duration > 0L && value.supports(MediaBridgeContract.CAP_SEEK));
        progress.setAlpha(progress.isEnabled() ? 1f : 0.42f);
        duration.setText(formatTime(value.duration));
        renderSources(value);
        tick(SystemClock.elapsedRealtime());
    }

    void renderDisconnected(String detail) {
        snapshot = null;
        header.setText("⋮⋮  MEDIA");
        title.setText(R.string.unknown_track);
        subtitle.setVisibility(GONE);
        status.setText(detail.toUpperCase(Locale.ROOT));
        status.setTextColor(Ui.ERROR);
        previous.setEnabled(false);
        playPause.setEnabled(false);
        playPause.setPlaying(false);
        next.setEnabled(false);
        progress.setEnabled(false);
        progress.setAlpha(0.42f);
        progress.setProgress(0);
        elapsed.setText("–:––");
        duration.setText("–:––");
        sources.removeAllViews();
    }

    void showTransientStatus(String message, boolean error) {
        status.setText(message.toUpperCase(Locale.ROOT));
        status.setTextColor(error ? Ui.ERROR : Ui.ACCENT);
    }

    void setArtwork(Bitmap bitmap) {
        artwork.animate().cancel();
        if (bitmap == null) {
            artwork.setImageDrawable(null);
            artwork.setAlpha(0f);
            watermark.setVisibility(VISIBLE);
        } else {
            artwork.setImageBitmap(bitmap);
            artwork.setAlpha(0f);
            watermark.setVisibility(GONE);
            artwork.animate().alpha(1f).setDuration(220L).start();
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

    private static void configureProgressStyle(Context context, SeekBar seekBar) {
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x66F5F5F5);
        track.setCornerRadius(Ui.dp(context, 2));

        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Ui.ACCENT);
        fill.setCornerRadius(Ui.dp(context, 2));
        ClipDrawable clippedFill = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);

        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{track, clippedFill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        int verticalInset = Ui.dp(context, 12);
        layers.setLayerInset(0, 0, verticalInset, 0, verticalInset);
        layers.setLayerInset(1, 0, verticalInset, 0, verticalInset);
        seekBar.setProgressDrawable(layers);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Ui.PRIMARY);
        int thumbSize = Ui.dp(context, 10);
        thumb.setSize(thumbSize, thumbSize);
        seekBar.setThumb(thumb);
    }

    private void renderSources(MediaSnapshot value) {
        sources.removeAllViews();
        for (MediaSource source : value.sources) {
            if (source.id == MediaSource.Id.UNKNOWN) continue;
            TextView chip = text(source.id.label(), 11,
                    source.selected ? Ui.BACKGROUND : Ui.PRIMARY, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(getContext(), 12), 0, Ui.dp(getContext(), 12), 0);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(Ui.dp(getContext(), 14));
            background.setColor(source.selected ? Ui.ACCENT : 0x99333333);
            background.setStroke(Ui.dp(getContext(), 1),
                    source.selected ? Ui.ACCENT : 0x557893A0);
            chip.setBackground(background);
            boolean canSelect = source.available
                    && (source.capabilities & MediaBridgeContract.CAP_SET_SOURCE) != 0L;
            chip.setEnabled(canSelect && !source.selected);
            chip.setAlpha(source.available || source.connected || source.selected ? 1f : 0.38f);
            chip.setOnClickListener(v -> listener.onSource(source.id));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, Ui.dp(getContext(), 30));
            params.rightMargin = Ui.dp(getContext(), 7);
            sources.addView(chip, params);
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

    private LayoutParams match() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams square(Context context, int dp) {
        int size = Ui.dp(context, dp);
        return new LinearLayout.LayoutParams(size, size);
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
