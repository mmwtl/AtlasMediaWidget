package com.mmwtl.atlasmediawidget;

enum CardStyle {
    COMPACT(0, "Прямоугольный", 500, 300, 84),
    SQUARE(1, "Квадратный", 500, 500, 102);

    static final CardStyle DEFAULT = SQUARE;

    final int preferenceValue;
    final String label;
    final int defaultWidthDp;
    final int defaultHeightDp;
    final int defaultControlPanelHeightDp;

    CardStyle(int preferenceValue, String label, int defaultWidthDp, int defaultHeightDp,
            int defaultControlPanelHeightDp) {
        this.preferenceValue = preferenceValue;
        this.label = label;
        this.defaultWidthDp = defaultWidthDp;
        this.defaultHeightDp = defaultHeightDp;
        this.defaultControlPanelHeightDp = defaultControlPanelHeightDp;
    }

    static CardStyle fromPreference(int value) {
        for (CardStyle style : values()) {
            if (style.preferenceValue == value) return style;
        }
        return DEFAULT;
    }
}
