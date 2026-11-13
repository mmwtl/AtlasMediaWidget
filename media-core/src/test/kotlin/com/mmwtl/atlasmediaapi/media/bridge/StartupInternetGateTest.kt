package com.mmwtl.atlasmediaapi.media.bridge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupInternetGateTest {
    @Test
    fun `available internet passes without waiting`() = runBlocking {
        var checks = 0
        assertTrue(StartupInternetGate.await(timeoutMs = 1L, pollIntervalMs = 1_000L) {
            checks++
            true
        })
        assertEquals(1, checks)
    }

    @Test
    fun `internet that appears within the limit releases the action`() = runBlocking {
        var checks = 0
        assertTrue(StartupInternetGate.await(timeoutMs = 1_000L, pollIntervalMs = 5L) {
            ++checks >= 3
        })
        assertEquals(3, checks)
    }

    @Test
    fun `missing internet skips the action after the limit`() = runBlocking {
        assertFalse(StartupInternetGate.await(timeoutMs = 30L, pollIntervalMs = 5L) { false })
    }
}
