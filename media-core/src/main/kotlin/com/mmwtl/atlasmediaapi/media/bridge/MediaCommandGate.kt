package com.mmwtl.atlasmediaapi.media.bridge

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs media commands one at a time. A source switch may wait tens of seconds for a player to
 * start; a newer user command cancels such a wait instead of queueing behind it.
 */
class MediaCommandGate {
    private class Running(val job: Job, val onPreempted: () -> Unit)

    private val mutex = Mutex()
    private val interruptible = AtomicReference<Running?>(null)

    /**
     * @param interruptible a newer user command may cancel this one.
     * @param onPreempted called when [preempt] cancels this command.
     */
    suspend fun <T> run(
        interruptible: Boolean,
        onPreempted: () -> Unit = {},
        block: suspend () -> T,
    ): T = mutex.withLock {
        val running = if (interruptible) {
            Running(currentCoroutineContext().job, onPreempted).also(this.interruptible::set)
        } else null
        try {
            block()
        } finally {
            if (running != null) this.interruptible.compareAndSet(running, null)
        }
    }

    /** Cancels the running interruptible command; returns whether one was cancelled. */
    fun preempt(): Boolean {
        val running = interruptible.getAndSet(null) ?: return false
        running.job.cancel()
        running.onPreempted()
        return true
    }
}
