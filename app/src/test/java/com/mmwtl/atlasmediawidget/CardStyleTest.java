package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CardStyleTest {
    @Test public void restoresKnownStyles() {
        assertEquals(CardStyle.COMPACT, CardStyle.fromPreference(0));
        assertEquals(CardStyle.SQUARE, CardStyle.fromPreference(1));
    }

    @Test public void unknownStyleFallsBackToSquare() {
        assertEquals(CardStyle.SQUARE, CardStyle.fromPreference(99));
    }

    @Test public void presetsKeepReferenceDimensions() {
        assertEquals(500, CardStyle.COMPACT.defaultWidthDp);
        assertEquals(300, CardStyle.COMPACT.defaultHeightDp);
        assertEquals(500, CardStyle.SQUARE.defaultWidthDp);
        assertEquals(500, CardStyle.SQUARE.defaultHeightDp);
        assertEquals(84, CardStyle.COMPACT.defaultControlPanelHeightDp);
        assertEquals(102, CardStyle.SQUARE.defaultControlPanelHeightDp);
    }
}
