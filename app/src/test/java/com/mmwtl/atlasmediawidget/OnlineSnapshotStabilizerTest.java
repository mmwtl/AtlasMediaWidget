package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public final class OnlineSnapshotStabilizerTest {
    @Test public void sameTrackKeepsAlbumAndArtworkFromRicherCallback() {
        OnlineSnapshotStabilizer stabilizer = new OnlineSnapshotStabilizer();
        stabilizer.stabilize(snapshot(1, "track", "Title", "Artist", "Album", "cover", 42_000L), 100L);

        MediaSnapshot result = stabilizer.stabilize(
                snapshot(2, "track", "Title", "Artist", "", "", 43_000L), 200L);

        assertEquals("Album", result.album);
        assertEquals("cover", result.artworkUri);
        assertEquals(43_000L, result.position);
    }

    @Test public void transientEmptyCallbackDoesNotFlashAwayTrackAndProgress() {
        OnlineSnapshotStabilizer stabilizer = new OnlineSnapshotStabilizer();
        stabilizer.stabilize(snapshot(1, "track", "Title", "Artist", "Album", "cover", 42_000L), 100L);

        MediaSnapshot result = stabilizer.stabilize(
                snapshot(2, "", "", "", "", "", -1L), 500L);

        assertEquals("track", result.mediaId);
        assertEquals("Title", result.title);
        assertEquals(42_000L, result.position);
        assertEquals(180_000L, result.duration);
    }

    @Test public void oneOsAndMediaSessionIdsForSameVisibleTrackAreMerged() {
        OnlineSnapshotStabilizer stabilizer = new OnlineSnapshotStabilizer();
        stabilizer.stabilize(
                snapshot(1, "one-os-id", "Title", "Artist", "Album", "cover", 42_000L), 100L);

        MediaSnapshot result = stabilizer.stabilize(
                snapshot(2, "session-id", "Title", "Artist", "", "", 43_000L), 200L);

        assertEquals("session-id", result.mediaId);
        assertEquals("Album", result.album);
        assertEquals("cover", result.artworkUri);
    }

    @Test public void idlessVisibleTrackChangeDoesNotInheritPreviousArtwork() {
        OnlineSnapshotStabilizer stabilizer = new OnlineSnapshotStabilizer();
        stabilizer.stabilize(snapshot(1, "", "Old", "Artist", "Album", "old-cover", 42_000L), 100L);

        MediaSnapshot result = stabilizer.stabilize(
                snapshot(2, "", "New", "Artist", "", "", 0L), 200L);

        assertEquals("New", result.title);
        assertEquals("", result.album);
        assertEquals("", result.artworkUri);
    }

    @Test public void emptyCallbackExpiresAndNewTrackIsNeverMerged() {
        OnlineSnapshotStabilizer stabilizer = new OnlineSnapshotStabilizer();
        stabilizer.stabilize(snapshot(1, "old", "Old", "Artist", "Album", "cover", 42_000L), 100L);
        stabilizer.stabilize(snapshot(2, "", "", "", "", "", -1L), 200L);

        MediaSnapshot expired = stabilizer.stabilize(
                snapshot(3, "", "", "", "", "", -1L), 1_701L);
        MediaSnapshot next = stabilizer.stabilize(
                snapshot(4, "new", "New", "", "", "", 0L), 1_800L);

        assertEquals("", expired.title);
        assertEquals("New", next.title);
        assertEquals("", next.album);
        assertEquals("", next.artworkUri);
    }

    private static MediaSnapshot snapshot(long generation, String mediaId, String title,
            String artist, String album, String artwork, long position) {
        return new MediaSnapshot(
                MediaBridgeContract.VERSION, generation, 1L, true, 0, "",
                MediaSource.Id.ONLINE, "", List.of(
                        new MediaSource(MediaSource.Id.ONLINE, true, true, true,
                                MediaBridgeContract.CAP_SET_SOURCE)),
                "owner", "app", mediaId, title, artist, album, 180_000L, position,
                10L, 1f, MediaSnapshot.STATE_PLAYING, 0, "", 1L,
                MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_PAUSE,
                artwork, artwork.isBlank() ? 0L : 7L);
    }
}
