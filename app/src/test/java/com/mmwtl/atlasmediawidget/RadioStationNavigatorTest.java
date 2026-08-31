package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class RadioStationNavigatorTest {
    private final RadioStation first = station("first", 94_200, 1, "Радио России");
    private final RadioStation second = station("second", 100_100, 1, "Радио 7");
    private final RadioStation third = station("third", 174_928, 3, "DAB One");
    private final List<RadioStation> stations = List.of(first, second, third);

    @Test public void selectsNextSavedStationFromSnapshotMediaId() {
        assertEquals(third, RadioStationNavigator.adjacent(stations,
                snapshot("radio:1:100100:Радио 7", "Радио 7", "100.1 MHz"), 1));
    }

    @Test public void wrapsPreviousSavedStation() {
        assertEquals(third, RadioStationNavigator.adjacent(stations,
                snapshot("radio:1:94200:Радио России", "Радио России", "94.2 MHz"), -1));
    }

    @Test public void fallsBackToDisplayedFrequencyWhenMediaIdIsUnavailable() {
        assertEquals(first, RadioStationNavigator.adjacent(stations,
                snapshot("", "Радио 7", "100.1 MHz"), -1));
    }

    @Test public void unknownCurrentStationStartsAtRequestedEdge() {
        assertEquals(first, RadioStationNavigator.adjacent(stations,
                snapshot("", "Неизвестная", "99.0 MHz"), 1));
        assertEquals(third, RadioStationNavigator.adjacent(stations,
                snapshot("", "Неизвестная", "99.0 MHz"), -1));
    }

    @Test public void nonFavoriteStationSelectsNearestStationInRequestedDirection() {
        MediaSnapshot current = snapshot("radio:1:97000:Неизвестная", "Неизвестная", "97 MHz");

        assertEquals(second, RadioStationNavigator.adjacent(stations, current, 1));
        assertEquals(first, RadioStationNavigator.adjacent(stations, current, -1));
    }

    @Test public void nonFavoriteStationWrapsOnlyWhenNoStationRemainsInDirection() {
        MediaSnapshot beforeFirst = snapshot(
                "radio:1:90000:Неизвестная", "Неизвестная", "90 MHz");
        MediaSnapshot afterLast = snapshot(
                "radio:3:200000:Неизвестная", "Неизвестная", "200 MHz");

        assertEquals(third, RadioStationNavigator.adjacent(stations, beforeFirst, -1));
        assertEquals(first, RadioStationNavigator.adjacent(stations, afterLast, 1));
    }

    @Test public void emptyListDoesNotProduceTarget() {
        assertNull(RadioStationNavigator.adjacent(List.of(),
                snapshot("", "", ""), 1));
    }

    @Test public void currentOnlySavedStationDoesNotTuneItAgain() {
        assertNull(RadioStationNavigator.adjacent(List.of(second),
                snapshot("radio:1:100100:Радио 7", "Радио 7", "100.1 MHz"), 1));
    }

    @Test public void unknownOnlySavedStationCanStillBeSelected() {
        assertEquals(second, RadioStationNavigator.adjacent(List.of(second),
                snapshot("radio:1:100050:Неизвестная", "Неизвестная", "100.05 MHz"), 1));
    }

    @Test public void frequencyMismatchFallsBackToNormalizedStationName() {
        assertEquals(third, RadioStationNavigator.adjacent(stations,
                snapshot("radio:1:100050:Радио 7", "  РАДИО 7  ", "100.05 MHz"), 1));
    }

    private static RadioStation station(String id, int frequency, int band, String name) {
        return new RadioStation(id, frequency,
                frequency == 94_200 ? "94.2 MHz"
                        : frequency == 100_100 ? "100.1 MHz" : "174.928 MHz",
                band, band == 3 ? "DAB" : "FM", name, "", name,
                "", 0, 0, "", true, "");
    }

    private static MediaSnapshot snapshot(String mediaId, String title, String artist) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                MediaSource.Id.RADIO, "", List.of(), "radio", "RADIO", mediaId,
                title, artist, "", -1, -1, 0, 1f, MediaSnapshot.STATE_PLAYING,
                0, "", 0, MediaBridgeContract.CAP_TUNE_RADIO, "", 0);
    }
}
