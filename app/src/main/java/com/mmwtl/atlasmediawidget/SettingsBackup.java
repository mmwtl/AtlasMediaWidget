package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class SettingsBackup {
    static final String FILE_NAME = "AtlasMediaWidget-settings.json";
    private static final String FORMAT = "atlas-media-widget-settings";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_FILE_BYTES = 256 * 1024;

    static final class Data {
        final boolean autoStart;
        final boolean showRadioCovers;
        final int appUiScaleTenths;
        final CardStyle selectedStyle;
        final Integer positionX;
        final Integer positionY;
        final StyleData compact;
        final StyleData square;

        Data(boolean autoStart, boolean showRadioCovers, int appUiScaleTenths,
                CardStyle selectedStyle, Integer positionX, Integer positionY,
                StyleData compact, StyleData square) throws IOException {
            this.autoStart = autoStart;
            this.showRadioCovers = showRadioCovers;
            this.appUiScaleTenths = requireRange("settings.uiScaleTenths", appUiScaleTenths,
                    ScaledActivity.MIN_SCALE_TENTHS, ScaledActivity.MAX_SCALE_TENTHS);
            if (selectedStyle == null) throw invalid("Не указан формат карточки");
            this.selectedStyle = selectedStyle;
            if ((positionX == null) != (positionY == null)) {
                throw invalid("Положение overlay должно содержать обе координаты");
            }
            if (positionX != null && (positionX == Prefs.POSITION_UNSET
                    || positionY == Prefs.POSITION_UNSET)) {
                throw invalid("Недопустимое положение overlay");
            }
            this.positionX = positionX;
            this.positionY = positionY;
            if (compact == null || square == null) {
                throw invalid("Отсутствуют настройки одного из форматов карточки");
            }
            this.compact = compact.validated("compact");
            this.square = square.validated("square");
        }

        StyleData style(CardStyle style) {
            return style == CardStyle.COMPACT ? compact : square;
        }
    }

    static final class StyleData {
        final int widthDp;
        final int heightDp;
        final WidgetAppearance appearance;

        StyleData(int widthDp, int heightDp, WidgetAppearance appearance) {
            this.widthDp = widthDp;
            this.heightDp = heightDp;
            this.appearance = appearance;
        }

        private StyleData validated(String path) throws IOException {
            requireRange(path + ".widthDp", widthDp,
                    Prefs.MIN_CARD_WIDTH_DP, Prefs.MAX_CARD_WIDTH_DP);
            requireRange(path + ".heightDp", heightDp,
                    Prefs.MIN_CARD_HEIGHT_DP, Prefs.MAX_CARD_HEIGHT_DP);
            if (appearance == null) throw invalid("Нет параметров внешнего вида: " + path);
            requireRange(path + ".textGapDp", appearance.textGapDp,
                    Prefs.MIN_TEXT_GAP_DP, Prefs.MAX_TEXT_GAP_DP);
            requireRange(path + ".controlPanelHeightDp", appearance.controlPanelHeightDp,
                    Prefs.MIN_CONTROL_PANEL_HEIGHT_DP, Prefs.MAX_CONTROL_PANEL_HEIGHT_DP);
            requireRange(path + ".controlIconScalePercent", appearance.controlIconScalePercent,
                    Prefs.MIN_CONTROL_ICON_SCALE_PERCENT, Prefs.MAX_CONTROL_ICON_SCALE_PERCENT);
            requireRange(path + ".controlSpreadPercent", appearance.controlSpreadPercent,
                    Prefs.MIN_CONTROL_SPREAD_PERCENT, Prefs.MAX_CONTROL_SPREAD_PERCENT);
            requireRange(path + ".controlBottomInsetDp", appearance.controlBottomInsetDp,
                    0, Prefs.MAX_CONTROL_BOTTOM_INSET_DP);
            requireRange(path + ".topInsetDp", appearance.topInsetDp,
                    Prefs.MIN_TOP_INSET_DP, Prefs.MAX_TOP_INSET_DP);
            requireRange(path + ".contentInsetDp", appearance.contentInsetDp,
                    Prefs.MIN_CONTENT_INSET_DP, Prefs.MAX_CONTENT_INSET_DP);
            requireRange(path + ".topRowTextSizeSp", appearance.topRowTextSizeSp,
                    Prefs.MIN_TOP_ROW_TEXT_SIZE_SP, Prefs.MAX_TOP_ROW_TEXT_SIZE_SP);
            requireRange(path + ".titleTextSizeSp", appearance.titleTextSizeSp,
                    Prefs.MIN_TITLE_TEXT_SIZE_SP, Prefs.MAX_TITLE_TEXT_SIZE_SP);
            requireRange(path + ".subtitleTextSizeSp", appearance.subtitleTextSizeSp,
                    Prefs.MIN_SUBTITLE_TEXT_SIZE_SP, Prefs.MAX_SUBTITLE_TEXT_SIZE_SP);
            requireRange(path + ".subtitleGapDp", appearance.subtitleGapDp,
                    0, Prefs.MAX_SUBTITLE_GAP_DP);
            requireRange(path + ".timeTextSizeSp", appearance.timeTextSizeSp,
                    Prefs.MIN_TIME_TEXT_SIZE_SP, Prefs.MAX_TIME_TEXT_SIZE_SP);
            requireRange(path + ".progressGapDp", appearance.progressGapDp,
                    0, Prefs.MAX_PROGRESS_GAP_DP);
            requireRange(path + ".progressThicknessDp", appearance.progressThicknessDp,
                    Prefs.MIN_PROGRESS_THICKNESS_DP, Prefs.MAX_PROGRESS_THICKNESS_DP);
            return this;
        }
    }

    private SettingsBackup() {}

    static Data capture(Prefs prefs) throws IOException {
        int x = prefs.getInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
        int y = prefs.getInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
        Integer positionX = x == Prefs.POSITION_UNSET || y == Prefs.POSITION_UNSET ? null : x;
        Integer positionY = positionX == null ? null : y;
        return new Data(
                prefs.getBoolean(Prefs.KEY_AUTO_START, false),
                prefs.getBoolean(Prefs.KEY_SHOW_RADIO_COVERS, true),
                clamp(prefs.getInt(Prefs.KEY_APP_UI_SCALE_TENTHS,
                                ScaledActivity.DEFAULT_SCALE_TENTHS),
                        ScaledActivity.MIN_SCALE_TENTHS, ScaledActivity.MAX_SCALE_TENTHS),
                CardStyle.fromPreference(prefs.getInt(Prefs.KEY_CARD_STYLE,
                        CardStyle.DEFAULT.preferenceValue)),
                positionX,
                positionY,
                captureStyle(prefs, CardStyle.COMPACT),
                captureStyle(prefs, CardStyle.SQUARE));
    }

    static void write(Context context, Prefs prefs, Uri uri) throws IOException {
        if (uri == null) throw invalid("Файл не выбран");
        byte[] contents = encode(capture(prefs), appVersion(context))
                .getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw invalid("Не удалось открыть файл для записи");
            output.write(contents);
        }
    }

    static Data read(Context context, Uri uri) throws IOException {
        if (uri == null) throw invalid("Файл не выбран");
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw invalid("Не удалось открыть файл");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) {
                    throw invalid("JSON настроек больше 256 КБ");
                }
                output.write(buffer, 0, count);
            }
            return decode(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    static String encode(Data data, String appVersion) throws IOException {
        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("appVersion", appVersion == null ? "" : appVersion);
            JSONObject settings = new JSONObject();
            settings.put("autoStart", data.autoStart);
            settings.put("showRadioCovers", data.showRadioCovers);
            settings.put("uiScaleTenths", data.appUiScaleTenths);
            settings.put("selectedCardStyle", styleName(data.selectedStyle));
            if (data.positionX == null) {
                settings.put("overlayPosition", JSONObject.NULL);
            } else {
                settings.put("overlayPosition", new JSONObject()
                        .put("x", data.positionX)
                        .put("y", data.positionY));
            }
            settings.put("cardStyles", new JSONObject()
                    .put("compact", encodeStyle(data.compact))
                    .put("square", encodeStyle(data.square)));
            root.put("settings", settings);
            return root.toString(2) + '\n';
        } catch (JSONException error) {
            throw new IOException("Не удалось сформировать JSON настроек", error);
        }
    }

    static Data decode(String json) throws IOException {
        try {
            if (json != null && !json.isEmpty() && json.charAt(0) == '\ufeff') {
                json = json.substring(1);
            }
            JSONObject root = new JSONObject(json == null ? "" : json);
            if (!FORMAT.equals(requireString(root, "format", "format"))) {
                throw invalid("Это не файл настроек Atlas Media Widget");
            }
            int version = requireInt(root, "schemaVersion", "schemaVersion");
            if (version != SCHEMA_VERSION) {
                throw invalid("Неподдерживаемая версия JSON: " + version);
            }
            JSONObject settings = requireObject(root, "settings", "settings");
            JSONObject styles = requireObject(settings, "cardStyles", "settings.cardStyles");
            Object positionValue = requireValue(settings, "overlayPosition",
                    "settings.overlayPosition");
            Integer x = null;
            Integer y = null;
            if (positionValue != JSONObject.NULL) {
                if (!(positionValue instanceof JSONObject position)) {
                    throw invalid("settings.overlayPosition должен быть объектом или null");
                }
                x = requireInt(position, "x", "settings.overlayPosition.x");
                y = requireInt(position, "y", "settings.overlayPosition.y");
            }
            return new Data(
                    requireBoolean(settings, "autoStart", "settings.autoStart"),
                    requireBoolean(settings, "showRadioCovers", "settings.showRadioCovers"),
                    requireInt(settings, "uiScaleTenths", "settings.uiScaleTenths"),
                    parseStyleName(requireString(settings, "selectedCardStyle",
                            "settings.selectedCardStyle")),
                    x,
                    y,
                    decodeStyle(requireObject(styles, "compact",
                            "settings.cardStyles.compact"), "compact"),
                    decodeStyle(requireObject(styles, "square",
                            "settings.cardStyles.square"), "square"));
        } catch (JSONException error) {
            throw invalid("Повреждённый JSON настроек", error);
        }
    }

    private static StyleData captureStyle(Prefs prefs, CardStyle style) {
        return new StyleData(
                clamp(prefs.cardWidthDp(style),
                        Prefs.MIN_CARD_WIDTH_DP, Prefs.MAX_CARD_WIDTH_DP),
                clamp(prefs.cardHeightDp(style),
                        Prefs.MIN_CARD_HEIGHT_DP, Prefs.MAX_CARD_HEIGHT_DP),
                prefs.appearance(style));
    }

    private static String appVersion(Context context) {
        try {
            String value = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return value == null ? "" : value;
        } catch (PackageManager.NameNotFoundException error) {
            return "";
        }
    }

    private static JSONObject encodeStyle(StyleData data) throws JSONException {
        WidgetAppearance value = data.appearance;
        return new JSONObject()
                .put("widthDp", data.widthDp)
                .put("heightDp", data.heightDp)
                .put("textGapDp", value.textGapDp)
                .put("controlPanelHeightDp", value.controlPanelHeightDp)
                .put("controlIconScalePercent", value.controlIconScalePercent)
                .put("controlSpreadPercent", value.controlSpreadPercent)
                .put("controlBottomInsetDp", value.controlBottomInsetDp)
                .put("topInsetDp", value.topInsetDp)
                .put("contentInsetDp", value.contentInsetDp)
                .put("topRowTextSizeSp", value.topRowTextSizeSp)
                .put("titleTextSizeSp", value.titleTextSizeSp)
                .put("subtitleTextSizeSp", value.subtitleTextSizeSp)
                .put("subtitleGapDp", value.subtitleGapDp)
                .put("timeTextSizeSp", value.timeTextSizeSp)
                .put("progressGapDp", value.progressGapDp)
                .put("progressThicknessDp", value.progressThicknessDp);
    }

    private static StyleData decodeStyle(JSONObject object, String path) throws IOException {
        return new StyleData(
                requireInt(object, "widthDp", path + ".widthDp"),
                requireInt(object, "heightDp", path + ".heightDp"),
                new WidgetAppearance(
                        requireInt(object, "textGapDp", path + ".textGapDp"),
                        requireInt(object, "controlPanelHeightDp",
                                path + ".controlPanelHeightDp"),
                        requireInt(object, "controlIconScalePercent",
                                path + ".controlIconScalePercent"),
                        requireInt(object, "controlSpreadPercent",
                                path + ".controlSpreadPercent"),
                        requireInt(object, "controlBottomInsetDp",
                                path + ".controlBottomInsetDp"),
                        requireInt(object, "topInsetDp", path + ".topInsetDp"),
                        requireInt(object, "contentInsetDp", path + ".contentInsetDp"),
                        requireInt(object, "topRowTextSizeSp", path + ".topRowTextSizeSp"),
                        requireInt(object, "titleTextSizeSp", path + ".titleTextSizeSp"),
                        requireInt(object, "subtitleTextSizeSp", path + ".subtitleTextSizeSp"),
                        requireInt(object, "subtitleGapDp", path + ".subtitleGapDp"),
                        requireInt(object, "timeTextSizeSp", path + ".timeTextSizeSp"),
                        requireInt(object, "progressGapDp", path + ".progressGapDp"),
                        requireInt(object, "progressThicknessDp",
                                path + ".progressThicknessDp")));
    }

    private static CardStyle parseStyleName(String value) throws IOException {
        if ("compact".equals(value)) return CardStyle.COMPACT;
        if ("square".equals(value)) return CardStyle.SQUARE;
        throw invalid("Неизвестный формат карточки: " + value);
    }

    private static String styleName(CardStyle style) {
        return style == CardStyle.COMPACT ? "compact" : "square";
    }

    private static Object requireValue(JSONObject object, String key, String path)
            throws IOException {
        if (!object.has(key)) throw invalid("Отсутствует поле " + path);
        try {
            return object.get(key);
        } catch (JSONException error) {
            throw invalid("Не удалось прочитать поле " + path, error);
        }
    }

    private static JSONObject requireObject(JSONObject object, String key, String path)
            throws IOException {
        Object value = requireValue(object, key, path);
        if (value instanceof JSONObject nested) return nested;
        throw invalid("Поле " + path + " должно быть объектом");
    }

    private static String requireString(JSONObject object, String key, String path)
            throws IOException {
        Object value = requireValue(object, key, path);
        if (value instanceof String text) return text;
        throw invalid("Поле " + path + " должно быть строкой");
    }

    private static boolean requireBoolean(JSONObject object, String key, String path)
            throws IOException {
        Object value = requireValue(object, key, path);
        if (value instanceof Boolean flag) return flag;
        throw invalid("Поле " + path + " должно быть true или false");
    }

    private static int requireInt(JSONObject object, String key, String path) throws IOException {
        Object value = requireValue(object, key, path);
        if (!(value instanceof Number number)) {
            throw invalid("Поле " + path + " должно быть целым числом");
        }
        double exact = number.doubleValue();
        if (!Double.isFinite(exact) || exact != Math.rint(exact)
                || exact < Integer.MIN_VALUE || exact > Integer.MAX_VALUE) {
            throw invalid("Поле " + path + " должно быть целым числом");
        }
        return (int) exact;
    }

    private static int requireRange(String path, int value, int min, int max)
            throws IOException {
        if (value < min || value > max) {
            throw invalid("Поле " + path + " вне диапазона " + min + "…" + max);
        }
        return value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static IOException invalid(String message) {
        return new IOException(message);
    }

    private static IOException invalid(String message, Throwable cause) {
        return new IOException(message, cause);
    }
}
