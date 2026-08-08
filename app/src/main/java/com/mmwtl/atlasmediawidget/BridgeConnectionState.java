package com.mmwtl.atlasmediawidget;

final class BridgeConnectionState {
    enum Phase { STOPPED, BINDING, REGISTERING, WAITING_SNAPSHOT, READY, INCOMPATIBLE }
    enum SnapshotResult { IGNORED, FIRST, UPDATE }

    private Phase phase = Phase.STOPPED;
    private int reconnectAttempt;

    synchronized boolean startBinding() {
        if (phase != Phase.STOPPED) return false;
        phase = Phase.BINDING;
        return true;
    }

    synchronized boolean onServiceConnected() {
        if (phase != Phase.BINDING) return false;
        phase = Phase.REGISTERING;
        return true;
    }

    synchronized boolean onRegistered() {
        if (phase != Phase.REGISTERING) return false;
        phase = Phase.WAITING_SNAPSHOT;
        return true;
    }

    synchronized SnapshotResult onSnapshot() {
        if (phase != Phase.WAITING_SNAPSHOT && phase != Phase.READY) {
            return SnapshotResult.IGNORED;
        }
        boolean first = phase == Phase.WAITING_SNAPSHOT;
        phase = Phase.READY;
        reconnectAttempt = 0;
        return first ? SnapshotResult.FIRST : SnapshotResult.UPDATE;
    }

    synchronized void onIncompatible() {
        phase = Phase.INCOMPATIBLE;
    }

    synchronized long onDisconnected() {
        if (phase == Phase.STOPPED) return -1L;
        phase = Phase.STOPPED;
        return ReconnectPolicy.delayMs(reconnectAttempt++);
    }

    synchronized void onStopped() {
        phase = Phase.STOPPED;
        reconnectAttempt = 0;
    }

    synchronized boolean is(Phase expected) {
        return phase == expected;
    }

    synchronized boolean hasBinding() {
        return phase != Phase.STOPPED;
    }

    synchronized boolean canSend() {
        return phase == Phase.WAITING_SNAPSHOT || phase == Phase.READY;
    }
}
