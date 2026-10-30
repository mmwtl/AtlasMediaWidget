package com.mmwtl.atlasmediawidget;

/** What fills the compact card behind its content: the artwork or one translucent colour. */
final class CardBackdrop {
    /** A colour left to the theme: the card surface of the mode; never an RGB value. */
    static final int THEME_COLOR = -1;
    /** What earlier versions stored for a backdrop nobody recoloured. */
    static final int LEGACY_DEFAULT_COLOR = 0x262626;
    static final int DEFAULT_ALPHA = 235;
    static final CardBackdrop ARTWORK =
            new CardBackdrop(false, THEME_COLOR, DEFAULT_ALPHA, false, false);

    /** Whether a single colour replaces the artwork backdrop. */
    final boolean solid;
    /** RGB colour without alpha, or {@link #THEME_COLOR}. */
    final int color;
    /** Opacity of {@link #color}, 0…255. */
    final int alpha;
    /** Whether the control panel keeps {@link #color} at full opacity. */
    final boolean opaqueControls;
    /** Whether the source and favorites row keeps {@link #color} at full opacity. */
    final boolean opaqueTopRow;
    /** RGB colour of the day mode without alpha, or {@link #THEME_COLOR}. */
    final int dayColor;
    /** Opacity of {@link #dayColor}, 0…255. */
    final int dayAlpha;

    CardBackdrop(boolean solid, int color, int alpha, boolean opaqueControls,
            boolean opaqueTopRow) {
        this(solid, color, alpha, opaqueControls, opaqueTopRow, color, alpha);
    }

    CardBackdrop(boolean solid, int color, int alpha, boolean opaqueControls,
            boolean opaqueTopRow, int dayColor, int dayAlpha) {
        this.solid = solid;
        this.color = rgbOrTheme(color);
        this.alpha = Math.max(0, Math.min(255, alpha));
        this.opaqueControls = opaqueControls;
        this.opaqueTopRow = opaqueTopRow;
        this.dayColor = rgbOrTheme(dayColor);
        this.dayAlpha = Math.max(0, Math.min(255, dayAlpha));
    }

    private static int rgbOrTheme(int color) {
        return color == THEME_COLOR ? THEME_COLOR : color & 0x00FFFFFF;
    }

    /** The colour an earlier version stored for what is now left to the theme. */
    static int fromLegacyColor(int color) {
        return color == LEGACY_DEFAULT_COLOR ? THEME_COLOR : color;
    }

    /** The backdrop of the day mode. */
    CardBackdrop forDay() {
        return new CardBackdrop(solid, dayColor, dayAlpha, opaqueControls, opaqueTopRow);
    }

    boolean themed() {
        return color == THEME_COLOR;
    }

    /** The RGB colour, with the given surface for a colour left to the theme. */
    int rgb(int themeSurface) {
        return themed() ? themeSurface & 0x00FFFFFF : color;
    }

    /** Only the compact layout offers the single-colour backdrop. */
    boolean solidFor(CardStyle style) {
        return solid && style == CardStyle.COMPACT;
    }

    /** Whether whatever lies under the card shows through it. */
    boolean translucentFor(CardStyle style) {
        return solidFor(style) && alpha < 255;
    }

    /** Whether the control panel needs its own opaque band over the translucent backdrop. */
    boolean opaqueControlsFor(CardStyle style) {
        return opaqueControls && translucentFor(style);
    }

    /** Whether the top row needs its own opaque band over the translucent backdrop. */
    boolean opaqueTopRowFor(CardStyle style) {
        return opaqueTopRow && translucentFor(style);
    }

    int argb(int themeSurface) {
        return alpha << 24 | rgb(themeSurface);
    }
}
