package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MediaSourceTest {
    @Test public void knownAndUnknownWireValuesMapDeterministically() {
        assertEquals(MediaSource.Id.BT, MediaSource.Id.fromWire("BT"));
        assertEquals(MediaSource.Id.CPAA, MediaSource.Id.fromWire("CPAA"));
        assertEquals(MediaSource.Id.UNKNOWN, MediaSource.Id.fromWire("NEW_SOURCE"));
        assertEquals(MediaSource.Id.UNKNOWN, MediaSource.Id.fromWire(null));
    }
}
