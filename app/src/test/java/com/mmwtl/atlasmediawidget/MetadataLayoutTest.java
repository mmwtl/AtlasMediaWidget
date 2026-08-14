package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MetadataLayoutTest {
    @Test public void shortContentRemainsCenteredInRequestedCorridor() {
        assertEquals(120, MetadataLayout.resolveTop(100, 40, 180, 166));
    }

    @Test public void wrappedTitleMovesWholeBlockAboveSafeBottom() {
        assertEquals(76, MetadataLayout.resolveTop(100, 90, 180, 166));
    }

    @Test public void downwardOffsetCannotCrossSafeBottom() {
        assertEquals(116, MetadataLayout.resolveTop(150, 50, 180, 166));
    }
}
