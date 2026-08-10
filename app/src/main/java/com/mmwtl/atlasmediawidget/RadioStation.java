package com.mmwtl.atlasmediawidget;

final class RadioStation {
    final int frequencyKHz;
    final String name;
    final String band;
    final ArtworkRef artwork;

    RadioStation(int frequencyKHz, String name, String band, ArtworkRef artwork) {
        this.frequencyKHz = frequencyKHz;
        this.name = name;
        this.band = band;
        this.artwork = artwork == null ? ArtworkRef.NONE : artwork;
    }
}
