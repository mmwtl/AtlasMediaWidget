package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MediaBridgeContractTest {
    @Test public void protocolV1ConstantsMatchGInputBridgeMediaapi() {
        assertEquals("com.salat.gbinder.media.BIND", MediaBridgeContract.SERVICE_ACTION);
        assertEquals("com.salat.gbinder.media.bridge.MediaBridgeService",
                MediaBridgeContract.SERVICE_CLASS);
        assertEquals(1, MediaBridgeContract.VERSION);
        assertEquals(1, MediaBridgeContract.REGISTER);
        assertEquals(4, MediaBridgeContract.COMMAND);
        assertEquals(100, MediaBridgeContract.REGISTERED);
        assertEquals(103, MediaBridgeContract.ERROR);
        assertEquals(0x40L, MediaBridgeContract.CAP_SET_SOURCE);
    }
}
