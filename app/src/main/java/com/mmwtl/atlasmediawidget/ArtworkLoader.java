package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

final class ArtworkLoader {
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
        long token = (generation * 31L) ^ revision ^ activeToken.incrementAndGet();
        activeToken.set(token);
        if (uri == null || uri.isBlank()) {
            main.post(() -> listener.onArtwork(token, null));
            return token;
        }
        executor.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uri))) {
                if (input != null) bitmap = BitmapFactory.decodeStream(input);
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

    void clear() {
        activeToken.incrementAndGet();
    }

    void shutdown() {
        clear();
        executor.shutdownNow();
    }
}
