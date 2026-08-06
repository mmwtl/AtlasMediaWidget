package com.mmwtl.atlasmediawidget;

enum CardStyle {
    COMPACT(0, "Компактный"),
    SQUARE(1, "Квадратный");

    static final CardStyle DEFAULT = SQUARE;

    final int preferenceValue;
    final String label;

    CardStyle(int preferenceValue, String label) {
        this.preferenceValue = preferenceValue;
        this.label = label;
    }

    static CardStyle fromPreference(int value) {
        for (CardStyle style : values()) {
            if (style.preferenceValue == value) return style;
        }
        return DEFAULT;
    }
}
