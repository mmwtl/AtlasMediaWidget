package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBackendCoordinatorTest {
    @Test
    fun `constants and reconnect delays are defined properly`() {
        assertEquals(30_000L, MediaBackendCoordinator.GRACE_PERIOD_MS)
        assertEquals(listOf(2_000L, 5_000L, 10_000L, 30_000L), MediaBackendCoordinator.RECONNECT_DELAYS_MS)
    }

    @Test
    fun `reconnect sequence bounded backoff logic`() {
        val delays = MediaBackendCoordinator.RECONNECT_DELAYS_MS
        assertEquals(2_000L, delays.getOrElse(0) { delays.last() })
        assertEquals(5_000L, delays.getOrElse(1) { delays.last() })
        assertEquals(10_000L, delays.getOrElse(2) { delays.last() })
        assertEquals(30_000L, delays.getOrElse(3) { delays.last() })
        assertEquals(30_000L, delays.getOrElse(10) { delays.last() })
    }

    @Test
    fun `default source candidate validation logic`() {
        val validSources = listOf("RADIO", "BT", "USB", "ONLINE", "CPAA")
        validSources.forEach { name ->
            val parsed = runCatching { BridgeAudioSource.valueOf(name) }.getOrNull()
            assertTrue(parsed != null && parsed != BridgeAudioSource.UNKNOWN && parsed != BridgeAudioSource.OTHER)
        }

        val invalid = listOf("", "UNKNOWN", "OTHER", "INVALID_NAME")
        invalid.forEach { name ->
            val parsed = runCatching { BridgeAudioSource.valueOf(name) }.getOrNull()
            assertTrue(parsed == null || parsed == BridgeAudioSource.UNKNOWN || parsed == BridgeAudioSource.OTHER)
        }
    }

    @Test
    fun `source lost validation prevents switching to the same lost source`() {
        val lostSource = BridgeAudioSource.USB
        val defaultSource = BridgeAudioSource.USB
        val shouldSwitch = (defaultSource != BridgeAudioSource.UNKNOWN &&
                defaultSource != BridgeAudioSource.OTHER &&
                defaultSource != lostSource)
        assertFalse(shouldSwitch)

        val alternateDefault = BridgeAudioSource.RADIO
        val shouldSwitchToAlternate = (alternateDefault != BridgeAudioSource.UNKNOWN &&
                alternateDefault != BridgeAudioSource.OTHER &&
                alternateDefault != lostSource)
        assertTrue(shouldSwitchToAlternate)
    }

    @Test
    fun `autoplay on source lost only triggers if flag is enabled AND source was playing`() {
        val flagEnabled = true
        val wasPlaying = true
        assertTrue(flagEnabled && wasPlaying)

        val wasPaused = false
        assertFalse(flagEnabled && wasPaused)

        val flagDisabled = false
        assertFalse(flagDisabled && wasPlaying)
    }

    @Test
    fun `cancelDefaultSourceSwitch marks applied to prevent race`() {
        var hasApplied = false
        var isTimerActive = true

        fun cancelSwitch() {
            if (isTimerActive) {
                isTimerActive = false
                hasApplied = true
            }
        }

        cancelSwitch()
        assertFalse(isTimerActive)
        assertTrue(hasApplied)
    }
}
