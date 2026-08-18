package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CompactDialogTest {
    @Test
    public void widthUsesScreenWithSideMarginsWhenScreenIsNarrowerThanMaximum() {
        assertEquals(1296, CompactDialog.calculateWidthPx(1440, 3f));
    }

    @Test
    public void widthDoesNotExceedMaximumOnWideScreen() {
        assertEquals(1920, CompactDialog.calculateWidthPx(2560, 3f));
    }

    @Test
    public void widthRemainsPositiveForVeryNarrowWindow() {
        assertTrue(CompactDialog.calculateWidthPx(100, 3f) > 0);
    }
}
