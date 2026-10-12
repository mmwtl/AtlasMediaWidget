package com.mmwtl.atlasmediaapi.media.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCommandGateTest {
    @Test
    fun `newer command cancels a waiting source switch and runs`() = runBlocking {
        val gate = MediaCommandGate()
        val switchStarted = CompletableDeferred<Unit>()
        var preempted = false
        val sourceSwitch = async {
            gate.run(interruptible = true, onPreempted = { preempted = true }) {
                switchStarted.complete(Unit)
                awaitCancellation()
            }
        }
        switchStarted.await()

        assertTrue(gate.preempt())
        val pause = withTimeout(1_000L) { gate.run(interruptible = false) { "paused" } }

        assertEquals("paused", pause)
        assertTrue(preempted)
        assertTrue(sourceSwitch.isCancelled)
    }

    @Test
    fun `quick commands are not cancelled`() = runBlocking {
        val gate = MediaCommandGate()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = false) {
                started.complete(Unit)
                release.await()
                "next"
            }
        }
        started.await()

        assertFalse(gate.preempt())
        release.complete(Unit)
        assertEquals("next", next.await())
    }

    @Test
    fun `finished source switch can no longer be preempted`() = runBlocking {
        val gate = MediaCommandGate()
        gate.run(interruptible = true) { Unit }

        assertFalse(gate.preempt())
    }
}
