@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import raven.cinterop.RError
import raven.cinterop.RMemoryStats
import raven.cinterop.RSeverity
import raven.cinterop.R_SEVERITY_ERROR
import raven.cinterop.R_SEVERITY_FATAL
import raven.cinterop.Rerror_set_callback
import raven.cinterop.Rmemory_get_stats
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.Window
import raven.native.CallbackRegistry
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Runs [block] against a fresh headless Raven, with a fixed animation time step, then shuts it
 * down. The test fails if Raven reported an error, if the host recorded one, or if anything leaked:
 * a block Raven still holds, or a widget reference still alive.
 */
internal fun runRavenTest(block: RavenTestScope.() -> Unit) {
    ravenErrors.clear()
    Rerror_set_callback(collectRavenError)
    val host = RavenHost(headless = true, animationTimeStep = ANIMATION_TIME_STEP)
    try {
        try {
            RavenTestScope(host).block()
        } finally {
            host.close()
        }
        host.error?.let { throw it }
    } finally {
        Rerror_set_callback(null)
    }
    assertEquals(emptyList(), ravenErrors.toList(), "errors reported by Raven")
    assertEquals(0uL, ravenBlocksHeld(), "blocks Raven still holds after Rshutdown")
    assertEquals(0, CallbackRegistry.liveCount, "widget references left after Rshutdown")
}

internal class RavenTestScope(val host: RavenHost) {
    /** Hosts [content] in a single window, then waits until it is idle. */
    fun setContent(content: @Composable () -> Unit) {
        host.start { Window(onCloseRequest = {}, title = "Test", content = content) }
        waitForIdle()
    }

    /**
     * Draws frames until no recomposition, snapshot apply or dispatched task is pending. Frame
     * awaiters don't count, so a running animation never stalls it.
     */
    fun waitForIdle() {
        repeat(MAX_IDLE_FRAMES) {
            if (host.isIdle()) return
            frame()
        }
        fail("not idle after $MAX_IDLE_FRAMES frames")
    }

    /** Draws one frame. Throws what ended the host, if a failure asked Raven to quit. */
    fun frame() {
        if (!host.frame()) throw host.error ?: AssertionError("Raven quit")
    }
}

/** One frame at 60 Hz. */
private const val ANIMATION_TIME_STEP = 1.0 / 60

private const val MAX_IDLE_FRAMES = 100

private val ravenErrors = mutableListOf<String>()

private val collectRavenError = staticCFunction { _: RError, severity: RSeverity, detail: CPointer<ByteVar>? ->
    if (severity == R_SEVERITY_ERROR || severity == R_SEVERITY_FATAL) ravenErrors += detail?.toKString() ?: "?"
}

private fun ravenBlocksHeld(): ULong = memScoped {
    val stats = alloc<RMemoryStats>()
    Rmemory_get_stats(stats.ptr)
    stats.allocation_count
}
