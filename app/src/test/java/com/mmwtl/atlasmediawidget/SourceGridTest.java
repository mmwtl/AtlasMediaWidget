package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SourceGridTest {
    private static int[] rows(int count, int width, int height) {
        return SourceGrid.plan(count, width, height, 80, 90).rowSizes;
    }

    @Test public void squareChooserKeepsTheTwoRowsOfEarlierVersions() {
        assertArrayEquals(new int[] {2, 3}, rows(5, 600, 500));
        assertArrayEquals(new int[] {3, 3}, rows(6, 600, 500));
        assertArrayEquals(new int[] {2, 2}, rows(4, 600, 500));
    }

    @Test public void flatWideChooserPutsAllTilesInOneRow() {
        assertArrayEquals(new int[] {6}, rows(6, 1300, 220));
        assertArrayEquals(new int[] {5}, rows(5, 1300, 220));
        assertArrayEquals(new int[] {2}, rows(2, 600, 300));
    }

    @Test public void tallNarrowChooserStacksPairs() {
        assertArrayEquals(new int[] {2, 2, 2}, rows(6, 420, 900));
        assertArrayEquals(new int[] {1, 2, 2}, rows(5, 420, 900));
        assertArrayEquals(new int[] {1, 1}, rows(2, 300, 700));
    }

    @Test public void tilesTooNarrowForTheirLabelMoveToAnotherRow() {
        // Six in one row would be 70 px wide, below the label's 80 px.
        assertArrayEquals(new int[] {3, 3}, rows(6, 420, 200));
    }

    @Test public void indicesMapToRowsTopToBottom() {
        SourceGrid grid = SourceGrid.plan(5, 600, 500, 80, 90);
        assertEquals(3, grid.columns);
        assertEquals(0, grid.rowOf(1));
        assertEquals(1, grid.columnOf(1));
        assertEquals(1, grid.rowOf(2));
        assertEquals(0, grid.columnOf(2));
        assertEquals(-1, grid.rowOf(5));
    }

    @Test public void singleAndEmptyChoosers() {
        assertArrayEquals(new int[] {1}, rows(1, 600, 500));
        assertEquals(0, SourceGrid.plan(0, 600, 500, 80, 90).rows());
    }
}
