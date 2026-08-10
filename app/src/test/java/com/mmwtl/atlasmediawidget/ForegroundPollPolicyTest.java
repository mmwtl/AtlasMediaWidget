package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ForegroundPollPolicyTest {
    @Test public void initialUsageQueryIsBoundedToFiveMinutes() {
        long now = 1_000_000L;

        assertEquals(700_000L, ForegroundPollPolicy.queryBegin(now, 0L));
        assertEquals(700_000L, ForegroundPollPolicy.queryBegin(now, now + 1L));
    }

    @Test public void incrementalUsageQueryKeepsSmallOverlap() {
        long now = 1_000_000L;

        assertEquals(988_000L, ForegroundPollPolicy.queryBegin(now, 990_000L));
        assertEquals(940_000L, ForegroundPollPolicy.queryBegin(now, 100_000L));
    }

    @Test public void probesQuicklyOnlyDuringStartupWindow() {
        assertEquals(250L, ForegroundPollPolicy.nextDelay(false, true, 5_000L, 10_000L));
        assertEquals(1_500L,
                ForegroundPollPolicy.nextDelay(false, true, 10_000L, 10_000L));
        assertEquals(1_000L,
                ForegroundPollPolicy.nextDelay(true, true, 5_000L, 10_000L));
        assertEquals(1_500L,
                ForegroundPollPolicy.nextDelay(false, false, 5_000L, 10_000L));
    }
}
