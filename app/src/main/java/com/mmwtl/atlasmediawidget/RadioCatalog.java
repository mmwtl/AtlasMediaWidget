package com.mmwtl.atlasmediawidget;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class RadioCatalog {
    private static final String BUILT_IN_MANIFEST = "radio/stations.csv";
    private static final String BUILT_IN_COVER_PREFIX = "radio/covers/";

    private final Map<Integer, RadioStation> stations;

    RadioCatalog(Map<Integer, RadioStation> stations) {
        this.stations = Collections.unmodifiableMap(new HashMap<>(stations));
    }

    static RadioCatalog loadBuiltIn(Context context) {
        Map<Integer, RadioStation> stations = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(BUILT_IN_MANIFEST), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                if (line.isBlank()) continue;
                String[] fields = line.split(",", 4);
                if (fields.length != 4) continue;
                int frequency = Integer.parseInt(fields[0].trim());
                String name = fields[1].trim();
                String band = fields[2].trim();
                String cover = fields[3].trim();
                stations.put(frequency, new RadioStation(frequency, name, band,
                        ArtworkRef.asset(BUILT_IN_COVER_PREFIX + cover)));
            }
        } catch (Exception error) {
            AppLog.warn("Cannot load built-in radio catalog", error);
        }
        return new RadioCatalog(stations);
    }

    RadioStation station(int frequencyKHz) {
        return stations.get(frequencyKHz);
    }

    RadioDisplay display(MediaSnapshot snapshot) {
        RadioTuning tuning = RadioTuning.from(snapshot);
        if (tuning == null) return null;
        RadioStation station = station(tuning.frequencyKHz);
        String title = station == null || station.name.isBlank()
                ? MediaPresentation.title(MediaSource.Id.RADIO, snapshot.title)
                : station.name;
        String band = station == null || station.band.isBlank() ? tuning.band : station.band;
        String subtitle = band + " " + tuning.displayFrequency();
        return new RadioDisplay(title, subtitle,
                station == null ? ArtworkRef.NONE : station.artwork);
    }
}
