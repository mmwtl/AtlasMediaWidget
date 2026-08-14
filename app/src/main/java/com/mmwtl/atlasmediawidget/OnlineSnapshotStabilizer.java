package com.mmwtl.atlasmediawidget;

/** Keeps partial ONLINE callbacks from erasing fields supplied by another callback channel. */
final class OnlineSnapshotStabilizer {
    static final long EMPTY_SNAPSHOT_HOLD_MS = 1_500L;

    private MediaSnapshot stable;
    private long emptySince = -1L;

    MediaSnapshot stabilize(MediaSnapshot candidate, long nowElapsedRealtime) {
        if (candidate == null || activeSource(candidate) != MediaSource.Id.ONLINE) {
            clear();
            return candidate;
        }
        if (stable == null) {
            stable = candidate;
            return candidate;
        }

        // Some ONLINE implementations do not expose a media ID. A non-empty title or artist
        // still gives us a useful visible identity and must not be treated as an "empty"
        // callback, otherwise the next track inherits the previous track's artwork/album.
        boolean candidateIdentifiesTrack = identifiesTrack(candidate);
        boolean sameTrack = candidateIdentifiesTrack && (
                !stable.mediaId.isBlank() && candidate.mediaId.equals(stable.mediaId)
                || sameVisibleIdentity(candidate, stable));
        if (candidateIdentifiesTrack && !sameTrack) {
            stable = candidate;
            emptySince = -1L;
            return candidate;
        }

        if (!candidateIdentifiesTrack) {
            if (emptySince < 0L) emptySince = nowElapsedRealtime;
            long elapsed = nowElapsedRealtime - emptySince;
            if (elapsed < 0L || elapsed > EMPTY_SNAPSHOT_HOLD_MS) {
                stable = candidate;
                return candidate;
            }
        } else {
            emptySince = -1L;
        }

        MediaSnapshot merged = candidate.withMediaFieldsFrom(stable);
        stable = merged;
        return merged;
    }

    void clear() {
        stable = null;
        emptySince = -1L;
    }

    private static MediaSource.Id activeSource(MediaSnapshot snapshot) {
        return MediaSource.selectedId(snapshot.audioSource, snapshot.sources).displayId();
    }

    private static boolean sameVisibleIdentity(MediaSnapshot first, MediaSnapshot second) {
        if (first.title.isBlank() || second.title.isBlank()
                || !first.title.equals(second.title)) return false;
        return first.artist.isBlank() || second.artist.isBlank()
                || first.artist.equals(second.artist);
    }

    private static boolean identifiesTrack(MediaSnapshot snapshot) {
        return !snapshot.mediaId.isBlank()
                || !snapshot.title.isBlank()
                || !snapshot.artist.isBlank();
    }
}
