package com.mmwtl.atlasmediawidget;

/** Keeps the card stable while an activity launched from it takes over the screen. */
final class CardSuppressionPolicy {
    enum State {
        IDLE,
        WAITING_FOR_NON_HOME,
        WAITING_FOR_HOME_RETURN
    }

    private State state = State.IDLE;
    private long deadline;

    void suppress(long now, long durationMs) {
        state = State.WAITING_FOR_NON_HOME;
        deadline = now + Math.max(0L, durationMs);
    }

    void onVisibility(boolean homeVisible, long now) {
        if (isExpired(now)) {
            state = State.IDLE;
            deadline = 0L;
            return;
        }
        if (state == State.WAITING_FOR_NON_HOME && !homeVisible) {
            state = State.WAITING_FOR_HOME_RETURN;
        } else if (state == State.WAITING_FOR_HOME_RETURN && homeVisible) {
            state = State.IDLE;
            deadline = 0L;
        }
    }

    boolean isCardAllowed(long now) {
        if (isExpired(now)) {
            state = State.IDLE;
            deadline = 0L;
        }
        return state == State.IDLE;
    }

    State state() {
        return state;
    }

    long deadline() {
        return deadline;
    }

    private boolean isExpired(long now) {
        return state != State.IDLE && now >= deadline;
    }
}
