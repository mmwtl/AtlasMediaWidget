package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class SourceTileTest {
    private static final SourceTile RADIO = SourceTile.source(MediaSource.Id.RADIO);
    private static final SourceTile BT = SourceTile.source(MediaSource.Id.BT);
    private static final SourceTile ONLINE = SourceTile.source(MediaSource.Id.ONLINE);
    private static final SourceTile YANDEX = SourceTile.player("ru.yandex.music");
    private static final SourceTile AIMP = SourceTile.player("com.aimp.player");

    @Test public void missingSettingKeepsTheStockFiveTiles() {
        assertEquals(SourceTile.DEFAULT, SourceTile.decode(null));
        assertEquals(SourceTile.DEFAULT, SourceTile.decode(""));
        assertEquals("RADIO,BT,USB,ONLINE,CPAA", SourceTile.encode(SourceTile.DEFAULT));
    }

    @Test public void tilesRoundTripInTheirOrder() {
        List<SourceTile> tiles = List.of(YANDEX, BT, AIMP);
        assertEquals(tiles, SourceTile.decode(SourceTile.encode(tiles)));
        assertEquals("player:ru.yandex.music", YANDEX.wire());
        assertTrue(YANDEX.isPlayer());
        assertEquals(MediaSource.Id.ONLINE, YANDEX.source);
    }

    @Test public void unknownRepeatedAndExtraEntriesAreDropped() {
        assertEquals(List.of(BT, YANDEX), SourceTile.decode(
                "BT,NEW_SOURCE,player:,player:../x,BT,player:ru.yandex.music,YUNTING"));
        assertEquals(SourceTile.DEFAULT, SourceTile.decode("UNKNOWN,player:bad"));
        List<SourceTile> capped = SourceTile.decode(
                "RADIO,BT,USB,ONLINE,CPAA,player:a.b,player:c.d");
        assertEquals(SourceTile.MAX_TILES, capped.size());
        assertEquals(SourceTile.player("a.b"), capped.get(5));
        assertNull(SourceTile.fromWire("OTHER"));
    }

    @Test public void onlineSelectsThePlayingPlayersTileBeforeTheGenericOne() {
        List<SourceTile> tiles = List.of(RADIO, ONLINE, YANDEX, AIMP);
        assertEquals(2, SourceTile.selectedIndex(tiles, MediaSource.Id.ONLINE, "ru.yandex.music"));
        assertEquals(1, SourceTile.selectedIndex(tiles, MediaSource.Id.ONLINE, "org.other"));
        assertEquals(1, SourceTile.selectedIndex(tiles, MediaSource.Id.YUNTING, ""));
        assertEquals(0, SourceTile.selectedIndex(tiles, MediaSource.Id.RADIO, "ru.yandex.music"));
        assertEquals(-1, SourceTile.selectedIndex(List.of(RADIO, YANDEX),
                MediaSource.Id.ONLINE, "org.other"));
        assertEquals(-1, SourceTile.selectedIndex(tiles, MediaSource.Id.BT, ""));
    }

    @Test public void anotherPlayerStaysSelectableWhileOnlinePlays() {
        List<SourceTile> tiles = List.of(RADIO, YANDEX, AIMP);
        MediaSnapshot snapshot = snapshot(MediaSource.Id.ONLINE, "ru.yandex.music", List.of(
                new MediaSource(MediaSource.Id.ONLINE, true, true, true,
                        MediaBridgeContract.CAP_SET_SOURCE),
                new MediaSource(MediaSource.Id.RADIO, true, true, false,
                        MediaBridgeContract.CAP_SET_SOURCE)));
        assertTrue(SourceTile.selectable(tiles, 0, snapshot, true));
        assertFalse(SourceTile.selectable(tiles, 1, snapshot, true));
        assertTrue(SourceTile.selectable(tiles, 2, snapshot, true));
        assertFalse(SourceTile.selectable(tiles, 2, snapshot, false));
        assertFalse(SourceTile.selectable(tiles, 3, snapshot, true));
    }

    @Test public void playerTilesNeedAnOnlineSourceThatAcceptsTheSwitch() {
        List<SourceTile> tiles = List.of(RADIO, YANDEX);
        MediaSnapshot withoutOnline = snapshot(MediaSource.Id.RADIO, "", List.of(
                new MediaSource(MediaSource.Id.RADIO, true, true, true,
                        MediaBridgeContract.CAP_SET_SOURCE)));
        assertFalse(SourceTile.selectable(tiles, 1, withoutOnline, true));
        MediaSnapshot readOnly = snapshot(MediaSource.Id.RADIO, "", List.of(
                new MediaSource(MediaSource.Id.ONLINE, true, true, false, 0L)));
        assertFalse(SourceTile.selectable(tiles, 1, readOnly, true));
    }

    @Test public void playerTilesDoNotWaitForOneOsToReportOnlineAvailable() {
        List<SourceTile> tiles = List.of(RADIO, ONLINE, YANDEX);
        MediaSnapshot withoutOneOs = snapshot(MediaSource.Id.ONLINE, "com.aimp.player", List.of(
                new MediaSource(MediaSource.Id.ONLINE, false, false, false,
                        MediaBridgeContract.CAP_SET_SOURCE),
                new MediaSource(MediaSource.Id.RADIO, false, false, false,
                        MediaBridgeContract.CAP_SET_SOURCE)));
        assertTrue(SourceTile.selectable(tiles, 2, withoutOneOs, true));
        assertFalse(SourceTile.selectable(tiles, 0, withoutOneOs, true));
        assertFalse(SourceTile.selectable(tiles, 1, withoutOneOs, true));
    }

    private static MediaSnapshot snapshot(MediaSource.Id source, String ownerPackage,
            List<MediaSource> sources) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                source, "", sources, ownerPackage, "", "", "", "", "",
                0L, -1L, 0L, 0, PlaybackState.STATE_NONE, 0, "", 0,
                MediaBridgeContract.CAP_PLAY, "", 0);
    }
}
