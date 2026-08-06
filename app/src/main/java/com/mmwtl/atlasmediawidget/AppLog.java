package com.mmwtl.atlasmediawidget;

import android.util.Log;

final class AppLog {
    private static final String TAG = "AtlasMediaWidget";

    private AppLog() {}

    static void info(String message) {
        Log.i(TAG, message);
    }

    static void warn(String message, Throwable error) {
        Log.w(TAG, message, error);
    }
}
