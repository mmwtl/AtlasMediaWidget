package com.mmwtl.atlasmediawidget;

final class TransportSnapshotGuard {
    static final long RECONCILIATION_MS = 900L;

    private boolean active;
    private long commandAt;
    private MediaSource.Id stableSource = MediaSource.Id.UNKNOWN;

    void begin(MediaSnapshot current, long nowElapsedRealtime) {
        stableSource = current == null ? MediaSource.Id.UNKNOWN
                : MediaSource.selectedId(current.audioSource, current.sources).displayId();
        active = stableSource != MediaSource.Id.UNKNOWN;
        commandAt = nowElapsedRealtime;
    }

    boolean shouldDefer(MediaSnapshot candidate, long nowElapsedRealtime) {
        if (!active || candidate == null) return false;
        long elapsed = nowElapsedRealtime - commandAt;
        if (elapsed < 0L || elapsed >= RECONCILIATION_MS) {
            active = false;
            return false;
        }
        MediaSource.Id candidateSource = MediaSource.selectedId(
                candidate.audioSource, candidate.sources).displayId();
        if (candidateSource == stableSource) return false;
        if (candidateSource != MediaSource.Id.ONLINE || hasMediaIdentity(candidate)) {
            active = false;
            return false;
        }
        return true;
    }

    void clear() {
        active = false;
        stableSource = MediaSource.Id.UNKNOWN;
    }

    private static boolean hasMediaIdentity(MediaSnapshot snapshot) {
        return !snapshot.title.isBlank()
                || !snapshot.artist.isBlank()
                || !snapshot.album.isBlank()
                || !snapshot.artworkUri.isBlank()
                || snapshot.duration > 0L;
    }
}
