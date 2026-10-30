package com.mmwtl.atlasmediawidget;

/**
 * The user's colours of the day or night card: its surface (also the cover veil), text and
 * accent. The defaults are the card_* resources of that mode, which stay as they are until a
 * colour changes.
 */
final class CardPalette {
    static final CardPalette DAY = new CardPalette(false, 0xEDEEF0, 0x202124, 0x5F7C89);
    static final CardPalette NIGHT = new CardPalette(true, 0x232528, 0xF5F5F5, 0x83AFC2);

    final boolean night;
    /** RGB colours without alpha. */
    final int background;
    final int text;
    final int accent;

    CardPalette(boolean night, int background, int text, int accent) {
        this.night = night;
        this.background = background & 0xFFFFFF;
        this.text = text & 0xFFFFFF;
        this.accent = accent & 0xFFFFFF;
    }

    static CardPalette defaults(boolean night) {
        return night ? NIGHT : DAY;
    }

    CardPalette with(int background, int text, int accent) {
        return new CardPalette(night, background, text, accent);
    }

    boolean isDefault() {
        return equals(defaults(night));
    }

    /**
     * This palette for content drawn in the other mode's resources: the colours the user chose
     * stay, and the ones left at the default take the default of that mode.
     */
    CardPalette forContent(boolean contentNight) {
        if (contentNight == night) return this;
        CardPalette from = defaults(night);
        CardPalette to = defaults(contentNight);
        return new CardPalette(contentNight,
                background == from.background ? to.background : background,
                text == from.text ? to.text : text,
                accent == from.accent ? to.accent : accent);
    }

    /**
     * The ARGB colour replacing a card_* resource of this mode, or null to keep the resource.
     * Secondary tones are the chosen colour at the opacity that reproduces the default shades.
     */
    Integer override(int id) {
        CardPalette defaults = defaults(night);
        if (background != defaults.background) {
            if (id == R.color.card_background) return 0xFF000000 | background;
            if (id == R.color.card_panel) return 0xF0000000 | background;
        }
        if (text != defaults.text) {
            if (id == R.color.card_text) return 0xFF000000 | text;
            // Night text is light on a dark surface, so its secondary tones swap their weights.
            if (id == R.color.card_text_subtitle) return (night ? 0xB3 : 0xD9) << 24 | text;
            if (id == R.color.card_text_secondary) return (night ? 0xD9 : 0xB3) << 24 | text;
            if (id == R.color.card_control_disabled) return 0x9E000000 | text;
        }
        if (accent != defaults.accent) {
            if (id == R.color.card_progress || id == R.color.card_control_pressed
                    || id == R.color.card_status || id == R.color.card_option_selected_stroke) {
                return 0xFF000000 | accent;
            }
            if (id == R.color.card_option_selected) return (night ? 0x59 : 0x33) << 24 | accent;
        }
        return null;
    }

    @Override public boolean equals(Object other) {
        return other instanceof CardPalette palette && palette.night == night
                && palette.background == background && palette.text == text
                && palette.accent == accent;
    }

    @Override public int hashCode() {
        return ((Boolean.hashCode(night) * 31 + background) * 31 + text) * 31 + accent;
    }
}
