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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
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
    private final MusicPlaceholderView placeholder;
    private final LinearLayout sourcePill;
    private final SourceGlyphView sourceGlyph;
    private final TextView sourceLabel;
    private final HorizontalScrollView sourceChooser;
    private final LinearLayout sourceOptions;
    private final TextView statusPill;
    private final LinearLayout metadata;
    private final TextView title;
    private final TextView subtitle;
    private final LinearLayout progressRow;
    private final SeekBar progress;
    private final TextView elapsed;
    private final TextView duration;
    private final View divider;
    private final LinearLayout controls;
    private final TransportButton previous;
    private final TransportButton playPause;
    private final TransportButton next;
    private final List<MediaSource> availableSources = new ArrayList<>();
    private MediaSnapshot snapshot;
    private boolean seeking;
    private boolean hasArtwork;
    private boolean hasMedia;

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
        setBackground(Ui.background(Ui.BACKGROUND, 26, context));
        setClipToOutline(true);
        setElevation(Ui.dp(context, 12));

        artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackgroundColor(Ui.BACKGROUND);
        addView(artwork, match());

        placeholder = new MusicPlaceholderView(context);
        addView(placeholder);

        View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                style == CardStyle.COMPACT
                        ? new int[]{0x5E11151E, 0x2211151E, 0xAD10141B, 0xF510141B}
                        : new int[]{0x6011151E, 0x1011151E, 0xB010141B, 0xFA10141B}));
        addView(scrim, match());

        View border = new View(context);
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(Color.TRANSPARENT);
        borderDrawable.setCornerRadius(Ui.dp(context, 26));
        borderDrawable.setStroke(Ui.dp(context, 1), 0x334F5E68);
        border.setBackground(borderDrawable);
        addView(border, match());

        sourcePill = new LinearLayout(context);
        sourcePill.setGravity(Gravity.CENTER_VERTICAL);
        sourcePill.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 13), 0);
        sourcePill.setBackground(pillBackground(context, 0xB333333B, 0x224F5E68, 18));
        sourceGlyph = new SourceGlyphView(context);
        int sourceIconSize = Ui.dp(context, style == CardStyle.COMPACT ? 22 : 24);
        sourcePill.addView(sourceGlyph, new LinearLayout.LayoutParams(sourceIconSize, sourceIconSize));
        TextView sourceDot = text("●", 8, 0xFF58A6FF, Typeface.BOLD);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        dotParams.leftMargin = Ui.dp(context, 7);
        sourcePill.addView(sourceDot, dotParams);
        sourceLabel = text("MEDIA", style == CardStyle.COMPACT ? 11 : 12,
                Ui.PRIMARY, Typeface.BOLD);
        sourceLabel.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams sourceLabelParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        sourceLabelParams.leftMargin = Ui.dp(context, 5);
        sourcePill.addView(sourceLabel, sourceLabelParams);
        sourcePill.setOnClickListener(v -> toggleSourceChooser());
        LayoutParams sourcePillParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, Ui.dp(context, style == CardStyle.COMPACT ? 34 : 38));
        sourcePillParams.gravity = Gravity.TOP | Gravity.START;
        sourcePillParams.leftMargin = Ui.dp(context, style == CardStyle.COMPACT ? 20 : 22);
        sourcePillParams.topMargin = Ui.dp(context, style == CardStyle.COMPACT ? 16 : 18);
        addView(sourcePill, sourcePillParams);

        sourceChooser = new HorizontalScrollView(context);
        sourceChooser.setHorizontalScrollBarEnabled(false);
        sourceChooser.setVisibility(GONE);
        sourceChooser.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
        sourceOptions = new LinearLayout(context);
        sourceOptions.setGravity(Gravity.CENTER_VERTICAL);
        sourceChooser.addView(sourceOptions, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        LayoutParams chooserParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, 42));
        chooserParams.gravity = Gravity.TOP;
        chooserParams.topMargin = Ui.dp(context, style == CardStyle.COMPACT ? 55 : 62);
        addView(sourceChooser, chooserParams);

        statusPill = text("", style == CardStyle.COMPACT ? 9 : 11, Ui.ERROR, Typeface.NORMAL);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setMaxLines(1);
        statusPill.setPadding(Ui.dp(context, 11), 0, Ui.dp(context, 11), 0);
        LayoutParams statusParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, Ui.dp(context, style == CardStyle.COMPACT ? 29 : 32));
        statusParams.gravity = Gravity.TOP | Gravity.END;
        statusParams.topMargin = Ui.dp(context, style == CardStyle.COMPACT ? 18 : 20);
        statusParams.rightMargin = Ui.dp(context, 49);
        addView(statusPill, statusParams);

        TextView dragHandle = text("⋮", style == CardStyle.COMPACT ? 24 : 27,
                Ui.SECONDARY, Typeface.BOLD);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setContentDescription("Перетащить виджет");
        dragHandle.setOnTouchListener(listener::onDragTouch);
        LayoutParams dragParams = new LayoutParams(Ui.dp(context, 42), Ui.dp(context, 48));
        dragParams.gravity = Gravity.TOP | Gravity.END;
        dragParams.topMargin = Ui.dp(context, 7);
        dragParams.rightMargin = Ui.dp(context, 4);
        addView(dragHandle, dragParams);

        metadata = new LinearLayout(context);
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        artworkThumbnail = new ImageView(context);
        artworkThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkThumbnail.setBackground(Ui.background(Ui.NESTED, 10, context));
        artworkThumbnail.setClipToOutline(true);
        metadata.addView(artworkThumbnail, new LinearLayout.LayoutParams(
                Ui.dp(context, 72), Ui.dp(context, 72)));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        title = text(getResources().getString(R.string.unknown_track),
                style == CardStyle.COMPACT ? 19 : 28, Ui.PRIMARY, Typeface.BOLD);
        title.setMaxLines(style == CardStyle.COMPACT ? 1 : 2);
        title.setShadowLayer(Ui.dp(context, 2), 0, Ui.dp(context, 1), 0xB0000000);
        textColumn.addView(title, fullWrap());
        subtitle = text("", style == CardStyle.COMPACT ? 13 : 17,
                0xFF9B9DA4, Typeface.NORMAL);
        subtitle.setMaxLines(1);
        LinearLayout.LayoutParams subtitleParams = fullWrap();
        subtitleParams.topMargin = Ui.dp(context, style == CardStyle.COMPACT ? 3 : 5);
        textColumn.addView(subtitle, subtitleParams);
        LinearLayout.LayoutParams textColumnParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        textColumnParams.leftMargin = Ui.dp(context, 14);
        metadata.addView(textColumn, textColumnParams);
        addView(metadata);

        progressRow = new LinearLayout(context);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = text("–:––", style == CardStyle.COMPACT ? 10 : 12,
                Ui.SECONDARY, Typeface.NORMAL);
        progress = new SeekBar(context);
        progress.setMax(PROGRESS_MAX);
        progress.setPadding(Ui.dp(context, 6), 0, Ui.dp(context, 6), 0);
        progress.setSplitTrack(false);
        configureProgressStyle(context, progress);
        duration = text("–:––", style == CardStyle.COMPACT ? 10 : 12,
                Ui.SECONDARY, Typeface.NORMAL);
        duration.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        int timeWidth = Ui.dp(context, style == CardStyle.COMPACT ? 42 : 52);
        progressRow.addView(elapsed, new LinearLayout.LayoutParams(
                timeWidth, LayoutParams.WRAP_CONTENT));
        progressRow.addView(progress, new LinearLayout.LayoutParams(
                0, Ui.dp(context, 28), 1f));
        progressRow.addView(duration, new LinearLayout.LayoutParams(
                timeWidth, LayoutParams.WRAP_CONTENT));
        addView(progressRow);

        divider = new View(context);
        divider.setBackgroundColor(0x553B444C);
        addView(divider);

        controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER);
        previous = new TransportButton(context, TransportButton.Type.PREVIOUS, false);
        playPause = new TransportButton(context, TransportButton.Type.PLAY_PAUSE, true);
        next = new TransportButton(context, TransportButton.Type.NEXT, false);
        int sideSize = style == CardStyle.COMPACT ? 48 : 64;
        int playSize = style == CardStyle.COMPACT ? 58 : 82;
        int gap = style == CardStyle.COMPACT ? 22 : 28;
        controls.addView(previous, square(context, sideSize));
        LinearLayout.LayoutParams playParams = square(context, playSize);
        playParams.setMargins(Ui.dp(context, gap), 0, Ui.dp(context, gap), 0);
        controls.addView(playPause, playParams);
        controls.addView(next, square(context, sideSize));
        addView(controls);

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
        hasMedia = !value.title.isBlank() || !value.artist.isBlank()
                || !value.album.isBlank() || value.duration > 0L;
        title.setText(hasMedia && !value.title.isBlank()
                ? value.title : getResources().getString(R.string.unknown_track));
        String detail = value.artist;
        if (!value.album.isBlank()) {
            detail = detail.isBlank() ? value.album : detail + "  •  " + value.album;
        }
        subtitle.setText(hasMedia && !detail.isBlank()
                ? detail : getResources().getString(R.string.empty_hint));
        subtitle.setVisibility(VISIBLE);
        if (bridgeConnected && value.backendConnected) {
            statusPill.setVisibility(GONE);
        } else {
            setStatusPill("Медиасервис недоступен", true);
        }
        previous.setEnabled(value.supports(MediaBridgeContract.CAP_PREVIOUS));
        next.setEnabled(value.supports(MediaBridgeContract.CAP_NEXT));
        boolean toggle = value.supports(MediaBridgeContract.CAP_TOGGLE)
                || value.isPlaying() && value.supports(MediaBridgeContract.CAP_PAUSE)
                || !value.isPlaying() && value.supports(MediaBridgeContract.CAP_PLAY);
        playPause.setEnabled(toggle);
        playPause.setPlaying(value.isPlaying());
        progress.setEnabled(value.duration > 0L && value.supports(MediaBridgeContract.CAP_SEEK));
        progress.setAlpha(progress.isEnabled() ? 1f : 0.55f);
        duration.setText(formatTime(value.duration));
        renderSources(value);
        updateContentLayout();
        tick(SystemClock.elapsedRealtime());
    }

    void renderDisconnected(String detail) {
        snapshot = null;
        hasMedia = false;
        title.setText(R.string.unknown_track);
        subtitle.setText(R.string.empty_hint);
        subtitle.setVisibility(VISIBLE);
        setStatusPill(detail.contains("Подключение")
                ? "Подключение к медиасервису" : "Медиасервис недоступен", true);
        previous.setEnabled(false);
        playPause.setEnabled(false);
        playPause.setPlaying(false);
        next.setEnabled(false);
        progress.setEnabled(false);
        progress.setAlpha(0.55f);
        progress.setProgress(0);
        elapsed.setText("–:––");
        duration.setText("–:––");
        setArtwork(null);
        renderFallbackSource();
        updateContentLayout();
    }

    void showTransientStatus(String message, boolean error) {
        setStatusPill(message, error);
    }

    void setArtwork(Bitmap bitmap) {
        artwork.animate().cancel();
        hasArtwork = bitmap != null;
        if (bitmap == null) {
            artwork.setImageDrawable(null);
            artwork.setAlpha(0f);
            artworkThumbnail.setImageDrawable(null);
        } else {
            artwork.setImageBitmap(bitmap);
            artwork.setAlpha(0f);
            artworkThumbnail.setImageBitmap(bitmap);
            artwork.animate().alpha(1f).setDuration(220L).start();
        }
        updateContentLayout();
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

    private void updateContentLayout() {
        boolean compact = style == CardStyle.COMPACT;
        boolean showProgress = hasMedia && snapshot != null && snapshot.duration > 0L;
        boolean showThumbnail = compact && hasMedia && hasArtwork;

        artworkThumbnail.setVisibility(showThumbnail ? VISIBLE : GONE);
        LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams)
                metadata.getChildAt(1).getLayoutParams();
        textParams.leftMargin = showThumbnail ? Ui.dp(getContext(), 14) : 0;
        metadata.getChildAt(1).setLayoutParams(textParams);

        LayoutParams placeholderParams = new LayoutParams(
                Ui.dp(getContext(), compact ? 94 : 158),
                Ui.dp(getContext(), compact ? 94 : 158));
        placeholderParams.gravity = Gravity.TOP | Gravity.END;
        placeholderParams.topMargin = Ui.dp(getContext(), compact ? 66 : 122);
        placeholderParams.rightMargin = Ui.dp(getContext(), compact ? 36 : 40);
        placeholder.setLayoutParams(placeholderParams);
        placeholder.setVisibility(hasArtwork ? GONE : VISIBLE);

        LayoutParams metadataParams;
        if (compact && !hasMedia) {
            metadataParams = new LayoutParams(Ui.dp(getContext(), 330), LayoutParams.WRAP_CONTENT);
            metadataParams.leftMargin = Ui.dp(getContext(), 34);
            metadataParams.topMargin = Ui.dp(getContext(), 92);
        } else {
            metadataParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            metadataParams.leftMargin = Ui.dp(getContext(), compact ? 24 : 30);
            metadataParams.rightMargin = Ui.dp(getContext(), compact ? 24 : 30);
            metadataParams.topMargin = Ui.dp(getContext(), compact ? 108 : 282);
        }
        metadataParams.gravity = Gravity.TOP | Gravity.START;
        metadata.setLayoutParams(metadataParams);

        LayoutParams progressParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 30));
        progressParams.gravity = Gravity.TOP;
        progressParams.leftMargin = Ui.dp(getContext(), compact ? 20 : 26);
        progressParams.rightMargin = Ui.dp(getContext(), compact ? 20 : 26);
        progressParams.topMargin = Ui.dp(getContext(), compact ? 194 : 378);
        progressRow.setLayoutParams(progressParams);
        progressRow.setVisibility(showProgress ? VISIBLE : GONE);

        LayoutParams dividerParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 1));
        dividerParams.gravity = Gravity.TOP;
        dividerParams.leftMargin = Ui.dp(getContext(), compact ? 28 : 30);
        dividerParams.rightMargin = Ui.dp(getContext(), compact ? 28 : 30);
        dividerParams.topMargin = Ui.dp(getContext(), compact ? 190 : 390);
        divider.setLayoutParams(dividerParams);
        divider.setVisibility(showProgress ? GONE : VISIBLE);

        LayoutParams controlsParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(getContext(), compact ? 60 : 82));
        controlsParams.gravity = Gravity.TOP;
        controlsParams.topMargin = Ui.dp(getContext(), compact ? 224 : 410);
        controls.setLayoutParams(controlsParams);
    }

    private void renderSources(MediaSnapshot value) {
        availableSources.clear();
        MediaSource selected = null;
        for (MediaSource source : value.sources) {
            if (source.id == MediaSource.Id.UNKNOWN) continue;
            availableSources.add(source);
            if (source.selected) selected = source;
        }
        MediaSource.Id active = selected == null ? value.audioSource : selected.id;
        setSourcePill(active, active == MediaSource.Id.UNKNOWN ? "MEDIA" : active.label());
        rebuildSourceOptions();
    }

    private void renderFallbackSource() {
        availableSources.clear();
        setSourcePill(MediaSource.Id.UNKNOWN, "MEDIA");
        sourceChooser.setVisibility(GONE);
        sourceOptions.removeAllViews();
    }

    private void setSourcePill(MediaSource.Id source, String label) {
        sourceGlyph.setSource(source);
        sourceLabel.setText(label.toUpperCase(Locale.ROOT));
    }

    private void rebuildSourceOptions() {
        sourceOptions.removeAllViews();
        for (MediaSource source : availableSources) {
            TextView option = text(source.id.label().toUpperCase(Locale.ROOT), 10,
                    source.selected ? Ui.BACKGROUND : Ui.PRIMARY, Typeface.BOLD);
            option.setGravity(Gravity.CENTER);
            option.setPadding(Ui.dp(getContext(), 12), 0, Ui.dp(getContext(), 12), 0);
            option.setBackground(pillBackground(getContext(),
                    source.selected ? Ui.ACCENT : 0xD933333B, 0x554F5E68, 15));
            boolean selectable = source.available
                    && (source.capabilities & MediaBridgeContract.CAP_SET_SOURCE) != 0L;
            option.setEnabled(selectable && !source.selected);
            option.setAlpha(source.available || source.connected || source.selected ? 1f : 0.4f);
            option.setOnClickListener(v -> {
                sourceChooser.setVisibility(GONE);
                listener.onSource(source.id);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, Ui.dp(getContext(), 30));
            params.rightMargin = Ui.dp(getContext(), 7);
            sourceOptions.addView(option, params);
        }
    }

    private void toggleSourceChooser() {
        if (sourceOptions.getChildCount() < 2) return;
        sourceChooser.setVisibility(sourceChooser.getVisibility() == VISIBLE ? GONE : VISIBLE);
    }

    private void setStatusPill(String message, boolean error) {
        String prefix = error ? "△  " : "";
        statusPill.setText(prefix + message);
        statusPill.setTextColor(error ? Ui.ERROR : Ui.ACCENT);
        statusPill.setBackground(pillBackground(getContext(),
                error ? 0x1FD98282 : 0x1F7893A0,
                error ? 0x88D98282 : 0x887893A0, 15));
        statusPill.setVisibility(VISIBLE);
    }

    private static GradientDrawable pillBackground(Context context, int color,
            int strokeColor, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(Ui.dp(context, radiusDp));
        background.setStroke(Ui.dp(context, 1), strokeColor);
        return background;
    }

    private static void configureProgressStyle(Context context, SeekBar seekBar) {
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x553C4148);
        track.setCornerRadius(Ui.dp(context, 2));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xFF83AFC2);
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
        thumb.setColor(0xFF83AFC2);
        int thumbSize = Ui.dp(context, 10);
        thumb.setSize(thumbSize, thumbSize);
        seekBar.setThumb(thumb);
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
