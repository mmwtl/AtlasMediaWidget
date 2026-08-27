package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RadioArtworkLoaderTest {
    @Test public void thumbnailDecodeUsesPowerOfTwoSampling() {
        assertEquals(1, RadioArtworkLoader.sampleSize(256, 128));
        assertEquals(2, RadioArtworkLoader.sampleSize(512, 256));
        assertEquals(8, RadioArtworkLoader.sampleSize(1920, 1080));
        assertEquals(8_388_608, RadioArtworkLoader.sampleSize(Integer.MAX_VALUE, 1));
        assertEquals(1, RadioArtworkLoader.sampleSize(0, 1080));
    }

    @Test public void transientFailureGetsTwoRetriesWithBoundedBackoff() {
        assertTrue(RadioArtworkLoader.shouldRetry(1));
        assertTrue(RadioArtworkLoader.shouldRetry(2));
        assertFalse(RadioArtworkLoader.shouldRetry(3));
        assertEquals(250L, RadioArtworkLoader.retryDelayMillis(1));
        assertEquals(500L, RadioArtworkLoader.retryDelayMillis(2));
    }
}
