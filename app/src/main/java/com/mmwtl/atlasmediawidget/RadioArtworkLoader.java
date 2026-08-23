package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RadioArtworkLoader {
    private static final int MAX_THUMBNAIL_DIMENSION_PX = 256;
    private static final int CACHE_ENTRIES = 32;

    interface Listener {
        void onRadioArtwork(String key, Bitmap bitmap);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> cache = new LruCache<>(CACHE_ENTRIES);
    private final Set<String> inFlight = new HashSet<>();
    private long generation;

    RadioArtworkLoader(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void load(RadioStation station) {
        if (station == null || station.artworkUri.isBlank()) return;
        String key = station.artworkKey();
        Bitmap cached = cache.get(key);
        if (cached != null) {
            main.post(() -> listener.onRadioArtwork(key, cached));
            return;
        }
        long requestGeneration;
        synchronized (inFlight) {
            if (!inFlight.add(key)) return;
            requestGeneration = generation;
        }
        try {
            executor.execute(() -> {
                Bitmap bitmap = decode(station.artworkUri);
                main.post(() -> {
                    synchronized (inFlight) {
                        inFlight.remove(key);
                        if (requestGeneration != generation) return;
                    }
                    if (bitmap != null) cache.put(key, bitmap);
                    listener.onRadioArtwork(key, bitmap);
                });
            });
        } catch (RuntimeException error) {
            synchronized (inFlight) {
                inFlight.remove(key);
            }
            AppLog.warn("Cannot schedule radio artwork decode", error);
        }
    }

    void clear() {
        synchronized (inFlight) {
            generation++;
            inFlight.clear();
        }
        cache.evictAll();
    }

    void shutdown() {
        clear();
        executor.shutdownNow();
    }

    private Bitmap decode(String uriValue) {
        try {
            Uri uri = Uri.parse(uriValue);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input != null) BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                return input == null ? null : BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception | OutOfMemoryError error) {
            AppLog.warn("Cannot decode radio station artwork", error);
            return null;
        }
    }

    static int sampleSize(int width, int height) {
        if (width <= 0 || height <= 0) return 1;
        int sample = 1;
        while ((width + (long) sample - 1L) / sample > MAX_THUMBNAIL_DIMENSION_PX
                || (height + (long) sample - 1L) / sample > MAX_THUMBNAIL_DIMENSION_PX) {
            if (sample >= 1 << 30) break;
            sample <<= 1;
        }
        return sample;
    }
}
