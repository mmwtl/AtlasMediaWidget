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
import android.widget.RemoteViews;

/** Lifecycle entry point only; the foreground service owns the bridge and live state. */
public final class AtlasMediaWidgetProvider extends AppWidgetProvider {
    static final String EXTRA_CONTROL = "widget_control";
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

    @Override public void onDeleted(Context context, int[] ids) { refresh(context); }
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

    static PendingIntent click(Context context, int id, String action, boolean command) {
        Intent intent = new Intent(context, command ? OverlayService.class : MainActivity.class)
                .setAction(command ? ACTION_COMMAND : Intent.ACTION_VIEW)
                .setData(Uri.parse("atlasmediawidget://" + id + "/" + action))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_CONTROL, action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return command ? PendingIntent.getForegroundService(context, id, intent, flags)
                : PendingIntent.getActivity(context, id, intent, flags);
    }

    static final class Frame {
        final MediaCardView card;
        final Rect progressBounds;
        final int width;
        final int height;
        final RemoteViews views;

        Frame(Context context, Prefs prefs, Bundle options, int id, MediaSnapshot snapshot,
                Bitmap artwork, boolean connected, MediaCardView.Listener listener) {
            width = Math.min(4096, Ui.dp(context, widthDp(context, options)));
            height = Math.min(4096, Ui.dp(context, heightDp(context, options)));
            CardStyle style = CardStyle.fromPreference(prefs.getInt(Prefs.KEY_CARD_STYLE,
                    CardStyle.DEFAULT.preferenceValue));
            card = new MediaCardView(context, width, height, width, height, style,
                    prefs.appearance(style), prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false),
                    false, prefs.radioFavoritesColumns(), prefs.radioFavoritesRows(), listener);
            if (snapshot == null || !connected) card.renderWidgetUnavailable(connected);
            else card.renderSnapshot(snapshot, true);
            card.prepareWidgetArtwork(connected ? artwork : null);
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            card.layout(0, 0, width, height);
            View progress = card.widgetTarget("seek");
            progressBounds = progress.getVisibility() == View.VISIBLE
                    ? card.widgetBounds(progress) : new Rect();
            views = new RemoteViews(context.getPackageName(), R.layout.media_widget);
            int visibility = progress.getVisibility();
            progress.setVisibility(View.INVISIBLE);
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
            views.setImageViewBitmap(R.id.widget_card, bitmap);
            views.setContentDescription(R.id.widget_card, !connected ? "Нет соединения" : snapshot == null ? "Нет данных"
                    : snapshot.title + ", " + snapshot.artist + (snapshot.isPlaying() ? ", воспроизведение" : ", пауза"));
            views.setOnClickPendingIntent(R.id.widget_card, click(context, id, "open", false));
            setProgress(views);
            views.removeAllViews(R.id.widget_targets);
            for (String action : ACTIONS) {
                View target = card.widgetTarget(action);
                if (target.getVisibility() != View.VISIBLE || !target.isEnabled()) continue;
                if ("seek".equals(action) && (snapshot == null
                        || !snapshot.supports(MediaBridgeContract.CAP_SEEK))) continue;
                boolean command = Character.isUpperCase(action.charAt(0));
                if (command && (!connected || snapshot == null)) continue;
                Rect rect = card.widgetBounds(target);
                if (command) {
                    int minimum = Ui.dp(context, 48);
                    rect.inset(-Math.max(0, (minimum - rect.width()) / 2),
                            -Math.max(0, (minimum - rect.height()) / 2));
                    if (!rect.intersect(0, 0, width, height)) continue;
                }
                RemoteViews hit = new RemoteViews(context.getPackageName(), R.layout.media_widget_target);
                hit.setViewPadding(R.id.widget_target_box, Math.max(0, rect.left), Math.max(0, rect.top),
                        Math.max(0, width - rect.right), Math.max(0, height - rect.bottom));
                hit.setContentDescription(R.id.widget_target, switch (action) {
                    case "PREVIOUS" -> "Предыдущий";
                    case "PLAY_PAUSE" -> snapshot != null && snapshot.isPlaying() ? "Пауза" : "Воспроизвести";
                    case "NEXT" -> "Следующий";
                    case "sources" -> "Выбрать источник";
                    case "favorites" -> "Избранные станции";
                    case "seek" -> "Открыть перемотку";
                    default -> "Открыть источник";
                });
                hit.setOnClickPendingIntent(R.id.widget_target, click(context, id, action, command));
                views.addView(R.id.widget_targets, hit);
            }
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
