package com.mmwtl.atlasmediaapi.media.bridge

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs media commands one at a time. A source switch may wait tens of seconds for a player to
 * start; a newer user command cancels such a wait instead of queueing behind it.
 */
class MediaCommandGate {
    private class Running(val job: Job, val onPreempted: () -> Unit) {
        fun cancel() {
            job.cancel()
            onPreempted()
        }
    }

    private val mutex = Mutex()
    private val interruptible = AtomicReference<Running?>(null)
    private val generation = AtomicLong()

    /**
     * @param interruptible a newer user command may cancel this one.
     * @param onPreempted called when a newer user command cancels this one.
     * @param admittedGeneration the value of [admit] when this command was received; an
     *   interruptible command admitted before a newer user command is skipped once its turn comes.
     */
    suspend fun <T> run(
        interruptible: Boolean,
        onPreempted: () -> Unit = {},
        admittedGeneration: Long = generation.get(),
        block: suspend () -> T,
    ): T = mutex.withLock {
        val running = if (interruptible) {
            Running(currentCoroutineContext().job, onPreempted).also(this.interruptible::set)
        } else null
        try {
            if (running != null && admittedGeneration != generation.get()) {
                // A newer command arrived while this one waited for its turn.
                if (this.interruptible.compareAndSet(running, null)) running.cancel()
                currentCoroutineContext().ensureActive()
            }
            block()
        } finally {
            if (running != null) this.interruptible.compareAndSet(running, null)
        }
    }

    /**
     * Admits a user command: supersedes interruptible commands that are running or still waiting
     * for their turn. Call it synchronously when the command is received and pass the result to
     * [run].
     */
    fun admit(): Long {
        val admitted = generation.incrementAndGet()
        interruptible.getAndSet(null)?.cancel()
        return admitted
    }
}
