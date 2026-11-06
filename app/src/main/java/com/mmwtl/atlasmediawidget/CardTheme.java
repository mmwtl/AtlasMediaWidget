package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;

/**
 * Picks the day or night card colours, which the resources hold in values and values-night.
 * The card is drawn into bitmaps, so the caller chooses the mode through the context it passes.
 */
final class CardTheme {
    /** Values of {@link Prefs#KEY_WIDGET_THEME}: the system mode or a fixed day or night. */
    static final int AUTO = 0;
    static final int DAY = 1;
    static final int NIGHT = 2;

    private CardTheme() {}

    /**
     * The context an AppWidget is drawn in: the system night mode for {@link #AUTO}, otherwise
     * the theme fixed in the widget's settings, whatever the system mode.
     */
    static Context forWidget(Context base, int theme) {
        return theme == AUTO ? base : withNight(base, theme == NIGHT);
    }

    /** The overlay keeps the night colours over the OEM launcher in either system mode. */
    static Context overlay(Context base, CardColors colors) {
        return content(base, colors.night, colors.night.background);
    }

    /** A context of the given mode, for previewing the widget card in either mode. */
    static Context mode(Context base, boolean night) {
        return withNight(base, night);
    }

    /**
     * An AppWidget follows the system night mode, as its launcher does, in the palette of that
     * mode. Content takes the resources that stay readable on the surface actually shown: a
     * picked single-colour backdrop or a picked card colour can be dark by day or light at night.
     */
    static Context widget(Context base, CardStyle style, WidgetAppearance appearance,
            CardColors colors) {
        CardPalette palette = colors.palette(isNight(base.getResources().getConfiguration()));
        CardBackdrop backdrop = appearance.backdrop;
        return content(base, palette, backdrop.solid && !backdrop.themed()
                ? backdrop.color : palette.background);
    }

    private static Context content(Context base, CardPalette palette, int surface) {
        boolean night = isDark(surface);
        Context themed = withNight(base, night);
        CardPalette shown = palette.forContent(night);
        return shown.isDefault() ? themed : new Palette(themed, shown);
    }

    /** The appearance an AppWidget shows in the mode of {@code base}: by day its day backdrop. */
    static WidgetAppearance widgetAppearance(Context base, WidgetAppearance appearance) {
        return isNight(base.getResources().getConfiguration()) ? appearance
                : appearance.withBackdrop(appearance.backdrop.forDay());
    }

    /** A card_* colour of the context's mode, with the user's palette of that mode applied. */
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

    /** Carries the palette to the card views, which read their colours through color(). */
    private static final class Palette extends ContextWrapper {
        final CardPalette palette;

        Palette(Context base, CardPalette palette) {
            super(base);
            this.palette = palette;
        }
    }
}
