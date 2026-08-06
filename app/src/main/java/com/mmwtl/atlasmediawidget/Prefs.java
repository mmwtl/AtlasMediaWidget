package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String KEY_SERVICE_ENABLED = "service_enabled";
    static final String KEY_AUTO_START = "auto_start";
    static final String KEY_POSITION_X = "position_x";
    static final String KEY_POSITION_Y = "position_y";
    static final String KEY_CARD_STYLE = "card_style";
    static final int POSITION_UNSET = Integer.MIN_VALUE;
    static final int BOOT_DELAY_SECONDS = 15;

    private final SharedPreferences preferences;

    Prefs(Context context) {
        preferences = context.getSharedPreferences("atlas_media_widget", Context.MODE_PRIVATE);
    }

    boolean getBoolean(String key, boolean fallback) {
        return preferences.getBoolean(key, fallback);
    }

    int getInt(String key, int fallback) {
        return preferences.getInt(key, fallback);
    }

    void putBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    void putInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }
}
