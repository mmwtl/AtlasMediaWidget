package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReconnectPolicyTest {
    @Test public void backoffGrowsAndIsBounded() {
        assertEquals(500L, ReconnectPolicy.delayMs(0));
        assertEquals(1_000L, ReconnectPolicy.delayMs(1));
        assertEquals(8_000L, ReconnectPolicy.delayMs(4));
        assertEquals(15_000L, ReconnectPolicy.delayMs(10));
        assertEquals(500L, ReconnectPolicy.delayMs(-5));
    }
}
