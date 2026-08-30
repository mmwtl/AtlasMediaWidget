package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public final class RadioStationListsTest {
    @Test public void navigationUsesIndependentFavoritesListWhenRequested() {
        RadioStation saved = station("saved", 94_200);
        RadioStation favorite = station("favorite", 100_100);
        RadioStationLists lists = new RadioStationLists(
                1L, List.of(saved), List.of(favorite));

        assertEquals(List.of(saved), lists.navigationStations(false));
        assertEquals(List.of(favorite), lists.navigationStations(true));
    }

    private static RadioStation station(String id, int frequencyKHz) {
        return new RadioStation(id, frequencyKHz, "", 1, "FM", id,
                "", id, "", 0, 0, "", false, "");
    }
}
