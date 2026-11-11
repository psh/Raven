@file:OptIn(ExperimentalAtomicApi::class)

package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import raven.cinterop.Rtask_post
import raven.cinterop.Rwake_up
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

/**
 * Runs coroutines on Raven's UI thread. A dispatch is always needed, so Applier work never runs
 * inside a Raven callback. Thread-safe.
 *
 * Blocks wait in a queue of its own, which one posted Raven task [drain]s until it is empty, and
 * the host drains again just before each frame. So a coroutine that resumes another (a snapshot
 * apply resuming the Recomposer) doesn't wait for the next frame's batch of Raven tasks.
 *
 * After [close], dispatches are dropped instead of reaching Raven. Call [release] after
 * `Rshutdown`, which runs the drain task still posted.
 */
internal class RavenDispatcher(private val onError: (Throwable) -> Unit) : CoroutineDispatcher() {
    // Guards queue, drainPosted and closed.
    private val lock = AtomicInt(0)
    private val queue = ArrayDeque<Runnable>()
    private var drainPosted = false
    private var closed = false
    private val selfRef = StableRef.create(this)

    /** Whether a dispatched block is waiting to run. */
    val hasQueuedTasks: Boolean get() = locked { queue.isNotEmpty() }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        locked {
            if (closed) return
            queue.addLast(block)
            if (!drainPosted) {
                drainPosted = true
                Rtask_post(drainTask, selfRef.asCPointer())
            }
        }
    }

    /** Runs the queued blocks, and those they queue, until none is left. UI thread only. */
    fun drain() {
        while (true) {
            val block = locked {
                queue.removeFirstOrNull().also { if (it == null) drainPosted = false }
            } ?: return
            try {
                block.run()
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    /** Wakes Raven's loop, from any thread, unless the dispatcher is closed. */
    fun wakeUp() {
        locked { if (!closed) Rwake_up() }
    }

    /** Drops the queued blocks and every later dispatch. */
    fun close() {
        locked {
            closed = true
            queue.clear()
        }
    }

    fun release() {
        selfRef.dispose()
    }

    private inline fun <T> locked(block: () -> T): T {
        while (!lock.compareAndSet(0, 1)) {
            // Held only to touch the queue and post one task.
        }
        try {
            return block()
        } finally {
            lock.store(0)
        }
    }
}

private val drainTask = staticCFunction { userData: COpaquePointer? ->
    userData!!.asStableRef<RavenDispatcher>().get().drain()
}
