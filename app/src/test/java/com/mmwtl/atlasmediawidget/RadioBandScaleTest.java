package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RadioBandScaleTest {
    @Test public void fmKilohertzMediaIdPlacesMarkerOnTheBand() {
        RadioBandScale scale = RadioBandScale.fromMediaId("radio:1:100100:Радио 7");
        assertEquals("87.5", scale.minLabel);
        assertEquals("108", scale.maxLabel);
        assertEquals((100_100 - 87_500) / 20_500f, scale.fraction(), 0.0001f);
        assertEquals(88_000, scale.firstTick());
        assertTrue(scale.isMajor(100_000));
    }

    @Test public void legacyFmHundredthsUseTheSameEdges() {
        RadioBandScale scale = RadioBandScale.fromMediaId("radio:1:10010:");
        assertEquals("87.5", scale.minLabel);
        assertEquals("108", scale.maxLabel);
        assertEquals((10_010 - 8_750) / 2_050f, scale.fraction(), 0.0001f);
    }

    @Test public void mediumWaveOutsideConventionalEdgesIsClamped() {
        RadioBandScale scale = RadioBandScale.fromMediaId("radio:2:510:");
        assertEquals("522", scale.minLabel);
        assertEquals("1620", scale.maxLabel);
        assertEquals(0f, scale.fraction(), 0f);
    }

    @Test public void mediaWithoutKnownFrequencyHasNoScale() {
        assertNull(RadioBandScale.fromMediaId(""));
        assertNull(RadioBandScale.fromMediaId("aimp:42"));
        assertNull(RadioBandScale.fromMediaId("radio:1:abc:"));
        assertNull(RadioBandScale.fromMediaId("radio:1:5000:"));
    }
}
