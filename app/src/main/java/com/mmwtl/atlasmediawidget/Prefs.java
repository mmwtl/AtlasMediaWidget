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
    private static final String KEY_CONTROL_HEIGHT_PREFIX = "control_height_";
    private static final String KEY_CONTROL_ICON_SCALE_PREFIX = "control_icon_scale_";
    private static final String KEY_CONTROL_SPREAD_PREFIX = "control_spread_";
    static final int POSITION_UNSET = Integer.MIN_VALUE;
    static final int BOOT_DELAY_SECONDS = 15;
    static final int MAX_TEXT_GAP_DP = 32;
    static final int MIN_CONTROL_PANEL_HEIGHT_DP = 64;
    static final int MAX_CONTROL_PANEL_HEIGHT_DP = 150;
    static final int MIN_CONTROL_ICON_SCALE_PERCENT = 60;
    static final int MAX_CONTROL_ICON_SCALE_PERCENT = 140;
    static final int DEFAULT_CONTROL_ICON_SCALE_PERCENT = 100;
    static final int MIN_CONTROL_SPREAD_PERCENT = 18;
    static final int MAX_CONTROL_SPREAD_PERCENT = 44;
    static final int DEFAULT_CONTROL_SPREAD_PERCENT = 33;

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

    int controlPanelHeightDp(CardStyle style) {
        return clamp(getInt(KEY_CONTROL_HEIGHT_PREFIX + style.preferenceValue,
                style.defaultControlPanelHeightDp), MIN_CONTROL_PANEL_HEIGHT_DP,
                MAX_CONTROL_PANEL_HEIGHT_DP);
    }

    int controlIconScalePercent(CardStyle style) {
        return clamp(getInt(KEY_CONTROL_ICON_SCALE_PREFIX + style.preferenceValue,
                DEFAULT_CONTROL_ICON_SCALE_PERCENT), MIN_CONTROL_ICON_SCALE_PERCENT,
                MAX_CONTROL_ICON_SCALE_PERCENT);
    }

    int controlSpreadPercent(CardStyle style) {
        return clamp(getInt(KEY_CONTROL_SPREAD_PREFIX + style.preferenceValue,
                DEFAULT_CONTROL_SPREAD_PERCENT), MIN_CONTROL_SPREAD_PERCENT,
                MAX_CONTROL_SPREAD_PERCENT);
    }

    void putControlLayout(CardStyle style, int heightDp, int iconScalePercent,
            int spreadPercent) {
        preferences.edit()
                .putInt(KEY_CONTROL_HEIGHT_PREFIX + style.preferenceValue,
                        clamp(heightDp, MIN_CONTROL_PANEL_HEIGHT_DP,
                                MAX_CONTROL_PANEL_HEIGHT_DP))
                .putInt(KEY_CONTROL_ICON_SCALE_PREFIX + style.preferenceValue,
                        clamp(iconScalePercent, MIN_CONTROL_ICON_SCALE_PERCENT,
                                MAX_CONTROL_ICON_SCALE_PERCENT))
                .putInt(KEY_CONTROL_SPREAD_PREFIX + style.preferenceValue,
                        clamp(spreadPercent, MIN_CONTROL_SPREAD_PERCENT,
                                MAX_CONTROL_SPREAD_PERCENT))
                .apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
