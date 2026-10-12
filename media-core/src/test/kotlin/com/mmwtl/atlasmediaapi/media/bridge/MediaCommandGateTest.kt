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
        val switchTicket = gate.admit()
        val sourceSwitch = async {
            gate.run(
                interruptible = true,
                onPreempted = { preempted = true },
                admittedGeneration = switchTicket,
            ) {
                switchStarted.complete(Unit)
                awaitCancellation()
            }
        }
        switchStarted.await()

        val pauseTicket = gate.admit()
        val pause = withTimeout(1_000L) {
            gate.run(interruptible = false, admittedGeneration = pauseTicket) { "paused" }
        }

        assertEquals("paused", pause)
        assertTrue(preempted)
        assertTrue(sourceSwitch.isCancelled)
    }

    @Test
    fun `newer command supersedes a source switch still waiting for its turn`() = runBlocking {
        val gate = MediaCommandGate()
        val release = CompletableDeferred<Unit>()
        val busy = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = false, admittedGeneration = gate.admit()) { release.await() }
        }
        var switchRan = false
        var preempted = false
        val switchTicket = gate.admit()
        val sourceSwitch = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(
                interruptible = true,
                onPreempted = { preempted = true },
                admittedGeneration = switchTicket,
            ) { switchRan = true }
        }
        val pauseTicket = gate.admit()
        val order = mutableListOf<String>()
        val pause = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = false, admittedGeneration = pauseTicket) { order += "pause" }
        }

        release.complete(Unit)
        withTimeout(1_000L) { pause.await() }

        busy.await()
        assertTrue(sourceSwitch.isCancelled)
        assertFalse(switchRan)
        assertTrue(preempted)
        assertEquals(listOf("pause"), order)
    }

    @Test
    fun `internal source switch queued before a user command is skipped`() = runBlocking {
        val gate = MediaCommandGate()
        val release = CompletableDeferred<Unit>()
        val busy = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = false) { release.await() }
        }
        var startupRan = false
        val startup = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = true) { startupRan = true }
        }

        gate.admit()
        release.complete(Unit)
        busy.await()
        startup.join()

        assertTrue(startup.isCancelled)
        assertFalse(startupRan)
    }

    @Test
    fun `queued ordinary commands all run in order`() = runBlocking {
        val gate = MediaCommandGate()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = false, admittedGeneration = gate.admit()) {
                release.await()
                order += "first"
            }
        }
        val secondTicket = gate.admit()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = false, admittedGeneration = secondTicket) { order += "second" }
        }
        val thirdTicket = gate.admit()
        val third = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = false, admittedGeneration = thirdTicket) { order += "third" }
        }

        release.complete(Unit)
        withTimeout(1_000L) {
            first.await()
            second.await()
            third.await()
        }

        assertEquals(listOf("first", "second", "third"), order)
    }

    @Test
    fun `quick commands are not cancelled`() = runBlocking {
        val gate = MediaCommandGate()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run(interruptible = false, admittedGeneration = gate.admit()) {
                started.complete(Unit)
                release.await()
                "next"
            }
        }
        started.await()

        gate.admit()
        release.complete(Unit)
        assertEquals("next", next.await())
    }

    @Test
    fun `source switch admitted after a finished one runs`() = runBlocking {
        val gate = MediaCommandGate()
        gate.run(interruptible = true, admittedGeneration = gate.admit()) { Unit }

        val ticket = gate.admit()
        assertEquals("switched", gate.run(interruptible = true, admittedGeneration = ticket) { "switched" })
    }
}
