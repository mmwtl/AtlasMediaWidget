package com.mmwtl.atlasmediawidget;

final class SnapshotReducer {
    static final long DISCONNECTED_RETENTION_MS = 5_000L;

    private MediaSnapshot snapshot;
    private boolean connected;
    private long disconnectedAt = -1L;
    private boolean acceptGenerationReset;

    void onConnected(long nowElapsedRealtime) {
        acceptGenerationReset = disconnectedAt >= 0L;
        if (disconnectedAt >= 0L
                && nowElapsedRealtime - disconnectedAt > DISCONNECTED_RETENTION_MS) {
            snapshot = null;
        }
        connected = true;
        disconnectedAt = -1L;
    }

    void onDisconnected(long nowElapsedRealtime) {
        connected = false;
        if (disconnectedAt < 0L) disconnectedAt = nowElapsedRealtime;
    }

    boolean accept(MediaSnapshot candidate) {
        if (candidate.protocolVersion != MediaBridgeContract.VERSION) return false;
        if (!acceptGenerationReset && snapshot != null
                && candidate.generation < snapshot.generation) return false;
        snapshot = candidate;
        acceptGenerationReset = false;
        return true;
    }

    MediaSnapshot visibleSnapshot(long nowElapsedRealtime) {
        if (connected) return snapshot;
        if (snapshot == null || disconnectedAt < 0L
                || nowElapsedRealtime - disconnectedAt > DISCONNECTED_RETENTION_MS) return null;
        return snapshot;
    }

    boolean isConnected() {
        return connected;
    }
}
