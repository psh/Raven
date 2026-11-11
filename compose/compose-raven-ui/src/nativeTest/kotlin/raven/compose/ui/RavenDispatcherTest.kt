@file:OptIn(ExperimentalAtomicApi::class, ObsoleteWorkersApi::class)

package raven.compose.ui

import kotlinx.coroutines.Runnable
import platform.posix.usleep
import raven.cinterop.Rdraw_frame
import raven.cinterop.Rwait_events
import raven.cinterop.Rwake_up
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class RavenDispatcherTest {
    /**
     * A block that waits in Raven's loop, as a blocking dialog does, with a block dispatched behind
     * it: no drain task is posted for that one, and no window needs a layout, so only the guard
     * keeps the loop from sleeping.
     */
    @Test
    fun aBlockThatWaitsDoesNotHideTheWorkQueuedBehindIt() = withDispatcher { dispatcher ->
        var behindRan = false
        var waited = Duration.ZERO
        dispatcher.dispatch(EmptyCoroutineContext, Runnable {
            dispatcher.dispatch(EmptyCoroutineContext, Runnable { behindRan = true })
            waited = watched { Rwait_events(-1.0) }
        })
        Rdraw_frame()
        assertTrue(waited < 500.milliseconds, "the loop slept for $waited with a block queued")
        assertTrue(behindRan)
    }

    /**
     * After a drain whose blocks all return, nothing is left due, so the loop sleeps as before. (A
     * guard left armed by a drain in a task would fire in the same frame anyway; the destroy matters
     * for the host's drains in the frame callback, which run after Raven's timers.)
     */
    @Test
    fun aDrainWhoseBlocksReturnLeavesTheLoopFreeToSleep() = withDispatcher { dispatcher ->
        var ran = 0
        dispatcher.dispatch(EmptyCoroutineContext, Runnable {
            ran++
            dispatcher.dispatch(EmptyCoroutineContext, Runnable { ran++ })
        })
        Rdraw_frame()
        assertEquals(2, ran)
        val slept = watched { Rwait_events(0.2) }
        assertTrue(slept >= 150.milliseconds, "the loop slept only $slept: something was left due")
    }
}

/** Runs [block] with Raven started headless, no window, and a dispatcher of its own. */
private fun withDispatcher(block: (RavenDispatcher) -> Unit) {
    val errors = mutableListOf<Throwable>()
    val dispatcher = RavenDispatcher { errors += it }
    try {
        withHeadlessRaven {
            try {
                block(dispatcher)
            } finally {
                dispatcher.close()
            }
        }
    } finally {
        dispatcher.release()
    }
    errors.firstOrNull()?.let { throw it }
}

/** Times [wait], which may sleep in Raven's loop; a worker wakes Raven after a second, so it ends. */
private fun watched(wait: () -> Unit): Duration {
    val watchdog = Watchdog()
    val worker = Worker.start()
    val done = worker.execute(TransferMode.SAFE, { watchdog }) { it.wakeAfterASecond() }
    try {
        return measureTime(wait)
    } finally {
        watchdog.finished.store(1)
        done.result
        worker.requestTermination().result
    }
}

private class Watchdog {
    val finished = AtomicInt(0)

    fun wakeAfterASecond() {
        repeat(100) {
            if (finished.load() != 0) return
            usleep(10_000u)
        }
        Rwake_up()
    }
}
