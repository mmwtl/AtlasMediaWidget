package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class RadioCatalogTest {
    @Test public void catalogOverridesRdsWithConfiguredNameAndFrequencySubtitle() {
        Map<Integer, RadioStation> stations = new HashMap<>();
        stations.put(100_100, new RadioStation(100_100, "Радио 7", "FM",
                ArtworkRef.asset("radio/covers/radio7.webp")));
        RadioDisplay display = new RadioCatalog(stations).display(snapshot(
                "radio:0:100100:RDS", "RDS station", "100.1 MHz"));

        assertEquals("Радио 7", display.title);
        assertEquals("FM 100.1", display.subtitle);
        assertEquals("ASSET:radio/covers/radio7.webp", display.artwork.cacheKey());
    }

    @Test public void unknownFrequencyStillUsesTuningAndBridgeTitle() {
        RadioDisplay display = new RadioCatalog(Collections.emptyMap()).display(snapshot(
                "radio:0:100500:RDS", "RDS station", "100.5 MHz"));

        assertEquals("RDS station", display.title);
        assertEquals("FM 100.5", display.subtitle);
        assertEquals(ArtworkRef.Kind.NONE, display.artwork.kind);
    }

    private static MediaSnapshot snapshot(String mediaId, String title, String artist) {
        return new MediaSnapshot(
                MediaBridgeContract.VERSION, 1L, 1L, true, 0, "", MediaSource.Id.RADIO, "",
                Collections.emptyList(), "pkg", "app", mediaId, title, artist, "",
                -1L, -1L, 0L, 0f, 0, 0, "", 0L, 0L, "", 0L);
    }
}
