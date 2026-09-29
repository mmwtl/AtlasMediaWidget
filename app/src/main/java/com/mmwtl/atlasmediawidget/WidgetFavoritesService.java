package com.mmwtl.atlasmediawidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Supplies the favorites tiles of an open widget grid, which the launcher scrolls itself.
 * Tiles are drawn from the widget frame that OverlayService owns on the main thread.
 */
public final class WidgetFavoritesService extends RemoteViewsService {
    static final String EXTRA_STATION_ID = "widget_station_id";

    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(this, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID));
    }

    static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private final int widgetId;
        private volatile List<RadioStation> stations = List.of();

        Factory(Context context, int widgetId) {
            this.context = context;
            this.widgetId = widgetId;
        }

        @Override public void onCreate() {}
        @Override public void onDestroy() {}

        @Override public void onDataSetChanged() {
            List<RadioStation> current = onMain(() -> {
                OverlayService service = OverlayService.current();
                return service == null ? List.of() : service.widgetFavoriteStations(widgetId);
            });
            stations = current == null ? List.of() : current;
        }

        @Override public int getCount() { return stations.size(); }

        @Override public RemoteViews getViewAt(int position) {
            List<RadioStation> current = stations;
            if (position < 0 || position >= current.size()) return null;
            RadioStation station = current.get(position);
            Bitmap tile = onMain(() -> {
                OverlayService service = OverlayService.current();
                return service == null ? null : service.widgetFavoriteTile(widgetId, station);
            });
            if (tile == null) return null;
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.media_widget_favorite);
            views.setImageViewBitmap(R.id.widget_favorite, tile);
            String detail = station.displayDetail();
            String name = station.name.isBlank() ? detail : station.name;
            views.setContentDescription(R.id.widget_favorite,
                    name.equals(detail) ? name : name + ", " + detail);
            views.setOnClickFillInIntent(R.id.widget_favorite,
                    new Intent().putExtra(EXTRA_STATION_ID, station.id));
            return views;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) {
            List<RadioStation> current = stations;
            return position < current.size() ? current.get(position).id.hashCode() : position;
        }
        @Override public boolean hasStableIds() { return true; }

        private static <T> T onMain(Supplier<T> work) {
            if (Looper.myLooper() == Looper.getMainLooper()) return work.get();
            FutureTask<T> task = new FutureTask<>(work::get);
            new Handler(Looper.getMainLooper()).post(task);
            try {
                return task.get(2, TimeUnit.SECONDS);
            } catch (Exception error) {
                task.cancel(false);
                AppLog.warn("Cannot read widget favorites", error);
                return null;
            }
        }
    }
}
