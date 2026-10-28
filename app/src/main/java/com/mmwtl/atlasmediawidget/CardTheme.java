package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;

/**
 * Picks the day or night card colours, which the resources hold in values and values-night.
 * The card is drawn into bitmaps, so the caller chooses the mode through the context it passes.
 */
final class CardTheme {
    private CardTheme() {}

    /** The overlay keeps the graphite night card over the OEM launcher in either system mode. */
    static Context overlay(Context base) {
        return withNight(base, true);
    }

    /** A day context for previewing the light widget card at night. */
    static Context day(Context base) {
        return withNight(base, false);
    }

    /**
     * An AppWidget follows the system night mode, as its launcher does. A single-colour backdrop
     * is the user's fixed colour, so its content takes the mode that stays readable on it.
     * Light content takes the user's day palette.
     */
    static Context widget(Context base, CardStyle style, WidgetAppearance appearance,
            DayPalette palette) {
        Context themed = appearance.backdrop.solidFor(style)
                ? withNight(base, isDark(appearance.backdrop.color)) : base;
        return palette.isDefault() || isNight(themed.getResources().getConfiguration())
                ? themed : new Palette(themed, palette);
    }

    /** The appearance an AppWidget shows in the mode of {@code base}: by day its day backdrop. */
    static WidgetAppearance widgetAppearance(Context base, WidgetAppearance appearance) {
        return isNight(base.getResources().getConfiguration()) ? appearance
                : appearance.withBackdrop(appearance.backdrop.forDay());
    }

    /** A card_* colour of the context's mode, with the day palette applied to light content. */
    static int color(Context context, int id) {
        if (context instanceof Palette themed) {
            Integer value = themed.palette.override(id);
            if (value != null) return value;
        }
        return context.getColor(id);
    }

    static boolean isNight(Configuration configuration) {
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Whether light text contrasts better than dark text with this RGB colour. */
    static boolean isDark(int rgb) {
        double luminance = 0.2126 * linear(rgb >> 16 & 0xFF)
                + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
        // Black and white text have equal WCAG contrast at this luminance.
        return luminance < 0.179;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static Context withNight(Context base, boolean night) {
        Configuration configuration = base.getResources().getConfiguration();
        if (isNight(configuration) == night) return base;
        Configuration themed = new Configuration(configuration);
        themed.uiMode = themed.uiMode & ~Configuration.UI_MODE_NIGHT_MASK
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(themed);
    }

    /** Carries the day palette to the card views, which read their colours through color(). */
    private static final class Palette extends ContextWrapper {
        final DayPalette palette;

        Palette(Context base, DayPalette palette) {
            super(base);
            this.palette = palette;
        }
    }
}
