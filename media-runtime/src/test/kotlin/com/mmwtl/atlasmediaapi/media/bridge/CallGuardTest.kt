package com.mmwtl.atlasmediaapi.media.bridge

import android.media.AudioManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CallGuardTest {
    @Test
    fun `call audio modes are recognised`() {
        assertTrue(CallGuard.isCallAudioMode(AudioManager.MODE_IN_CALL))
        assertTrue(CallGuard.isCallAudioMode(AudioManager.MODE_IN_COMMUNICATION))
        assertTrue(CallGuard.isCallAudioMode(AudioManager.MODE_RINGTONE))
        assertTrue(CallGuard.isCallAudioMode(AudioManager.MODE_CALL_SCREENING))
        assertFalse(CallGuard.isCallAudioMode(AudioManager.MODE_NORMAL))
    }

    @Test
    fun `action runs without a call`() = runBlocking {
        assertEquals(true, CallGuard.runUnlessCallActive(isCallActive = { false }, pollMs = 10L) { true })
    }

    @Test
    fun `active call skips the action`() = runBlocking {
        val runs = AtomicInteger()
        val result = CallGuard.runUnlessCallActive(isCallActive = { true }, pollMs = 10L) {
            runs.incrementAndGet()
            true
        }
        assertNull(result)
        assertEquals(0, runs.get())
    }

    @Test
    fun `call starting mid-action stops further play retries`() = runBlocking {
        val call = AtomicBoolean(false)
        val plays = AtomicInteger()
        val result = CallGuard.runUnlessCallActive(isCallActive = call::get, pollMs = 10L) {
            repeat(5) {
                plays.incrementAndGet()
                if (plays.get() == 2) call.set(true)
                delay(100L)
            }
            true
        }
        assertNull(result)
        assertEquals(2, plays.get())
    }

    @Test
    fun `cancellation without a call is propagated`() = runBlocking {
        val outer = async {
            CallGuard.runUnlessCallActive(isCallActive = { false }, pollMs = 10L) {
                delay(10_000L)
                true
            }
        }
        delay(50L)
        outer.cancel()
        val cancelled = try {
            outer.await()
            false
        } catch (_: CancellationException) {
            true
        }
        assertTrue(cancelled)
    }
}
