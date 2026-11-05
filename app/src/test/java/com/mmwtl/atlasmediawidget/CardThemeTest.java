package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CardThemeTest {
    private static final String PREFS_NAME = "atlas_media_widget";

    @Test public void nightUsesTheLauncherPalette() {
        RuntimeEnvironment.setQualifiers("+night");
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(0xFF232528, context.getColor(R.color.card_background));
        assertEquals(0xFFF1F2F4, context.getColor(R.color.card_text));
        assertEquals(0xFFC4C7CA, context.getColor(R.color.card_text_secondary));
        assertEquals(0xFF404347, context.getColor(R.color.card_nested));
        assertEquals(0xFF83AFC2, context.getColor(R.color.card_progress));
        assertEquals(0xB3404347, context.getColor(R.color.card_pill));
        assertEquals(0xFF232528, context.getColor(R.color.widget_empty_background));
    }

    @Test public void dayUsesTheLauncherPalette() {
        RuntimeEnvironment.setQualifiers("+notnight");
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(0xFFEDEEF0, context.getColor(R.color.card_background));
        assertEquals(0xFF202124, context.getColor(R.color.card_text));
        assertEquals(0xFF5F6368, context.getColor(R.color.card_text_secondary));
    }

    @Test public void widgetCardFollowsTheSystemModeAndOverlayStaysNight() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        RuntimeEnvironment.setQualifiers("+notnight");
        assertColor(0xEDEEF0, surface(card(context, prefs)));
        assertEquals(0xFF232528, CardTheme.overlay(context, CardColors.DEFAULT).getColor(R.color.card_background));
        RuntimeEnvironment.setQualifiers("+night");
        assertColor(0x232528, surface(card(context, prefs)));
        assertSame(context, CardTheme.overlay(context, CardColors.DEFAULT));
    }

    @Test public void fixedWidgetThemeIgnoresTheSystemMode() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_WIDGET_THEME, CardTheme.NIGHT);
        RuntimeEnvironment.setQualifiers("+notnight");
        assertColor(0x232528, surface(card(context, prefs)));
        prefs.putInt(Prefs.KEY_WIDGET_THEME, CardTheme.DAY);
        RuntimeEnvironment.setQualifiers("+night");
        assertColor(0xEDEEF0, surface(card(context, prefs)));
        prefs.putInt(Prefs.KEY_WIDGET_THEME, CardTheme.AUTO);
        assertColor(0x232528, surface(card(context, prefs)));
        prefs.putInt(Prefs.KEY_WIDGET_THEME, 7);
        assertEquals("an unknown value follows the system", CardTheme.AUTO, prefs.widgetTheme());
    }

    @Test public void singleColourBackdropKeepsReadableContentInBothModes() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_CARD_STYLE, CardStyle.COMPACT.preferenceValue);
        WidgetAppearance defaults = WidgetAppearance.defaults(CardStyle.COMPACT);
        prefs.putAppearance(CardStyle.COMPACT, defaults.withBackdrop(
                new CardBackdrop(true, 0x262626, 255, false, false)));
        RuntimeEnvironment.setQualifiers("+notnight");
        Bitmap day = card(context, prefs);
        RuntimeEnvironment.setQualifiers("+night");
        Bitmap night = card(context, prefs);
        assertColor(0x262626, surface(day));
        assertTrue("a dark user colour keeps the night content", day.sameAs(night));
        CardStyle style = CardStyle.COMPACT;
        WidgetAppearance light = defaults.withBackdrop(
                new CardBackdrop(true, 0xF0F0F0, 255, false, false));
        assertEquals(0xFF202124, CardTheme.widget(context, style, light, CardColors.DEFAULT).getColor(R.color.card_text));
    }

    @Test public void eachModeTakesItsOwnPalette() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        WidgetAppearance appearance = WidgetAppearance.defaults(CardStyle.SQUARE);
        RuntimeEnvironment.setQualifiers("+notnight");
        assertSame("the default palettes keep the resources", context,
                CardTheme.widget(context, CardStyle.SQUARE, appearance, CardColors.DEFAULT));
        CardColors colors = new CardColors(CardPalette.DAY.with(0xFFF4E0, 0x102030, 0xC04000),
                CardPalette.NIGHT.with(0x101830, 0xE0E8FF, 0x40C0A0));
        prefs.putCardColors(colors);
        assertEquals(colors, prefs.cardColors());
        assertColor(0xFFF4E0, surface(card(context, prefs)));
        Context day = CardTheme.widget(context, CardStyle.SQUARE, appearance, colors);
        assertEquals(0xFF102030, CardTheme.color(day, R.color.card_text));
        assertEquals(0xB3102030, CardTheme.color(day, R.color.card_text_secondary));
        assertEquals(0xFFC04000, CardTheme.color(day, R.color.card_progress));
        assertEquals("untouched tones stay", 0xFFB3261E, CardTheme.color(day, R.color.card_error));
        Context overlay = CardTheme.overlay(context, colors);
        assertEquals("the overlay is night by day too", 0xFFE0E8FF,
                CardTheme.color(overlay, R.color.card_text));
        RuntimeEnvironment.setQualifiers("+night");
        assertColor(0x101830, surface(card(context, prefs)));
        Context night = CardTheme.widget(context, CardStyle.SQUARE, appearance, colors);
        assertEquals(0xFFE0E8FF, CardTheme.color(night, R.color.card_text));
        assertEquals(0xD9E0E8FF, CardTheme.color(night, R.color.card_text_secondary));
        assertEquals(0xFF40C0A0, CardTheme.color(night, R.color.card_progress));
    }

    @Test public void palettesKeepTheDefaultsUntilAColourChanges() {
        CardPalette accentOnly = CardPalette.DAY.with(CardPalette.DAY.background,
                CardPalette.DAY.text, 0x00AA00);
        assertTrue(CardPalette.DAY.isDefault());
        assertTrue(CardPalette.NIGHT.isDefault());
        assertNull(CardPalette.DAY.override(R.color.card_text));
        assertNull(CardPalette.NIGHT.override(R.color.card_text));
        assertNull(accentOnly.override(R.color.card_background));
        assertNull(accentOnly.override(R.color.card_text));
        assertEquals(Integer.valueOf(0x3300AA00), accentOnly.override(R.color.card_option_selected));
        assertFalse("a night palette with day colours is customised",
                new CardPalette(true, 0xEDEEF0, 0x202124, 0x5F7C89).isDefault());
    }

    @Test public void separateDayBackdropShowsOnlyInTheDayMode() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_CARD_STYLE, CardStyle.COMPACT.preferenceValue);
        prefs.putAppearance(CardStyle.COMPACT, WidgetAppearance.defaults(CardStyle.COMPACT)
                .withBackdrop(new CardBackdrop(true, 0x262626, 255, false, false,
                        0xF0F0F0, 255)));
        RuntimeEnvironment.setQualifiers("+notnight");
        assertColor(0xF0F0F0, surface(card(context, prefs)));
        WidgetAppearance day = CardTheme.widgetAppearance(context,
                prefs.appearance(CardStyle.COMPACT));
        assertEquals(0xFF202124, CardTheme.widget(context, CardStyle.COMPACT, day,
                CardColors.DEFAULT).getColor(R.color.card_text));
        RuntimeEnvironment.setQualifiers("+night");
        assertColor(0x262626, surface(card(context, prefs)));
        assertColor(0xF0F0F0, surface(card(CardTheme.mode(context, false), prefs)));
    }

    @Test public void themedBackdropTakesTheSurfaceOfEachMode() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_CARD_STYLE, CardStyle.COMPACT.preferenceValue);
        prefs.putAppearance(CardStyle.COMPACT, WidgetAppearance.defaults(CardStyle.COMPACT)
                .withBackdrop(new CardBackdrop(true, CardBackdrop.THEME_COLOR, 255, false, false)));
        assertEquals(CardBackdrop.THEME_COLOR, prefs.backdrop(CardStyle.COMPACT).color);
        RuntimeEnvironment.setQualifiers("+notnight");
        assertColor(0xEDEEF0, surface(card(context, prefs)));
        RuntimeEnvironment.setQualifiers("+night");
        assertColor(0x232528, surface(card(context, prefs)));
    }

    @Test public void pickedCardColorKeepsTextReadableInBothModes() {
        Context context = RuntimeEnvironment.getApplication();
        WidgetAppearance appearance = WidgetAppearance.defaults(CardStyle.SQUARE);
        CardColors colors = new CardColors(
                CardPalette.DAY.with(0x102030, CardPalette.DAY.text, CardPalette.DAY.accent),
                CardPalette.NIGHT.with(0xF0E0D0, CardPalette.NIGHT.text, CardPalette.NIGHT.accent));
        RuntimeEnvironment.setQualifiers("+notnight");
        Context day = CardTheme.widget(context, CardStyle.SQUARE, appearance, colors);
        assertEquals(0xFF102030, CardTheme.color(day, R.color.card_background));
        assertEquals("light text on a dark day card", 0xFFF1F2F4,
                CardTheme.color(day, R.color.card_text));
        RuntimeEnvironment.setQualifiers("+night");
        Context night = CardTheme.widget(context, CardStyle.SQUARE, appearance, colors);
        assertEquals(0xFFF0E0D0, CardTheme.color(night, R.color.card_background));
        assertEquals("dark text on a light night card", 0xFF202124,
                CardTheme.color(night, R.color.card_text));
    }

    @Test public void pickedTextStaysOnAnyBackground() {
        Context context = RuntimeEnvironment.getApplication();
        CardColors colors = new CardColors(
                CardPalette.DAY.with(0x102030, 0xC0FF00, CardPalette.DAY.accent),
                CardPalette.NIGHT);
        RuntimeEnvironment.setQualifiers("+notnight");
        Context day = CardTheme.widget(context, CardStyle.SQUARE,
                WidgetAppearance.defaults(CardStyle.SQUARE), colors);
        assertEquals(0xFFC0FF00, CardTheme.color(day, R.color.card_text));
    }

    @Test public void upgradeMakesTheGraphiteBackdropFollowTheTheme() {
        Context context = RuntimeEnvironment.getApplication();
        context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("backdrop_solid_0", true)
                .putInt("backdrop_color_0", 0x262626)
                .putInt("backdrop_alpha_0", 200)
                .putBoolean("backdrop_solid_1", true)
                .putInt("backdrop_color_1", 0x102030)
                .putInt("backdrop_alpha_1", 90)
                .commit();
        Prefs prefs = new Prefs(context);
        CardBackdrop graphite = prefs.backdrop(CardStyle.fromPreference(0));
        assertEquals(CardBackdrop.THEME_COLOR, graphite.color);
        assertEquals(CardBackdrop.THEME_COLOR, graphite.dayColor);
        assertEquals(200, graphite.dayAlpha);
        CardBackdrop picked = prefs.backdrop(CardStyle.fromPreference(1));
        assertEquals(0x102030, picked.color);
        assertEquals(0x102030, picked.dayColor);
        assertEquals(90, picked.dayAlpha);
    }

    @Test public void upgradeForgetsTheOldStoredNightDefaults() {
        Context context = RuntimeEnvironment.getApplication();
        context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt("night_background", 0x1D2228)
                .putInt("night_text", 0xF5F5F5)
                .putInt("night_accent", 0x40C0A0)
                .commit();
        CardPalette night = new Prefs(context).cardColors().night;
        assertEquals(CardPalette.NIGHT.background, night.background);
        assertEquals(CardPalette.NIGHT.text, night.text);
        assertEquals("a picked colour stays", 0x40C0A0, night.accent);
    }

    @Test public void darknessPicksTheMoreReadableText() {
        assertTrue(CardTheme.isDark(0x262626));
        assertFalse(CardTheme.isDark(0xEDEEF0));
        assertFalse(CardTheme.isDark(0xFFFFFF));
    }

    @Test public void coverDimmingUsesTheSurfaceAndKeepsPresetOrder() {
        int[] night = CoverDimPreset.MEDIUM.colors(CardStyle.SQUARE);
        int[] day = CoverDimPreset.MEDIUM.colors(CardStyle.SQUARE, 0xEDEEF0);
        assertArrayEquals(night, CoverDimPreset.MEDIUM.colors(CardStyle.SQUARE, 0x1D2228));
        for (int index = 0; index < night.length; index++) {
            assertTrue("a light veil is denser", day[index] >>> 24 > night[index] >>> 24);
            assertEquals(0xEDEEF0, day[index] & 0xFFFFFF);
        }
        int[] weak = CoverDimPreset.WEAK.colors(CardStyle.SQUARE, 0xEDEEF0);
        assertTrue("presets keep their order", weak[3] >>> 24 < day[3] >>> 24);
    }

    @Test public void squareDayVeilKeepsTheArtworkMiddleClear() {
        for (CoverDimPreset preset : CoverDimPreset.values()) {
            int[] night = preset.colors(CardStyle.SQUARE);
            int[] day = preset.colors(CardStyle.SQUARE, 0xEDEEF0);
            assertTrue(preset + " middle stays near the night veil",
                    (day[1] >>> 24) - (night[1] >>> 24) <= 8);
        }
        int[] minimum = CoverDimPreset.MINIMUM.colors(CardStyle.SQUARE, 0xEDEEF0);
        assertEquals("a clear stop stays clear", 0, minimum[1] >>> 24);
    }

    private static Bitmap card(Context context, Prefs prefs) {
        Bundle options = new Bundle();
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 500);
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300);
        var frame = new AtlasMediaWidgetProvider.Frame(context, prefs, options, 0,
                WidgetPreview.demoSnapshot(), null, true, WidgetPreview.INERT);
        View view = frame.views.apply(context, null);
        return ((BitmapDrawable) ((ImageView) view.findViewById(R.id.widget_card))
                .getDrawable()).getBitmap();
    }

    /** Gradient blending may round each channel by one. */
    private static void assertColor(int expected, int actual) {
        for (int shift = 0; shift <= 16; shift += 8) {
            assertEquals(String.format("#%06X vs #%06X", expected, actual),
                    expected >> shift & 0xFF, actual >> shift & 0xFF, 1);
        }
    }

    /** RGB just inside the left edge, half way down: plain card surface in every layout. */
    private static int surface(Bitmap card) {
        return card.getPixel(Math.max(3, card.getWidth() / 100), card.getHeight() / 2) & 0xFFFFFF;
    }
}
