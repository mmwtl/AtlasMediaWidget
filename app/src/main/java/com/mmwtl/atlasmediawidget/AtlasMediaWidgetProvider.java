package com.mmwtl.atlasmediawidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;
import android.widget.RemoteViews;

/** Lifecycle entry point only; the foreground service owns the bridge and live state. */
public final class AtlasMediaWidgetProvider extends AppWidgetProvider {
    static final String EXTRA_CONTROL = "widget_control";
    static final String EXTRA_TARGET_BOUNDS = "widget_target_bounds";
    static final String EXTRA_PROGRESS_BOUNDS = "widget_progress_bounds";
    static final String EXTRA_SEEK_ZONE = "widget_seek_zone";
    static final String EXTRA_CUSTOM_ACTION = "widget_custom_action";
    static final String CUSTOM_ACTION_PREFIX = "custom_action:";
    static final String SEEK_ZONE_PREFIX = "seek_";
    static final String ACTION_COMMAND = "com.mmwtl.atlasmediawidget.WIDGET_COMMAND";
    static final String[] ACTIONS = {"open", "sources", "favorites", "seek",
            "PREVIOUS", "PLAY_PAUSE", "NEXT"};

    static int[] ids(Context context) {
        return AppWidgetManager.getInstance(context.getApplicationContext()).getAppWidgetIds(
                new ComponentName(context, AtlasMediaWidgetProvider.class));
    }

    static boolean owns(Context context, int id) {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return false;
        var info = AppWidgetManager.getInstance(context.getApplicationContext()).getAppWidgetInfo(id);
        return info != null && new ComponentName(context, AtlasMediaWidgetProvider.class)
                .equals(info.provider);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        refresh(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int id, Bundle options) {
        refresh(context);
    }

    @Override public void onRestored(Context context, int[] oldIds, int[] newIds) {
        refresh(context);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        new Prefs(context).setWidgetsConfigured(ids, false);
        refresh(context);
    }
    @Override public void onDisabled(Context context) { refresh(context); }

    static void refresh(Context context) {
        Prefs prefs = new Prefs(context);
        if (!prefs.isWidgetMode()) showInactive(context, "Неактивен: выбран режим «Оверлей»\nНажмите для выбора режима");
        if (prefs.isWidgetMode() && ids(context).length > 0 || OverlayService.isRunning()) {
            try {
                context.startForegroundService(new Intent(context, OverlayService.class)
                        .setAction(OverlayService.ACTION_WIDGET_REFRESH));
            } catch (RuntimeException error) {
                AppLog.warn("Cannot resume widget media service", error);
                showInactive(context, "Нет соединения\nОткройте приложение для запуска");
            }
        }
    }

    static void showInactive(Context context, String text) {
        for (int id : ids(context)) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.media_widget_empty);
            views.setTextViewText(R.id.widget_empty, text);
            views.setOnClickPendingIntent(R.id.widget_empty, click(context, id, "settings", false));
            AppWidgetManager.getInstance(context.getApplicationContext()).updateAppWidget(id, views);
        }
    }

    static int widthDp(Context context, Bundle options) {
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        return Math.max(1, options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 400));
    }

    static int heightDp(Context context, Bundle options) {
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        return Math.max(1, options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300));
    }

    /** The progress strip shows whole seconds; paused or position-less media never changes it. */
    static long progressSecond(MediaSnapshot snapshot, long nowElapsedRealtime) {
        if (snapshot == null) return -1L;
        long position = ProgressEstimator.estimate(snapshot.position, snapshot.duration,
                snapshot.updateElapsedRealtime, snapshot.speed, snapshot.playbackState,
                nowElapsedRealtime);
        return position < 0L ? -1L : position / 1_000L;
    }

    /** Maps a "seek_<zone>_<count>" control to the centre of that zone, or -1 when invalid. */
    static long seekZonePosition(String control, long durationMs) {
        if (control == null || !control.startsWith(SEEK_ZONE_PREFIX) || durationMs <= 0L) return -1L;
        String[] parts = control.substring(SEEK_ZONE_PREFIX.length()).split("_");
        if (parts.length != 2) return -1L;
        try {
            int zone = Integer.parseInt(parts[0]);
            int count = Integer.parseInt(parts[1]);
            if (count <= 0 || zone < 0 || zone >= count) return -1L;
            return durationMs * (2L * zone + 1L) / (2L * count);
        } catch (NumberFormatException error) {
            return -1L;
        }
    }

    /**
     * Opens the progress scrubber. RemoteViews supplies the tapped view's screen bounds only as a
     * fill-in, which an immutable PendingIntent would drop; the explicit component and preset
     * action, data and extras cannot be replaced by the host.
     */
    static PendingIntent scrubClick(Context context, int id, String part, Rect target, Rect progress) {
        Intent intent = new Intent(context, WidgetControlActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("atlasmediawidget://" + id + "/scrub/" + part))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_CONTROL, "scrub")
                .putExtra(EXTRA_TARGET_BOUNDS, target)
                .putExtra(EXTRA_PROGRESS_BOUNDS, progress)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (part.startsWith(SEEK_ZONE_PREFIX)) intent.putExtra(EXTRA_SEEK_ZONE, part);
        return PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    /** Collection items can only add their station through a fill-in, so the template is mutable. */
    static PendingIntent favoriteTemplate(Context context, int id) {
        Intent intent = new Intent(context, OverlayService.class)
                .setAction(ACTION_COMMAND)
                .setData(Uri.parse("atlasmediawidget://" + id + "/favorite"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_CONTROL, "favorite");
        return PendingIntent.getForegroundService(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    /** The player's action id is part of the data URI, so each published action keeps its own intent. */
    static PendingIntent customActionClick(Context context, int id, String customAction) {
        Intent intent = new Intent(context, OverlayService.class)
                .setAction(ACTION_COMMAND)
                .setData(new Uri.Builder().scheme("atlasmediawidget").authority(String.valueOf(id))
                        .appendPath("custom").appendPath(customAction).build())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_CONTROL, "custom")
                .putExtra(EXTRA_CUSTOM_ACTION, customAction);
        return PendingIntent.getForegroundService(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent click(Context context, int id, String action, boolean command) {
        Intent intent = new Intent(context, command ? OverlayService.class
                : "settings".equals(action) ? MainActivity.class : WidgetControlActivity.class)
                .setAction(command ? ACTION_COMMAND : Intent.ACTION_VIEW)
                .setData(Uri.parse("atlasmediawidget://" + id + "/" + action))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_CONTROL, action);
        if (!command && !"settings".equals(action)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return command ? PendingIntent.getForegroundService(context, id, intent, flags)
                : PendingIntent.getActivity(context, id, intent, flags);
    }

    static final class Frame {
        final MediaCardView card;
        final Rect progressBounds;
        /** The card picture without the progress row, drawn at {@link #cardScale}. */
        final Bitmap cardBitmap;
        final float cardScale;
        final int width;
        final int height;
        final RemoteViews views;
        final boolean favoritesGridShown;

        /** Favorites state held by OverlayService; the frame never requests stations itself. */
        record Favorites(RadioStationLists lists, boolean loading, String error) {}

        Frame(Context context, Prefs prefs, Bundle options, int id, MediaSnapshot snapshot,
                Bitmap artwork, boolean connected, MediaCardView.Listener listener) {
            this(context, prefs, options, id, snapshot, artwork, connected, null, null, listener);
        }

        Frame(Context context, Prefs prefs, Bundle options, int id, MediaSnapshot snapshot,
                Bitmap artwork, boolean connected, boolean showSources, MediaCardView.Listener listener) {
            this(context, prefs, options, id, snapshot, artwork, connected,
                    showSources ? "sources" : null, null, listener);
        }

        Frame(Context context, Prefs prefs, Bundle options, int id, MediaSnapshot snapshot,
                Bitmap artwork, boolean connected, String chooser, Favorites favorites,
                MediaCardView.Listener listener) {
            width = Math.min(4096, Ui.dp(context, widthDp(context, options)));
            height = Math.min(4096, Ui.dp(context, heightDp(context, options)));
            CardStyle style = CardStyle.fromPreference(prefs.getInt(Prefs.KEY_CARD_STYLE,
                    CardStyle.DEFAULT.preferenceValue));
            card = new MediaCardView(context, width, height, width, height, style,
                    prefs.appearance(style), prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false),
                    false, prefs.radioFavoritesColumns(), prefs.radioFavoritesRows(), listener);
            card.setCustomActionLimit(prefs.playerActionsCount());
            if (snapshot == null || !connected) card.renderWidgetUnavailable(connected);
            else card.renderSnapshot(snapshot, true);
            boolean showSources = "sources".equals(chooser) && snapshot != null && connected;
            boolean showFavorites = "favorites".equals(chooser) && snapshot != null && connected
                    && favorites != null;
            if (showSources) card.openWidgetChooser("sources");
            if (showFavorites) card.openWidgetFavorites(favorites.lists(), favorites.loading(), favorites.error());
            card.prepareWidgetArtwork(connected ? artwork : null);
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            card.layout(0, 0, width, height);
            View progress = card.widgetTarget("seek");
            progressBounds = !showSources && !showFavorites && progress.getVisibility() == View.VISIBLE
                    ? card.widgetBounds(progress) : new Rect();
            GridView grid = (GridView) card.widgetFavoritesGrid();
            favoritesGridShown = showFavorites && grid.getVisibility() == View.VISIBLE;
            views = new RemoteViews(context.getPackageName(), R.layout.media_widget);
            int visibility = progress.getVisibility();
            progress.setVisibility(View.INVISIBLE);
            // The launcher draws and scrolls the tiles; the card supplies only the panel behind them.
            if (favoritesGridShown) grid.setVisibility(View.INVISIBLE);
            float scale = Math.min(1f, 800f / Math.max(width, height));
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(scale, scale);
            Path clip = new Path();
            float radius = Ui.dp(context, 26) * Math.max(.72f, Math.min(1.75f,
                    Math.min(width / (float) Ui.dp(context, style.defaultWidthDp),
                            height / (float) Ui.dp(context, style.defaultHeightDp))));
            clip.addRoundRect(new RectF(0, 0, width, height), radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            card.draw(canvas);
            progress.setVisibility(visibility);
            if (favoritesGridShown) grid.setVisibility(View.VISIBLE);
            cardBitmap = bitmap;
            cardScale = scale;
            views.setImageViewBitmap(R.id.widget_card, bitmap);
            views.setContentDescription(R.id.widget_card, !connected ? "Нет соединения" : snapshot == null ? "Нет данных"
                    : snapshot.title + ", " + snapshot.artist + (snapshot.isPlaying() ? ", воспроизведение" : ", пауза"));
            views.setOnClickPendingIntent(R.id.widget_card, click(context, id, "open", false));
            setProgress(views);
            views.removeAllViews(R.id.widget_targets);
            setFavoritesGrid(context, id, grid);
            if (showFavorites) {
                addTarget(context, id, "dismiss_favorites", "Закрыть избранное",
                        new Rect(0, 0, width, height), true);
                addTarget(context, id, "favorites", "Закрыть избранное",
                        card.widgetBounds(card.widgetTarget("favorites")), true);
                return;
            }
            if (showSources) {
                addTarget(context, id, "dismiss_sources", "Закрыть выбор источника",
                        new Rect(0, 0, width, height), true);
                for (MediaSource.Id source : new MediaSource.Id[]{MediaSource.Id.RADIO,
                        MediaSource.Id.BT, MediaSource.Id.USB, MediaSource.Id.ONLINE,
                        MediaSource.Id.CPAA}) {
                    View option = card.widgetSourceOption(source);
                    if (option != null && option.isEnabled()) {
                        addTarget(context, id, "source_" + source.name(), source.label(),
                                card.widgetBounds(option), true);
                    }
                }
                addTarget(context, id, "sources", "Закрыть выбор источника",
                        card.widgetBounds(card.widgetTarget("sources")), true);
                return;
            }
            for (String action : ACTIONS) {
                View target = card.widgetTarget(action);
                if (target.getVisibility() != View.VISIBLE || !target.isEnabled()) continue;
                if ("seek".equals(action)) {
                    if (snapshot != null && connected
                            && snapshot.supports(MediaBridgeContract.CAP_SEEK)) {
                        addSeekTargets(context, id);
                    }
                    continue;
                }
                boolean command = Character.isUpperCase(action.charAt(0));
                if (command && (!connected || snapshot == null)) continue;
                Rect rect = card.widgetBounds(target);
                if (command) {
                    int minimum = Ui.dp(context, 48);
                    rect.inset(-Math.max(0, (minimum - rect.width()) / 2),
                            -Math.max(0, (minimum - rect.height()) / 2));
                    if (!rect.intersect(0, 0, width, height)) continue;
                }
                addTarget(context, id, action, switch (action) {
                    case "PREVIOUS" -> "Предыдущий";
                    case "PLAY_PAUSE" -> snapshot != null && snapshot.isPlaying() ? "Пауза" : "Воспроизвести";
                    case "NEXT" -> "Следующий";
                    case "sources" -> "Выбрать источник";
                    case "favorites" -> "Избранные станции";
                    default -> "Открыть источник";
                }, rect, command || "sources".equals(action) || "favorites".equals(action));
            }
            if (!connected || snapshot == null) return;
            for (int slot = 0; slot < MediaCardView.MAX_CUSTOM_ACTIONS; slot++) {
                MediaCustomAction customAction = card.widgetCustomAction(slot);
                if (customAction == null) break;
                Rect rect = card.widgetBounds(card.widgetTarget("custom_" + slot));
                // Slots sit edge to edge in one pill, so only the height grows to a touch target.
                rect.inset(0, -Math.max(0, (Ui.dp(context, 48) - rect.height()) / 2));
                if (!rect.intersect(0, 0, width, height)) continue;
                addTarget(context, id, customAction.label(), rect,
                        customActionClick(context, id, customAction.action));
            }
        }

        /**
         * RemoteViews reports only completed taps, never a touch position or a drag, so the track
         * is split into tap zones. A zone seeks to its centre and opens the live scrubber over the
         * same strip for fine dragging; the time labels open the scrubber without seeking.
         */
        private void addSeekTargets(Context context, int id) {
            View bar = card.widgetTarget("seek_bar");
            Rect barBounds = card.widgetBounds(bar);
            int left = barBounds.left + bar.getPaddingLeft();
            int right = barBounds.right - bar.getPaddingRight();
            if (right <= left) return;
            int minimumHeight = Ui.dp(context, 48);
            int top = Math.max(0, barBounds.centerY() - minimumHeight / 2);
            int bottom = Math.min(height, Math.max(barBounds.bottom, top + minimumHeight));
            int count = Math.max(8, Math.min(32, (right - left) / Math.max(1, Ui.dp(context, 16))));
            for (int zone = 0; zone < count; zone++) {
                Rect rect = new Rect(zone == 0 ? barBounds.left : left + (right - left) * zone / count,
                        top, zone == count - 1 ? barBounds.right
                                : left + (right - left) * (zone + 1) / count, bottom);
                addTarget(context, id, "Перемотать на " + (200 * zone + 100) / (2 * count) + "%", rect,
                        scrubClick(context, id, SEEK_ZONE_PREFIX + zone + "_" + count,
                                new Rect(rect), new Rect(progressBounds)));
            }
            for (String part : new String[]{"elapsed", "duration"}) {
                Rect rect = card.widgetBounds(card.widgetTarget(part));
                rect.top = Math.min(rect.top, top);
                rect.bottom = Math.max(rect.bottom, bottom);
                addTarget(context, id, "Точная перемотка", rect,
                        scrubClick(context, id, part, new Rect(rect), new Rect(progressBounds)));
            }
        }

        @SuppressWarnings("deprecation") // RemoteCollectionItems needs API 31.
        private void setFavoritesGrid(Context context, int id, GridView grid) {
            views.removeAllViews(R.id.widget_favorites_box);
            views.setViewVisibility(R.id.widget_favorites_box,
                    favoritesGridShown ? View.VISIBLE : View.GONE);
            if (!favoritesGridShown) return;
            // Tiles carry half of each gap on every side, so the collection grid has no spacing.
            Rect bounds = card.widgetBounds(grid);
            int gapX = grid.getHorizontalSpacing() / 2;
            int gapY = grid.getVerticalSpacing() / 2;
            views.setViewPadding(R.id.widget_favorites_box,
                    Math.max(0, bounds.left + grid.getPaddingLeft() - gapX),
                    Math.max(0, bounds.top + grid.getPaddingTop() - gapY),
                    Math.max(0, width - bounds.right + grid.getPaddingRight() - gapX),
                    Math.max(0, height - bounds.bottom + grid.getPaddingBottom() - gapY));
            RemoteViews collection = new RemoteViews(context.getPackageName(),
                    switch (card.favoriteColumns()) {
                        case 3 -> R.layout.media_widget_favorites_3;
                        case 4 -> R.layout.media_widget_favorites_4;
                        default -> R.layout.media_widget_favorites_2;
                    });
            views.addView(R.id.widget_favorites_box, collection);
            // Android 11 accepts a collection adapter only from the root RemoteViews, whose parent
            // is the AppWidgetHostView; on the nested views the launcher drops it.
            views.setRemoteAdapter(R.id.widget_favorites, new Intent(context, WidgetFavoritesService.class)
                    .setData(Uri.parse("atlasmediawidget://" + id + "/favorites_grid"))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            views.setPendingIntentTemplate(R.id.widget_favorites, favoriteTemplate(context, id));
        }

        private void addTarget(Context context, int id, String action, String description,
                Rect rect, boolean command) {
            addTarget(context, id, description, rect, click(context, id, action, command));
        }

        private void addTarget(Context context, int id, String description, Rect rect,
                PendingIntent click) {
            RemoteViews hit = new RemoteViews(context.getPackageName(), R.layout.media_widget_target);
            hit.setViewPadding(R.id.widget_target_box, Math.max(0, rect.left), Math.max(0, rect.top),
                    Math.max(0, width - rect.right), Math.max(0, height - rect.bottom));
            hit.setContentDescription(R.id.widget_target, description);
            hit.setOnClickPendingIntent(R.id.widget_target, click);
            views.addView(R.id.widget_targets, hit);
        }

        RemoteViews progress(Context context, MediaSnapshot snapshot) {
            card.renderSnapshot(snapshot, true);
            RemoteViews update = new RemoteViews(context.getPackageName(), R.layout.media_widget);
            setProgress(update);
            return update;
        }

        private void setProgress(RemoteViews target) {
            target.setViewVisibility(R.id.widget_progress_box,
                    progressBounds.isEmpty() ? View.GONE : View.VISIBLE);
            if (progressBounds.isEmpty()) return;
            target.setViewPadding(R.id.widget_progress_box, progressBounds.left, progressBounds.top,
                    width - progressBounds.right, height - progressBounds.bottom);
            float scale = Math.min(1f, 800f / progressBounds.width());
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(progressBounds.width() * scale)),
                    Math.max(1, Math.round(progressBounds.height() * scale)), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(scale, scale);
            card.widgetTarget("seek").draw(canvas);
            target.setImageViewBitmap(R.id.widget_progress, bitmap);
        }
    }
}
