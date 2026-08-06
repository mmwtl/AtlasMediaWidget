package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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
    private final CardStyle style;
    private final int cardWidth;
    private final int cardHeight;
    private final ImageView artwork;
    private final ImageView artworkThumbnail;
    private final AudioWaveformView emptyArtwork;
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

    MediaCardView(Context context, int availableWidth, CardStyle style, Listener listener) {
        super(context);
        this.listener = listener;
        this.style = style;
        if (style == CardStyle.COMPACT) {
            cardWidth = Math.min(availableWidth, Ui.dp(context, 500));
            cardHeight = Math.round(cardWidth * 300f / 500f);
        } else {
            cardWidth = Math.min(availableWidth, Ui.dp(context, 500));
            cardHeight = cardWidth;
        }
        setMinimumWidth(cardWidth);
        setMinimumHeight(cardHeight);
        setBackground(Ui.background(Ui.CARD, 30, context));
        setClipToOutline(true);
        setElevation(Ui.dp(context, 12));

        artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackgroundColor(Ui.CARD);
        addView(artwork, match());

        emptyArtwork = new AudioWaveformView(context);
        LayoutParams emptyParams = new LayoutParams(
                Ui.dp(context, style == CardStyle.COMPACT ? 150 : 220),
                Ui.dp(context, style == CardStyle.COMPACT ? 64 : 120));
        emptyParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        emptyParams.topMargin = Ui.dp(context, style == CardStyle.COMPACT ? 58 : 108);
        addView(emptyArtwork, emptyParams);

        View scrim = new View(context);
        int[] scrimColors = style == CardStyle.COMPACT
                ? new int[]{0x66000000, 0x12000000, 0xBD171717, 0xF8171717}
                : new int[]{0x70000000, 0x14000000, 0xB8191919, 0xF5171717};
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, scrimColors));
        addView(scrim, match());

        View border = new View(context);
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(Color.TRANSPARENT);
        borderDrawable.setCornerRadius(Ui.dp(context, 30));
        borderDrawable.setStroke(Ui.dp(context, 1), 0x667893A0);
        border.setBackground(borderDrawable);
        addView(border, match());

        HorizontalScrollView sourceScroll = new HorizontalScrollView(context);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sourceScroll.setClipToPadding(false);
        sourceScroll.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 68), 0);
        sources = new LinearLayout(context);
        sources.setOrientation(LinearLayout.HORIZONTAL);
        sources.setGravity(Gravity.CENTER_VERTICAL);
        sourceScroll.addView(sources, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        LayoutParams sourceParams = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 48));
        sourceParams.gravity = Gravity.TOP;
        sourceParams.topMargin = Ui.dp(context, 10);
        addView(sourceScroll, sourceParams);

        TextView dragHandle = text("⋮", 27, Ui.SECONDARY, Typeface.BOLD);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setContentDescription("Перетащить виджет");
        dragHandle.setOnTouchListener(listener::onDragTouch);
        LayoutParams dragParams = new LayoutParams(Ui.dp(context, 54), Ui.dp(context, 54));
        dragParams.gravity = Gravity.TOP | Gravity.END;
        dragParams.topMargin = Ui.dp(context, 7);
        dragParams.rightMargin = Ui.dp(context, 8);
        addView(dragHandle, dragParams);

        title = text(getResources().getString(R.string.unknown_track),
                style == CardStyle.COMPACT ? 21 : 26, Ui.PRIMARY, Typeface.BOLD);
        title.setMaxLines(style == CardStyle.COMPACT ? 1 : 2);
        title.setShadowLayer(Ui.dp(context, 3), 0, Ui.dp(context, 1), 0xCC000000);

        subtitle = text("", style == CardStyle.COMPACT ? 13 : 16,
                Ui.SECONDARY, Typeface.NORMAL);
        subtitle.setMaxLines(1);
        subtitle.setShadowLayer(Ui.dp(context, 2), 0, Ui.dp(context, 1), 0xCC000000);

        status = text(getResources().getString(R.string.bridge_connecting),
                style == CardStyle.COMPACT ? 10 : 12, Ui.ACCENT, Typeface.BOLD);
        status.setMaxLines(1);
        status.setLetterSpacing(0.04f);

        progress = new SeekBar(context);
        progress.setMax(PROGRESS_MAX);
        progress.setPadding(Ui.dp(context, 6), 0, Ui.dp(context, 6), 0);
        progress.setSplitTrack(false);
        configureProgressStyle(context, progress);

        elapsed = text("–:––", 11, Ui.SECONDARY, Typeface.BOLD);
        duration = text("–:––", 11, Ui.SECONDARY, Typeface.BOLD);
        duration.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        previous = new TransportButton(context, TransportButton.Type.PREVIOUS, false);
        playPause = new TransportButton(context, TransportButton.Type.PLAY_PAUSE, true);
        next = new TransportButton(context, TransportButton.Type.NEXT, false);

        artworkThumbnail = new ImageView(context);
        artworkThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkThumbnail.setBackground(Ui.background(Ui.NESTED, 14, context));
        artworkThumbnail.setClipToOutline(true);
        artworkThumbnail.setVisibility(GONE);

        if (style == CardStyle.COMPACT) buildCompactLower(context);
        else buildSquareLower(context);

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

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                seeking = true;
            }

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

    int cardWidth() {
        return cardWidth;
    }

    int cardHeight() {
        return cardHeight;
    }

    void renderSnapshot(MediaSnapshot value, boolean bridgeConnected) {
        snapshot = value;
        if (value == null) {
            renderDisconnected(getResources().getString(R.string.bridge_disconnected));
            return;
        }
        title.setText(value.title.isBlank()
                ? getResources().getString(R.string.unknown_track) : value.title);
        String detail = value.artist;
        if (!value.album.isBlank()) {
            detail = detail.isBlank() ? value.album : detail + "  •  " + value.album;
        }
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
        title.setText(R.string.unknown_track);
        subtitle.setText(R.string.empty_hint);
        subtitle.setVisibility(VISIBLE);
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
        renderFallbackSource();
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
            artworkThumbnail.setImageDrawable(null);
            artworkThumbnail.setVisibility(GONE);
            emptyArtwork.setVisibility(VISIBLE);
        } else {
            artwork.setImageBitmap(bitmap);
            artwork.setAlpha(0f);
            artworkThumbnail.setImageBitmap(bitmap);
            artworkThumbnail.setVisibility(style == CardStyle.COMPACT ? VISIBLE : GONE);
            emptyArtwork.setVisibility(GONE);
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

    private void buildCompactLower(Context context) {
        LinearLayout lower = lowerContainer(context, 18, 12);

        LinearLayout metadata = new LinearLayout(context);
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams thumbnailParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 64), Ui.dp(context, 64));
        thumbnailParams.rightMargin = Ui.dp(context, 14);
        metadata.addView(artworkThumbnail, thumbnailParams);

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        textColumn.addView(title, fullWrap());
        LinearLayout.LayoutParams subtitleParams = fullWrap();
        subtitleParams.topMargin = Ui.dp(context, 3);
        textColumn.addView(subtitle, subtitleParams);
        LinearLayout.LayoutParams statusParams = fullWrap();
        statusParams.topMargin = Ui.dp(context, 4);
        textColumn.addView(status, statusParams);
        metadata.addView(textColumn, new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f));
        lower.addView(metadata, fullWrap());

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressRow.addView(elapsed, new LinearLayout.LayoutParams(
                Ui.dp(context, 43), LayoutParams.WRAP_CONTENT));
        progressRow.addView(progress, new LinearLayout.LayoutParams(
                0, Ui.dp(context, 28), 1f));
        progressRow.addView(duration, new LinearLayout.LayoutParams(
                Ui.dp(context, 43), LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams progressRowParams = fullWrap();
        progressRowParams.topMargin = Ui.dp(context, 3);
        lower.addView(progressRow, progressRowParams);

        LinearLayout controls = controls(context, 42, 54, 14);
        LinearLayout.LayoutParams controlsParams = fullWrap();
        controlsParams.topMargin = Ui.dp(context, 5);
        lower.addView(controls, controlsParams);
    }

    private void buildSquareLower(Context context) {
        LinearLayout lower = lowerContainer(context, 24, 20);
        lower.addView(title, fullWrap());

        LinearLayout.LayoutParams subtitleParams = fullWrap();
        subtitleParams.topMargin = Ui.dp(context, 4);
        lower.addView(subtitle, subtitleParams);

        LinearLayout.LayoutParams statusParams = fullWrap();
        statusParams.topMargin = Ui.dp(context, 8);
        lower.addView(status, statusParams);

        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, 28));
        progressParams.topMargin = Ui.dp(context, 8);
        lower.addView(progress, progressParams);

        LinearLayout times = new LinearLayout(context);
        times.addView(elapsed, new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f));
        times.addView(duration, new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f));
        lower.addView(times, fullWrap());

        LinearLayout controls = controls(context, 54, 68, 20);
        LinearLayout.LayoutParams controlsParams = fullWrap();
        controlsParams.topMargin = Ui.dp(context, 12);
        lower.addView(controls, controlsParams);
    }

    private LinearLayout lowerContainer(Context context, int horizontalPadding, int bottomPadding) {
        LinearLayout lower = new LinearLayout(context);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(Ui.dp(context, horizontalPadding), 0,
                Ui.dp(context, horizontalPadding), Ui.dp(context, bottomPadding));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        addView(lower, params);
        return lower;
    }

    private LinearLayout controls(Context context, int sideSize, int playSize, int gap) {
        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER);
        controls.addView(previous, square(context, sideSize));
        LinearLayout.LayoutParams playParams = square(context, playSize);
        playParams.setMargins(Ui.dp(context, gap), 0, Ui.dp(context, gap), 0);
        controls.addView(playPause, playParams);
        controls.addView(next, square(context, sideSize));
        return controls;
    }

    private static void configureProgressStyle(Context context, SeekBar seekBar) {
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x66F5F5F5);
        track.setCornerRadius(Ui.dp(context, 2));

        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Ui.ACCENT);
        fill.setCornerRadius(Ui.dp(context, 2));
        ClipDrawable clippedFill = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{track, clippedFill});
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
        boolean added = false;
        for (MediaSource source : value.sources) {
            if (source.id == MediaSource.Id.UNKNOWN) continue;
            addSourceChip(source.id.label(), source.selected,
                    source.available || source.connected || source.selected,
                    source.available
                            && (source.capabilities & MediaBridgeContract.CAP_SET_SOURCE) != 0L,
                    source.id);
            added = true;
        }
        if (!added) {
            addSourceChip(value.audioSource.label(), true, true, false, value.audioSource);
        }
    }

    private void renderFallbackSource() {
        sources.removeAllViews();
        addSourceChip("MEDIA", true, true, false, MediaSource.Id.UNKNOWN);
    }

    private void addSourceChip(String label, boolean selected, boolean available,
            boolean canSelect, MediaSource.Id sourceId) {
        TextView chip = text("", 11, selected ? Ui.PRIMARY : Ui.SECONDARY, Typeface.BOLD);
        SpannableString chipText = new SpannableString(
                "●  " + label.toUpperCase(Locale.ROOT));
        chipText.setSpan(new ForegroundColorSpan(Ui.ACCENT), 0, 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        chip.setText(chipText);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(getContext(), 13), 0, Ui.dp(getContext(), 13), 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Ui.dp(getContext(), 16));
        background.setColor(selected ? 0xB3333333 : 0x80333333);
        background.setStroke(Ui.dp(getContext(), 1), selected ? Ui.ACCENT : 0x557893A0);
        chip.setBackground(background);
        chip.setEnabled(canSelect && !selected);
        chip.setAlpha(available ? 1f : 0.38f);
        chip.setOnClickListener(v -> listener.onSource(sourceId));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                Ui.dp(getContext(), style == CardStyle.COMPACT ? 28 : 32));
        params.rightMargin = Ui.dp(getContext(), 7);
        sources.addView(chip, params);
    }

    private TextView text(String value, float size, int color, int textStyle) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, textStyle);
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

    private LinearLayout.LayoutParams square(Context context, int sizeDp) {
        int size = Ui.dp(context, sizeDp);
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
