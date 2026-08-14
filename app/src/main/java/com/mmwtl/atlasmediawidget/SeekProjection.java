package com.mmwtl.atlasmediawidget;

/**
 * Keeps the progress UI responsive while a seek command is waiting for the backend snapshot.
 * The projection is local UI state; it never replaces the backend snapshot in the reducer.
 */
final class SeekProjection {
    static final long CONFIRM_TOLERANCE_MS = 3_000L;
    static final long TIMEOUT_MS = 8_000L;

    private SeekProjection() {}

    static long estimate(MediaSnapshot snapshot, long requestedPosition,
            long requestedAtElapsedRealtime, long nowElapsedRealtime) {
        if (snapshot == null) return -1L;
        return ProgressEstimator.estimate(
                requestedPosition,
                snapshot.duration,
                requestedAtElapsedRealtime,
                snapshot.speed,
                snapshot.playbackState,
                nowElapsedRealtime);
    }

    static boolean isConfirmed(MediaSnapshot candidate, long requestedPosition,
            long requestedAtElapsedRealtime) {
        if (candidate == null || candidate.position < 0L || requestedPosition < 0L
                || requestedAtElapsedRealtime < 0L
                || candidate.updateElapsedRealtime < requestedAtElapsedRealtime) return false;
        long delta = candidate.position - requestedPosition;
        return delta >= -CONFIRM_TOLERANCE_MS && delta <= CONFIRM_TOLERANCE_MS;
    }

    static boolean isTimedOut(long requestedAtElapsedRealtime, long nowElapsedRealtime) {
        return requestedAtElapsedRealtime >= 0L
                && nowElapsedRealtime - requestedAtElapsedRealtime >= TIMEOUT_MS;
    }
}
