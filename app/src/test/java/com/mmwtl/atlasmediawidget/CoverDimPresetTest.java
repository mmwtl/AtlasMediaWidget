package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class CoverDimPresetTest {
    @Test public void maximumPreservesOriginalGradients() {
        assertArrayEquals(new int[]{0x5E1D2228, 0x221D2228, 0xAD1D2228, 0xF51D2228},
                CoverDimPreset.MAXIMUM.colors(CardStyle.COMPACT));
        assertArrayEquals(new int[]{0x601D2228, 0x101D2228, 0xB01D2228, 0xFA1D2228},
                CoverDimPreset.MAXIMUM.colors(CardStyle.SQUARE));
    }

    @Test public void mediumIsDefaultAndPreferenceFallback() {
        assertEquals(CoverDimPreset.MEDIUM, CoverDimPreset.DEFAULT);
        assertEquals(CoverDimPreset.MEDIUM, CoverDimPreset.fromPreference(-1));
    }

    @Test public void backupNamesRoundTripAndRejectUnknownValue() {
        for (CoverDimPreset preset : CoverDimPreset.values()) {
            assertEquals(preset, CoverDimPreset.fromBackupName(preset.backupName));
        }
        assertNull(CoverDimPreset.fromBackupName("unknown"));
    }
}
