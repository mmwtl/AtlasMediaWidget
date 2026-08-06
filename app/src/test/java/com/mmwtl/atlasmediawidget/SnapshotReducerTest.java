package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SnapshotReducerTest {
    @Test public void olderSessionGenerationCannotReplaceCurrentTrack() {
        SnapshotReducer reducer = new SnapshotReducer();
        reducer.onConnected(0L);
        MediaSnapshot current = snapshot(8, "new");
        MediaSnapshot late = snapshot(7, "old");

        assertTrue(reducer.accept(current));
        assertFalse(reducer.accept(late));
        assertSame(current, reducer.visibleSnapshot(100L));
    }

    @Test public void disconnectedSnapshotExpiresInsteadOfStickingForever() {
        SnapshotReducer reducer = new SnapshotReducer();
        reducer.onConnected(0L);
        MediaSnapshot current = snapshot(1, "track");
        reducer.accept(current);
        reducer.onDisconnected(1_000L);

        assertSame(current, reducer.visibleSnapshot(
                1_000L + SnapshotReducer.DISCONNECTED_RETENTION_MS));
        assertNull(reducer.visibleSnapshot(
                1_001L + SnapshotReducer.DISCONNECTED_RETENTION_MS));
    }

    @Test public void reconnectAfterLongDisconnectRequiresFreshSnapshot() {
        SnapshotReducer reducer = new SnapshotReducer();
        MediaSnapshot current = snapshot(2, "track");
        reducer.accept(current);
        reducer.onDisconnected(0L);
        assertNull(reducer.visibleSnapshot(10_000L));
        reducer.onConnected(10_000L);
        assertNull(reducer.visibleSnapshot(10_000L));
        MediaSnapshot fresh = snapshot(3, "fresh");
        assertTrue(reducer.accept(fresh));
        assertSame(fresh, reducer.visibleSnapshot(10_000L));
    }

    @Test public void firstSnapshotAfterBinderReconnectMayResetServerGeneration() {
        SnapshotReducer reducer = new SnapshotReducer();
        reducer.onConnected(0L);
        reducer.accept(snapshot(100, "before-restart"));
        reducer.onDisconnected(1_000L);
        reducer.onConnected(1_100L);

        MediaSnapshot restarted = snapshot(1, "after-restart");
        assertTrue(reducer.accept(restarted));
        assertSame(restarted, reducer.visibleSnapshot(1_100L));
    }

    private static MediaSnapshot snapshot(long generation, String mediaId) {
        return new MediaSnapshot(
                1, generation, 0L, true, 0, "",
                MediaSource.Id.ONLINE, "", List.of(), "pkg", "app", mediaId,
                mediaId, "artist", "", 60_000L, 1_000L, 1_000L, 1f,
                MediaSnapshot.STATE_PLAYING, 0, "", 0L,
                MediaBridgeContract.CAP_TOGGLE, "", 0L);
    }
}
