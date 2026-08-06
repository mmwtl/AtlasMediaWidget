package com.mmwtl.atlasmediawidget;

final class ForegroundEventTracker {
    private String foregroundPackage;
    private long lastEventTime;
    private boolean observed;

    void onResumed(long timestamp, String packageName) {
        if (timestamp < lastEventTime || packageName == null) return;
        lastEventTime = timestamp;
        foregroundPackage = packageName;
        observed = true;
    }

    void onStopped(long timestamp, String packageName) {
        if (timestamp < lastEventTime || packageName == null) return;
        lastEventTime = timestamp;
        if (packageName.equals(foregroundPackage)) foregroundPackage = null;
        observed = true;
    }

    void seed(long timestamp, String packageName) {
        if (observed || timestamp < lastEventTime || packageName == null) return;
        lastEventTime = timestamp;
        foregroundPackage = packageName;
    }

    boolean hasObservedEvent() {
        return observed;
    }

    String foregroundPackage() {
        return foregroundPackage;
    }
}
