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
    static final String KEY_FREEFORM_HIDE_THRESHOLD_PERCENT = "freeform_hide_threshold_percent";
    static final String KEY_POSITION_X = "position_x";
    static final String KEY_POSITION_Y = "position_y";
    static final String KEY_POSITION_CORNER = "position_corner";
    static final String KEY_CARD_STYLE = "card_style";
    static final String KEY_CARD_WIDTH_PX = "card_width_px";
    static final String KEY_CARD_HEIGHT_PX = "card_height_px";
    static final String KEY_APP_UI_SCALE_TENTHS = "app_ui_scale_tenths";
    static final String KEY_RADIO_SAVED_NAVIGATION = "radio_saved_navigation";
    static final String KEY_RADIO_FAVORITES_NAVIGATION = "radio_favorites_navigation";
    static final String KEY_RADIO_FAVORITES_COLUMNS = "radio_favorites_columns";
    static final String KEY_RADIO_FAVORITES_ROWS = "radio_favorites_rows";
    static final String KEY_DRAG_HANDLE_VISIBLE = "drag_handle_visible";
    private static final String KEY_CARD_WIDTH_PREFIX = "card_width_";
    private static final String KEY_CARD_HEIGHT_PREFIX = "card_height_";
    private static final String KEY_METADATA_PROGRESS_GAP_PREFIX = "metadata_progress_gap_";
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
    private static final String KEY_COVER_DIM_PRESET_PREFIX = "cover_dim_preset_";
    private static final String KEY_COVER_DIM_PRESET_MIGRATED = "cover_dim_preset_migrated";
    static final int POSITION_UNSET = Integer.MIN_VALUE;
    static final int MIN_CARD_WIDTH_DP = 360;
    static final int MAX_CARD_WIDTH_DP = 900;
    static final int MIN_CARD_HEIGHT_DP = 220;
    static final int MAX_CARD_HEIGHT_DP = 900;
    static final int MIN_CARD_WIDTH_PX = 320;
    static final int MIN_CARD_HEIGHT_PX = 220;
    static final int MIN_METADATA_PROGRESS_GAP_DP = 4;
    static final int MAX_METADATA_PROGRESS_GAP_DP = 40;
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
    static final int MAX_TOP_ROW_TEXT_SIZE_SP = 28;
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
    static final int MIN_RADIO_FAVORITES_GRID_COLUMNS = 2;
    static final int MAX_RADIO_FAVORITES_GRID_COLUMNS = 4;
    static final int MIN_RADIO_FAVORITES_GRID_ROWS = 2;
    static final int MAX_RADIO_FAVORITES_GRID_ROWS = 4;
    static final int DEFAULT_RADIO_FAVORITES_GRID_COLUMNS = 2;
    static final int DEFAULT_RADIO_FAVORITES_GRID_ROWS = 2;

    private final Context appContext;
    private final SharedPreferences preferences;

    Prefs(Context context) {
        Context app = context.getApplicationContext();
        appContext = app;
        Context storage = app.createDeviceProtectedStorageContext();
        migrateCredentialPreferencesWhenAvailable(app, storage);
        preferences = storage.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        migrateCardSizePx();
        // Before this setting existed, the only available gradient was the strongest one.
        // A completely empty store is a new installation and gets the gentler default.
        migrateCoverDimPreset();
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

    int freeformHideThresholdPercent() {
        return clamp(getInt(KEY_FREEFORM_HIDE_THRESHOLD_PERCENT,
                WindowVisibilityPolicy.DEFAULT_HIDE_THRESHOLD_PERCENT),
                WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT,
                WindowVisibilityPolicy.MAX_HIDE_THRESHOLD_PERCENT);
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

    void putPosition(OverlayCorner corner, int offsetX, int offsetY) {
        preferences.edit()
                .putString(KEY_POSITION_CORNER, corner.preferenceValue)
                .putInt(KEY_POSITION_X, Math.max(0, offsetX))
                .putInt(KEY_POSITION_Y, Math.max(0, offsetY))
                .apply();
    }

    void putString(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    int cardWidthPx() {
        return Math.max(MIN_CARD_WIDTH_PX, getInt(KEY_CARD_WIDTH_PX,
                Ui.dp(appContext, CardStyle.DEFAULT.defaultWidthDp)));
    }

    int cardHeightPx() {
        return Math.max(MIN_CARD_HEIGHT_PX, getInt(KEY_CARD_HEIGHT_PX,
                Ui.dp(appContext, CardStyle.DEFAULT.defaultHeightDp)));
    }

    void putCardSizePx(int widthPx, int heightPx) {
        preferences.edit()
                .putInt(KEY_CARD_WIDTH_PX, Math.max(MIN_CARD_WIDTH_PX, widthPx))
                .putInt(KEY_CARD_HEIGHT_PX, Math.max(MIN_CARD_HEIGHT_PX, heightPx))
                .apply();
    }

    private void migrateCardSizePx() {
        if (preferences.contains(KEY_CARD_WIDTH_PX) && preferences.contains(KEY_CARD_HEIGHT_PX)) {
            return;
        }
        CardStyle style = CardStyle.fromPreference(getInt(KEY_CARD_STYLE,
                CardStyle.DEFAULT.preferenceValue));
        int widthDp = clamp(cardWidthDp(style), MIN_CARD_WIDTH_DP, MAX_CARD_WIDTH_DP);
        int heightDp = clamp(cardHeightDp(style), MIN_CARD_HEIGHT_DP, MAX_CARD_HEIGHT_DP);
        preferences.edit()
                .putInt(KEY_CARD_WIDTH_PX, Ui.dp(appContext, widthDp))
                .putInt(KEY_CARD_HEIGHT_PX, Ui.dp(appContext, heightDp))
                .apply();
    }

    int radioFavoritesColumns() {
        return clamp(getInt(KEY_RADIO_FAVORITES_COLUMNS,
                        DEFAULT_RADIO_FAVORITES_GRID_COLUMNS),
                MIN_RADIO_FAVORITES_GRID_COLUMNS, MAX_RADIO_FAVORITES_GRID_COLUMNS);
    }

    int radioFavoritesRows() {
        return clamp(getInt(KEY_RADIO_FAVORITES_ROWS,
                        DEFAULT_RADIO_FAVORITES_GRID_ROWS),
                MIN_RADIO_FAVORITES_GRID_ROWS, MAX_RADIO_FAVORITES_GRID_ROWS);
    }

    void putRadioFavoritesGrid(int columns, int rows) {
        preferences.edit()
                .putInt(KEY_RADIO_FAVORITES_COLUMNS, clamp(columns,
                        MIN_RADIO_FAVORITES_GRID_COLUMNS, MAX_RADIO_FAVORITES_GRID_COLUMNS))
                .putInt(KEY_RADIO_FAVORITES_ROWS, clamp(rows,
                        MIN_RADIO_FAVORITES_GRID_ROWS, MAX_RADIO_FAVORITES_GRID_ROWS))
                .apply();
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

    CoverDimPreset coverDimPreset(CardStyle style) {
        return CoverDimPreset.fromPreference(getInt(
                KEY_COVER_DIM_PRESET_PREFIX + style.preferenceValue,
                CoverDimPreset.DEFAULT.preferenceValue));
    }

    private void migrateCoverDimPreset() {
        if (preferences.contains(KEY_COVER_DIM_PRESET_MIGRATED)) return;
        boolean existingInstallation = !preferences.getAll().isEmpty();
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_COVER_DIM_PRESET_MIGRATED, true);
        if (existingInstallation) {
            for (CardStyle style : CardStyle.values()) {
                String key = KEY_COVER_DIM_PRESET_PREFIX + style.preferenceValue;
                if (!preferences.contains(key)) {
                    editor.putInt(key, CoverDimPreset.MAXIMUM.preferenceValue);
                }
            }
        }
        editor.apply();
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
                ranged(KEY_METADATA_PROGRESS_GAP_PREFIX, style,
                        defaults.metadataProgressGapDp,
                        MIN_METADATA_PROGRESS_GAP_DP, MAX_METADATA_PROGRESS_GAP_DP),
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
                        MIN_PROGRESS_THICKNESS_DP, MAX_PROGRESS_THICKNESS_DP),
                coverDimPreset(style));
    }

    void putAppearance(CardStyle style, WidgetAppearance value) {
        preferences.edit()
                .putInt(KEY_METADATA_PROGRESS_GAP_PREFIX + style.preferenceValue,
                        clamp(value.metadataProgressGapDp,
                                MIN_METADATA_PROGRESS_GAP_DP,
                                MAX_METADATA_PROGRESS_GAP_DP))
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
                .putInt(KEY_COVER_DIM_PRESET_PREFIX + style.preferenceValue,
                        value.coverDimPreset.preferenceValue)
                .apply();
    }

    boolean replacePortableSettings(SettingsBackup.Data data) {
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_AUTO_START, data.autoStart)
                .putBoolean(KEY_RADIO_SAVED_NAVIGATION, data.radioSavedNavigation)
                .putBoolean(KEY_RADIO_FAVORITES_NAVIGATION,
                        data.radioFavoritesNavigation)
                .putInt(KEY_RADIO_FAVORITES_COLUMNS, data.favoriteColumns)
                .putInt(KEY_RADIO_FAVORITES_ROWS, data.favoriteRows)
                .putBoolean(KEY_DRAG_HANDLE_VISIBLE, data.dragHandleVisible)
                .putInt(KEY_FREEFORM_HIDE_THRESHOLD_PERCENT, data.freeformHideThresholdPercent)
                .putInt(KEY_APP_UI_SCALE_TENTHS, data.appUiScaleTenths)
                .putInt(KEY_CARD_STYLE, data.selectedStyle.preferenceValue);
        if (data.positionX == null) {
            editor.remove(KEY_POSITION_X).remove(KEY_POSITION_Y).remove(KEY_POSITION_CORNER);
        } else {
            editor.putInt(KEY_POSITION_X, data.positionX)
                    .putInt(KEY_POSITION_Y, data.positionY);
            if (data.positionCorner == null) {
                editor.remove(KEY_POSITION_CORNER);
            } else {
                editor.putString(KEY_POSITION_CORNER, data.positionCorner.preferenceValue);
            }
        }
        if (data.cardWidthPx == null) {
            SettingsBackup.StyleData legacy = data.style(data.selectedStyle);
            editor.putInt(KEY_CARD_WIDTH_PX, Ui.dp(appContext, legacy.widthDp))
                    .putInt(KEY_CARD_HEIGHT_PX, Ui.dp(appContext, legacy.heightDp));
        } else {
            editor.putInt(KEY_CARD_WIDTH_PX, Math.max(MIN_CARD_WIDTH_PX, data.cardWidthPx))
                    .putInt(KEY_CARD_HEIGHT_PX, Math.max(MIN_CARD_HEIGHT_PX, data.cardHeightPx));
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
                .putInt(KEY_METADATA_PROGRESS_GAP_PREFIX + suffix,
                        value.metadataProgressGapDp)
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
                .putInt(KEY_PROGRESS_THICKNESS_PREFIX + suffix, value.progressThicknessDp)
                .putInt(KEY_COVER_DIM_PRESET_PREFIX + suffix,
                        value.coverDimPreset.preferenceValue);
    }

    private int ranged(String prefix, CardStyle style, int fallback, int min, int max) {
        return clamp(getInt(prefix + style.preferenceValue, fallback), min, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
