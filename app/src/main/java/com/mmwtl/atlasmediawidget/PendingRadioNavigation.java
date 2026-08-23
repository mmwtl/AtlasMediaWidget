package com.mmwtl.atlasmediawidget;

final class PendingRadioNavigation {
    private int direction;

    void schedule(int requestedDirection) {
        direction = Integer.signum(requestedDirection);
    }

    void cancelIfSourceChanged(MediaSource.Id source) {
        if (source != MediaSource.Id.RADIO) clear();
    }

    int consume(MediaSource.Id source) {
        int pending = direction;
        clear();
        return source == MediaSource.Id.RADIO ? pending : 0;
    }

    void clear() {
        direction = 0;
    }
}
