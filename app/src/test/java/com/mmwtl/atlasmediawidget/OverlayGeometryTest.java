package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OverlayGeometryTest {
    private static final int LEFT = 10;
    private static final int TOP = 20;
    private static final int RIGHT = 1010;
    private static final int BOTTOM = 1520;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 400;

    @Test public void zeroOffsetsAnchorEachCorner() {
        assertPosition(OverlayCorner.TOP_START, 10, 20);
        assertPosition(OverlayCorner.TOP_END, 710, 20);
        assertPosition(OverlayCorner.BOTTOM_START, 10, 1120);
        assertPosition(OverlayCorner.BOTTOM_END, 710, 1120);
    }

    @Test public void offsetsMoveAwayFromSelectedEdges() {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(
                OverlayCorner.BOTTOM_END, LEFT, TOP, RIGHT, BOTTOM,
                WIDTH, HEIGHT, 7, 11);

        assertEquals(703, position.x());
        assertEquals(1109, position.y());
    }

    @Test public void reverseConversionRoundTripsEveryCorner() {
        for (OverlayCorner corner : OverlayCorner.values()) {
            OverlayGeometry.Position position = OverlayGeometry.positionFor(corner,
                    LEFT, TOP, RIGHT, BOTTOM, WIDTH, HEIGHT, 37, 53);
            OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(corner,
                    LEFT, TOP, RIGHT, BOTTOM, WIDTH, HEIGHT, position.x(), position.y());

            assertEquals(37, offsets.x());
            assertEquals(53, offsets.y());
        }
    }

    @Test public void coordinatesClampToSafeBounds() {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(
                OverlayCorner.TOP_START, LEFT, TOP, RIGHT, BOTTOM,
                WIDTH, HEIGHT, 10_000, 10_000);

        assertEquals(710, position.x());
        assertEquals(1120, position.y());
        OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(OverlayCorner.TOP_START,
                LEFT, TOP, RIGHT, BOTTOM, WIDTH, HEIGHT, -500, 9999);
        assertEquals(0, offsets.x());
        assertEquals(1100, offsets.y());
    }

    @Test public void cardLargerThanBoundsStaysAtSafeOrigin() {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(
                OverlayCorner.BOTTOM_END, LEFT, TOP, 200, 300,
                500, 600, 0, 0);

        assertEquals(10, position.x());
        assertEquals(20, position.y());
    }

    @Test public void veryLargeOffsetsDoNotOverflow() {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(
                OverlayCorner.TOP_START, LEFT, TOP, RIGHT, BOTTOM,
                WIDTH, HEIGHT, Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertEquals(710, position.x());
        assertEquals(1120, position.y());
    }

    @Test public void bottomOffsetKeepsFrameBottomWhenCardHeightChanges() {
        int safeTop = 24;
        int safeBottom = 1920;
        int offsetY = 37;

        for (OverlayCorner corner : new OverlayCorner[] {
                OverlayCorner.BOTTOM_START, OverlayCorner.BOTTOM_END}) {
            OverlayGeometry.Position shortCard = OverlayGeometry.positionFor(
                    corner, 0, safeTop, 1440, safeBottom,
                    500, 150, 0, offsetY);
            OverlayGeometry.Position tallCard = OverlayGeometry.positionFor(
                    corner, 0, safeTop, 1440, safeBottom,
                    500, 500, 0, offsetY);

            assertEquals(safeBottom - offsetY, shortCard.y() + 150);
            assertEquals(safeBottom - offsetY, tallCard.y() + 500);
        }
    }

    @Test public void safeAreaPositionCanBeConvertedToRelativeLayoutParams() {
        int safeLeft = 12;
        int safeTop = 24;
        OverlayGeometry.Position absolute = OverlayGeometry.positionFor(
                OverlayCorner.BOTTOM_END, safeLeft, safeTop, 1452, 1920,
                500, 150, 31, 37);
        OverlayGeometry.Position fromRelativeParams = OverlayGeometry.positionFor(
                OverlayCorner.TOP_START, safeLeft, safeTop, 1452, 1920,
                500, 150, absolute.x() - safeLeft, absolute.y() - safeTop);

        assertEquals(absolute.x(), fromRelativeParams.x());
        assertEquals(absolute.y(), fromRelativeParams.y());
    }

    private static void assertPosition(OverlayCorner corner, int x, int y) {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(corner,
                LEFT, TOP, RIGHT, BOTTOM, WIDTH, HEIGHT, 0, 0);
        assertEquals(x, position.x());
        assertEquals(y, position.y());
    }
}
