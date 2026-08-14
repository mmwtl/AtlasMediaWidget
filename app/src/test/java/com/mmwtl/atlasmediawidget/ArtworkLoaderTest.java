package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ArtworkLoaderTest {
    @Test public void keepsSmallArtworkAtFullResolution() {
        assertEquals(1, ArtworkLoader.calculateInSampleSize(1024, 1024));
    }

    @Test public void downsamplesLargeArtworkToBoundedDimensions() {
        assertEquals(4, ArtworkLoader.calculateInSampleSize(3840, 2160));
        assertEquals(2, ArtworkLoader.calculateInSampleSize(2048, 2048));
    }
}
