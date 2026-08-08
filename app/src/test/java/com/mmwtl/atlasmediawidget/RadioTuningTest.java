package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;

public final class RadioTuningTest {
    @Test public void readsOneOsFrequencyFromVersionedMediaId() {
        RadioTuning tuning = RadioTuning.from(snapshot("radio:0:100100:"));

        assertEquals(100_100, tuning.frequencyKHz);
        assertEquals("FM", tuning.band);
        assertEquals("100.1", tuning.displayFrequency());
        assertEquals("FM 100.1", tuning.subtitle());
    }

    @Test public void normalizesAlternativeFmUnits() {
        assertEquals(100_100,
                RadioTuning.from(snapshot("radio:FM:10010:station")).frequencyKHz);
        assertEquals(100_100,
                RadioTuning.from(snapshot("radio:FM:100100000:station")).frequencyKHz);
    }

    @Test public void rejectsNonRadioAndMalformedIds() {
        assertNull(RadioTuning.from(snapshot("not-radio")));
        assertNull(RadioTuning.from(nonRadioSnapshot("radio:0:100100:")));
    }

    private static MediaSnapshot snapshot(String mediaId) {
        return createSnapshot(MediaSource.Id.RADIO, mediaId);
    }

    private static MediaSnapshot nonRadioSnapshot(String mediaId) {
        return createSnapshot(MediaSource.Id.BT, mediaId);
    }

    private static MediaSnapshot createSnapshot(MediaSource.Id source, String mediaId) {
        return new MediaSnapshot(
                MediaBridgeContract.VERSION, 1L, 1L, true, 0, "", source, "",
                Collections.emptyList(), "pkg", "app", mediaId, "", "", "",
                -1L, -1L, 0L, 0f, 0, 0, "", 0L, 0L, "", 0L);
    }
}
