package com.mmwtl.atlasmediawidget;

import android.os.Bundle;

import java.util.Objects;

final class RadioStation {
    final String id;
    final int frequencyKHz;
    final String formattedFrequency;
    final int band;
    final String bandName;
    final String name;
    final String ensembleName;
    final String serviceName;
    final String genre;
    final int iconId;
    final int signalQuality;
    final String selector;
    final boolean favorite;
    final String artworkUri;

    RadioStation(String id, int frequencyKHz, String formattedFrequency, int band,
            String bandName, String name, String ensembleName, String serviceName,
            String genre, int iconId, int signalQuality, String selector,
            boolean favorite, String artworkUri) {
        this.id = nonNull(id);
        this.frequencyKHz = frequencyKHz;
        this.formattedFrequency = nonNull(formattedFrequency);
        this.band = band;
        this.bandName = nonNull(bandName);
        this.name = nonNull(name);
        this.ensembleName = nonNull(ensembleName);
        this.serviceName = nonNull(serviceName);
        this.genre = nonNull(genre);
        this.iconId = iconId;
        this.signalQuality = signalQuality;
        this.selector = nonNull(selector);
        this.favorite = favorite;
        this.artworkUri = nonNull(artworkUri);
    }

    static RadioStation fromBundle(Bundle bundle) {
        if (bundle == null) return null;
        int frequency = bundle.getInt(MediaBridgeContract.K_RADIO_FREQUENCY_KHZ, -1);
        if (!isSupportedFrequency(frequency)) return null;
        String id = bundle.getString(MediaBridgeContract.K_RADIO_STATION_ID, "");
        int band = bundle.getInt(MediaBridgeContract.K_RADIO_BAND);
        if (id.isBlank()) id = band + ":" + frequency + ":"
                + bundle.getString(MediaBridgeContract.K_RADIO_SELECTOR, "");
        return new RadioStation(
                id, frequency,
                bundle.getString(MediaBridgeContract.K_RADIO_FORMATTED_FREQUENCY, ""),
                band, bundle.getString(MediaBridgeContract.K_RADIO_BAND_NAME, ""),
                bundle.getString(MediaBridgeContract.K_RADIO_NAME, ""),
                bundle.getString(MediaBridgeContract.K_RADIO_ENSEMBLE_NAME, ""),
                bundle.getString(MediaBridgeContract.K_RADIO_SERVICE_NAME, ""),
                bundle.getString(MediaBridgeContract.K_RADIO_GENRE, ""),
                bundle.getInt(MediaBridgeContract.K_RADIO_ICON_ID),
                bundle.getInt(MediaBridgeContract.K_RADIO_SIGNAL_QUALITY),
                bundle.getString(MediaBridgeContract.K_RADIO_SELECTOR, ""),
                bundle.getBoolean(MediaBridgeContract.K_RADIO_FAVORITE),
                bundle.getString(MediaBridgeContract.K_RADIO_ARTWORK_URI, ""));
    }

    String displayName() {
        if (!name.isBlank()) return name;
        if (!serviceName.isBlank()) return serviceName;
        if (!formattedFrequency.isBlank()) return formattedFrequency;
        return Integer.toString(frequencyKHz);
    }

    String displayDetail() {
        if (!formattedFrequency.isBlank()) return formattedFrequency;
        return bandName;
    }

    String artworkKey() {
        return id + '|' + artworkUri;
    }

    private static boolean isSupportedFrequency(int value) {
        return value >= 500 && value <= 1_800
                || value >= 8_750 && value <= 10_800
                || value >= 87_500 && value <= 108_000
                || value >= 174_000 && value <= 240_000;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RadioStation station)) return false;
        return id.equals(station.id) && frequencyKHz == station.frequencyKHz
                && band == station.band && selector.equals(station.selector);
    }

    @Override public int hashCode() {
        return Objects.hash(id, frequencyKHz, band, selector);
    }
}
