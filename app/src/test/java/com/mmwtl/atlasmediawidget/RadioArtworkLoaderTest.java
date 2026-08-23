package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class RadioArtworkLoaderTest {
    @Test public void thumbnailDecodeUsesPowerOfTwoSampling() {
        assertEquals(1, RadioArtworkLoader.sampleSize(256, 128));
        assertEquals(2, RadioArtworkLoader.sampleSize(512, 256));
        assertEquals(8, RadioArtworkLoader.sampleSize(1920, 1080));
        assertEquals(8_388_608, RadioArtworkLoader.sampleSize(Integer.MAX_VALUE, 1));
        assertEquals(1, RadioArtworkLoader.sampleSize(0, 1080));
    }
}
