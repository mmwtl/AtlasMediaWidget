package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

final class Prefs {
    private static final String NAME = "atlas_media_widget";
    private static final Object MIGRATION_LOCK = new Object();
    private static volatile boolean credentialMigrationAttempted;
    static final String KEY_SERVICE_ENABLED = "service_enabled";
    static final String KEY_AUTO_START = "auto_start";
    static final String KEY_POSITION_X = "position_x";
    static final String KEY_POSITION_Y = "position_y";
    static final String KEY_CARD_STYLE = "card_style";
    static final String KEY_APP_UI_SCALE_TENTHS = "app_ui_scale_tenths";
    static final String KEY_SHOW_RADIO_COVERS = "show_radio_covers";
    static final String KEY_CUSTOM_RADIO_CATALOG = "custom_radio_catalog";
    private static final String KEY_CARD_WIDTH_PREFIX = "card_width_";
    private static final String KEY_CARD_HEIGHT_PREFIX = "card_height_";
    private static final String KEY_TEXT_GAP_PREFIX = "text_gap_";
    private static final String KEY_CONTROL_HEIGHT_PREFIX = "control_height_";
    private static final String KEY_CONTROL_ICON_SCALE_PREFIX = "control_icon_scale_";
    private static final String KEY_CONTROL_SPREAD_PREFIX = "control_spread_";
    private static final String KEY_CONTROL_BOTTOM_INSET_PREFIX = "control_bottom_inset_";
    private static final String KEY_TOP_INSET_PREFIX = "top_inset_";
    private static final String KEY_CONTENT_INSET_PREFIX = "content_inset_";
    private static final String KEY_TOP_ROW_TEXT_SIZE_PREFIX = "top_row_text_size_";
    private static final String KEY_TITLE_TEXT_SIZE_PREFIX = "title_text_size_";
    private static final String KEY_SUBTITLE_TEXT_SIZE_PREFIX = "subtitle_text_size_";
    private static final String KEY_SUBTITLE_GAP_PREFIX = "subtitle_gap_";
    private static final String KEY_TIME_TEXT_SIZE_PREFIX = "time_text_size_";
    private static final String KEY_PROGRESS_GAP_PREFIX = "progress_gap_";
    private static final String KEY_PROGRESS_THICKNESS_PREFIX = "progress_thickness_";
    static final int POSITION_UNSET = Integer.MIN_VALUE;
    static final int MIN_CARD_WIDTH_DP = 360;
    static final int MAX_CARD_WIDTH_DP = 900;
    static final int MIN_CARD_HEIGHT_DP = 220;
    static final int MAX_CARD_HEIGHT_DP = 900;
    static final int MIN_TEXT_GAP_DP = -24;
    static final int MAX_TEXT_GAP_DP = 48;
    static final int MIN_CONTROL_PANEL_HEIGHT_DP = 64;
    static final int MAX_CONTROL_PANEL_HEIGHT_DP = 150;
    static final int MIN_CONTROL_ICON_SCALE_PERCENT = 60;
    static final int MAX_CONTROL_ICON_SCALE_PERCENT = 140;
    static final int DEFAULT_CONTROL_ICON_SCALE_PERCENT = 100;
    static final int MIN_CONTROL_SPREAD_PERCENT = 18;
    static final int MAX_CONTROL_SPREAD_PERCENT = 44;
    static final int DEFAULT_CONTROL_SPREAD_PERCENT = 33;
    static final int MAX_CONTROL_BOTTOM_INSET_DP = 60;
    static final int MIN_TOP_INSET_DP = 4;
    static final int MAX_TOP_INSET_DP = 36;
    static final int MIN_CONTENT_INSET_DP = 12;
    static final int MAX_CONTENT_INSET_DP = 60;
    static final int MIN_TOP_ROW_TEXT_SIZE_SP = 9;
    static final int MAX_TOP_ROW_TEXT_SIZE_SP = 20;
    static final int MIN_TITLE_TEXT_SIZE_SP = 16;
    static final int MAX_TITLE_TEXT_SIZE_SP = 44;
    static final int MIN_SUBTITLE_TEXT_SIZE_SP = 10;
    static final int MAX_SUBTITLE_TEXT_SIZE_SP = 30;
    static final int MAX_SUBTITLE_GAP_DP = 18;
    static final int MIN_TIME_TEXT_SIZE_SP = 9;
    static final int MAX_TIME_TEXT_SIZE_SP = 24;
    static final int MAX_PROGRESS_GAP_DP = 40;
    static final int MIN_PROGRESS_THICKNESS_DP = 2;
    static final int MAX_PROGRESS_THICKNESS_DP = 16;

    private final SharedPreferences preferences;

    Prefs(Context context) {
        Context app = context.getApplicationContext();
        Context storage = app.createDeviceProtectedStorageContext();
        migrateCredentialPreferencesWhenAvailable(app, storage);
        preferences = storage.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private static void migrateCredentialPreferencesWhenAvailable(Context credentialContext,
            Context deviceContext) {
        if (credentialMigrationAttempted) return;
        UserManager users = credentialContext.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return;
        synchronized (MIGRATION_LOCK) {
            if (credentialMigrationAttempted) return;
            try {
                deviceContext.moveSharedPreferencesFrom(credentialContext, NAME);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot migrate preferences to Direct Boot storage", error);
            }
            credentialMigrationAttempted = true;
        }
    }

    boolean getBoolean(String key, boolean fallback) {
        return preferences.getBoolean(key, fallback);
    }

    int getInt(String key, int fallback) {
        return preferences.getInt(key, fallback);
    }

    String getString(String key, String fallback) {
        return preferences.getString(key, fallback);
    }

    void putBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    void putInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    void putString(String key, String value) {
        preferences.edit().putString(key, value).apply();
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
        return clamp(getInt(KEY_TEXT_GAP_PREFIX + style.preferenceValue, 0),
                MIN_TEXT_GAP_DP, MAX_TEXT_GAP_DP);
    }

    void putTextGap(CardStyle style, int gapDp) {
        putInt(KEY_TEXT_GAP_PREFIX + style.preferenceValue,
                clamp(gapDp, MIN_TEXT_GAP_DP, MAX_TEXT_GAP_DP));
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

    int controlBottomInsetDp(CardStyle style) {
        return clamp(getInt(KEY_CONTROL_BOTTOM_INSET_PREFIX + style.preferenceValue, 0),
                0, MAX_CONTROL_BOTTOM_INSET_DP);
    }

    void putControlLayout(CardStyle style, int heightDp, int iconScalePercent,
            int spreadPercent, int bottomInsetDp) {
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
                .putInt(KEY_CONTROL_BOTTOM_INSET_PREFIX + style.preferenceValue,
                        clamp(bottomInsetDp, 0, MAX_CONTROL_BOTTOM_INSET_DP))
                .apply();
    }

    WidgetAppearance appearance(CardStyle style) {
        WidgetAppearance defaults = WidgetAppearance.defaults(style);
        return new WidgetAppearance(
                textGapDp(style),
                controlPanelHeightDp(style),
                controlIconScalePercent(style),
                controlSpreadPercent(style),
                controlBottomInsetDp(style),
                ranged(KEY_TOP_INSET_PREFIX, style, defaults.topInsetDp,
                        MIN_TOP_INSET_DP, MAX_TOP_INSET_DP),
                ranged(KEY_CONTENT_INSET_PREFIX, style, defaults.contentInsetDp,
                        MIN_CONTENT_INSET_DP, MAX_CONTENT_INSET_DP),
                ranged(KEY_TOP_ROW_TEXT_SIZE_PREFIX, style, defaults.topRowTextSizeSp,
                        MIN_TOP_ROW_TEXT_SIZE_SP, MAX_TOP_ROW_TEXT_SIZE_SP),
                ranged(KEY_TITLE_TEXT_SIZE_PREFIX, style, defaults.titleTextSizeSp,
                        MIN_TITLE_TEXT_SIZE_SP, MAX_TITLE_TEXT_SIZE_SP),
                ranged(KEY_SUBTITLE_TEXT_SIZE_PREFIX, style, defaults.subtitleTextSizeSp,
                        MIN_SUBTITLE_TEXT_SIZE_SP, MAX_SUBTITLE_TEXT_SIZE_SP),
                ranged(KEY_SUBTITLE_GAP_PREFIX, style, defaults.subtitleGapDp,
                        0, MAX_SUBTITLE_GAP_DP),
                ranged(KEY_TIME_TEXT_SIZE_PREFIX, style, defaults.timeTextSizeSp,
                        MIN_TIME_TEXT_SIZE_SP, MAX_TIME_TEXT_SIZE_SP),
                ranged(KEY_PROGRESS_GAP_PREFIX, style, defaults.progressGapDp,
                        0, MAX_PROGRESS_GAP_DP),
                ranged(KEY_PROGRESS_THICKNESS_PREFIX, style, defaults.progressThicknessDp,
                        MIN_PROGRESS_THICKNESS_DP, MAX_PROGRESS_THICKNESS_DP));
    }

    void putAppearance(CardStyle style, WidgetAppearance value) {
        preferences.edit()
                .putInt(KEY_TEXT_GAP_PREFIX + style.preferenceValue,
                        clamp(value.textGapDp, MIN_TEXT_GAP_DP, MAX_TEXT_GAP_DP))
                .putInt(KEY_CONTROL_HEIGHT_PREFIX + style.preferenceValue,
                        clamp(value.controlPanelHeightDp, MIN_CONTROL_PANEL_HEIGHT_DP,
                                MAX_CONTROL_PANEL_HEIGHT_DP))
                .putInt(KEY_CONTROL_ICON_SCALE_PREFIX + style.preferenceValue,
                        clamp(value.controlIconScalePercent, MIN_CONTROL_ICON_SCALE_PERCENT,
                                MAX_CONTROL_ICON_SCALE_PERCENT))
                .putInt(KEY_CONTROL_SPREAD_PREFIX + style.preferenceValue,
                        clamp(value.controlSpreadPercent, MIN_CONTROL_SPREAD_PERCENT,
                                MAX_CONTROL_SPREAD_PERCENT))
                .putInt(KEY_CONTROL_BOTTOM_INSET_PREFIX + style.preferenceValue,
                        clamp(value.controlBottomInsetDp, 0, MAX_CONTROL_BOTTOM_INSET_DP))
                .putInt(KEY_TOP_INSET_PREFIX + style.preferenceValue,
                        clamp(value.topInsetDp, MIN_TOP_INSET_DP, MAX_TOP_INSET_DP))
                .putInt(KEY_CONTENT_INSET_PREFIX + style.preferenceValue,
                        clamp(value.contentInsetDp, MIN_CONTENT_INSET_DP,
                                MAX_CONTENT_INSET_DP))
                .putInt(KEY_TOP_ROW_TEXT_SIZE_PREFIX + style.preferenceValue,
                        clamp(value.topRowTextSizeSp, MIN_TOP_ROW_TEXT_SIZE_SP,
                                MAX_TOP_ROW_TEXT_SIZE_SP))
                .putInt(KEY_TITLE_TEXT_SIZE_PREFIX + style.preferenceValue,
                        clamp(value.titleTextSizeSp, MIN_TITLE_TEXT_SIZE_SP,
                                MAX_TITLE_TEXT_SIZE_SP))
                .putInt(KEY_SUBTITLE_TEXT_SIZE_PREFIX + style.preferenceValue,
                        clamp(value.subtitleTextSizeSp, MIN_SUBTITLE_TEXT_SIZE_SP,
                                MAX_SUBTITLE_TEXT_SIZE_SP))
                .putInt(KEY_SUBTITLE_GAP_PREFIX + style.preferenceValue,
                        clamp(value.subtitleGapDp, 0, MAX_SUBTITLE_GAP_DP))
                .putInt(KEY_TIME_TEXT_SIZE_PREFIX + style.preferenceValue,
                        clamp(value.timeTextSizeSp, MIN_TIME_TEXT_SIZE_SP,
                                MAX_TIME_TEXT_SIZE_SP))
                .putInt(KEY_PROGRESS_GAP_PREFIX + style.preferenceValue,
                        clamp(value.progressGapDp, 0, MAX_PROGRESS_GAP_DP))
                .putInt(KEY_PROGRESS_THICKNESS_PREFIX + style.preferenceValue,
                        clamp(value.progressThicknessDp, MIN_PROGRESS_THICKNESS_DP,
                                MAX_PROGRESS_THICKNESS_DP))
                .apply();
    }

    boolean replacePortableSettings(SettingsBackup.Data data) {
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_AUTO_START, data.autoStart)
                .putBoolean(KEY_SHOW_RADIO_COVERS, data.showRadioCovers)
                .putInt(KEY_APP_UI_SCALE_TENTHS, data.appUiScaleTenths)
                .putInt(KEY_CARD_STYLE, data.selectedStyle.preferenceValue);
        if (data.positionX == null) {
            editor.remove(KEY_POSITION_X).remove(KEY_POSITION_Y);
        } else {
            editor.putInt(KEY_POSITION_X, data.positionX)
                    .putInt(KEY_POSITION_Y, data.positionY);
        }
        putStyle(editor, CardStyle.COMPACT, data.compact);
        putStyle(editor, CardStyle.SQUARE, data.square);
        return editor.commit();
    }

    private static void putStyle(SharedPreferences.Editor editor, CardStyle style,
            SettingsBackup.StyleData data) {
        WidgetAppearance value = data.appearance;
        String suffix = Integer.toString(style.preferenceValue);
        editor.putInt(KEY_CARD_WIDTH_PREFIX + suffix, data.widthDp)
                .putInt(KEY_CARD_HEIGHT_PREFIX + suffix, data.heightDp)
                .putInt(KEY_TEXT_GAP_PREFIX + suffix, value.textGapDp)
                .putInt(KEY_CONTROL_HEIGHT_PREFIX + suffix, value.controlPanelHeightDp)
                .putInt(KEY_CONTROL_ICON_SCALE_PREFIX + suffix, value.controlIconScalePercent)
                .putInt(KEY_CONTROL_SPREAD_PREFIX + suffix, value.controlSpreadPercent)
                .putInt(KEY_CONTROL_BOTTOM_INSET_PREFIX + suffix, value.controlBottomInsetDp)
                .putInt(KEY_TOP_INSET_PREFIX + suffix, value.topInsetDp)
                .putInt(KEY_CONTENT_INSET_PREFIX + suffix, value.contentInsetDp)
                .putInt(KEY_TOP_ROW_TEXT_SIZE_PREFIX + suffix, value.topRowTextSizeSp)
                .putInt(KEY_TITLE_TEXT_SIZE_PREFIX + suffix, value.titleTextSizeSp)
                .putInt(KEY_SUBTITLE_TEXT_SIZE_PREFIX + suffix, value.subtitleTextSizeSp)
                .putInt(KEY_SUBTITLE_GAP_PREFIX + suffix, value.subtitleGapDp)
                .putInt(KEY_TIME_TEXT_SIZE_PREFIX + suffix, value.timeTextSizeSp)
                .putInt(KEY_PROGRESS_GAP_PREFIX + suffix, value.progressGapDp)
                .putInt(KEY_PROGRESS_THICKNESS_PREFIX + suffix, value.progressThicknessDp);
    }

    private int ranged(String prefix, CardStyle style, int fallback, int min, int max) {
        return clamp(getInt(prefix + style.preferenceValue, fallback), min, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
