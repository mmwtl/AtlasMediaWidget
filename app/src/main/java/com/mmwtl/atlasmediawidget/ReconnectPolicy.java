package com.mmwtl.atlasmediawidget;

final class ReconnectPolicy {
    private static final long BASE_MS = 500L;
    private static final long MAX_MS = 15_000L;
    static final long BIND_TIMEOUT_MS = 5_000L;
    static final long REGISTER_TIMEOUT_MS = 3_000L;
    static final long SNAPSHOT_TIMEOUT_MS = 3_000L;

    private ReconnectPolicy() {}

    static long delayMs(int attempt) {
        int bounded = Math.max(0, Math.min(attempt, 10));
        return Math.min(MAX_MS, BASE_MS << bounded);
    }
}
