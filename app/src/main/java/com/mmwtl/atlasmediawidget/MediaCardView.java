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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
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
        void onOpenSource();
    }

    private static final int PROGRESS_MAX = 10_000;
    private static final MediaSource.Id[] WIDGET_SOURCES = {
            MediaSource.Id.BT, MediaSource.Id.RADIO,
            MediaSource.Id.USB, MediaSource.Id.ONLINE
    };

    private final Listener listener;
    private final CardStyle style;
    private final int cardWidth;
    private final int cardHeight;
    private final float widthScale;
    private final float heightScale;
    private final float uiScale;
    private final WidgetAppearance appearance;
    private final ImageView artwork;
    private final ImageView artworkThumbnail;
    private final ImageView placeholder;
    private final LinearLayout sourcePill;
    private final SourceGlyphView sourceGlyph;
    private final TextView sourceLabel;
    private final FrameLayout sourceChooser;
    private final GridLayout sourceOptions;
    private final TextView statusPill;
    private final LinearLayout metadata;
    private final TextView title;
    private final TextView subtitle;
    private final LinearLayout progressRow;
    private final SeekBar progress;
    private final TextView elapsed;
    private final TextView duration;
    private final View divider;
    private final FrameLayout controls;
    private final TransportButton previous;
    private final TransportButton playPause;
    private final TransportButton next;
    private final List<MediaSource> availableSources = new ArrayList<>();
    private MediaSnapshot snapshot;
    private MediaSource.Id activeSource = MediaSource.Id.UNKNOWN;
    private boolean seeking;
    private boolean hasArtwork;
    private boolean hasMedia;

    MediaCardView(Context context, int requestedWidthDp, int requestedHeightDp,
            int maxWidthPx, int maxHeightPx, CardStyle style,
            WidgetAppearance appearance, Listener listener) {
        super(context);
        this.listener = listener;
        this.style = style;
        this.appearance = appearance;
        cardWidth = Math.min(maxWidthPx, Math.max(Ui.dp(context, 320),
                Ui.dp(context, requestedWidthDp)));
        cardHeight = Math.min(maxHeightPx, Math.max(Ui.dp(context, 220),
                Ui.dp(context, requestedHeightDp)));
        widthScale = cardWidth / (float) Ui.dp(context, style.defaultWidthDp);
        heightScale = cardHeight / (float) Ui.dp(context, style.defaultHeightDp);
        uiScale = Math.max(0.72f, Math.min(1.75f, Math.min(widthScale, heightScale)));
        setMinimumWidth(cardWidth);
        setMinimumHeight(cardHeight);
        setBackground(Ui.background(Ui.BACKGROUND, 26 * uiScale, context));
        setClipToOutline(true);
        setClickable(true);
        setOnClickListener(v -> listener.onOpenSource());

        artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackgroundColor(Ui.BACKGROUND);
        addView(artwork, match());

        placeholder = new ImageView(context);
        placeholder.setImageResource(R.drawable.ic_sound_wave);
        placeholder.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        placeholder.setScaleType(ImageView.ScaleType.FIT_CENTER);
        placeholder.setAlpha(0.22f);
        placeholder.setPadding(d(7), d(7), d(7), d(7));
        placeholder.setContentDescription("Звуковая дорожка — обложка отсутствует");
        addView(placeholder);

        View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                style == CardStyle.COMPACT
                        ? new int[]{0x5E1D2228, 0x221D2228, 0xAD1D2228, 0xF51D2228}
                        : new int[]{0x601D2228, 0x101D2228, 0xB01D2228, 0xFA1D2228}));
        addView(scrim, match());

        View border = new View(context);
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(Color.TRANSPARENT);
        borderDrawable.setCornerRadius(d(26));
        borderDrawable.setStroke(Math.max(1, d(1)), 0x334F5E68);
        border.setBackground(borderDrawable);
        addView(border, match());

        sourcePill = new LinearLayout(context);
        sourcePill.setGravity(Gravity.CENTER_VERTICAL);
        sourcePill.setPadding(d(11), 0, d(15), 0);
        sourcePill.setBackground(pillBackground(context, 0xB333333B, 0x334F5E68, d(19)));
        sourceGlyph = new SourceGlyphView(context);
        sourcePill.addView(sourceGlyph, new LinearLayout.LayoutParams(d(25), d(25)));
        TextView sourceDot = text("●", 8, 0xFF58A6FF, Typeface.BOLD);
        LinearLayout.LayoutParams dotParams = wrap();
        dotParams.leftMargin = d(7);
        sourcePill.addView(sourceDot, dotParams);
        sourceLabel = text("MEDIA", appearance.topRowTextSizeSp,
                Ui.PRIMARY, Typeface.BOLD);
        sourceLabel.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams sourceTextParams = wrap();
        sourceTextParams.leftMargin = d(5);
        sourcePill.addView(sourceLabel, sourceTextParams);
        sourcePill.setOnClickListener(v -> toggleSourceChooser());
        LayoutParams sourcePillParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                d(Math.max(38, appearance.topRowTextSizeSp + 24)));
        sourcePillParams.gravity = Gravity.TOP | Gravity.START;
        sourcePillParams.leftMargin = bx(Math.max(8,
                appearance.contentInsetDp - (style == CardStyle.COMPACT ? 4 : 8)));
        sourcePillParams.topMargin = by(appearance.topInsetDp);
        addView(sourcePill, sourcePillParams);

        statusPill = text("", Math.max(8, appearance.topRowTextSizeSp - 2),
                Ui.ERROR, Typeface.NORMAL);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setMaxLines(1);
        statusPill.setPadding(d(11), 0, d(11), 0);
        LayoutParams statusParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                d(Math.max(31, appearance.topRowTextSizeSp + 20)));
        statusParams.gravity = Gravity.TOP | Gravity.END;
        statusParams.topMargin = by(appearance.topInsetDp + 3);
        statusParams.rightMargin = bx(49);
        addView(statusPill, statusParams);

        TextView dragHandle = text("⋮", style == CardStyle.COMPACT ? 27 : 29,
                Ui.SECONDARY, Typeface.BOLD);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setContentDescription("Перетащить виджет");
        dragHandle.setOnTouchListener(listener::onDragTouch);
        LayoutParams dragParams = new LayoutParams(d(43), d(50));
        dragParams.gravity = Gravity.TOP | Gravity.END;
        dragParams.topMargin = by(5);
        dragParams.rightMargin = bx(3);
        addView(dragHandle, dragParams);

        metadata = new LinearLayout(context);
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        artworkThumbnail = new ImageView(context);
        artworkThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkThumbnail.setBackground(Ui.background(Ui.NESTED, 10 * uiScale, context));
        artworkThumbnail.setClipToOutline(true);
        metadata.addView(artworkThumbnail, new LinearLayout.LayoutParams(d(76), d(76)));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        title = text(getResources().getString(R.string.unknown_track),
                appearance.titleTextSizeSp, Ui.PRIMARY, Typeface.BOLD);
        title.setMaxLines(2);
        title.setLineSpacing(d(2), 1.06f);
        title.setShadowLayer(d(2), 0, d(1), 0xB0000000);
        textColumn.addView(title, fullWrap());
        subtitle = text("", appearance.subtitleTextSizeSp,
                0xFFAFB1B7, Typeface.NORMAL);
        subtitle.setMaxLines(1);
        LinearLayout.LayoutParams subtitleParams = fullWrap();
        subtitleParams.topMargin = d(appearance.subtitleGapDp);
        textColumn.addView(subtitle, subtitleParams);
        LinearLayout.LayoutParams textColumnParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        textColumnParams.leftMargin = d(15);
        metadata.addView(textColumn, textColumnParams);
        addView(metadata);

        progressRow = new LinearLayout(context);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = text("–:––", appearance.timeTextSizeSp,
                Ui.SECONDARY, Typeface.NORMAL);
        progress = new SeekBar(context);
        progress.setMax(PROGRESS_MAX);
        progress.setPadding(d(6), 0, d(6), 0);
        progress.setSplitTrack(false);
        configureProgressStyle(context, progress, uiScale, appearance.progressThicknessDp);
        duration = text("–:––", appearance.timeTextSizeSp,
                Ui.SECONDARY, Typeface.NORMAL);
        duration.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        int timeWidth = d(Math.max(style == CardStyle.COMPACT ? 46 : 58,
                appearance.timeTextSizeSp * 3.6f));
        progressRow.addView(elapsed, new LinearLayout.LayoutParams(timeWidth, LayoutParams.WRAP_CONTENT));
        progressRow.addView(progress, new LinearLayout.LayoutParams(0, d(30), 1f));
        progressRow.addView(duration, new LinearLayout.LayoutParams(timeWidth, LayoutParams.WRAP_CONTENT));
        addView(progressRow);

        divider = new View(context);
        divider.setBackgroundColor(0x553B444C);
        addView(divider);

        controls = new FrameLayout(context);
        previous = new TransportButton(context, TransportButton.Type.PREVIOUS);
        playPause = new TransportButton(context, TransportButton.Type.PLAY_PAUSE);
        next = new TransportButton(context, TransportButton.Type.NEXT);
        controls.addView(previous);
        controls.addView(playPause);
        controls.addView(next);
        addView(controls);

        sourceChooser = new FrameLayout(context);
        sourceChooser.setVisibility(GONE);
        sourceChooser.setClickable(true);
        sourceChooser.setPadding(d(12), d(10), d(12), d(10));
        sourceChooser.setBackground(pillBackground(context, 0xF0191D23, 0x77596872, d(22)));
        sourceOptions = new GridLayout(context);
        sourceOptions.setColumnCount(2);
        sourceOptions.setRowCount(2);
        sourceOptions.setUseDefaultMargins(false);
        sourceChooser.addView(sourceOptions, match());
        addView(sourceChooser);

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

    int cardWidth() { return cardWidth; }
    int cardHeight() { return cardHeight; }

    void renderSnapshot(MediaSnapshot value, boolean bridgeConnected) {
        renderSnapshot(value, bridgeConnected, null);
    }

    void renderSnapshot(MediaSnapshot value, boolean bridgeConnected, RadioDisplay radioDisplay) {
        snapshot = value;
        if (value == null) {
            renderDisconnected(getResources().getString(R.string.bridge_disconnected));
            return;
        }
        activeSource = selectedSource(value);
        hasMedia = MediaPresentation.hasContent(activeSource, bridgeConnected,
                value.backendConnected, value.title, value.artist, value.album, value.duration);
        String displayTitle = radioDisplay == null
                ? MediaPresentation.title(activeSource, value.title) : radioDisplay.title;
        title.setText(hasMedia && !displayTitle.isBlank()
                ? displayTitle : getResources().getString(R.string.unknown_track));
        String detail = radioDisplay == null
                ? MediaPresentation.subtitle(activeSource, value.artist, value.album)
                : radioDisplay.subtitle;
        subtitle.setText(hasMedia && !detail.isBlank()
                ? detail : getResources().getString(R.string.empty_hint));
        if (bridgeConnected && value.backendConnected) statusPill.setVisibility(GONE);
        else setStatusPill("Медиасервис недоступен", true);
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
        activeSource = MediaSource.Id.UNKNOWN;
        hasMedia = false;
        title.setText(R.string.unknown_track);
        subtitle.setText(R.string.empty_hint);
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

    void showTransientStatus(String message, boolean error) { setStatusPill(message, error); }

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
        } else progress.setProgress(0);
    }

    private void updateContentLayout() {
        boolean compact = style == CardStyle.COMPACT;
        boolean chooserVisible = sourceChooser.getVisibility() == VISIBLE;
        boolean showProgress = hasMedia && snapshot != null && snapshot.duration > 0L;
        boolean showThumbnail = compact && hasMedia && hasArtwork;
        int panelHeight = Math.min(cardHeight, by(appearance.controlPanelHeightDp));
        int controlsTop = Math.max(0, cardHeight - panelHeight);

        artworkThumbnail.setVisibility(showThumbnail ? VISIBLE : GONE);
        LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams)
                metadata.getChildAt(1).getLayoutParams();
        textParams.leftMargin = showThumbnail ? d(15) : 0;
        metadata.getChildAt(1).setLayoutParams(textParams);

        int placeholderSize = d(compact ? 150 : 220);
        LayoutParams placeholderParams = new LayoutParams(placeholderSize, placeholderSize);
        placeholderParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        placeholderParams.topMargin = by(compact ? 58 : 72);
        placeholder.setLayoutParams(placeholderParams);
        placeholder.setVisibility(!hasArtwork && !chooserVisible ? VISIBLE : GONE);

        LayoutParams metadataParams;
        if (compact && !hasMedia) {
            int left = bx(appearance.contentInsetDp + 10);
            int width = Math.max(bx(180), cardWidth - left - bx(121));
            metadataParams = new LayoutParams(width, LayoutParams.WRAP_CONTENT);
            metadataParams.leftMargin = left;
            metadataParams.topMargin = by(78 + appearance.textGapDp);
        } else {
            metadataParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            metadataParams.leftMargin = bx(appearance.contentInsetDp);
            metadataParams.rightMargin = bx(appearance.contentInsetDp);
            metadataParams.topMargin = by((compact ? 76 : 258) + appearance.textGapDp);
        }
        metadataParams.gravity = Gravity.TOP | Gravity.START;
        metadata.setLayoutParams(metadataParams);
        metadata.setVisibility(chooserVisible ? GONE : VISIBLE);

        LayoutParams progressParams = new LayoutParams(LayoutParams.MATCH_PARENT, d(32));
        progressParams.gravity = Gravity.TOP;
        progressParams.leftMargin = bx(Math.max(8, appearance.contentInsetDp - 4));
        progressParams.rightMargin = bx(Math.max(8, appearance.contentInsetDp - 4));
        progressParams.topMargin = Math.max(0,
                controlsTop - by((compact ? 32 : 33) + appearance.progressGapDp));
        progressRow.setLayoutParams(progressParams);
        progressRow.setVisibility(!chooserVisible && showProgress ? VISIBLE : GONE);

        LayoutParams dividerParams = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, d(1)));
        dividerParams.gravity = Gravity.TOP;
        dividerParams.leftMargin = bx(appearance.contentInsetDp + (compact ? 4 : 0));
        dividerParams.rightMargin = bx(appearance.contentInsetDp + (compact ? 4 : 0));
        dividerParams.topMargin = Math.max(0,
                controlsTop - by((compact ? 22 : 8) + appearance.progressGapDp));
        divider.setLayoutParams(dividerParams);
        divider.setVisibility(!chooserVisible && !showProgress ? VISIBLE : GONE);

        LayoutParams controlsParams = new LayoutParams(LayoutParams.MATCH_PARENT, panelHeight);
        controlsParams.gravity = Gravity.TOP;
        int controlBottomInset = Math.min(by(appearance.controlBottomInsetDp),
                Math.max(0, controlsTop));
        controlsParams.topMargin = controlsTop - controlBottomInset;
        controls.setLayoutParams(controlsParams);
        controls.setVisibility(chooserVisible ? GONE : VISIBLE);
        updateControlLayout(compact, panelHeight);

        LayoutParams chooserParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                Math.max(d(120), cardHeight - by(compact ? 63 : 68) - by(16)));
        chooserParams.gravity = Gravity.TOP;
        chooserParams.leftMargin = bx(18);
        chooserParams.rightMargin = bx(18);
        chooserParams.topMargin = by(compact ? 63 : 68);
        sourceChooser.setLayoutParams(chooserParams);
    }

    private void updateControlLayout(boolean compact, int panelHeight) {
        float iconScale = appearance.controlIconScalePercent / 100f;
        int maxButtonSize = Math.max(1, Math.round(panelHeight * 0.96f));
        int sideSize = Math.min(maxButtonSize,
                d((compact ? 62 : 80) * iconScale));
        int playSize = Math.min(maxButtonSize,
                d((compact ? 74 : 98) * iconScale));
        layoutControl(previous, sideSize);
        layoutControl(playPause, playSize);
        layoutControl(next, sideSize);

        float requestedOffset = cardWidth * appearance.controlSpreadPercent / 100f;
        float maxOffset = Math.max(0f, cardWidth / 2f - sideSize / 2f - d(8));
        float offset = Math.min(requestedOffset, maxOffset);
        previous.setTranslationX(-offset);
        playPause.setTranslationX(0f);
        next.setTranslationX(offset);
    }

    private static void layoutControl(TransportButton button, int size) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        button.setLayoutParams(params);
        int padding = Math.round(size * 0.15f);
        button.setPadding(padding, padding, padding, padding);
    }

    private void renderSources(MediaSnapshot value) {
        availableSources.clear();
        activeSource = selectedSource(value).displayId();
        for (MediaSource.Id id : WIDGET_SOURCES) {
            MediaSource match = null;
            for (MediaSource source : value.sources) {
                if (source.id.displayId() == id) {
                    if (match == null || source.id == id) match = source;
                }
            }
            if (match == null) match = new MediaSource(id, id == activeSource,
                    id == activeSource, id == activeSource, 0L);
            availableSources.add(match);
        }
        setSourcePill(activeSource, activeSource == MediaSource.Id.UNKNOWN ? "MEDIA" : activeSource.label());
        rebuildSourceOptions();
    }

    private static MediaSource.Id selectedSource(MediaSnapshot value) {
        for (MediaSource source : value.sources) if (source.selected) return source.id;
        return value.audioSource;
    }

    private void renderFallbackSource() {
        availableSources.clear();
        setSourcePill(MediaSource.Id.UNKNOWN, "MEDIA");
        sourceChooser.setVisibility(GONE);
        sourceOptions.removeAllViews();
    }

    private void setSourcePill(MediaSource.Id source, String label) {
        sourceGlyph.setSource(source.displayId());
        sourceLabel.setText(label.toUpperCase(Locale.ROOT));
    }

    private void rebuildSourceOptions() {
        sourceOptions.removeAllViews();
        for (int index = 0; index < availableSources.size(); index++) {
            MediaSource source = availableSources.get(index);
            MediaSource.Id id = source.id.displayId();
            boolean selected = id == activeSource;
            LinearLayout option = new LinearLayout(getContext());
            option.setOrientation(LinearLayout.VERTICAL);
            option.setGravity(Gravity.CENTER);
            option.setPadding(d(8), d(7), d(8), d(7));
            option.setBackground(pillBackground(getContext(), selected ? 0x593E5966 : 0xB3262A30,
                    selected ? 0xFF83AFC2 : 0x66505B64, d(17)));
            SourceGlyphView glyph = new SourceGlyphView(getContext());
            glyph.setSource(id);
            option.addView(glyph, new LinearLayout.LayoutParams(d(36), d(36)));
            TextView label = text(id.label(), style == CardStyle.COMPACT ? 14 : 16,
                    Ui.PRIMARY, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = wrap();
            labelParams.topMargin = d(4);
            option.addView(label, labelParams);
            boolean selectable = source.available
                    && (source.capabilities & MediaBridgeContract.CAP_SET_SOURCE) != 0L;
            option.setEnabled(selectable && !selected);
            option.setAlpha(selected || source.available || source.connected ? 1f : 0.42f);
            option.setOnClickListener(v -> {
                hideSourceChooser();
                listener.onSource(id);
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / 2, 1f), GridLayout.spec(index % 2, 1f));
            params.width = 0;
            params.height = 0;
            params.setGravity(Gravity.FILL);
            params.setMargins(d(5), d(5), d(5), d(5));
            sourceOptions.addView(option, params);
        }
    }

    private void toggleSourceChooser() {
        if (sourceOptions.getChildCount() == 0) return;
        if (sourceChooser.getVisibility() == VISIBLE) hideSourceChooser();
        else {
            sourceChooser.setVisibility(VISIBLE);
            sourceChooser.bringToFront();
            sourcePill.bringToFront();
            updateContentLayout();
        }
    }

    private void hideSourceChooser() {
        sourceChooser.setVisibility(GONE);
        updateContentLayout();
    }

    private void setStatusPill(String message, boolean error) {
        statusPill.setText((error ? "△  " : "") + message);
        statusPill.setTextColor(error ? Ui.ERROR : Ui.ACCENT);
        statusPill.setBackground(pillBackground(getContext(),
                error ? 0x1FD98282 : 0x1F7893A0,
                error ? 0x88D98282 : 0x887893A0, d(16)));
        statusPill.setVisibility(VISIBLE);
    }

    private static GradientDrawable pillBackground(Context context, int color,
            int strokeColor, int radiusPx) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radiusPx);
        background.setStroke(Math.max(1, Ui.dp(context, 1)), strokeColor);
        return background;
    }

    private static void configureProgressStyle(Context context, SeekBar seekBar, float scale,
            int thicknessDp) {
        int trackHeight = Math.max(1, Math.round(Ui.dp(context, thicknessDp) * scale));
        float radius = trackHeight / 2f;
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x553C4148);
        track.setCornerRadius(radius);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xFF83AFC2);
        fill.setCornerRadius(radius);
        ClipDrawable clippedFill = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{track, clippedFill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        int drawableHeight = Math.max(trackHeight, Math.round(Ui.dp(context, 30) * scale));
        int inset = Math.max(0, (drawableHeight - trackHeight) / 2);
        layers.setLayerInset(0, 0, inset, 0, inset);
        layers.setLayerInset(1, 0, inset, 0, inset);
        seekBar.setProgressDrawable(layers);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xFF83AFC2);
        int thumbSize = Math.max(trackHeight + Math.round(Ui.dp(context, 4) * scale),
                Math.round(Ui.dp(context, 11) * scale));
        thumb.setSize(thumbSize, thumbSize);
        seekBar.setThumb(thumb);
    }

    private TextView text(String value, float sizeSp, int color, int textStyle) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp * uiScale);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, textStyle);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private int d(float baseDp) { return Math.max(1, Math.round(Ui.dp(getContext(), baseDp) * uiScale)); }
    private int bx(float baseDp) { return Math.round(Ui.dp(getContext(), baseDp) * widthScale); }
    private int by(float baseDp) { return Math.round(Ui.dp(getContext(), baseDp) * heightScale); }
    private LayoutParams match() { return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT); }
    private LinearLayout.LayoutParams fullWrap() { return new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
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
