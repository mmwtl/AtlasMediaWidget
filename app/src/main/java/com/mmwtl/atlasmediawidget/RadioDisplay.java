package com.mmwtl.atlasmediawidget;

final class RadioDisplay {
    final String title;
    final String subtitle;
    final ArtworkRef artwork;

    RadioDisplay(String title, String subtitle, ArtworkRef artwork) {
        this.title = title;
        this.subtitle = subtitle;
        this.artwork = artwork == null ? ArtworkRef.NONE : artwork;
    }
}
