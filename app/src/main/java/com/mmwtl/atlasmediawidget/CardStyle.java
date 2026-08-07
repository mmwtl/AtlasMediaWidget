package com.mmwtl.atlasmediawidget;

enum CardStyle {
    COMPACT(0, "Прямоугольный", 500, 300),
    SQUARE(1, "Квадратный", 500, 500);

    static final CardStyle DEFAULT = SQUARE;

    final int preferenceValue;
    final String label;
    final int defaultWidthDp;
    final int defaultHeightDp;

    CardStyle(int preferenceValue, String label, int defaultWidthDp, int defaultHeightDp) {
        this.preferenceValue = preferenceValue;
        this.label = label;
        this.defaultWidthDp = defaultWidthDp;
        this.defaultHeightDp = defaultHeightDp;
    }

    static CardStyle fromPreference(int value) {
        for (CardStyle style : values()) {
            if (style.preferenceValue == value) return style;
        }
        return DEFAULT;
    }
}
