package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ShownPlayerHoldTest {
    @Test
    fun `shown player that just stopped keeps the card from another playing player`() {
        assertEquals(1_000L, remaining(stoppedAt = 100L, now = 100L))
        assertEquals(400L, remaining(stoppedAt = 100L, now = 700L))
    }

    @Test
    fun `other player takes the card once the hold expires`() {
        assertEquals(0L, remaining(stoppedAt = 0L, now = 1_000L))
        assertEquals(0L, remaining(stoppedAt = 0L, now = 1_200L))
    }

    @Test
    fun `shown player that did not stop playing is not held`() {
        assertEquals(0L, remaining(stoppedAt = null, now = 0L))
    }

    @Test
    fun `selection of the shown player or of nothing is not held`() {
        assertEquals(0L, ShownPlayerHold.remainingMs("com.example.shown", "com.example.shown", 0L, 0L, 1_000L))
        assertEquals(0L, ShownPlayerHold.remainingMs(null, "com.example.shown", 0L, 0L, 1_000L))
    }

    @Test
    fun `gone shown player is not held`() {
        assertEquals(0L, ShownPlayerHold.remainingMs("com.example.other", null, 0L, 0L, 1_000L))
    }

    private fun remaining(stoppedAt: Long?, now: Long): Long = ShownPlayerHold.remainingMs(
        selected = "com.example.other",
        shown = "com.example.shown",
        shownStoppedPlayingAt = stoppedAt,
        now = now,
        graceMs = 1_000L,
    )
}
