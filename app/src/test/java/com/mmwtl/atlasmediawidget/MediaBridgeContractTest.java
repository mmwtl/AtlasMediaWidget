package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MediaBridgeContractTest {
    @Test public void protocolV1ConstantsMatchAtlasMediaApi() {
        assertEquals("com.mmwtl.atlasmediaapi.media.BIND", MediaBridgeContract.SERVICE_ACTION);
        assertEquals(BuildConfig.INTEGRATED_MEDIA_API
                        ? "com.mmwtl.atlasmediawidget"
                        : "com.mmwtl.atlasmediaapi",
                MediaBridgeContract.SERVICE_PACKAGE);
        assertEquals("com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService",
                MediaBridgeContract.SERVICE_CLASS);
        assertEquals(1, MediaBridgeContract.VERSION);
        assertEquals(1, MediaBridgeContract.REGISTER);
        assertEquals(4, MediaBridgeContract.COMMAND);
        assertEquals(5, MediaBridgeContract.GET_RADIO_STATIONS);
        assertEquals(100, MediaBridgeContract.REGISTERED);
        assertEquals(103, MediaBridgeContract.ERROR);
        assertEquals(104, MediaBridgeContract.RADIO_STATIONS);
        assertEquals(0x40L, MediaBridgeContract.CAP_SET_SOURCE);
        assertEquals(0x80L, MediaBridgeContract.CAP_TUNE_RADIO);
        assertEquals("radioFavoriteStations",
                MediaBridgeContract.K_RADIO_FAVORITE_STATIONS);
        assertEquals("uiScaleTenths", MediaBridgeContract.K_UI_SCALE_TENTHS);
    }
}
