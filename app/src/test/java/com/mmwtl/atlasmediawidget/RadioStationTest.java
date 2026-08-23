package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RadioStationTest {
    @Test public void acceptsPositiveFrequenciesOutsideLegacyRanges() {
        assertTrue(RadioStation.isValidFrequency(68_000));
        assertTrue(RadioStation.isValidFrequency(149));
        assertFalse(RadioStation.isValidFrequency(0));
        assertFalse(RadioStation.isValidFrequency(-1));
    }

    @Test public void formatsMissingOirtFrequencyInMhz() {
        assertEquals("68 MHz", station(68_000, "").displayName());
        assertEquals("65.8 MHz", station(65_800, "").displayDetail());
    }

    @Test public void formatsMissingLongWaveFrequencyInKhz() {
        assertEquals("149 kHz", station(149, "").displayName());
    }

    @Test public void preservesBackendFormattedFrequency() {
        assertEquals("100,1 МГц", station(100_100, "100,1 МГц").displayName());
    }

    private static RadioStation station(int frequency, String formattedFrequency) {
        return new RadioStation("station", frequency, formattedFrequency, 1, "", "", "", "",
                "", 0, 0, "", false, "");
    }
}
