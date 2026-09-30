package com.mmwtl.atlasmediawidget;

/**
 * Tuning scale for the current radio frequency. The frequency comes from the runtime's radio
 * media ID ({@code radio:<band>:<frequency>:<service>}); the raw ranges mirror the Media Bridge
 * frequency allowlist, and the scale edges are the conventional band limits.
 */
final class RadioBandScale {
    final int min;
    final int max;
    final int value;
    final int minorStep;
    final int majorStep;
    final String minLabel;
    final String maxLabel;

    private RadioBandScale(int min, int max, int value, int minorStep, int majorStep,
            int labelDivisor) {
        this.min = min;
        this.max = max;
        this.value = value;
        this.minorStep = minorStep;
        this.majorStep = majorStep;
        this.minLabel = format(min, labelDivisor);
        this.maxLabel = format(max, labelDivisor);
    }

    /** Returns the scale for a runtime radio media ID, or null when it carries no known frequency. */
    static RadioBandScale fromMediaId(String mediaId) {
        if (mediaId == null || !mediaId.startsWith("radio:")) return null;
        String[] parts = mediaId.split(":", 4);
        if (parts.length < 3) return null;
        int frequency;
        try {
            frequency = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (frequency >= 149 && frequency <= 284) {
            return new RadioBandScale(153, 279, frequency, 18, 126, 1);
        }
        if (frequency >= 500 && frequency <= 1_800) {
            return new RadioBandScale(522, 1_620, frequency, 100, 500, 1);
        }
        if (frequency >= 8_750 && frequency <= 10_800) {
            return new RadioBandScale(8_750, 10_800, frequency, 100, 500, 100);
        }
        if (frequency >= 65_800 && frequency <= 74_000) {
            return new RadioBandScale(65_800, 74_000, frequency, 1_000, 5_000, 1_000);
        }
        if (frequency >= 87_500 && frequency <= 108_000) {
            return new RadioBandScale(87_500, 108_000, frequency, 1_000, 5_000, 1_000);
        }
        if (frequency >= 174_000 && frequency <= 240_000) {
            return new RadioBandScale(174_000, 240_000, frequency, 5_000, 20_000, 1_000);
        }
        return null;
    }

    float fraction() { return fractionOf(value); }

    float fractionOf(int frequency) {
        float fraction = (frequency - min) / (float) (max - min);
        return Math.max(0f, Math.min(1f, fraction));
    }

    /** First tick at or above the lower edge; ticks follow every {@link #minorStep}. */
    int firstTick() {
        return (min + minorStep - 1) / minorStep * minorStep;
    }

    boolean isMajor(int tick) {
        return tick % majorStep == 0;
    }

    private static String format(int value, int divisor) {
        int whole = value / divisor;
        int remainder = value % divisor;
        if (remainder == 0) return Integer.toString(whole);
        String fraction = Integer.toString(divisor + remainder).substring(1);
        while (fraction.endsWith("0")) fraction = fraction.substring(0, fraction.length() - 1);
        return whole + "." + fraction;
    }
}
