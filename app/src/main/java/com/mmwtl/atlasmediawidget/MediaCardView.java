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
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MediaCardView extends FrameLayout {
    interface Listener {
        boolean onDragTouch(View view, MotionEvent event);
        void onCommand(String command);
        void onSeek(long positionMs);
        void onSource(MediaSource.Id source);
        void onOpenSource();
        void onRadioStationsRequested();
        void onRadioStation(RadioStation station);
        void onRadioArtworkRequested(RadioStation station);
    }

    private static final int PROGRESS_MAX = 10_000;
    private static final long CHOOSER_AUTO_HIDE_MS = 10_000L;
    private static final MediaSource.Id[] WIDGET_SOURCES = {
            MediaSource.Id.RADIO, MediaSource.Id.BT,
            MediaSource.Id.USB, MediaSource.Id.ONLINE, MediaSource.Id.CPAA
    };

    private final Listener listener;
    private final CardStyle style;
    private final int cardWidth;
    private final int cardHeight;
    private final float widthScale;
    private final float heightScale;
    private final float uiScale;
    private final WidgetAppearance appearance;
    private final boolean radioSavedNavigation;
    private final boolean dragHandleVisible;
    private final int favoriteColumns;
    private final int favoriteRows;
    private final ImageView artwork;
    private final ImageView artworkThumbnail;
    private final ImageView placeholder;
    private final LinearLayout sourcePill;
    private final SourceGlyphView sourceGlyph;
    private final TextView sourceLabel;
    private final FrameLayout sourceChooser;
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
    private final FrameLayout controls;
    private final TransportButton previous;
    private final TransportButton playPause;
    private final TransportButton next;
    private final LinearLayout favoritesButton;
    private final FrameLayout favoritesChooser;
    private final TextView favoritesEmpty;
    private final GridView favoritesGrid;
    private final FavoriteStationAdapter favoritesAdapter;
    private int favoriteTileHeight;
    private int favoriteColumnWidth;
    private final List<MediaSource> availableSources = new ArrayList<>();
    private final Map<String, Bitmap> radioArtwork = new HashMap<>();
    private final Set<String> failedRadioArtwork = new HashSet<>();
    private List<RadioStation> favoriteStations = List.of();
    private boolean favoritesLoading;
    private MediaSnapshot snapshot;
    private MediaSource.Id activeSource = MediaSource.Id.UNKNOWN;
    private boolean seeking;
    private boolean hasArtwork;
    private boolean hasMedia;
    private long pendingSeekPosition = -1L;
    private long pendingSeekAtElapsedRealtime = -1L;
    private MediaSnapshot pendingSeekSnapshot;
    private long lastElapsedSecond = Long.MIN_VALUE;
    private int lastRenderedProgress = Integer.MIN_VALUE;
    private final Runnable chooserAutoHide = this::hideOpenChooser;

    MediaCardView(Context context, int requestedWidthPx, int requestedHeightPx,
            int maxWidthPx, int maxHeightPx, CardStyle style,
            WidgetAppearance appearance, boolean radioSavedNavigation,
            boolean dragHandleVisible, Listener listener) {
        this(context, requestedWidthPx, requestedHeightPx, maxWidthPx, maxHeightPx, style,
                appearance, radioSavedNavigation, dragHandleVisible,
                Prefs.DEFAULT_RADIO_FAVORITES_GRID_COLUMNS,
                Prefs.DEFAULT_RADIO_FAVORITES_GRID_ROWS, listener);
    }

    MediaCardView(Context context, int requestedWidthPx, int requestedHeightPx,
            int maxWidthPx, int maxHeightPx, CardStyle style,
            WidgetAppearance appearance, boolean radioSavedNavigation,
            boolean dragHandleVisible, int favoriteColumns, int favoriteRows,
            Listener listener) {
        super(context);
        this.listener = listener;
        this.style = style;
        this.appearance = appearance;
        this.radioSavedNavigation = radioSavedNavigation;
        this.dragHandleVisible = dragHandleVisible;
        this.favoriteColumns = Math.max(Prefs.MIN_RADIO_FAVORITES_GRID_COLUMNS,
                Math.min(Prefs.MAX_RADIO_FAVORITES_GRID_COLUMNS, favoriteColumns));
        this.favoriteRows = Math.max(Prefs.MIN_RADIO_FAVORITES_GRID_ROWS,
                Math.min(Prefs.MAX_RADIO_FAVORITES_GRID_ROWS, favoriteRows));
        cardWidth = Math.min(maxWidthPx, Math.max(Prefs.MIN_CARD_WIDTH_PX, requestedWidthPx));
        cardHeight = Math.min(maxHeightPx, Math.max(Prefs.MIN_CARD_HEIGHT_PX, requestedHeightPx));
        widthScale = cardWidth / (float) Ui.dp(context, style.defaultWidthDp);
        heightScale = cardHeight / (float) Ui.dp(context, style.defaultHeightDp);
        uiScale = Math.max(0.72f, Math.min(1.75f, Math.min(widthScale, heightScale)));
        setMinimumWidth(cardWidth);
        setMinimumHeight(cardHeight);
        setBackground(Ui.background(Ui.BACKGROUND, 26 * uiScale, context));
        setClipToOutline(true);
        setClickable(true);

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
                appearance.coverDimPreset.colors(style)));
        addView(scrim, match());

        View border = new View(context);
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(Color.TRANSPARENT);
        borderDrawable.setCornerRadius(d(26));
        borderDrawable.setStroke(Math.max(1, d(1)), 0x334F5E68);
        border.setBackground(borderDrawable);
        addView(border, match());

        int topPillHeightDp = Math.max(38, appearance.topRowTextSizeSp + 24);
        int topPillIconDp = Math.max(21, appearance.topRowTextSizeSp + 12);
        int topEndInsetDp = dragHandleVisible ? 47 : Math.max(8,
                appearance.contentInsetDp - (style == CardStyle.COMPACT ? 4 : 8));

        sourcePill = new LinearLayout(context);
        sourcePill.setGravity(Gravity.CENTER_VERTICAL);
        sourcePill.setPadding(d(11), 0, d(15), 0);
        sourcePill.setBackground(pillBackground(context, 0xB333333B, 0x334F5E68, d(19)));
        sourceGlyph = new SourceGlyphView(context);
        sourcePill.addView(sourceGlyph,
                new LinearLayout.LayoutParams(d(topPillIconDp), d(topPillIconDp)));
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
                d(topPillHeightDp));
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

        favoritesButton = new LinearLayout(context);
        favoritesButton.setGravity(Gravity.CENTER_VERTICAL);
        favoritesButton.setPadding(d(11), 0, d(15), 0);
        ImageView favoritesIcon = new ImageView(context);
        favoritesIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        favoritesIcon.setImageResource(R.drawable.ic_radio_favorites);
        favoritesIcon.setImageTintList(
                android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        favoritesButton.addView(favoritesIcon,
                new LinearLayout.LayoutParams(d(topPillIconDp), d(topPillIconDp)));
        TextView favoritesLabel = text("ИЗБРАННОЕ", appearance.topRowTextSizeSp,
                Ui.PRIMARY, Typeface.BOLD);
        favoritesLabel.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams favoritesTextParams = wrap();
        favoritesTextParams.leftMargin = d(7);
        favoritesButton.addView(favoritesLabel, favoritesTextParams);
        favoritesButton.setClickable(true);
        favoritesButton.setFocusable(true);
        favoritesButton.setContentDescription("Лайкнутые радиостанции");
        favoritesButton.setBackground(pillBackground(context, 0xB333333B, 0x334F5E68, d(19)));
        favoritesButton.setVisibility(GONE);
        favoritesButton.setOnClickListener(v -> toggleFavoritesChooser());
        LayoutParams favoriteButtonParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, d(topPillHeightDp));
        favoriteButtonParams.gravity = Gravity.TOP | Gravity.END;
        favoriteButtonParams.topMargin = by(appearance.topInsetDp);
        favoriteButtonParams.rightMargin = bx(topEndInsetDp);
        addView(favoritesButton, favoriteButtonParams);
        favoritesButton.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateStatusPillPosition());

        TextView dragHandle = text("⋮", style == CardStyle.COMPACT ? 27 : 29,
                Ui.SECONDARY, Typeface.BOLD);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setContentDescription("Перетащить виджет");
        dragHandle.setOnTouchListener(listener::onDragTouch);
        dragHandle.setVisibility(dragHandleVisible ? VISIBLE : GONE);
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
        metadata.setClipChildren(true);
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
        sourceOptions = new LinearLayout(context);
        sourceOptions.setOrientation(LinearLayout.VERTICAL);
        sourceChooser.addView(sourceOptions, match());
        sourceChooser.setOnClickListener(v -> hideSourceChooser());
        addView(sourceChooser);

        favoritesChooser = new FrameLayout(context);
        favoritesChooser.setVisibility(GONE);
        favoritesChooser.setClickable(true);
        favoritesChooser.setPadding(d(12), d(10), d(12), d(10));
        favoritesChooser.setBackground(
                pillBackground(context, 0xF0191D23, 0x77596872, d(22)));
        LinearLayout favoritesContent = new LinearLayout(context);
        favoritesContent.setOrientation(LinearLayout.VERTICAL);
        favoritesEmpty = text("Загрузка…", style == CardStyle.COMPACT ? 14 : 16,
                Ui.SECONDARY, Typeface.NORMAL);
        favoritesEmpty.setGravity(Gravity.CENTER);
        favoritesContent.addView(favoritesEmpty,
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        favoritesGrid = new GridView(context);
        favoritesGrid.setNumColumns(this.favoriteColumns);
        favoritesGrid.setHorizontalSpacing(d(6));
        favoritesGrid.setVerticalSpacing(d(6));
        favoritesGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        favoritesGrid.setGravity(Gravity.CENTER);
        favoritesGrid.setPadding(d(4), d(2), d(4), d(4));
        favoritesGrid.setClipToPadding(true);
        favoritesGrid.setSelector(android.R.color.transparent);
        favoritesGrid.setVerticalScrollBarEnabled(true);
        favoritesAdapter = new FavoriteStationAdapter();
        favoritesGrid.setAdapter(favoritesAdapter);
        favoritesGrid.setOnItemClickListener((parent, view, position, id) -> {
            RadioStation station = favoritesAdapter.getItem(position);
            hideFavoritesChooser();
            listener.onRadioStation(station);
        });
        favoritesContent.addView(favoritesGrid,
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        favoritesChooser.addView(favoritesContent, match());
        addView(favoritesChooser);
        setOnClickListener(v -> {
            if (favoritesChooser.getVisibility() == VISIBLE) {
                hideFavoritesChooser();
            } else if (sourceChooser.getVisibility() == VISIBLE) {
                hideSourceChooser();
            } else {
                listener.onOpenSource();
            }
        });

        previous.setOnClickListener(v -> listener.onCommand("PREVIOUS"));
        next.setOnClickListener(v -> listener.onCommand("NEXT"));
        playPause.setOnClickListener(v -> {
            if (snapshot == null) return;
            listener.onCommand(PlayPauseActionPolicy.command(
                    activeSource, snapshot.isPlaying(), snapshot.capabilities));
        });
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser && snapshot != null && snapshot.duration > 0L) {
                    setElapsed(snapshot.duration * value / PROGRESS_MAX);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                seeking = true;
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                seeking = false;
                if (snapshot != null && snapshot.duration > 0L
                        && snapshot.supports(MediaBridgeContract.CAP_SEEK)) {
                    long position = snapshot.duration * seekBar.getProgress() / PROGRESS_MAX;
                    beginPendingSeek(position);
                    listener.onSeek(position);
                }
            }
        });
        renderDisconnected(getResources().getString(R.string.bridge_connecting));
    }

    int cardWidth() { return cardWidth; }
    int cardHeight() { return cardHeight; }

    void renderSnapshot(MediaSnapshot value, boolean bridgeConnected) {
        updatePendingSeek(value);
        snapshot = value;
        if (value == null) {
            renderDisconnected(getResources().getString(R.string.bridge_disconnected));
            return;
        }
        activeSource = selectedSource(value);
        hasMedia = MediaPresentation.hasContent(activeSource, bridgeConnected,
                value.backendConnected, value.title, value.artist, value.album, value.duration);
        String displayTitle = MediaPresentation.title(activeSource, value.title);
        title.setText(hasMedia && !displayTitle.isBlank()
                ? displayTitle : getResources().getString(R.string.unknown_track));
        String detail = MediaPresentation.subtitle(activeSource, value.artist, value.album);
        subtitle.setText(hasMedia && !detail.isBlank()
                ? detail : getResources().getString(R.string.empty_hint));
        if (bridgeConnected && value.backendConnected) statusPill.setVisibility(GONE);
        else setStatusPill("Медиасервис недоступен", true);
        boolean directSavedNavigation = radioSavedNavigation
                && activeSource.displayId() == MediaSource.Id.RADIO;
        previous.setEnabled(value.supports(directSavedNavigation
                ? MediaBridgeContract.CAP_TUNE_RADIO : MediaBridgeContract.CAP_PREVIOUS));
        next.setEnabled(value.supports(directSavedNavigation
                ? MediaBridgeContract.CAP_TUNE_RADIO : MediaBridgeContract.CAP_NEXT));
        favoritesButton.setVisibility(activeSource.displayId() == MediaSource.Id.RADIO
                ? VISIBLE : GONE);
        favoritesButton.setEnabled(value.supports(MediaBridgeContract.CAP_TUNE_RADIO));
        favoritesButton.setAlpha(favoritesButton.isEnabled() ? 1f : 0.45f);
        updateStatusPillPosition();
        if (activeSource.displayId() != MediaSource.Id.RADIO) hideFavoritesChooser();
        boolean currentlyPlaying = PlayPauseActionPolicy.isCurrentlyPlaying(
                activeSource, value.isPlaying());
        boolean toggle = value.supports(MediaBridgeContract.CAP_TOGGLE)
                || currentlyPlaying && value.supports(MediaBridgeContract.CAP_PAUSE)
                || !currentlyPlaying && value.supports(MediaBridgeContract.CAP_PLAY);
        playPause.setEnabled(toggle);
        playPause.setPlaying(currentlyPlaying);
        progress.setEnabled(value.duration > 0L && value.supports(MediaBridgeContract.CAP_SEEK));
        progress.setAlpha(progress.isEnabled() ? 1f : 0.55f);
        duration.setText(formatTime(value.duration));
        renderSources(value);
        updateContentLayout();
        tick(SystemClock.elapsedRealtime());
    }

    void renderDisconnected(String detail) {
        clearPendingSeek();
        snapshot = null;
        activeSource = MediaSource.Id.UNKNOWN;
        favoritesButton.setVisibility(GONE);
        hideFavoritesChooser();
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
        lastRenderedProgress = 0;
        setElapsed(-1L);
        duration.setText("–:––");
        setArtwork(null);
        renderFallbackSource();
        updateContentLayout();
    }

    void showTransientStatus(String message, boolean error) { setStatusPill(message, error); }

    void onTransportResult(boolean success) {
        if (!success) clearPendingSeek();
        tick(SystemClock.elapsedRealtime());
    }

    void setRadioStations(RadioStationLists lists) {
        favoriteStations = lists == null ? List.of() : lists.favorites;
        Set<String> currentArtworkKeys = new HashSet<>();
        for (RadioStation station : favoriteStations) {
            currentArtworkKeys.add(station.artworkKey());
        }
        radioArtwork.keySet().retainAll(currentArtworkKeys);
        // A failed content-provider read is temporary. A fresh station-list response also
        // refreshes URI grants, so allow one new bounded loader cycle for its current entries.
        failedRadioArtwork.clear();
        favoritesLoading = false;
        favoritesAdapter.notifyDataSetChanged();
        updateFavoritesEmptyState();
    }

    void setRadioStationsError(String message) {
        favoriteStations = List.of();
        radioArtwork.clear();
        failedRadioArtwork.clear();
        favoritesLoading = false;
        favoritesEmpty.setText(message == null || message.isBlank()
                ? "Список станций недоступен" : message);
        favoritesEmpty.setTextColor(Ui.ERROR);
        favoritesEmpty.setVisibility(VISIBLE);
        favoritesGrid.setVisibility(GONE);
    }

    void setRadioArtwork(String key, Bitmap bitmap) {
        boolean belongsToCurrentList = false;
        for (RadioStation station : favoriteStations) {
            if (station.artworkKey().equals(key)) {
                belongsToCurrentList = true;
                break;
            }
        }
        if (!belongsToCurrentList) return;
        if (bitmap == null) failedRadioArtwork.add(key);
        else {
            failedRadioArtwork.remove(key);
            radioArtwork.put(key, bitmap);
        }
        favoritesAdapter.notifyDataSetChanged();
    }

    void setArtwork(Bitmap bitmap) {
        artwork.animate().cancel();
        boolean replacingVisibleArtwork = hasArtwork && bitmap != null;
        hasArtwork = bitmap != null;
        if (bitmap == null) {
            artwork.setImageDrawable(null);
            artwork.setAlpha(0f);
            artworkThumbnail.setImageDrawable(null);
        } else {
            artwork.setImageBitmap(bitmap);
            artworkThumbnail.setImageBitmap(bitmap);
            if (replacingVisibleArtwork) {
                artwork.setAlpha(1f);
            } else {
                artwork.setAlpha(0f);
                artwork.animate().alpha(1f).setDuration(220L).start();
            }
        }
        updateContentLayout();
    }

    void tick(long nowElapsedRealtime) {
        if (snapshot == null || seeking) return;
        if (SeekProjection.isTimedOut(pendingSeekAtElapsedRealtime, nowElapsedRealtime)) {
            clearPendingSeek();
        }
        long value = pendingSeekPosition >= 0L
                ? SeekProjection.estimate(snapshot, pendingSeekPosition,
                        pendingSeekAtElapsedRealtime, nowElapsedRealtime)
                : ProgressEstimator.estimate(snapshot.position, snapshot.duration,
                        snapshot.updateElapsedRealtime, snapshot.speed, snapshot.playbackState,
                        nowElapsedRealtime);
        setElapsed(value);
        if (value >= 0L && snapshot.duration > 0L) {
            setRenderedProgress((int) Math.min(PROGRESS_MAX,
                    value * PROGRESS_MAX / snapshot.duration));
        } else setRenderedProgress(0);
    }

    private void beginPendingSeek(long position) {
        pendingSeekPosition = Math.max(0L, position);
        pendingSeekAtElapsedRealtime = SystemClock.elapsedRealtime();
        pendingSeekSnapshot = snapshot;
        tick(pendingSeekAtElapsedRealtime);
    }

    private void updatePendingSeek(MediaSnapshot candidate) {
        if (pendingSeekPosition < 0L) return;
        if (candidate == null || pendingSeekSnapshot == null
                || !sameMedia(pendingSeekSnapshot, candidate)
                || SeekProjection.isConfirmed(candidate, pendingSeekPosition,
                        pendingSeekAtElapsedRealtime)
                || SeekProjection.isTimedOut(pendingSeekAtElapsedRealtime,
                        SystemClock.elapsedRealtime())) {
            clearPendingSeek();
        }
    }

    private void clearPendingSeek() {
        pendingSeekPosition = -1L;
        pendingSeekAtElapsedRealtime = -1L;
        pendingSeekSnapshot = null;
    }

    private static boolean sameMedia(MediaSnapshot first, MediaSnapshot second) {
        MediaSource.Id firstSource = MediaSource.selectedId(first.audioSource, first.sources);
        MediaSource.Id secondSource = MediaSource.selectedId(second.audioSource, second.sources);
        if (firstSource.displayId() != secondSource.displayId()) return false;
        if (!first.mediaId.isBlank() && !second.mediaId.isBlank()) {
            return first.mediaId.equals(second.mediaId)
                    && first.ownerPackage.equals(second.ownerPackage);
        }
        return first.ownerPackage.equals(second.ownerPackage)
                && first.title.equals(second.title)
                && first.artist.equals(second.artist);
    }

    private void updateContentLayout() {
        boolean compact = style == CardStyle.COMPACT;
        boolean chooserVisible = sourceChooser.getVisibility() == VISIBLE
                || favoritesChooser.getVisibility() == VISIBLE;
        boolean showProgress = hasMedia && snapshot != null && snapshot.duration > 0L;
        boolean showThumbnail = compact && hasMedia && hasArtwork;
        int panelHeight = Math.min(cardHeight, by(appearance.controlPanelHeightDp));
        int controlsTop = Math.max(0, cardHeight - panelHeight);
        int progressTop = Math.max(0,
                controlsTop - by((compact ? 32 : 33) + appearance.progressGapDp));

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
        } else {
            metadataParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            metadataParams.leftMargin = bx(appearance.contentInsetDp);
            metadataParams.rightMargin = bx(appearance.contentInsetDp);
        }
        metadataParams.height = LayoutParams.WRAP_CONTENT;
        int metadataBottom = showProgress
                ? progressTop - d(appearance.metadataProgressGapDp) : controlsTop - d(4);
        metadataParams.gravity = Gravity.BOTTOM | Gravity.START;
        metadataParams.bottomMargin = Math.max(0, cardHeight - metadataBottom);
        metadata.setLayoutParams(metadataParams);
        metadata.setVisibility(chooserVisible ? GONE : VISIBLE);

        LayoutParams progressParams = new LayoutParams(LayoutParams.MATCH_PARENT, d(32));
        progressParams.gravity = Gravity.TOP;
        progressParams.leftMargin = bx(Math.max(8, appearance.contentInsetDp - 4));
        progressParams.rightMargin = bx(Math.max(8, appearance.contentInsetDp - 4));
        progressParams.topMargin = progressTop;
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
        favoritesChooser.setLayoutParams(new LayoutParams(chooserParams));
        updateFavoriteTileDimensions(chooserParams);
    }

    private void updateFavoriteTileDimensions(LayoutParams chooserParams) {
        int gridWidth = cardWidth - chooserParams.leftMargin - chooserParams.rightMargin
                - favoritesChooser.getPaddingLeft() - favoritesChooser.getPaddingRight()
                - favoritesGrid.getPaddingLeft() - favoritesGrid.getPaddingRight();
        int gridHeight = chooserParams.height
                - favoritesChooser.getPaddingTop() - favoritesChooser.getPaddingBottom()
                - favoritesGrid.getPaddingTop() - favoritesGrid.getPaddingBottom();
        int columnWidth = Math.max(1,
                (gridWidth - favoritesGrid.getHorizontalSpacing() * (favoriteColumns - 1))
                        / favoriteColumns);
        int tileHeight = Math.max(1,
                (gridHeight - favoritesGrid.getVerticalSpacing() * (favoriteRows - 1))
                        / favoriteRows);
        if (columnWidth == favoriteColumnWidth && tileHeight == favoriteTileHeight) return;
        favoriteColumnWidth = columnWidth;
        favoriteTileHeight = tileHeight;
        favoritesAdapter.notifyDataSetChanged();
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
        return MediaSource.selectedId(value.audioSource, value.sources);
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
        LinearLayout topRow = sourceRow();
        LinearLayout bottomRow = sourceRow();
        sourceOptions.addView(topRow, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));
        sourceOptions.addView(bottomRow, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));
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
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LayoutParams.MATCH_PARENT, 1f);
            params.setMargins(d(5), d(5), d(5), d(5));
            (index < 2 ? topRow : bottomRow).addView(option, params);
        }
    }

    private LinearLayout sourceRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private void toggleSourceChooser() {
        if (sourceOptions.getChildCount() == 0) return;
        if (sourceChooser.getVisibility() == VISIBLE) hideSourceChooser();
        else {
            hideFavoritesChooser();
            sourceChooser.setVisibility(VISIBLE);
            sourceChooser.bringToFront();
            sourcePill.bringToFront();
            scheduleChooserAutoHide();
            updateContentLayout();
        }
    }

    private void hideSourceChooser() {
        sourceChooser.setVisibility(GONE);
        cancelChooserAutoHideIfClosed();
        updateContentLayout();
    }

    private void toggleFavoritesChooser() {
        if (favoritesChooser.getVisibility() == VISIBLE) {
            hideFavoritesChooser();
            return;
        }
        hideSourceChooser();
        favoritesLoading = true;
        updateFavoritesEmptyState();
        favoritesChooser.setVisibility(VISIBLE);
        favoritesChooser.bringToFront();
        favoritesButton.bringToFront();
        listener.onRadioStationsRequested();
        scheduleChooserAutoHide();
        updateContentLayout();
    }

    private void hideFavoritesChooser() {
        if (favoritesChooser.getVisibility() == GONE) return;
        favoritesChooser.setVisibility(GONE);
        cancelChooserAutoHideIfClosed();
        updateContentLayout();
    }

    private void scheduleChooserAutoHide() {
        removeCallbacks(chooserAutoHide);
        postDelayed(chooserAutoHide, CHOOSER_AUTO_HIDE_MS);
    }

    private void cancelChooserAutoHideIfClosed() {
        if (sourceChooser.getVisibility() != VISIBLE
                && favoritesChooser.getVisibility() != VISIBLE) {
            removeCallbacks(chooserAutoHide);
        }
    }

    private void hideOpenChooser() {
        if (sourceChooser.getVisibility() == VISIBLE) {
            hideSourceChooser();
        } else if (favoritesChooser.getVisibility() == VISIBLE) {
            hideFavoritesChooser();
        }
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(chooserAutoHide);
        super.onDetachedFromWindow();
    }

    private void updateFavoritesEmptyState() {
        if (favoritesLoading && favoriteStations.isEmpty()) {
            favoritesEmpty.setText("Загрузка…");
            favoritesEmpty.setTextColor(Ui.SECONDARY);
            favoritesEmpty.setVisibility(VISIBLE);
            favoritesGrid.setVisibility(GONE);
        } else if (favoriteStations.isEmpty()) {
            favoritesEmpty.setText("Нет лайкнутых станций");
            favoritesEmpty.setTextColor(Ui.SECONDARY);
            favoritesEmpty.setVisibility(VISIBLE);
            favoritesGrid.setVisibility(GONE);
        } else {
            favoritesEmpty.setVisibility(GONE);
            favoritesGrid.setVisibility(VISIBLE);
        }
    }

    private final class FavoriteStationAdapter extends BaseAdapter {
        @Override public int getCount() { return favoriteStations.size(); }
        @Override public RadioStation getItem(int position) { return favoriteStations.get(position); }
        @Override public long getItemId(int position) { return getItem(position).id.hashCode(); }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            StationTile tile;
            if (convertView == null) {
                boolean horizontal = style == CardStyle.COMPACT
                        || favoriteTileHeight < d(72);
                LinearLayout container = new LinearLayout(getContext());
                container.setOrientation(horizontal
                        ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
                container.setGravity(horizontal
                        ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
                container.setBackground(pillBackground(getContext(), 0xD1262A30,
                        0x554F5E68, d(14)));
                ImageView cover = new ImageView(getContext());
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setBackground(Ui.background(Ui.NESTED, 14 * uiScale, getContext()));
                cover.setClipToOutline(true);
                int logoSize = d(horizontal ? 72 : 128);
                container.addView(cover, new LinearLayout.LayoutParams(
                        logoSize, logoSize));
                LinearLayout labels = new LinearLayout(getContext());
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setGravity(horizontal
                        ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
                TextView name = text("", style == CardStyle.COMPACT ? 15 : 18,
                        Ui.PRIMARY, Typeface.BOLD);
                name.setGravity(horizontal ? Gravity.START : Gravity.CENTER);
                name.setMaxLines(1);
                name.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameParams = fullWrap();
                labels.addView(name, nameParams);
                LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                        horizontal ? 0 : LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT, horizontal ? 1f : 0f);
                container.addView(labels, labelsParams);
                tile = new StationTile(container, cover, name, labels, horizontal);
                container.setTag(tile);
                convertView = container;
            } else {
                tile = (StationTile) convertView.getTag();
            }
            RadioStation station = getItem(position);
            String detail = station.displayDetail();
            String visibleName = station.name.isBlank() ? detail : station.name;
            tile.name.setText(visibleName);
            int tileHeight = Math.max(1, favoriteTileHeight);
            int requestedPadding = d(tile.horizontal ? 6 : 8);
            int tilePadding = Math.min(requestedPadding, Math.max(1, tileHeight / 10));
            tile.container.setPadding(tilePadding, tilePadding, tilePadding, tilePadding);
            float baseTextPx = (style == CardStyle.COMPACT ? 15f : 18f) * uiScale
                    * getResources().getDisplayMetrics().density
                    * getResources().getConfiguration().fontScale;
            float heightFraction = tile.horizontal ? 0.42f : 0.22f;
            tile.name.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    Math.max(1f, Math.min(baseTextPx, tileHeight * heightFraction)));
            int labelGap = tile.horizontal
                    ? Math.min(d(8), Math.max(1, favoriteColumnWidth / 30))
                    : Math.min(d(8), Math.max(0,
                            (tileHeight - tile.name.getLineHeight()) / 10));
            LinearLayout.LayoutParams nameParams =
                    (LinearLayout.LayoutParams) tile.name.getLayoutParams();
            nameParams.topMargin = tile.horizontal ? 0 : labelGap;
            tile.name.setLayoutParams(nameParams);
            LinearLayout.LayoutParams labelsParams =
                    (LinearLayout.LayoutParams) tile.labels.getLayoutParams();
            labelsParams.leftMargin = tile.horizontal ? labelGap : 0;
            tile.labels.setLayoutParams(labelsParams);
            int artworkSize;
            if (tile.horizontal) {
                int widthLimit = Math.round(favoriteColumnWidth * 0.42f);
                artworkSize = Math.min(tileHeight - tilePadding * 2, widthLimit);
            } else {
                int widthLimit = favoriteColumnWidth - tilePadding * 2;
                int heightLimit = tileHeight - tilePadding * 2
                        - tile.name.getLineHeight() - labelGap;
                artworkSize = Math.min(widthLimit, heightLimit);
            }
            artworkSize = Math.max(1, artworkSize);
            tile.cover.setLayoutParams(new LinearLayout.LayoutParams(
                    artworkSize, artworkSize));
            convertView.setLayoutParams(new AbsListView.LayoutParams(
                    LayoutParams.MATCH_PARENT, tileHeight));
            Bitmap bitmap = radioArtwork.get(station.artworkKey());
            if (bitmap != null) {
                tile.cover.setImageBitmap(bitmap);
                tile.cover.setImageTintList(null);
                tile.cover.setPadding(0, 0, 0, 0);
                tile.cover.setAlpha(1f);
            } else {
                tile.cover.setImageResource(R.drawable.ic_sound_wave);
                tile.cover.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
                int placeholderPadding = Math.min(d(12), artworkSize / 4);
                tile.cover.setPadding(placeholderPadding, placeholderPadding,
                        placeholderPadding, placeholderPadding);
                tile.cover.setAlpha(0.45f);
                if (!station.artworkUri.isBlank()
                        && !failedRadioArtwork.contains(station.artworkKey())) {
                    listener.onRadioArtworkRequested(station);
                }
            }
            convertView.setContentDescription(visibleName.equals(detail)
                    ? visibleName : visibleName + ", " + detail);
            return convertView;
        }
    }

    private record StationTile(LinearLayout container, ImageView cover, TextView name,
            LinearLayout labels, boolean horizontal) {}

    private void setElapsed(long milliseconds) {
        long second = milliseconds < 0L ? -1L : milliseconds / 1000L;
        if (second == lastElapsedSecond) return;
        lastElapsedSecond = second;
        elapsed.setText(formatTime(milliseconds));
    }

    private void setRenderedProgress(int value) {
        if (value == lastRenderedProgress) return;
        lastRenderedProgress = value;
        progress.setProgress(value);
    }

    private void setStatusPill(String message, boolean error) {
        statusPill.setText((error ? "△  " : "") + message);
        statusPill.setTextColor(error ? Ui.ERROR : Ui.ACCENT);
        statusPill.setBackground(pillBackground(getContext(),
                error ? 0x1FD98282 : 0x1F7893A0,
                error ? 0x88D98282 : 0x887893A0, d(16)));
        statusPill.setVisibility(VISIBLE);
        updateStatusPillPosition();
    }

    private void updateStatusPillPosition() {
        LayoutParams params = (LayoutParams) statusPill.getLayoutParams();
        if (favoritesButton != null && favoritesButton.getVisibility() == VISIBLE) {
            LayoutParams favoritesParams = (LayoutParams) favoritesButton.getLayoutParams();
            int fallbackWidth = d(Math.max(128, appearance.topRowTextSizeSp * 8 + 55));
            params.rightMargin = favoritesParams.rightMargin
                    + Math.max(favoritesButton.getWidth(), fallbackWidth) + d(8);
        } else {
            params.rightMargin = bx(dragHandleVisible ? 49 : Math.max(8,
                    appearance.contentInsetDp - (style == CardStyle.COMPACT ? 4 : 8)));
        }
        statusPill.setLayoutParams(params);
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
