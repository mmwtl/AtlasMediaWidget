package com.mmwtl.atlasmediawidget;

final class WidgetAppearance {
    final int metadataProgressGapDp;
    final int controlPanelHeightDp;
    final int controlIconScalePercent;
    final int controlSpreadPercent;
    final int controlBottomInsetDp;
    final int topInsetDp;
    final int contentInsetDp;
    final int topRowTextSizeSp;
    final int titleTextSizeSp;
    final int subtitleTextSizeSp;
    final int subtitleGapDp;
    final int timeTextSizeSp;
    final int progressGapDp;
    final int progressThicknessDp;

    WidgetAppearance(int metadataProgressGapDp, int controlPanelHeightDp,
            int controlIconScalePercent, int controlSpreadPercent, int controlBottomInsetDp,
            int topInsetDp, int contentInsetDp, int topRowTextSizeSp, int titleTextSizeSp,
            int subtitleTextSizeSp, int subtitleGapDp, int timeTextSizeSp,
            int progressGapDp, int progressThicknessDp) {
        this.metadataProgressGapDp = metadataProgressGapDp;
        this.controlPanelHeightDp = controlPanelHeightDp;
        this.controlIconScalePercent = controlIconScalePercent;
        this.controlSpreadPercent = controlSpreadPercent;
        this.controlBottomInsetDp = controlBottomInsetDp;
        this.topInsetDp = topInsetDp;
        this.contentInsetDp = contentInsetDp;
        this.topRowTextSizeSp = topRowTextSizeSp;
        this.titleTextSizeSp = titleTextSizeSp;
        this.subtitleTextSizeSp = subtitleTextSizeSp;
        this.subtitleGapDp = subtitleGapDp;
        this.timeTextSizeSp = timeTextSizeSp;
        this.progressGapDp = progressGapDp;
        this.progressThicknessDp = progressThicknessDp;
    }

    static WidgetAppearance defaults(CardStyle style) {
        boolean compact = style == CardStyle.COMPACT;
        return new WidgetAppearance(
                14,
                style.defaultControlPanelHeightDp,
                Prefs.DEFAULT_CONTROL_ICON_SCALE_PERCENT,
                Prefs.DEFAULT_CONTROL_SPREAD_PERCENT,
                0,
                compact ? 14 : 17,
                compact ? 24 : 30,
                compact ? 12 : 13,
                compact ? 22 : 32,
                compact ? 15 : 20,
                5,
                compact ? 11 : 14,
                0,
                6);
    }
}
