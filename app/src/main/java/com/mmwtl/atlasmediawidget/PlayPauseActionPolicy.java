package com.mmwtl.atlasmediawidget;

final class PlayPauseActionPolicy {
    private PlayPauseActionPolicy() {}

    /**
     * Target-device observation: the RADIO state reaching this client is inverted relative
     * to audible playback. Keep that firmware-specific compatibility rule out of the generic
     * MediaSnapshot model and leave regular media-session semantics unchanged.
     */
    static boolean isCurrentlyPlaying(MediaSource.Id source, boolean reportedPlaying) {
        return source.displayId() == MediaSource.Id.RADIO
                ? !reportedPlaying
                : reportedPlaying;
    }

    static String explicitCommand(MediaSource.Id source, boolean reportedPlaying) {
        return isCurrentlyPlaying(source, reportedPlaying) ? "PAUSE" : "PLAY";
    }

    static String command(MediaSource.Id source, boolean reportedPlaying, long capabilities) {
        String explicit = explicitCommand(source, reportedPlaying);
        long explicitCapability = "PAUSE".equals(explicit)
                ? MediaBridgeContract.CAP_PAUSE
                : MediaBridgeContract.CAP_PLAY;
        if (source.displayId() == MediaSource.Id.RADIO
                && (capabilities & explicitCapability) != 0L) {
            return explicit;
        }
        return (capabilities & MediaBridgeContract.CAP_TOGGLE) != 0L ? "TOGGLE" : explicit;
    }
}
