package com.mmwtl.atlasmediawidget;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
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
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(BUILT_IN_MANIFEST), StandardCharsets.UTF_8)) {
            for (RadioCatalogCsv.Row row : RadioCatalogCsv.read(reader)) {
                stations.put(row.frequencyKHz, new RadioStation(row.frequencyKHz,
                        row.name, row.band, ArtworkRef.asset(BUILT_IN_COVER_PREFIX + row.cover)));
            }
        } catch (Exception error) {
            AppLog.warn("Cannot load built-in radio catalog", error);
        }
        return new RadioCatalog(stations);
    }

    static RadioCatalog load(Context context) {
        Map<Integer, RadioStation> stations = new HashMap<>(loadBuiltIn(context).stations);
        String directoryName = new Prefs(context).getString(Prefs.KEY_CUSTOM_RADIO_CATALOG, "");
        File root = RadioCatalogImporter.catalogDirectory(context, directoryName);
        if (root == null) return new RadioCatalog(stations);
        File manifest = new File(root, RadioCatalogImporter.MANIFEST_NAME);
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(manifest), StandardCharsets.UTF_8)) {
            for (RadioCatalogCsv.Row row : RadioCatalogCsv.read(reader)) {
                File cover = row.cover.isBlank() ? null : new File(new File(root, "covers"), row.cover);
                ArtworkRef artwork = cover != null && cover.isFile()
                        ? ArtworkRef.file(cover.getAbsolutePath()) : ArtworkRef.NONE;
                stations.put(row.frequencyKHz, new RadioStation(row.frequencyKHz,
                        row.name, row.band, artwork));
            }
        } catch (Exception error) {
            AppLog.warn("Cannot load custom radio catalog", error);
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
