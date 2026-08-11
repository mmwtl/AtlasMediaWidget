package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public final class SourceFallbackPolicyTest {
    @Test public void onlineDisconnectFallsBackToConnectedBluetoothOnce() {
        MediaSnapshot before = snapshot(MediaSource.Id.ONLINE, true, true, false);
        MediaSnapshot after = snapshot(MediaSource.Id.ONLINE, false, true, false);

        assertEquals(MediaSource.Id.BT, SourceFallbackPolicy.fallback(before, after));
        assertEquals(MediaSource.Id.UNKNOWN, SourceFallbackPolicy.fallback(after, after));
    }

    @Test public void cpaaDisconnectAlsoFallsBackToBluetooth() {
        assertEquals(MediaSource.Id.BT, SourceFallbackPolicy.fallback(
                snapshot(MediaSource.Id.CPAA, true, true, false),
                snapshot(MediaSource.Id.CPAA, false, true, false)));
    }

    @Test public void doesNotSelectUnavailableBluetoothOrRepeatBackendSwitch() {
        assertEquals(MediaSource.Id.UNKNOWN, SourceFallbackPolicy.fallback(
                snapshot(MediaSource.Id.ONLINE, true, false, false),
                snapshot(MediaSource.Id.ONLINE, false, false, false)));
        assertEquals(MediaSource.Id.UNKNOWN, SourceFallbackPolicy.fallback(
                snapshot(MediaSource.Id.ONLINE, true, true, false),
                snapshot(MediaSource.Id.BT, false, true, true)));
    }

    private static MediaSnapshot snapshot(MediaSource.Id active, boolean activeConnected,
            boolean bluetoothAvailable, boolean bluetoothSelected) {
        List<MediaSource> sources = List.of(
                new MediaSource(MediaSource.Id.ONLINE,
                        active == MediaSource.Id.ONLINE && activeConnected, true,
                        active == MediaSource.Id.ONLINE, MediaBridgeContract.CAP_SET_SOURCE),
                new MediaSource(MediaSource.Id.CPAA,
                        active == MediaSource.Id.CPAA && activeConnected, true,
                        active == MediaSource.Id.CPAA, MediaBridgeContract.CAP_SET_SOURCE),
                new MediaSource(MediaSource.Id.BT, bluetoothAvailable, bluetoothAvailable,
                        bluetoothSelected, MediaBridgeContract.CAP_SET_SOURCE));
        return new MediaSnapshot(
                MediaBridgeContract.VERSION, 1L, 1L, true, 0, "", active, "", sources,
                "", "", "", "", "", "", -1L, -1L, 0L, 0f, 0, 0, "",
                0L, MediaBridgeContract.CAP_SET_SOURCE, "", 0L);
    }
}
