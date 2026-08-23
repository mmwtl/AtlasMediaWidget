package com.mmwtl.atlasmediawidget;

import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

final class RadioStationLists {
    static final RadioStationLists EMPTY = new RadioStationLists(0L, List.of(), List.of());

    final long generation;
    final List<RadioStation> saved;
    final List<RadioStation> favorites;

    RadioStationLists(long generation, List<RadioStation> saved,
            List<RadioStation> favorites) {
        this.generation = generation;
        this.saved = immutableDeduplicated(saved);
        this.favorites = immutableDeduplicated(favorites);
    }

    static RadioStationLists fromBundle(Bundle bundle) {
        int version = bundle.getInt(MediaBridgeContract.K_VERSION, -1);
        if (version != MediaBridgeContract.VERSION) {
            throw new IllegalArgumentException("Unsupported radio list protocolVersion=" + version);
        }
        return new RadioStationLists(
                bundle.getLong(MediaBridgeContract.K_GENERATION),
                parse(bundle, MediaBridgeContract.K_RADIO_SAVED_STATIONS),
                parse(bundle, MediaBridgeContract.K_RADIO_FAVORITE_STATIONS));
    }

    private static List<RadioStation> parse(Bundle bundle, String key) {
        ArrayList<Bundle> raw = Build.VERSION.SDK_INT >= 33
                ? bundle.getParcelableArrayList(key, Bundle.class)
                : legacyList(bundle, key);
        if (raw == null || raw.isEmpty()) return List.of();
        ArrayList<RadioStation> result = new ArrayList<>(raw.size());
        for (Bundle item : raw) {
            RadioStation station = RadioStation.fromBundle(item);
            if (station != null) result.add(station);
        }
        return result;
    }

    private static List<RadioStation> immutableDeduplicated(List<RadioStation> source) {
        LinkedHashMap<String, RadioStation> unique = new LinkedHashMap<>();
        if (source != null) {
            for (RadioStation station : source) {
                if (station != null) unique.putIfAbsent(station.id, station);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<Bundle> legacyList(Bundle bundle, String key) {
        return bundle.getParcelableArrayList(key);
    }
}
