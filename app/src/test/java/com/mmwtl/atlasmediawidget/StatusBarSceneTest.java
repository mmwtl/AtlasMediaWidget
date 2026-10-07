package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.media.session.PlaybackState;

import java.util.List;

import org.junit.Test;

public final class StatusBarSceneTest {
    @Test public void playingTrackShowsArtistAndTitle() {
        assertEquals("Artist — Song", StatusBarScene.text(
                snapshot(MediaSource.Id.ONLINE, "Song", "Artist", MediaSnapshot.STATE_PLAYING)));
        assertEquals("Song", StatusBarScene.text(
                snapshot(MediaSource.Id.BT, " Song\n", " ", MediaSnapshot.STATE_PLAYING)));
        assertEquals("A B — C", StatusBarScene.text(
                snapshot(MediaSource.Id.USB, "C", "A\t B", PlaybackState.STATE_BUFFERING)));
    }

    @Test public void noPillWhilePausedStoppedOrWithoutMedia() {
        assertNull(StatusBarScene.text(null));
        assertNull(StatusBarScene.text(
                snapshot(MediaSource.Id.ONLINE, "Song", "Artist", PlaybackState.STATE_PAUSED)));
        assertNull(StatusBarScene.text(
                snapshot(MediaSource.Id.ONLINE, "Song", "Artist", PlaybackState.STATE_STOPPED)));
        assertNull(StatusBarScene.text(
                snapshot(MediaSource.Id.ONLINE, "", "Artist", MediaSnapshot.STATE_PLAYING)));
    }

    @Test public void radioShowsStationOrFrequency() {
        assertEquals("Europa Plus", StatusBarScene.text(
                snapshot(MediaSource.Id.RADIO, "Europa Plus", "FM 106.2", MediaSnapshot.STATE_PLAYING)));
        assertEquals("FM 101.7", StatusBarScene.text(
                snapshot(MediaSource.Id.RADIO, "FM 101.7", "Радио", MediaSnapshot.STATE_PLAYING)));
        assertEquals("Радио", StatusBarScene.text(
                snapshot(MediaSource.Id.RADIO, "", "", MediaSnapshot.STATE_PLAYING)));
        assertNull(StatusBarScene.text(
                snapshot(MediaSource.Id.RADIO, "Europa Plus", "", PlaybackState.STATE_PAUSED)));
    }

    @Test public void reopensOnlyWhenTheTextChanges() {
        StatusBarScene scene = new StatusBarScene();
        assertArrayEquals(StatusBarScene.NONE, scene.show(null));
        assertArrayEquals(new int[] {1}, scene.show("A — 1"));
        assertArrayEquals(StatusBarScene.NONE, scene.show("A — 1"));
        assertArrayEquals(new int[] {0, 1}, scene.show("A — 2"));
        assertEquals("A — 2", scene.shown());
        assertArrayEquals(new int[] {0}, scene.show(null));
        assertArrayEquals(StatusBarScene.NONE, scene.show(null));
        assertNull(scene.shown());
        assertArrayEquals(new int[] {1}, scene.show("A — 2"));
    }

    private static MediaSnapshot snapshot(MediaSource.Id source, String title, String artist,
            int state) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                source, "", List.of(), "player", "Player", "", title, artist, "",
                0L, -1L, 0L, 1, state, 0, "", 0,
                MediaBridgeContract.CAP_PLAY, "", 0);
    }
}
