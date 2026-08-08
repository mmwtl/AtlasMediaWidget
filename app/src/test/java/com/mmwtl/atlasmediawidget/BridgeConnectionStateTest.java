package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeConnectionStateTest {
    @Test public void reachesReadyOnlyAfterFirstSnapshot() {
        BridgeConnectionState state = new BridgeConnectionState();

        assertTrue(state.startBinding());
        assertFalse(state.startBinding());
        assertTrue(state.onServiceConnected());
        assertTrue(state.onRegistered());
        assertTrue(state.canSend());
        assertEquals(BridgeConnectionState.SnapshotResult.FIRST, state.onSnapshot());
        assertTrue(state.is(BridgeConnectionState.Phase.READY));
        assertEquals(BridgeConnectionState.SnapshotResult.UPDATE, state.onSnapshot());
    }

    @Test public void backoffDoesNotResetBeforeSnapshot() {
        BridgeConnectionState state = new BridgeConnectionState();

        assertTrue(state.startBinding());
        assertTrue(state.onServiceConnected());
        assertEquals(500L, state.onDisconnected());

        assertTrue(state.startBinding());
        assertTrue(state.onServiceConnected());
        assertTrue(state.onRegistered());
        assertEquals(1_000L, state.onDisconnected());

        assertTrue(state.startBinding());
        assertTrue(state.onServiceConnected());
        assertTrue(state.onRegistered());
        assertEquals(BridgeConnectionState.SnapshotResult.FIRST, state.onSnapshot());
        assertEquals(500L, state.onDisconnected());
    }

    @Test public void duplicateDisconnectIsIgnored() {
        BridgeConnectionState state = new BridgeConnectionState();

        assertTrue(state.startBinding());
        assertEquals(500L, state.onDisconnected());
        assertEquals(-1L, state.onDisconnected());
    }
}
