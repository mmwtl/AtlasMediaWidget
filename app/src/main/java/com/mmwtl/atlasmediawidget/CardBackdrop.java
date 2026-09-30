package com.mmwtl.atlasmediawidget;

/** What fills the compact card behind its content: the artwork or one translucent colour. */
final class CardBackdrop {
    /** The graphite of {@link Ui#BACKGROUND}. */
    static final int DEFAULT_COLOR = 0x1D2228;
    static final int DEFAULT_ALPHA = 235;
    static final CardBackdrop ARTWORK =
            new CardBackdrop(false, DEFAULT_COLOR, DEFAULT_ALPHA, false, false);

    /** Whether a single colour replaces the artwork backdrop. */
    final boolean solid;
    /** RGB colour without alpha. */
    final int color;
    /** Opacity of {@link #color}, 0…255. */
    final int alpha;
    /** Whether the control panel keeps {@link #color} at full opacity. */
    final boolean opaqueControls;
    /** Whether the source and favorites row keeps {@link #color} at full opacity. */
    final boolean opaqueTopRow;

    CardBackdrop(boolean solid, int color, int alpha, boolean opaqueControls,
            boolean opaqueTopRow) {
        this.solid = solid;
        this.color = color & 0x00FFFFFF;
        this.alpha = Math.max(0, Math.min(255, alpha));
        this.opaqueControls = opaqueControls;
        this.opaqueTopRow = opaqueTopRow;
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

    int argb() {
        return alpha << 24 | color;
    }
}
