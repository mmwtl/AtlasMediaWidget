package com.mmwtl.atlasmediawidget;

/** The day and night palettes of the card; the overlay always uses the night one. */
final class CardColors {
    static final CardColors DEFAULT = new CardColors(CardPalette.DAY, CardPalette.NIGHT);

    final CardPalette day;
    final CardPalette night;

    CardColors(CardPalette day, CardPalette night) {
        if (day.night || !night.night) throw new IllegalArgumentException("Palette mode mismatch");
        this.day = day;
        this.night = night;
    }

    CardPalette palette(boolean night) {
        return night ? this.night : day;
    }

    CardColors with(CardPalette palette) {
        return palette.night ? new CardColors(day, palette) : new CardColors(palette, night);
    }

    @Override public boolean equals(Object other) {
        return other instanceof CardColors colors && colors.day.equals(day)
                && colors.night.equals(night);
    }

    @Override public int hashCode() {
        return day.hashCode() * 31 + night.hashCode();
    }
}
