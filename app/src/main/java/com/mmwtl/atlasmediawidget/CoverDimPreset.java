package com.mmwtl.atlasmediawidget;

/** Presets for the gradient drawn above the artwork. */
enum CoverDimPreset {
    MINIMUM(0, "Минимум", "minimum",
            new int[]{0x081D2228, 0x001D2228, 0x181D2228, 0x381D2228},
            new int[]{0x0C1D2228, 0x001D2228, 0x201D2228, 0x401D2228}),
    WEAK(1, "Слабо", "weak",
            new int[]{0x201D2228, 0x081D2228, 0x401D2228, 0x801D2228},
            new int[]{0x241D2228, 0x061D2228, 0x481D2228, 0x881D2228}),
    MEDIUM(2, "Средне", "medium",
            new int[]{0x341D2228, 0x101D2228, 0x681D2228, 0xB01D2228},
            new int[]{0x381D2228, 0x081D2228, 0x701D2228, 0xB81D2228}),
    STRONG(3, "Сильно", "strong",
            new int[]{0x481D2228, 0x181D2228, 0x901D2228, 0xD81D2228},
            new int[]{0x4C1D2228, 0x0C1D2228, 0x981D2228, 0xE01D2228}),
    MAXIMUM(4, "Максимум", "maximum",
            new int[]{0x5E1D2228, 0x221D2228, 0xAD1D2228, 0xF51D2228},
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
}
