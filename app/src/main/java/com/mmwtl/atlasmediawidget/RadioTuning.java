package com.mmwtl.atlasmediawidget;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RadioTuning {
    private static final Pattern MEDIA_ID = Pattern.compile("^radio:([^:]*):(\\d+)(?::.*)?$");

    final String band;
    final int frequencyKHz;

    RadioTuning(String band, int frequencyKHz) {
        this.band = normalizeBand(band, frequencyKHz);
        this.frequencyKHz = normalizeFrequency(frequencyKHz);
    }

    static RadioTuning from(MediaSnapshot snapshot) {
        if (snapshot == null || snapshot.audioSource.displayId() != MediaSource.Id.RADIO) {
            return null;
        }
        Matcher matcher = MEDIA_ID.matcher(snapshot.mediaId);
        if (!matcher.matches()) return null;
        try {
            int rawFrequency = Integer.parseInt(matcher.group(2));
            int frequencyKHz = normalizeFrequency(rawFrequency);
            if (frequencyKHz <= 0) return null;
            return new RadioTuning(matcher.group(1), frequencyKHz);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    String displayFrequency() {
        if ("FM".equals(band)) {
            return String.format(Locale.US, "%.1f", frequencyKHz / 1_000d);
        }
        return Integer.toString(frequencyKHz);
    }

    String subtitle() {
        return band + " " + displayFrequency();
    }

    private static int normalizeFrequency(int value) {
        if (value >= 8_750 && value <= 10_800) return value * 10;
        if (value >= 87_500_000 && value <= 108_000_000) return value / 1_000;
        if (value >= 87_500 && value <= 108_000) return value;
        if (value >= 500 && value <= 1_800) return value;
        return -1;
    }

    private static String normalizeBand(String value, int frequency) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("FM")) return "FM";
        if (normalized.contains("AM")) return "AM";
        int normalizedFrequency = normalizeFrequency(frequency);
        return normalizedFrequency >= 87_500 ? "FM" : "AM";
    }
}
