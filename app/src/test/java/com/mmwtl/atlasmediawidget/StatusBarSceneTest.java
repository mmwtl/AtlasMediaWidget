package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.media.session.PlaybackState;

import java.util.List;

import org.junit.Test;

public final class StatusBarSceneTest {
    @Test public void formatsTrackAsChosen() {
        MediaSnapshot track = snapshot(MediaSource.Id.ONLINE, "Song", "Artist",
                MediaSnapshot.STATE_PLAYING);
        assertEquals("Artist — Song", text(track, StatusBarScene.Format.ARTIST_TITLE));
        assertEquals("Song — Artist", text(track, StatusBarScene.Format.TITLE_ARTIST));
        assertEquals("Song", text(track, StatusBarScene.Format.TITLE));
        assertEquals("[Song]", text(track, StatusBarScene.Format.TITLE_FITTED));
        MediaSnapshot noArtist = snapshot(MediaSource.Id.BT, " Song\n", " ",
                MediaSnapshot.STATE_PLAYING);
        assertEquals("Song", text(noArtist, StatusBarScene.Format.ARTIST_TITLE));
        assertEquals("Song", text(noArtist, StatusBarScene.Format.TITLE_ARTIST));
        assertEquals("A B — C", text(snapshot(MediaSource.Id.USB, "C", "A\t B",
                PlaybackState.STATE_BUFFERING), StatusBarScene.Format.ARTIST_TITLE));
    }

    @Test public void unknownFormatPreferenceFallsBackToFitted() {
        assertEquals(StatusBarScene.Format.TITLE_FITTED, StatusBarScene.Format.fromPreference(42));
        assertEquals(StatusBarScene.Format.ARTIST_TITLE, StatusBarScene.Format.fromPreference(
                StatusBarScene.Format.ARTIST_TITLE.preferenceValue));
    }

    @Test public void noPillWhilePausedStoppedOrWithoutMedia() {
        for (StatusBarScene.Format format : StatusBarScene.Format.values()) {
            assertNull(text(null, format));
            assertNull(text(snapshot(MediaSource.Id.ONLINE, "Song", "Artist",
                    PlaybackState.STATE_PAUSED), format));
            assertNull(text(snapshot(MediaSource.Id.ONLINE, "Song", "Artist",
                    PlaybackState.STATE_STOPPED), format));
            assertNull(text(snapshot(MediaSource.Id.ONLINE, "", "Artist",
                    MediaSnapshot.STATE_PLAYING), format));
        }
    }

    @Test public void radioShowsStationOrFrequencyInEveryFormat() {
        MediaSnapshot station = snapshot(MediaSource.Id.RADIO, "Europa Plus", "FM 106.2",
                MediaSnapshot.STATE_PLAYING);
        assertEquals("Europa Plus", text(station, StatusBarScene.Format.ARTIST_TITLE));
        assertEquals("Europa Plus", text(station, StatusBarScene.Format.TITLE_ARTIST));
        assertEquals("[Europa Plus]", text(station, StatusBarScene.Format.TITLE_FITTED));
        assertEquals("FM 101.7", text(snapshot(MediaSource.Id.RADIO, "FM 101.7", "Радио",
                MediaSnapshot.STATE_PLAYING), StatusBarScene.Format.TITLE));
        assertEquals("Радио", text(snapshot(MediaSource.Id.RADIO, "", "",
                MediaSnapshot.STATE_PLAYING), StatusBarScene.Format.TITLE));
        assertNull(text(snapshot(MediaSource.Id.RADIO, "Europa Plus", "",
                PlaybackState.STATE_PAUSED), StatusBarScene.Format.TITLE));
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

    private static String text(MediaSnapshot snapshot, StatusBarScene.Format format) {
        return StatusBarScene.text(snapshot, format, value -> "[" + value + "]");
    }

    private static MediaSnapshot snapshot(MediaSource.Id source, String title, String artist,
            int state) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                source, "", List.of(), "player", "Player", "", title, artist, "",
                0L, -1L, 0L, 1, state, 0, "", 0,
                MediaBridgeContract.CAP_PLAY, "", 0);
    }
}
