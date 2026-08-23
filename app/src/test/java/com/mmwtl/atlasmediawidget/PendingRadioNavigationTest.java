package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PendingRadioNavigationTest {
    @Test public void sourceSelectionCancelsPendingRadioNavigationImmediately() {
        PendingRadioNavigation pending = new PendingRadioNavigation();
        pending.schedule(1);

        pending.cancelIfSourceChanged(MediaSource.Id.BT);

        assertEquals(0, pending.consume(MediaSource.Id.RADIO));
    }

    @Test public void stationResponseCannotConsumePendingDirectionForAnotherSource() {
        PendingRadioNavigation pending = new PendingRadioNavigation();
        pending.schedule(-1);

        assertEquals(0, pending.consume(MediaSource.Id.USB));
        assertEquals(0, pending.consume(MediaSource.Id.RADIO));
    }

    @Test public void radioResponseConsumesDirectionOnlyOnce() {
        PendingRadioNavigation pending = new PendingRadioNavigation();
        pending.schedule(1);

        assertEquals(1, pending.consume(MediaSource.Id.RADIO));
        assertEquals(0, pending.consume(MediaSource.Id.RADIO));
    }
}
