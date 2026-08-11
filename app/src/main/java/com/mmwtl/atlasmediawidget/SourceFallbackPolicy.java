package com.mmwtl.atlasmediawidget;

final class SourceFallbackPolicy {
    private SourceFallbackPolicy() {}

    static MediaSource.Id fallback(MediaSnapshot previous, MediaSnapshot current) {
        if (previous == null || current == null) return MediaSource.Id.UNKNOWN;
        MediaSource.Id active = MediaSource.selectedId(
                previous.audioSource, previous.sources);
        if (active != MediaSource.Id.ONLINE && active != MediaSource.Id.YUNTING
                && active != MediaSource.Id.CPAA) return MediaSource.Id.UNKNOWN;
        MediaSource.Id currentActive = MediaSource.selectedId(
                current.audioSource, current.sources);
        if (currentActive == MediaSource.Id.BT) return MediaSource.Id.UNKNOWN;

        MediaSource before = find(previous, active);
        MediaSource after = find(current, active);
        if (before == null || after == null || !before.connected || after.connected) {
            return MediaSource.Id.UNKNOWN;
        }
        MediaSource bluetooth = find(current, MediaSource.Id.BT);
        if (bluetooth == null || !bluetooth.connected || !bluetooth.available
                || (bluetooth.capabilities & MediaBridgeContract.CAP_SET_SOURCE) == 0L) {
            return MediaSource.Id.UNKNOWN;
        }
        return MediaSource.Id.BT;
    }

    private static MediaSource find(MediaSnapshot snapshot, MediaSource.Id id) {
        for (MediaSource source : snapshot.sources) if (source.id == id) return source;
        return null;
    }
}
