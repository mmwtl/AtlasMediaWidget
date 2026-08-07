package com.mmwtl.atlasmediawidget;

final class WidgetAppearance {
    final int textGapDp;
    final int controlPanelHeightDp;
    final int controlIconScalePercent;
    final int controlSpreadPercent;
    final int topInsetDp;
    final int contentInsetDp;
    final int topRowTextSizeSp;
    final int titleTextSizeSp;
    final int subtitleTextSizeSp;
    final int subtitleGapDp;
    final int timeTextSizeSp;
    final int progressGapDp;
    final int progressThicknessDp;

    WidgetAppearance(int textGapDp, int controlPanelHeightDp,
            int controlIconScalePercent, int controlSpreadPercent, int topInsetDp,
            int contentInsetDp, int topRowTextSizeSp, int titleTextSizeSp,
            int subtitleTextSizeSp, int subtitleGapDp, int timeTextSizeSp,
            int progressGapDp, int progressThicknessDp) {
        this.textGapDp = textGapDp;
        this.controlPanelHeightDp = controlPanelHeightDp;
        this.controlIconScalePercent = controlIconScalePercent;
        this.controlSpreadPercent = controlSpreadPercent;
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
                0,
                style.defaultControlPanelHeightDp,
                Prefs.DEFAULT_CONTROL_ICON_SCALE_PERCENT,
                Prefs.DEFAULT_CONTROL_SPREAD_PERCENT,
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
