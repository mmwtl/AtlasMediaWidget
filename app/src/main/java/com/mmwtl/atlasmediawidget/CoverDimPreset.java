package com.mmwtl.atlasmediawidget;

/** Presets for the gradient drawn above the artwork. */
enum CoverDimPreset {
    MINIMUM(0, "Минимум", "minimum",
            new int[]{0x221D2228, 0x141D2228, 0x301D2228, 0x481D2228},
            new int[]{0x0C1D2228, 0x001D2228, 0x201D2228, 0x401D2228}),
    WEAK(1, "Слабо", "weak",
            new int[]{0x481D2228, 0x381D2228, 0x601D2228, 0x901D2228},
            new int[]{0x241D2228, 0x061D2228, 0x481D2228, 0x881D2228}),
    MEDIUM(2, "Средне", "medium",
            new int[]{0x681D2228, 0x581D2228, 0x881D2228, 0xB81D2228},
            new int[]{0x381D2228, 0x081D2228, 0x701D2228, 0xB81D2228}),
    STRONG(3, "Сильно", "strong",
            new int[]{0x881D2228, 0x781D2228, 0xA81D2228, 0xD81D2228},
            new int[]{0x4C1D2228, 0x0C1D2228, 0x981D2228, 0xE01D2228}),
    MAXIMUM(4, "Максимум", "maximum",
            new int[]{0xA81D2228, 0x981D2228, 0xC81D2228, 0xF51D2228},
            new int[]{0x601D2228, 0x101D2228, 0xB01D2228, 0xFA1D2228});

    static final CoverDimPreset DEFAULT = MEDIUM;

    final int preferenceValue;
    final String label;
    final String backupName;
    private final int[] compactColors;
    private final int[] squareColors;

    CoverDimPreset(int preferenceValue, String label, String backupName,
            int[] compactColors, int[] squareColors) {
        this.preferenceValue = preferenceValue;
        this.label = label;
        this.backupName = backupName;
        this.compactColors = compactColors;
        this.squareColors = squareColors;
    }

    static CoverDimPreset fromPreference(int value) {
        for (CoverDimPreset preset : values()) {
            if (preset.preferenceValue == value) return preset;
        }
        return DEFAULT;
    }

    static CoverDimPreset fromBackupName(String value) {
        for (CoverDimPreset preset : values()) {
            if (preset.backupName.equals(value)) return preset;
        }
        return null;
    }

    int[] colors(CardStyle style) {
        return style == CardStyle.COMPACT ? compactColors : squareColors;
    }

    /**
     * The preset's gradient in {@code rgb}, the card surface of the current day or night mode.
     * Covers are mostly dark, so the dark text of a light surface needs a denser veil than the
     * shadowed light text of the night card; each preset keeps its relative strength.
     * The compact card has text across its whole height and gets a uniformly denser veil. The
     * square card keeps its text at the edges: there the veil grows with its own density, so the
     * clear middle of the artwork stays as clear as at night instead of a 40 % white floor.
     */
    int[] colors(CardStyle style, int rgb) {
        boolean light = !CardTheme.isDark(rgb);
        int[] colors = colors(style).clone();
        for (int index = 0; index < colors.length; index++) {
            int alpha = colors[index] >>> 24;
            if (light) alpha = lightAlpha(style, alpha);
            colors[index] = alpha << 24 | rgb & 0xFFFFFF;
        }
        return colors;
    }

    private static int lightAlpha(CardStyle style, int alpha) {
        if (style == CardStyle.COMPACT) return 255 - (255 - alpha) * 3 / 5;
        return alpha + (255 - alpha) * alpha * 3 / (255 * 5);
    }
}
