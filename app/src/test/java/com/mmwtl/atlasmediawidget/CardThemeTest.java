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
    @Test public void nightKeepsTheOriginalGraphiteCard() {
        RuntimeEnvironment.setQualifiers("+night");
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(0xFF1D2228, context.getColor(R.color.card_background));
        assertEquals(0xFFF5F5F5, context.getColor(R.color.card_text));
        assertEquals(0xFFD4D4D4, context.getColor(R.color.card_text_secondary));
        assertEquals(0xFF83AFC2, context.getColor(R.color.card_progress));
        assertEquals(0xB333333B, context.getColor(R.color.card_pill));
        assertEquals(0xFF262626, context.getColor(R.color.widget_empty_background));
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
        assertEquals(0xFF1D2228, CardTheme.overlay(context).getColor(R.color.card_background));
        RuntimeEnvironment.setQualifiers("+night");
        assertColor(0x1D2228, surface(card(context, prefs)));
        assertSame(context, CardTheme.overlay(context));
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
        assertEquals(0xFF202124, CardTheme.widget(context, style, light).getColor(R.color.card_text));
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
