package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class SeekProjectionTest {
    @Test public void projectionStartsFromRequestedPositionInsteadOfOldSnapshot() {
        MediaSnapshot oldSnapshot = snapshot(90_000L, 1_000L, 1f);

        assertEquals(1_000L, SeekProjection.estimate(oldSnapshot, 0L, 2_000L, 3_000L));
    }

    @Test public void onlyFreshNearTargetSnapshotConfirmsSeek() {
        MediaSnapshot stale = snapshot(90_000L, 1_000L, 1f);
        MediaSnapshot fresh = snapshot(1_000L, 3_000L, 1f);

        assertFalse(SeekProjection.isConfirmed(stale, 0L, 2_000L));
        assertTrue(SeekProjection.isConfirmed(fresh, 0L, 2_000L));
    }

    @Test public void projectionExpiresWhenBackendNeverConfirms() {
        assertTrue(SeekProjection.isTimedOut(2_000L, 10_000L));
        assertFalse(SeekProjection.isTimedOut(2_000L, 9_999L));
    }

    private static MediaSnapshot snapshot(long position, long updateElapsedRealtime, float speed) {
        return new MediaSnapshot(
                MediaBridgeContract.VERSION, 1L, 1L, true, 0, "",
                MediaSource.Id.BT, "", List.of(), "owner", "app", "track", "Title",
                "Artist", "", 180_000L, position, updateElapsedRealtime, speed,
                MediaSnapshot.STATE_PLAYING, 0, "", 1L,
                MediaBridgeContract.CAP_SEEK, "", 0L);
    }
}
