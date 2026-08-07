package com.mmwtl.atlasmediawidget;

final class MediaPresentation {
    private MediaPresentation() {}

    static boolean hasContent(MediaSource.Id source, boolean bridgeConnected,
            boolean backendConnected, String title, String artist, String album, long duration) {
        boolean metadataPresent = !title.isBlank() || !artist.isBlank()
                || !album.isBlank() || duration > 0L;
        return metadataPresent || source.displayId() == MediaSource.Id.RADIO
                && bridgeConnected && backendConnected;
    }

    static String title(MediaSource.Id source, String value) {
        if (!value.isBlank()) return value;
        return source.displayId() == MediaSource.Id.RADIO ? "Радио" : "";
    }

    static String subtitle(MediaSource.Id source, String artist, String album) {
        String detail = artist;
        if (!album.isBlank()) detail = detail.isBlank() ? album : detail + "  •  " + album;
        if (detail.isBlank() && source.displayId() == MediaSource.Id.RADIO) {
            return "Штатный радиоприёмник";
        }
        return detail;
    }
}
