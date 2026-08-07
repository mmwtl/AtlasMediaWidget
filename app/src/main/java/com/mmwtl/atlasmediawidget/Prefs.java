package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String KEY_SERVICE_ENABLED = "service_enabled";
    static final String KEY_AUTO_START = "auto_start";
    static final String KEY_POSITION_X = "position_x";
    static final String KEY_POSITION_Y = "position_y";
    static final String KEY_CARD_STYLE = "card_style";
    private static final String KEY_CARD_WIDTH_PREFIX = "card_width_";
    private static final String KEY_CARD_HEIGHT_PREFIX = "card_height_";
    private static final String KEY_TEXT_GAP_PREFIX = "text_gap_";
    static final int POSITION_UNSET = Integer.MIN_VALUE;
    static final int BOOT_DELAY_SECONDS = 15;
    static final int MAX_TEXT_GAP_DP = 32;

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

    int cardWidthDp(CardStyle style) {
        return getInt(KEY_CARD_WIDTH_PREFIX + style.preferenceValue, style.defaultWidthDp);
    }

    int cardHeightDp(CardStyle style) {
        return getInt(KEY_CARD_HEIGHT_PREFIX + style.preferenceValue, style.defaultHeightDp);
    }

    void putCardSize(CardStyle style, int widthDp, int heightDp) {
        preferences.edit()
                .putInt(KEY_CARD_WIDTH_PREFIX + style.preferenceValue, widthDp)
                .putInt(KEY_CARD_HEIGHT_PREFIX + style.preferenceValue, heightDp)
                .apply();
    }

    int textGapDp(CardStyle style) {
        return Math.max(0, Math.min(MAX_TEXT_GAP_DP,
                getInt(KEY_TEXT_GAP_PREFIX + style.preferenceValue, 0)));
    }

    void putTextGap(CardStyle style, int gapDp) {
        putInt(KEY_TEXT_GAP_PREFIX + style.preferenceValue,
                Math.max(0, Math.min(MAX_TEXT_GAP_DP, gapDp)));
    }
}
