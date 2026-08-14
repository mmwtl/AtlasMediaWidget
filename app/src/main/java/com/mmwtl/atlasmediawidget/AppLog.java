package com.mmwtl.atlasmediawidget;

import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class AppLog {
    private static final String TAG = "AtlasMediaWidget";
    private static final long RATE_LIMIT_MS = 10_000L;
    private static final Map<String, Long> lastWarnings = new ConcurrentHashMap<>();

    private AppLog() {}

    static void info(String message) {
        Log.i(TAG, message);
    }

    static void warn(String message, Throwable error) {
        Log.w(TAG, message, error);
    }

    static void warnRateLimited(String key, String message, Throwable error) {
        long now = System.currentTimeMillis();
        Long previous = lastWarnings.putIfAbsent(key, now);
        if (previous == null || now - previous >= RATE_LIMIT_MS) {
            lastWarnings.put(key, now);
            warn(message, error);
        }
    }
}
