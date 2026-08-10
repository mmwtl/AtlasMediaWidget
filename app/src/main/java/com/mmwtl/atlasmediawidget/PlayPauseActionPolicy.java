package com.mmwtl.atlasmediawidget;

final class PlayPauseActionPolicy {
    private PlayPauseActionPolicy() {}

    static boolean isCurrentlyPlaying(MediaSource.Id source, boolean reportedPlaying) {
        return reportedPlaying;
    }

    static String explicitCommand(MediaSource.Id source, boolean reportedPlaying) {
        return isCurrentlyPlaying(source, reportedPlaying) ? "PAUSE" : "PLAY";
    }

    static String command(MediaSource.Id source, boolean reportedPlaying, long capabilities) {
        String explicit = explicitCommand(source, reportedPlaying);
        return (capabilities & MediaBridgeContract.CAP_TOGGLE) != 0L ? "TOGGLE" : explicit;
    }
}
