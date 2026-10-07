package com.mmwtl.atlasmediawidget;

/**
 * Counts how long a live stream (online, CarPlay or Bluetooth radio without a duration) has been
 * playing on this device. The stream reports no usable position, so the card shows this time in
 * place of a track position. Pauses stop the count; another source or player starts it over.
 */
final class LiveListeningClock {
    private String streamKey;
    private long accumulatedMs;
    private long playingSinceMs = -1L;

    /** Identifies the live stream of a snapshot, or returns null when it is not one. */
    static String streamKey(MediaSnapshot snapshot) {
        if (snapshot == null) return null;
        MediaSource.Id source = MediaSource.selectedId(snapshot.audioSource, snapshot.sources);
        boolean hasMedia = MediaPresentation.hasContent(source, true, snapshot.backendConnected,
                snapshot.title, snapshot.artist, snapshot.album, snapshot.duration);
        if (!MediaPresentation.isLiveStream(source, hasMedia, snapshot.duration)) return null;
        return source.displayId().name() + '|' + snapshot.ownerPackage;
    }

    /** Returns the listening time of the snapshot's live stream, or -1 when it is not one. */
    long elapsed(MediaSnapshot snapshot, long nowElapsedRealtime) {
        return observe(streamKey(snapshot), snapshot != null && snapshot.isPlaying(),
                nowElapsedRealtime);
    }

    synchronized long observe(String key, boolean playing, long nowElapsedRealtime) {
        if (key == null) {
            streamKey = null;
            accumulatedMs = 0L;
            playingSinceMs = -1L;
            return -1L;
        }
        if (!key.equals(streamKey)) {
            streamKey = key;
            accumulatedMs = 0L;
            playingSinceMs = -1L;
        }
        if (playing && playingSinceMs < 0L) {
            playingSinceMs = nowElapsedRealtime;
        } else if (!playing && playingSinceMs >= 0L) {
            accumulatedMs += Math.max(0L, nowElapsedRealtime - playingSinceMs);
            playingSinceMs = -1L;
        }
        return accumulatedMs + (playingSinceMs < 0L
                ? 0L : Math.max(0L, nowElapsedRealtime - playingSinceMs));
    }
}
