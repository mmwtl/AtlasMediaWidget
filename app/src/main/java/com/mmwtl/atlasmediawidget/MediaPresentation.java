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

    /**
     * Media without a duration is a live stream; it keeps the progress row in place so the text
     * above does not shift. The native radio shows its tuning scale there instead.
     */
    static boolean isLiveStream(MediaSource.Id source, boolean hasMedia, long duration) {
        return hasMedia && duration <= 0L && source.displayId() != MediaSource.Id.RADIO;
    }

    static boolean isBackendAvailable(MediaSource.Id source, boolean backendConnected,
            boolean hasMedia) {
        if (backendConnected) return true;
        if (source.displayId() == MediaSource.Id.ONLINE) return true;
        return hasMedia && (source.displayId() == MediaSource.Id.UNKNOWN
                || source.displayId() == MediaSource.Id.OTHER);
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
