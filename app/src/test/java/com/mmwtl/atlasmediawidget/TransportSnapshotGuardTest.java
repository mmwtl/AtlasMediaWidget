package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransportSnapshotGuardTest {
    @Test public void emptyOnlineTransitionIsHeldDuringTransportReconciliation() {
        TransportSnapshotGuard guard = new TransportSnapshotGuard();
        guard.begin(snapshot(10L, MediaSource.Id.RADIO, "station", "Радио"), 1_000L);

        assertTrue(guard.shouldDefer(
                snapshot(11L, MediaSource.Id.ONLINE, "", ""), 1_200L));
        assertFalse(guard.shouldDefer(
                snapshot(12L, MediaSource.Id.RADIO, "station", "Радио"), 1_300L));
    }

    @Test public void sustainedOnlineTransitionIsAcceptedAfterBoundedHold() {
        TransportSnapshotGuard guard = new TransportSnapshotGuard();
        guard.begin(snapshot(10L, MediaSource.Id.BT, "track", "Трек"), 1_000L);

        assertFalse(guard.shouldDefer(snapshot(11L, MediaSource.Id.ONLINE, "", ""),
                1_000L + TransportSnapshotGuard.RECONCILIATION_MS));
    }

    @Test public void populatedOnlineTransitionIsNeverHidden() {
        TransportSnapshotGuard guard = new TransportSnapshotGuard();
        guard.begin(snapshot(10L, MediaSource.Id.USB, "old", "Старый"), 1_000L);

        assertFalse(guard.shouldDefer(
                snapshot(11L, MediaSource.Id.ONLINE, "new", "Новый"), 1_100L));
    }

    @Test public void technicalOwnerAndMediaIdDoNotMakeAnEmptyCardVisible() {
        TransportSnapshotGuard guard = new TransportSnapshotGuard();
        guard.begin(snapshot(10L, MediaSource.Id.RADIO, "station", "Радио"), 1_000L);

        assertTrue(guard.shouldDefer(
                snapshot(11L, MediaSource.Id.ONLINE, "generated-id", ""), 1_100L));
    }

    @Test public void onlinePlaybackDoesNotHoldItsOwnSnapshots() {
        TransportSnapshotGuard guard = new TransportSnapshotGuard();
        guard.begin(snapshot(10L, MediaSource.Id.ONLINE, "track", "Трек"), 1_000L);

        assertFalse(guard.shouldDefer(
                snapshot(11L, MediaSource.Id.ONLINE, "", ""), 1_100L));
    }

    private static MediaSnapshot snapshot(long generation, MediaSource.Id source,
            String mediaId, String title) {
        return new MediaSnapshot(
                1, generation, 0L, true, 0, "", source, "", List.of(),
                mediaId.isBlank() ? "" : "pkg", "app", mediaId, title, "", "",
                title.isBlank() ? -1L : 60_000L, 1_000L, 1_000L, 1f,
                MediaSnapshot.STATE_PLAYING, 0, "", 0L,
                MediaBridgeContract.CAP_TOGGLE, "", 0L);
    }
}
