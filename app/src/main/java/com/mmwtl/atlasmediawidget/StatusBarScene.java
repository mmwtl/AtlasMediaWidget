package com.mmwtl.atlasmediawidget;

/**
 * Pure part of the status bar output: the pill text for a snapshot and the scene open-state
 * notifications that move the OEM SystemUI plugin from what it shows to that text.
 */
final class StatusBarScene {
    static final int[] NONE = {};
    private static final int[] OPEN = {1};
    private static final int[] CLOSE = {0};
    // The plugin keeps its model while the scene stays open, so a new name needs a reopen.
    private static final int[] REOPEN = {0, 1};

    private String shown;

    /**
     * "Artist — Title" while the media plays, the station or frequency for the native radio, and
     * null (no pill) while paused, stopped or without media.
     */
    static String text(MediaSnapshot snapshot) {
        if (snapshot == null || !snapshot.isPlaying()) return null;
        MediaSource.Id source = MediaSource.selectedId(snapshot.audioSource, snapshot.sources);
        String title = clean(snapshot.title);
        if (source.displayId() == MediaSource.Id.RADIO) {
            return snapshot.backendConnected ? MediaPresentation.title(source, title) : null;
        }
        if (title.isEmpty()) return null;
        String artist = clean(snapshot.artist);
        return artist.isEmpty() ? title : artist + " — " + title;
    }

    /** Records the wanted text (null closes the scene) and returns the open states to send. */
    int[] show(String text) {
        String previous = shown;
        shown = text;
        if (text == null) return previous == null ? NONE : CLOSE;
        if (previous == null) return OPEN;
        return previous.equals(text) ? NONE : REOPEN;
    }

    String shown() {
        return shown;
    }

    private static String clean(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
