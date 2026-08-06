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
}
