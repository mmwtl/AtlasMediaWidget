package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

public final class SettingsBackupTest {
    @Test public void jsonRoundTripPreservesPortableSettings() throws Exception {
        SettingsBackup.Data original = data(17, CardStyle.COMPACT, 321, 654);

        String json = SettingsBackup.encode(original, "1.2.3");
        SettingsBackup.Data restored = SettingsBackup.decode(json);

        assertTrue(restored.autoStart);
        assertTrue(restored.radioSavedNavigation);
        assertTrue(restored.radioFavoritesNavigation);
        assertEquals(2, restored.favoriteColumns);
        assertEquals(2, restored.favoriteRows);
        assertTrue(restored.dragHandleVisible);
        assertEquals(17, restored.appUiScaleTenths);
        assertEquals(CardStyle.COMPACT, restored.selectedStyle);
        assertEquals(Integer.valueOf(321), restored.positionX);
        assertEquals(Integer.valueOf(654), restored.positionY);
        assertNull(restored.positionCorner);
        assertEquals(481, restored.compact.widthDp);
        assertEquals(302, restored.compact.heightDp);
        assertEquals(27, restored.square.appearance.contentInsetDp);
        assertEquals(CoverDimPreset.DEFAULT, restored.compact.appearance.coverDimPreset);
        assertEquals(76, restored.compact.appearance.thumbnailSizeDp);
        JSONObject root = new JSONObject(json);
        assertEquals("atlas-media-widget-settings", root.getString("format"));
        assertEquals(14, root.getInt("schemaVersion"));
        assertEquals("1.2.3", root.getString("appVersion"));
        JSONObject settings = root.getJSONObject("settings");
        assertFalse(settings.has("serviceEnabled"));
        assertFalse(settings.has("customRadioCatalog"));
        assertFalse(settings.has("showRadioCovers"));
    }

    @Test public void hideThresholdRoundTripAndLegacyDefault() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, CardStyle.COMPACT, null, null), "test"));
        root.getJSONObject("settings").put("freeformHideThresholdPercent", 60);
        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());
        assertEquals(60, restored.freeformHideThresholdPercent);
        assertEquals(60, SettingsBackup.decode(SettingsBackup.encode(restored, "test"))
                .freeformHideThresholdPercent);
        root.getJSONObject("settings").put("freeformHideThresholdPercent", 29);
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        root.getJSONObject("settings").put("freeformHideThresholdPercent", 96);
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        root.getJSONObject("settings").remove("freeformHideThresholdPercent");
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        root.put("schemaVersion", 9);
        assertEquals(85, SettingsBackup.decode(root.toString()).freeformHideThresholdPercent);
    }

    @Test public void thumbnailSizeRoundTripAndLegacyDefault() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.COMPACT, null, null), "test"));
        JSONObject compact = root.getJSONObject("settings").getJSONObject("cardStyles")
                .getJSONObject("compact");
        compact.put("thumbnailSizeDp", Prefs.MAX_THUMBNAIL_SIZE_DP);
        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());
        assertEquals(Prefs.MAX_THUMBNAIL_SIZE_DP, restored.compact.appearance.thumbnailSizeDp);
        assertEquals(Prefs.MAX_THUMBNAIL_SIZE_DP,
                SettingsBackup.decode(SettingsBackup.encode(restored, "test"))
                        .compact.appearance.thumbnailSizeDp);
        compact.put("thumbnailSizeDp", Prefs.MAX_THUMBNAIL_SIZE_DP + 1);
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        root.put("schemaVersion", 11);
        compact.remove("thumbnailSizeDp");
        assertEquals(76, SettingsBackup.decode(root.toString())
                .compact.appearance.thumbnailSizeDp);
    }

    @Test public void backdropRoundTripAndLegacyDefault() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.COMPACT, null, null), "test"));
        JSONObject compact = root.getJSONObject("settings").getJSONObject("cardStyles")
                .getJSONObject("compact");
        assertFalse(compact.getJSONObject("backdrop").getBoolean("solid"));
        compact.put("backdrop", new JSONObject()
                .put("solid", true).put("color", "#12a0FF").put("alpha", 128)
                .put("opaqueControls", true).put("opaqueTopRow", true));
        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());
        CardBackdrop backdrop = SettingsBackup.decode(SettingsBackup.encode(restored, "test"))
                .compact.appearance.backdrop;
        assertTrue(backdrop.solid);
        assertEquals(0x12A0FF, backdrop.color);
        assertEquals(128, backdrop.alpha);
        assertEquals(0x8012A0FF, backdrop.argb());
        assertTrue(backdrop.opaqueControlsFor(CardStyle.COMPACT));
        assertTrue(backdrop.opaqueTopRowFor(CardStyle.COMPACT));
        assertFalse(backdrop.solidFor(CardStyle.SQUARE));
        assertFalse(backdrop.opaqueControlsFor(CardStyle.SQUARE));
        compact.getJSONObject("backdrop").remove("opaqueControls");
        compact.getJSONObject("backdrop").remove("opaqueTopRow");
        CardBackdrop legacy = SettingsBackup.decode(root.toString()).compact.appearance.backdrop;
        assertFalse(legacy.opaqueControls);
        assertFalse(legacy.opaqueTopRow);
        compact.getJSONObject("backdrop").put("alpha", 256);
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        compact.getJSONObject("backdrop").put("alpha", 0).put("color", "red");
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        compact.remove("backdrop");
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        root.put("schemaVersion", 12);
        assertFalse(SettingsBackup.decode(root.toString()).compact.appearance.backdrop.solid);
    }

    @Test public void jsonRoundTripPreservesFavoriteGrid() throws Exception {
        SettingsBackup.Data original = dataWithGrid(15, CardStyle.COMPACT, null, null, 4, 3);

        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(original, "test"));

        assertEquals(4, restored.favoriteColumns);
        assertEquals(3, restored.favoriteRows);
    }

    @Test public void oldSchemaDefaultsFavoriteGridToTwoByTwo() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                dataWithGrid(15, CardStyle.SQUARE, null, null, 4, 4), "test"));
        root.put("schemaVersion", 4);
        JSONObject settings = root.getJSONObject("settings");
        settings.remove("favoriteColumns");
        settings.remove("favoriteRows");

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertEquals(2, restored.favoriteColumns);
        assertEquals(2, restored.favoriteRows);
    }

    @Test public void schemaSixDefaultsRadioFavoritesNavigationToFalse() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, null, null), "test"));
        root.put("schemaVersion", 6);
        root.getJSONObject("settings").remove("radioFavoritesNavigation");

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertFalse(restored.radioFavoritesNavigation);
    }

    @Test public void rejectsFavoriteGridOutsideSupportedRange() {
        assertThrows(IOException.class,
                () -> dataWithGrid(15, CardStyle.SQUARE, null, null, 1, 2));
        assertThrows(IOException.class,
                () -> dataWithGrid(15, CardStyle.SQUARE, null, null, 4, 5));
    }

    @Test public void jsonRoundTripPreservesDefaultPosition() throws Exception {
        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(data(15, CardStyle.SQUARE, null, null), "test"));

        assertNull(restored.positionX);
        assertNull(restored.positionY);
    }

    @Test public void schemaNineRoundTripPreservesGlobalCardSizeAndPositionCorner()
            throws Exception {
        SettingsBackup.Data original = new SettingsBackup.Data(
                true, true, true, 15, CardStyle.SQUARE, 31, 42,
                OverlayCorner.BOTTOM_END,
                new SettingsBackup.StyleData(500, 500,
                        WidgetAppearance.defaults(CardStyle.SQUARE)),
                new SettingsBackup.StyleData(500, 500,
                        WidgetAppearance.defaults(CardStyle.SQUARE)), 800, 810, 2, 2, false);

        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(original, "test"));

        assertEquals(OverlayCorner.BOTTOM_END, restored.positionCorner);
        assertEquals(Integer.valueOf(31), restored.positionX);
        assertEquals(Integer.valueOf(42), restored.positionY);
        assertEquals(Integer.valueOf(800), restored.cardWidthPx);
        assertEquals(Integer.valueOf(810), restored.cardHeightPx);
    }

    @Test public void schemaNineRejectsNegativePositionOffsets() throws Exception {
        SettingsBackup.Data original = new SettingsBackup.Data(
                true, true, true, 15, CardStyle.SQUARE, 31, 42,
                OverlayCorner.BOTTOM_END,
                new SettingsBackup.StyleData(500, 500,
                        WidgetAppearance.defaults(CardStyle.SQUARE)),
                new SettingsBackup.StyleData(500, 500,
                        WidgetAppearance.defaults(CardStyle.SQUARE)), 2, 2, false);
        JSONObject root = new JSONObject(SettingsBackup.encode(original, "test"));
        root.getJSONObject("settings").getJSONObject("overlayPosition").put("x", -1);

        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
    }

    @Test public void schemaSevenPositionRemainsLegacyAbsolute() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, 321, 654), "test"));
        root.put("schemaVersion", 7);
        root.getJSONObject("settings").getJSONObject("overlayPosition")
                .remove("legacyAbsolute");

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertEquals(Integer.valueOf(321), restored.positionX);
        assertEquals(Integer.valueOf(654), restored.positionY);
        assertNull(restored.positionCorner);
    }

    @Test public void schemaNineRejectsUnknownPositionCorner() throws Exception {
        SettingsBackup.Data original = new SettingsBackup.Data(
                true, true, true, 15, CardStyle.SQUARE, 31, 42,
                OverlayCorner.BOTTOM_END,
                new SettingsBackup.StyleData(500, 500,
                        WidgetAppearance.defaults(CardStyle.SQUARE)),
                new SettingsBackup.StyleData(500, 500,
                        WidgetAppearance.defaults(CardStyle.SQUARE)), 2, 2, false);
        JSONObject root = new JSONObject(SettingsBackup.encode(original, "test"));
        root.getJSONObject("settings").getJSONObject("overlayPosition")
                .put("corner", "middle");

        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
    }

    @Test public void jsonRoundTripPreservesHiddenDragHandle() throws Exception {
        SettingsBackup.Data restored = SettingsBackup.decode(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, null, null, false), "test"));

        assertFalse(restored.dragHandleVisible);
    }

    @Test public void rejectsUnsupportedSchemaVersion() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, null, null), "test"));
        root.put("schemaVersion", 15);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("Неподдерживаемая версия"));
    }

    @Test public void schemaOneIgnoresLegacyVerticalTextShift() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, null, null), "test"));
        root.put("schemaVersion", 1);
        JSONObject styles = root.getJSONObject("settings").getJSONObject("cardStyles");
        for (String name : new String[]{"compact", "square"}) {
            JSONObject style = styles.getJSONObject(name);
            style.remove("metadataProgressGapDp");
            style.put("textGapDp", 48);
        }

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertEquals(14, restored.compact.appearance.metadataProgressGapDp);
        assertEquals(14, restored.square.appearance.metadataProgressGapDp);
    }

    @Test public void schemaThreeDefaultsDragHandleToVisible() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, null, null, false), "test"));
        root.put("schemaVersion", 3);
        root.getJSONObject("settings").remove("dragHandleVisible");

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertTrue(restored.dragHandleVisible);
    }

    @Test public void rejectsValuesOutsideUiLimits() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, null, null), "test"));
        root.getJSONObject("settings").getJSONObject("cardStyles")
                .getJSONObject("compact").put("widthDp", 10_000);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("widthDp"));
    }

    @Test public void rejectsCoercedBooleanStrings() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, null, null), "test"));
        root.getJSONObject("settings").put("autoStart", "true");

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("true или false"));
    }

    @Test public void legacyBackupWithShowRadioCoversDecodesCleanly() throws Exception {
        String json = "{\n"
                + "  \"format\": \"atlas-media-widget-settings\",\n"
                + "  \"schemaVersion\": 2,\n"
                + "  \"appVersion\": \"1.1.6\",\n"
                + "  \"settings\": {\n"
                + "    \"autoStart\": true,\n"
                + "    \"showRadioCovers\": true,\n"
                + "    \"uiScaleTenths\": 10,\n"
                + "    \"selectedCardStyle\": \"compact\",\n"
                + "    \"overlayPosition\": null,\n"
                + "    \"cardStyles\": {\n"
                + "      \"compact\": {\n"
                + "        \"widthDp\": 500,\n"
                + "        \"heightDp\": 300,\n"
                + "        \"metadataProgressGapDp\": 14,\n"
                + "        \"controlPanelHeightDp\": 90,\n"
                + "        \"controlIconScalePercent\": 100,\n"
                + "        \"controlSpreadPercent\": 33,\n"
                + "        \"controlBottomInsetDp\": 0,\n"
                + "        \"topInsetDp\": 10,\n"
                + "        \"contentInsetDp\": 24,\n"
                + "        \"topRowTextSizeSp\": 13,\n"
                + "        \"titleTextSizeSp\": 22,\n"
                + "        \"subtitleTextSizeSp\": 15,\n"
                + "        \"subtitleGapDp\": 4,\n"
                + "        \"timeTextSizeSp\": 13,\n"
                + "        \"progressGapDp\": 8,\n"
                + "        \"progressThicknessDp\": 4\n"
                + "      },\n"
                + "      \"square\": {\n"
                + "        \"widthDp\": 500,\n"
                + "        \"heightDp\": 500,\n"
                + "        \"metadataProgressGapDp\": 14,\n"
                + "        \"controlPanelHeightDp\": 110,\n"
                + "        \"controlIconScalePercent\": 100,\n"
                + "        \"controlSpreadPercent\": 33,\n"
                + "        \"controlBottomInsetDp\": 0,\n"
                + "        \"topInsetDp\": 10,\n"
                + "        \"contentInsetDp\": 24,\n"
                + "        \"topRowTextSizeSp\": 13,\n"
                + "        \"titleTextSizeSp\": 26,\n"
                + "        \"subtitleTextSizeSp\": 17,\n"
                + "        \"subtitleGapDp\": 4,\n"
                + "        \"timeTextSizeSp\": 14,\n"
                + "        \"progressGapDp\": 8,\n"
                + "        \"progressThicknessDp\": 4\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";

        SettingsBackup.Data restored = SettingsBackup.decode(json);
        assertTrue(restored.autoStart);
        assertFalse(restored.radioSavedNavigation);
        assertTrue(restored.dragHandleVisible);
        assertEquals(CardStyle.COMPACT, restored.selectedStyle);
        assertEquals(500, restored.compact.widthDp);
        assertEquals(CoverDimPreset.MAXIMUM, restored.compact.appearance.coverDimPreset);
    }

    private static SettingsBackup.Data data(int scale, CardStyle selected,
            Integer x, Integer y) throws IOException {
        return data(scale, selected, x, y, true);
    }

    private static SettingsBackup.Data data(int scale, CardStyle selected,
            Integer x, Integer y, boolean dragHandleVisible) throws IOException {
        return dataWithGrid(scale, selected, x, y, dragHandleVisible, 2, 2);
    }

    private static SettingsBackup.Data dataWithGrid(int scale, CardStyle selected,
            Integer x, Integer y, int columns, int rows) throws IOException {
        return dataWithGrid(scale, selected, x, y, true, columns, rows);
    }

    private static SettingsBackup.Data dataWithGrid(int scale, CardStyle selected,
            Integer x, Integer y, boolean dragHandleVisible, int columns, int rows)
            throws IOException {
        WidgetAppearance compactAppearance = WidgetAppearance.defaults(CardStyle.COMPACT);
        WidgetAppearance squareDefaults = WidgetAppearance.defaults(CardStyle.SQUARE);
        WidgetAppearance squareAppearance = new WidgetAppearance(
                squareDefaults.metadataProgressGapDp,
                squareDefaults.controlPanelHeightDp,
                squareDefaults.controlIconScalePercent,
                squareDefaults.controlSpreadPercent,
                squareDefaults.controlBottomInsetDp,
                squareDefaults.topInsetDp,
                27,
                squareDefaults.topRowTextSizeSp,
                squareDefaults.titleTextSizeSp,
                squareDefaults.subtitleTextSizeSp,
                squareDefaults.subtitleGapDp,
                squareDefaults.timeTextSizeSp,
                squareDefaults.progressGapDp,
                squareDefaults.progressThicknessDp);
        return new SettingsBackup.Data(
                true,
                true,
                dragHandleVisible,
                scale,
                selected,
                x,
                y,
                new SettingsBackup.StyleData(481, 302, compactAppearance),
                new SettingsBackup.StyleData(512, 506, squareAppearance),
                columns, rows, true);
    }

    @Test public void playerActionsCountRoundTripsAndDefaultsForOlderFiles() throws Exception {
        SettingsBackup.Data base = data(15, CardStyle.COMPACT, null, null);
        assertEquals(Prefs.DEFAULT_PLAYER_ACTIONS, base.playerActionsCount);
        JSONObject root = new JSONObject(SettingsBackup.encode(base, "test"));
        root.getJSONObject("settings").put("playerActionsCount", 5);
        assertEquals(5, SettingsBackup.decode(root.toString()).playerActionsCount);
        root.getJSONObject("settings").put("hiddenPlayerActions",
                new JSONObject().put("com.aimp.player",
                        new org.json.JSONArray().put("com.aimp.service.action.toggleRepeatMode")));
        assertEquals(java.util.Map.of("com.aimp.player",
                        java.util.List.of("com.aimp.service.action.toggleRepeatMode")),
                SettingsBackup.decode(root.toString()).playerActionRules.hidden);
        root.getJSONObject("settings").put("hiddenPlayerActions",
                new JSONObject().put("com.aimp.player", new org.json.JSONArray().put(7)));
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        root.getJSONObject("settings").put("hiddenPlayerActions", new JSONObject());
        root.getJSONObject("settings").put("playerActionOrder", new JSONObject().put(
                "ru.yandex.music", new org.json.JSONArray().put("actionLike").put("actionDislike")));
        root.getJSONObject("settings").put("playerActionIds", new JSONObject().put(
                "ru.yandex.music", new org.json.JSONArray().put("actionDislike").put("actionLike")));
        var ordered = SettingsBackup.decode(root.toString()).playerActionRules;
        assertEquals(java.util.List.of("actionLike", "actionDislike"),
                ordered.order.get("ru.yandex.music"));
        assertEquals(java.util.List.of("actionDislike", "actionLike"),
                ordered.published.get("ru.yandex.music"));
        root.getJSONObject("settings").remove("playerActionOrder");
        root.getJSONObject("settings").remove("playerActionIds");
        assertTrue("early schema 14 files without order still import",
                SettingsBackup.decode(root.toString()).playerActionRules.order.isEmpty());

        root.getJSONObject("settings").put("playerActionsCount", Prefs.MAX_PLAYER_ACTIONS + 1);
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));

        root.put("schemaVersion", 13);
        root.getJSONObject("settings").remove("playerActionsCount");
        root.getJSONObject("settings").remove("hiddenPlayerActions");
        assertEquals(Prefs.DEFAULT_PLAYER_ACTIONS,
                SettingsBackup.decode(root.toString()).playerActionsCount);
        assertTrue(SettingsBackup.decode(root.toString()).playerActionRules.hidden.isEmpty());
    }

    @Test public void statusBarMediaRoundTripsAndDefaultsToOff() throws Exception {
        SettingsBackup.Data base = data(15, CardStyle.COMPACT, null, null);
        assertFalse(base.statusBarMedia);
        JSONObject root = new JSONObject(SettingsBackup.encode(base, "test"));
        assertFalse(root.getJSONObject("settings").getBoolean("statusBarMedia"));
        root.getJSONObject("settings").put("statusBarMedia", true);
        assertTrue(SettingsBackup.decode(root.toString()).statusBarMedia);
        root.getJSONObject("settings").put("statusBarMedia", "true");
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        root.getJSONObject("settings").remove("statusBarMedia");
        assertFalse(SettingsBackup.decode(root.toString()).statusBarMedia);

        assertEquals(StatusBarScene.Format.DEFAULT, base.statusBarFormat);
        root.getJSONObject("settings").put("statusBarFormat",
                StatusBarScene.Format.ARTIST_TITLE.preferenceValue);
        assertEquals(StatusBarScene.Format.ARTIST_TITLE,
                SettingsBackup.decode(root.toString()).statusBarFormat);
        root.getJSONObject("settings").put("statusBarFormat", 9);
        assertThrows(IOException.class, () -> SettingsBackup.decode(root.toString()));
        root.getJSONObject("settings").remove("statusBarFormat");
        assertEquals(StatusBarScene.Format.DEFAULT,
                SettingsBackup.decode(root.toString()).statusBarFormat);
    }
}
