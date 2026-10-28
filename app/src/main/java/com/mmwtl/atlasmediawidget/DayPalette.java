package com.mmwtl.atlasmediawidget;

/**
 * The user's colours of the light card: its surface (also the cover veil), text and accent.
 * The defaults are the day card_* resources, which stay as they are until a colour changes.
 */
final class DayPalette {
    static final int DEFAULT_BACKGROUND = 0xEDEEF0;
    static final int DEFAULT_TEXT = 0x202124;
    static final int DEFAULT_ACCENT = 0x5F7C89;
    static final DayPalette DEFAULT =
            new DayPalette(DEFAULT_BACKGROUND, DEFAULT_TEXT, DEFAULT_ACCENT);

    /** RGB colours without alpha. */
    final int background;
    final int text;
    final int accent;

    DayPalette(int background, int text, int accent) {
        this.background = background & 0xFFFFFF;
        this.text = text & 0xFFFFFF;
        this.accent = accent & 0xFFFFFF;
    }

    boolean isDefault() {
        return background == DEFAULT_BACKGROUND && text == DEFAULT_TEXT
                && accent == DEFAULT_ACCENT;
    }

    /**
     * The ARGB colour replacing a light card_* resource, or null to keep the resource. Secondary
     * tones are the chosen colour at the opacity that reproduces the default palette's shades.
     */
    Integer override(int id) {
        if (background != DEFAULT_BACKGROUND) {
            if (id == R.color.card_background) return 0xFF000000 | background;
            if (id == R.color.card_panel) return 0xF0000000 | background;
        }
        if (text != DEFAULT_TEXT) {
            if (id == R.color.card_text) return 0xFF000000 | text;
            if (id == R.color.card_text_subtitle) return 0xD9000000 | text;
            if (id == R.color.card_text_secondary) return 0xB3000000 | text;
            if (id == R.color.card_control_disabled) return 0x9E000000 | text;
        }
        if (accent != DEFAULT_ACCENT) {
            if (id == R.color.card_progress || id == R.color.card_control_pressed
                    || id == R.color.card_status || id == R.color.card_option_selected_stroke) {
                return 0xFF000000 | accent;
            }
            if (id == R.color.card_option_selected) return 0x33000000 | accent;
        }
        return null;
    }

    @Override public boolean equals(Object other) {
        return other instanceof DayPalette palette && palette.background == background
                && palette.text == text && palette.accent == accent;
    }

    @Override public int hashCode() {
        return (background * 31 + text) * 31 + accent;
    }
}
