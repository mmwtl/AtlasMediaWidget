package com.mmwtl.atlasmediawidget;

final class ProgressEstimator {
    private ProgressEstimator() {}

    static long estimate(long position, long duration, long updateElapsedRealtime,
            float speed, int playbackState, long nowElapsedRealtime) {
        if (position < 0L) return -1L;
        double result = position;
        if (playbackState == MediaSnapshot.STATE_PLAYING && updateElapsedRealtime > 0L) {
            long elapsed = Math.max(0L, nowElapsedRealtime - updateElapsedRealtime);
            result += elapsed * Math.max(0f, speed);
        }
        long estimated = Math.max(0L, Math.round(result));
        return duration >= 0L ? Math.min(estimated, duration) : estimated;
    }
}
