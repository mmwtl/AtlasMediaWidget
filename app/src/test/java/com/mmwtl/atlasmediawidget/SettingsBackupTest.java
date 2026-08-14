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
        assertFalse(restored.showRadioCovers);
        assertEquals(17, restored.appUiScaleTenths);
        assertEquals(CardStyle.COMPACT, restored.selectedStyle);
        assertEquals(Integer.valueOf(321), restored.positionX);
        assertEquals(Integer.valueOf(654), restored.positionY);
        assertEquals(481, restored.compact.widthDp);
        assertEquals(302, restored.compact.heightDp);
        assertEquals(27, restored.square.appearance.contentInsetDp);
        JSONObject root = new JSONObject(json);
        assertEquals("atlas-media-widget-settings", root.getString("format"));
        assertEquals(2, root.getInt("schemaVersion"));
        assertEquals("1.2.3", root.getString("appVersion"));
        JSONObject settings = root.getJSONObject("settings");
        assertFalse(settings.has("serviceEnabled"));
        assertFalse(settings.has("customRadioCatalog"));
    }

    @Test public void jsonRoundTripPreservesDefaultPosition() throws Exception {
        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(data(15, CardStyle.SQUARE, null, null), "test"));

        assertNull(restored.positionX);
        assertNull(restored.positionY);
    }

    @Test public void rejectsUnsupportedSchemaVersion() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(
                data(15, CardStyle.SQUARE, null, null), "test"));
        root.put("schemaVersion", 3);

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

    private static SettingsBackup.Data data(int scale, CardStyle selected,
            Integer x, Integer y) throws IOException {
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
                false,
                scale,
                selected,
                x,
                y,
                new SettingsBackup.StyleData(481, 302, compactAppearance),
                new SettingsBackup.StyleData(512, 506, squareAppearance));
    }
}
