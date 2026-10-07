package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class LiveListeningClockTest {
    @Test public void countsOnlyPlayingTimeOfTheSameStream() {
        LiveListeningClock clock = new LiveListeningClock();
        assertEquals(0L, clock.observe("ONLINE|radio", true, 1_000L));
        assertEquals(5_000L, clock.observe("ONLINE|radio", true, 6_000L));
        assertEquals(7_000L, clock.observe("ONLINE|radio", false, 8_000L));
        assertEquals(7_000L, clock.observe("ONLINE|radio", false, 60_000L));
        assertEquals(7_000L, clock.observe("ONLINE|radio", true, 62_000L));
        assertEquals(10_000L, clock.observe("ONLINE|radio", true, 65_000L));
    }

    @Test public void anotherStreamOrTrackWithDurationStartsOver() {
        LiveListeningClock clock = new LiveListeningClock();
        clock.observe("ONLINE|radio", true, 0L);
        assertEquals(4_000L, clock.observe("ONLINE|radio", true, 4_000L));
        assertEquals(0L, clock.observe("BT|phone", true, 5_000L));
        assertEquals(-1L, clock.observe(null, true, 6_000L));
        assertEquals(0L, clock.observe("BT|phone", true, 7_000L));
    }

    @Test public void streamKeyCoversLiveMediaButNotTracksOrNativeRadio() {
        assertEquals("ONLINE|player", LiveListeningClock.streamKey(
                snapshot(MediaSource.Id.ONLINE, "Song", 0L)));
        assertEquals("CPAA|player", LiveListeningClock.streamKey(
                snapshot(MediaSource.Id.CPAA, "Song", -1L)));
        assertNull(LiveListeningClock.streamKey(snapshot(MediaSource.Id.ONLINE, "Song", 180_000L)));
        assertNull(LiveListeningClock.streamKey(snapshot(MediaSource.Id.RADIO, "FM", 0L)));
        assertNull(LiveListeningClock.streamKey(snapshot(MediaSource.Id.BT, "", 0L)));
        assertNull(LiveListeningClock.streamKey(null));
    }

    @Test public void liveStreamNeedsMediaWithoutDurationOutsideNativeRadio() {
        assertTrue(MediaPresentation.isLiveStream(MediaSource.Id.BT, true, 0L));
        assertFalse(MediaPresentation.isLiveStream(MediaSource.Id.BT, false, 0L));
        assertFalse(MediaPresentation.isLiveStream(MediaSource.Id.BT, true, 1L));
        assertFalse(MediaPresentation.isLiveStream(MediaSource.Id.RADIO, true, 0L));
    }

    private static MediaSnapshot snapshot(MediaSource.Id source, String title, long duration) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                source, "", List.of(), "player", "Player", "", title, "", "",
                duration, -1L, 0L, 1, MediaSnapshot.STATE_PLAYING, 0, "", 0,
                MediaBridgeContract.CAP_PLAY, "", 0);
    }
}
