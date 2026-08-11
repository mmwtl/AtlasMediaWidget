package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayPauseActionPolicyTest {
    @Test public void radioUsesCorrectedBridgeStateAndToggle() {
        assertTrue(PlayPauseActionPolicy.isCurrentlyPlaying(MediaSource.Id.RADIO, true));
        assertFalse(PlayPauseActionPolicy.isCurrentlyPlaying(MediaSource.Id.RADIO, false));
        assertEquals("PAUSE", PlayPauseActionPolicy.explicitCommand(MediaSource.Id.RADIO, true));
        assertEquals("PLAY", PlayPauseActionPolicy.explicitCommand(MediaSource.Id.RADIO, false));
        assertEquals("TOGGLE", PlayPauseActionPolicy.command(MediaSource.Id.RADIO, true,
                MediaBridgeContract.CAP_TOGGLE | MediaBridgeContract.CAP_PAUSE));
        assertEquals("TOGGLE", PlayPauseActionPolicy.command(MediaSource.Id.RADIO, false,
                MediaBridgeContract.CAP_TOGGLE | MediaBridgeContract.CAP_PLAY));
    }

    @Test public void regularMediaSessionsKeepFrameworkPlaybackSemantics() {
        assertTrue(PlayPauseActionPolicy.isCurrentlyPlaying(MediaSource.Id.BT, true));
        assertFalse(PlayPauseActionPolicy.isCurrentlyPlaying(MediaSource.Id.USB, false));
        assertEquals("PAUSE", PlayPauseActionPolicy.explicitCommand(MediaSource.Id.BT, true));
        assertEquals("PLAY", PlayPauseActionPolicy.explicitCommand(MediaSource.Id.ONLINE, false));
        assertEquals("TOGGLE", PlayPauseActionPolicy.command(MediaSource.Id.BT, true,
                MediaBridgeContract.CAP_TOGGLE | MediaBridgeContract.CAP_PAUSE));
    }

    @Test public void yuntingAliasUsesRegularOnlinePlaybackSemantics() {
        assertTrue(PlayPauseActionPolicy.isCurrentlyPlaying(MediaSource.Id.YUNTING, true));
        assertFalse(PlayPauseActionPolicy.isCurrentlyPlaying(MediaSource.Id.YUNTING, false));
    }

    @Test public void radioUsesExplicitActionOnlyWhenToggleIsUnavailable() {
        assertEquals("PAUSE", PlayPauseActionPolicy.command(MediaSource.Id.RADIO, true,
                MediaBridgeContract.CAP_PAUSE));
        assertEquals("PLAY", PlayPauseActionPolicy.command(MediaSource.Id.RADIO, false,
                MediaBridgeContract.CAP_PLAY));
    }
}
