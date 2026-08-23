package com.mmwtl.atlasmediawidget;

import java.util.List;
import java.util.Locale;

final class RadioStationNavigator {
    private RadioStationNavigator() {}

    static RadioStation adjacent(List<RadioStation> stations, MediaSnapshot snapshot,
            int direction) {
        if (stations == null || stations.isEmpty() || direction == 0) return null;
        int current = currentIndex(stations, snapshot);
        if (current < 0) return direction > 0 ? stations.get(0) : stations.get(stations.size() - 1);
        if (stations.size() == 1) return null;
        int next = Math.floorMod(current + Integer.signum(direction), stations.size());
        return stations.get(next);
    }

    static int currentIndex(List<RadioStation> stations, MediaSnapshot snapshot) {
        if (snapshot == null) return -1;
        int[] radioId = parseMediaId(snapshot.mediaId);
        if (radioId != null) {
            for (int index = 0; index < stations.size(); index++) {
                RadioStation station = stations.get(index);
                if (station.band == radioId[0] && station.frequencyKHz == radioId[1]) return index;
            }
            for (int index = 0; index < stations.size(); index++) {
                if (stations.get(index).frequencyKHz == radioId[1]) return index;
            }
        }
        String title = normalize(snapshot.title);
        String artist = normalize(snapshot.artist);
        for (int index = 0; index < stations.size(); index++) {
            RadioStation station = stations.get(index);
            if (matches(title, station.name) || matches(title, station.serviceName)
                    || matches(title, station.formattedFrequency)
                    || matches(artist, station.formattedFrequency)) return index;
        }
        return -1;
    }

    private static int[] parseMediaId(String mediaId) {
        if (mediaId == null || !mediaId.startsWith("radio:")) return null;
        String[] parts = mediaId.split(":", 4);
        if (parts.length < 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean matches(String normalized, String candidate) {
        return !normalized.isEmpty() && normalized.equals(normalize(candidate));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
