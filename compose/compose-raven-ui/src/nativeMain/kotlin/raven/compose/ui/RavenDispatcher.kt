@file:OptIn(ExperimentalAtomicApi::class)

package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rid
import raven.cinterop.Rtask_post
import raven.cinterop.Rtimer_create
import raven.cinterop.Rtimer_destroy
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
 * While [drain] runs blocks, the drain task counts as posted, so what is dispatched meanwhile
 * waits for the loop to come back. A block can instead wait in nested frames (a blocking dialog),
 * so each drain also arms a guard: a Raven timer due at once, which keeps the nested loop from
 * sleeping and drains when it fires. A drain whose blocks all return destroys it unfired, so the
 * guard costs no frame and no wake-up.
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

    // The guards of the drains running now, innermost last. UI thread only.
    private val guards = ArrayList<Rid>()

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
        var guard = R_INVALID_ID
        try {
            while (true) {
                val block = locked {
                    queue.removeFirstOrNull().also { if (it == null) drainPosted = false }
                } ?: return
                // Armed again if a block waited long enough for it to fire.
                if (guard !in guards) guard = armGuard()
                try {
                    block.run()
                } catch (t: Throwable) {
                    onError(t)
                }
            }
        } finally {
            if (guards.remove(guard)) Rtimer_destroy(guard)
        }
    }

    private fun armGuard(): Rid {
        val guard = Rtimer_create(0.0, R_FALSE, guardTimer, selfRef.asCPointer())
        if (guard != R_INVALID_ID) guards += guard
        return guard
    }

    fun onGuardFired(guard: Rid) {
        // Raven deleted the one-shot timer: the drain that armed it won't destroy it.
        guards.remove(guard)
        drain()
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

private val guardTimer = staticCFunction { timer: Rid, userData: COpaquePointer? ->
    userData!!.asStableRef<RavenDispatcher>().get().onGuardFired(timer)
}
