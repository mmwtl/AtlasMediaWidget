package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.*;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class WidgetProfilesTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private void place(int... ids) {
        var manager = Shadows.shadowOf(AppWidgetManager.getInstance(context));
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(context, AtlasMediaWidgetProvider.class);
        for (int id : ids) manager.addBoundWidget(id, info);
    }

    private static int style(Prefs prefs) {
        return prefs.getInt(Prefs.KEY_CARD_STYLE, -1);
    }

    @Test public void widgetFollowsSharedLookUntilItsFirstEdit() {
        Prefs shared = new Prefs(context);
        Prefs widget = shared.forWidget(7);
        shared.putInt(Prefs.KEY_CARD_STYLE, CardStyle.SQUARE.preferenceValue);
        assertFalse(widget.hasProfile());
        assertEquals(CardStyle.SQUARE.preferenceValue, style(widget));

        widget.putRadioFavoritesGrid(4, 3);
        assertTrue(widget.hasProfile());
        // The first edit keeps everything else the widget showed and detaches it.
        assertEquals(CardStyle.SQUARE.preferenceValue, style(widget));
        shared.putInt(Prefs.KEY_CARD_STYLE, CardStyle.COMPACT.preferenceValue);
        assertEquals(CardStyle.SQUARE.preferenceValue, style(widget));
        assertEquals(4, widget.radioFavoritesColumns());
        assertEquals(Prefs.DEFAULT_RADIO_FAVORITES_GRID_COLUMNS, shared.radioFavoritesColumns());
    }

    @Test public void widgetsAndOverlayKeepSeparateAppearance() {
        Prefs shared = new Prefs(context);
        Prefs first = shared.forWidget(1);
        Prefs second = shared.forWidget(12);
        WidgetAppearance look = WidgetAppearance.defaults(CardStyle.COMPACT);
        first.putAppearance(CardStyle.COMPACT, look.withBackdrop(
                new CardBackdrop(true, 0x112233, 50, true, false, 0x445566, 60)));
        second.ensureProfile();

        assertTrue(first.backdrop(CardStyle.COMPACT).solid);
        assertFalse(second.backdrop(CardStyle.COMPACT).solid);
        assertFalse(shared.backdrop(CardStyle.COMPACT).solid);
    }

    @Test public void onlyLookKeysAreKeptPerWidget() {
        Prefs shared = new Prefs(context);
        Prefs widget = shared.forWidget(3);
        widget.putBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, true);
        widget.putPlayerActionRules("player", java.util.List.of("a", "b"),
                java.util.List.of("a"), java.util.List.of("a", "b"));
        assertFalse(widget.hasProfile());
        assertTrue(shared.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false));
        assertEquals(java.util.List.of("a"), shared.playerActionRules().hidden.get("player"));

        // Migration markers share a prefix with the look keys they migrate.
        assertFalse(Prefs.isProfileKey("cover_dim_preset_migrated"));
        assertFalse(Prefs.isProfileKey(Prefs.KEY_CARD_WIDTH_PX));
        assertTrue(Prefs.isProfileKey("cover_dim_preset_0"));
    }

    @Test public void copyLookReplacesOtherWidgetsAndLeavesTemplate() {
        Prefs shared = new Prefs(context);
        shared.putInt(Prefs.KEY_CARD_STYLE, CardStyle.COMPACT.preferenceValue);
        Prefs source = shared.forWidget(1);
        Prefs target = shared.forWidget(2);
        target.putRadioFavoritesGrid(4, 4);
        source.putInt(Prefs.KEY_CARD_STYLE, CardStyle.SQUARE.preferenceValue);

        source.copyLookTo(new int[]{1, 2});

        assertEquals(CardStyle.SQUARE.preferenceValue, style(target));
        assertEquals(Prefs.DEFAULT_RADIO_FAVORITES_GRID_COLUMNS, target.radioFavoritesColumns());
        assertEquals(CardStyle.COMPACT.preferenceValue, style(shared));
    }

    @Test public void deletingAndRestoringFollowTheWidgetId() {
        place(5);
        Prefs shared = new Prefs(context);
        Prefs widget = shared.forWidget(5);
        widget.putInt(Prefs.KEY_CARD_STYLE, CardStyle.SQUARE.preferenceValue);

        var provider = new AtlasMediaWidgetProvider();
        provider.onRestored(context, new int[]{5}, new int[]{6});
        assertFalse(widget.hasProfile());
        assertEquals(CardStyle.SQUARE.preferenceValue, style(shared.forWidget(6)));

        provider.onDeleted(context, new int[]{6});
        assertFalse(shared.forWidget(6).hasProfile());
    }

    @Test public void backupRestoresProfilesByPosition() throws Exception {
        place(3, 9);
        Prefs shared = new Prefs(context);
        shared.forWidget(9).putInt(Prefs.KEY_CARD_STYLE, CardStyle.SQUARE.preferenceValue);
        // Captured through a widget's view: the backup still holds the shared settings.
        String json = SettingsBackup.encode(SettingsBackup.capture(shared.forWidget(9)), "test");
        SettingsBackup.Data data = SettingsBackup.decode(json);
        assertNull(data.widgetProfiles.get(0));
        assertEquals(CardStyle.SQUARE.preferenceValue,
                data.widgetProfiles.get(1).get(Prefs.KEY_CARD_STYLE));

        shared.forWidget(9).removeProfile();
        shared.forWidget(3).ensureProfile();
        assertTrue(shared.replacePortableSettings(data));

        assertFalse(shared.forWidget(3).hasProfile());
        assertEquals(CardStyle.SQUARE.preferenceValue, style(shared.forWidget(9)));
    }

    @Test public void importingFileWithoutProfilesPutsWidgetsBackOnSharedLook() throws Exception {
        place(4);
        Prefs shared = new Prefs(context);
        SettingsBackup.Data data = SettingsBackup.capture(shared);
        data.widgetProfiles = java.util.List.of();
        shared.forWidget(4).ensureProfile();

        assertTrue(shared.replacePortableSettings(
                SettingsBackup.decode(SettingsBackup.encode(data, "test"))));

        assertFalse(shared.forWidget(4).hasProfile());
    }

    @Test public void unknownProfileKeysAreDroppedOnImport() throws Exception {
        place(4);
        SettingsBackup.Data data = SettingsBackup.capture(new Prefs(context));
        data.widgetProfiles = java.util.List.of(Map.of(
                Prefs.KEY_CARD_STYLE, CardStyle.SQUARE.preferenceValue,
                Prefs.KEY_DISPLAY_MODE, "overlay",
                Prefs.KEY_AUTO_START, true));

        Map<String, Object> restored = SettingsBackup.decode(SettingsBackup.encode(data, "test"))
                .widgetProfiles.get(0);

        assertEquals(Map.of(Prefs.KEY_CARD_STYLE, CardStyle.SQUARE.preferenceValue), restored);
    }

    @Test public void settingsEditTheRequestedWidgetOnly() {
        place(31, 32);
        Prefs shared = new Prefs(context);
        shared.setWidgetMode(true);
        var controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(context, MainActivity.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 32)).create();
        try {
            var activity = controller.get();
            android.widget.Spinner chooser = findChooser(activity.findViewById(android.R.id.content));
            assertNotNull(chooser);
            assertEquals(1, chooser.getSelectedItemPosition());
            assertEquals(3, chooser.getCount());
            assertEquals("Шаблон для новых виджетов", chooser.getItemAtPosition(2));

            findText(activity.findViewById(android.R.id.content), "Компактная").performClick();
            assertEquals(CardStyle.COMPACT.preferenceValue, style(shared.forWidget(32)));
            assertFalse(shared.forWidget(31).hasProfile());
            assertNotEquals(CardStyle.COMPACT.preferenceValue, style(shared));
        } finally {
            controller.destroy();
        }
    }

    @Test public void overlayModeEditsSharedLookEvenWhenWidgetsArePlaced() {
        place(41);
        var controller = Robolectric.buildActivity(MainActivity.class).create();
        try {
            assertFalse(new Prefs(context).forWidget(41).hasProfile());
        } finally {
            controller.destroy();
        }
    }

    private static android.view.View findText(android.view.View view, String text) {
        if (view instanceof android.widget.TextView label && text.contentEquals(label.getText())) {
            return view;
        }
        if (view instanceof android.view.ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                android.view.View found = findText(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static android.widget.Spinner findChooser(android.view.View view) {
        if (view instanceof android.widget.Spinner spinner
                && "Какой виджет настраивать".contentEquals(spinner.getContentDescription() == null
                        ? "" : spinner.getContentDescription())) {
            return spinner;
        }
        if (view instanceof android.view.ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                android.widget.Spinner found = findChooser(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }
}
