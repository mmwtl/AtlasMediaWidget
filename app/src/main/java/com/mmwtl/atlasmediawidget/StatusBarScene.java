package com.mmwtl.atlasmediawidget;

import java.util.function.UnaryOperator;

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

    /** How the pill text is built; the native radio always shows its station or frequency. */
    enum Format {
        TITLE_FITTED(0, "Название, обрезанное по ширине"),
        TITLE(1, "Название полностью"),
        TITLE_ARTIST(2, "Название — исполнитель"),
        ARTIST_TITLE(3, "Исполнитель — название");

        static final Format DEFAULT = TITLE_FITTED;

        final int preferenceValue;
        final String label;

        Format(int preferenceValue, String label) {
            this.preferenceValue = preferenceValue;
            this.label = label;
        }

        static Format fromPreference(int value) {
            for (Format format : values()) if (format.preferenceValue == value) return format;
            return DEFAULT;
        }
    }

    /**
     * The pill text while the media plays, and null (no pill) while paused, stopped or without
     * media. {@code fit} shortens text to the pill width for {@link Format#TITLE_FITTED}; longer
     * text scrolls in the plugin's marquee.
     */
    static String text(MediaSnapshot snapshot, Format format, UnaryOperator<String> fit) {
        if (snapshot == null || !snapshot.isPlaying()) return null;
        MediaSource.Id source = MediaSource.selectedId(snapshot.audioSource, snapshot.sources);
        String title = clean(snapshot.title);
        String text;
        if (source.displayId() == MediaSource.Id.RADIO) {
            if (!snapshot.backendConnected) return null;
            text = MediaPresentation.title(source, title);
        } else {
            if (title.isEmpty()) return null;
            String artist = clean(snapshot.artist);
            text = switch (format) {
                case TITLE_ARTIST -> artist.isEmpty() ? title : title + " — " + artist;
                case ARTIST_TITLE -> artist.isEmpty() ? title : artist + " — " + title;
                default -> title;
            };
        }
        return format == Format.TITLE_FITTED ? fit.apply(text) : text;
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
