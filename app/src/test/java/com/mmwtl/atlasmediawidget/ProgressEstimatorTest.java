package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProgressEstimatorTest {
    @Test public void playingPositionUsesMonotonicBaseAndSpeed() {
        assertEquals(12_000L, ProgressEstimator.estimate(
                10_000L, 60_000L, 1_000L, 1f,
                MediaSnapshot.STATE_PLAYING, 3_000L));
        assertEquals(14_000L, ProgressEstimator.estimate(
                10_000L, 60_000L, 1_000L, 2f,
                MediaSnapshot.STATE_PLAYING, 3_000L));
    }

    @Test public void pausedPositionDoesNotMoveAndResultIsClamped() {
        assertEquals(10_000L, ProgressEstimator.estimate(
                10_000L, 60_000L, 1_000L, 1f, 2, 20_000L));
        assertEquals(11_000L, ProgressEstimator.estimate(
                10_000L, 11_000L, 1_000L, 1f,
                MediaSnapshot.STATE_PLAYING, 20_000L));
        assertEquals(-1L, ProgressEstimator.estimate(
                -1L, 11_000L, 1_000L, 1f,
                MediaSnapshot.STATE_PLAYING, 20_000L));
    }
}
