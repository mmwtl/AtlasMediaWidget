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

    @Test public void widgetChoicesAreLimitedToFourUserFacingSources() {
        assertEquals(true, MediaSource.Id.BT.isWidgetChoice());
        assertEquals(true, MediaSource.Id.RADIO.isWidgetChoice());
        assertEquals(true, MediaSource.Id.USB.isWidgetChoice());
        assertEquals(true, MediaSource.Id.ONLINE.isWidgetChoice());
        assertEquals(false, MediaSource.Id.CPAA.isWidgetChoice());
        assertEquals(false, MediaSource.Id.YUNTING.isWidgetChoice());
    }

    @Test public void yuntingIsPresentedAsOnlineWithoutChangingWireId() {
        assertEquals(MediaSource.Id.ONLINE, MediaSource.Id.YUNTING.displayId());
        assertEquals(MediaSource.Id.YUNTING, MediaSource.Id.fromWire("YUNTING"));
    }

    @Test public void onlyUserFacingMediaSourcesCanBeOpened() {
        assertEquals(true, MediaSourceLauncher.canOpen(MediaSource.Id.RADIO));
        assertEquals(true, MediaSourceLauncher.canOpen(MediaSource.Id.YUNTING));
        assertEquals(false, MediaSourceLauncher.canOpen(MediaSource.Id.UNKNOWN));
        assertEquals(false, MediaSourceLauncher.canOpen(MediaSource.Id.CPAA));
    }
}
