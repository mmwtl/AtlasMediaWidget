package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

final class ArtworkLoader {
    // Artwork is rendered inside a bounded card. Decode only enough pixels for that surface;
    // accepting arbitrary provider dimensions here can otherwise allocate tens or hundreds of
    // megabytes in the app heap for a single cover.
    private static final int MAX_DECODE_DIMENSION_PX = 1440;
    private static final long MAX_DECODE_PIXELS = 2_000_000L;

    interface Listener {
        void onArtwork(long token, Bitmap bitmap);
    }

    private final Context context;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicLong activeToken = new AtomicLong();

    ArtworkLoader(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    long load(String uri, long generation, long revision) {
        return load(ArtworkRef.contentUri(uri), generation, revision);
    }

    long load(ArtworkRef artwork, long generation, long revision) {
        long token = activeToken.incrementAndGet();
        if (artwork == null || artwork.isEmpty()) {
            main.post(() -> {
                if (activeToken.get() == token) listener.onArtwork(token, null);
            });
            return token;
        }
        executor.execute(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream input = open(artwork)) {
                    if (input != null) BitmapFactory.decodeStream(input, null, bounds);
                }
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = calculateInSampleSize(
                            bounds.outWidth, bounds.outHeight);
                    options.inScaled = false;
                    bitmap = decode(artwork, options);
                }
            } catch (OutOfMemoryError error) {
                // The bounds/sample guard should make this exceptional. Keep the service alive if
                // a provider changes its stream between the bounds and decode passes.
                AppLog.warn("Cannot allocate media artwork bitmap", error);
            } catch (Exception error) {
                AppLog.warn("Cannot decode media artwork", error);
            }
            Bitmap result = bitmap;
            main.post(() -> {
                if (activeToken.get() == token) listener.onArtwork(token, result);
            });
        });
        return token;
    }

    private Bitmap decode(ArtworkRef artwork, BitmapFactory.Options options) throws Exception {
        try (InputStream input = open(artwork)) {
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        }
    }

    static int calculateInSampleSize(int width, int height) {
        if (width <= 0 || height <= 0) return 1;
        int sample = 1;
        while (sample < (1 << 30)) {
            long sampledWidth = (width + (long) sample - 1L) / sample;
            long sampledHeight = (height + (long) sample - 1L) / sample;
            long pixels = sampledWidth * sampledHeight;
            if (sampledWidth <= MAX_DECODE_DIMENSION_PX
                    && sampledHeight <= MAX_DECODE_DIMENSION_PX
                    && pixels <= MAX_DECODE_PIXELS) {
                return sample;
            }
            sample <<= 1;
        }
        return sample;
    }

    private InputStream open(ArtworkRef artwork) throws Exception {
        return switch (artwork.kind) {
            case CONTENT_URI -> context.getContentResolver().openInputStream(Uri.parse(artwork.value));
            case ASSET -> context.getAssets().open(artwork.value);
            case FILE -> new FileInputStream(artwork.value);
            case NONE -> null;
        };
    }

    long clear() {
        return activeToken.incrementAndGet();
    }

    long shutdown() {
        long token = clear();
        executor.shutdownNow();
        return token;
    }
}
