package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtask_post
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwindow_close
import raven.cinterop.Rwindow_get_content
import raven.cinterop.Rwindow_is_valid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RavenHostTest {
    private val labels = mutableListOf<Rid>()

    @Composable
    private fun Label(name: String) {
        ComposeNode<RavenNode, RavenApplier>(factory = { node(name).also { labels += it.widget } }, update = {})
    }

    @Test
    fun aWindowsContentGoesUnderItsOverlayRoot() = withHost({
        Window(onCloseRequest = {}) { Label("label") }
    }) {
        val label = labels.single()
        val window = Rwidget_get_window(label)
        assertEquals(R_TRUE, Rwindow_is_valid(window))
        assertEquals(Rwindow_get_content(window), Rwidget_get_parent(label))
    }

    @Test
    fun removingAWindowDisposesItsCompositionBeforeTheWindow() {
        var shown by mutableStateOf(true)
        var window: Rid = 0u
        var windowValidAtDispose = R_FALSE
        withHost({
            if (shown) {
                Window(onCloseRequest = {}) {
                    Label("label")
                    DisposableEffect(Unit) {
                        onDispose { windowValidAtDispose = Rwindow_is_valid(window) }
                    }
                }
            }
            // Raven ends the loop when its last window goes.
            Window(onCloseRequest = {}) { Label("other") }
        }) {
            val label = labels.first()
            window = Rwidget_get_window(label)
            shown = false
            runUntilIdle()
            assertEquals(R_TRUE, windowValidAtDispose, "the window was still there when its content was disposed")
            assertEquals(R_FALSE, Rwidget_is_valid(label))
            assertEquals(R_FALSE, Rwindow_is_valid(window))
        }
    }

    @Test
    fun closingAWindowCallsOnCloseRequestAndKeepsTheWindow() {
        var requests = 0
        withHost({ Window(onCloseRequest = { requests++ }) { Label("label") } }) {
            val window = Rwidget_get_window(labels.single())
            Rwindow_close(window)
            assertEquals(1, requests)
            assertEquals(R_TRUE, Rwindow_is_valid(window))
        }
    }

    @Test
    fun exitApplicationEndsTheLoop() = withRavenChecks {
        var frames = 0
        runApplication(RavenHost(headless = true)) {
            Window(onCloseRequest = {}) {
                LaunchedEffect(Unit) {
                    frames++
                    exitApplication()
                }
            }
        }
        assertEquals(1, frames)
    }

    @Test
    fun anExceptionInTheFirstCompositionShutsDownAndRethrows() = withRavenChecks {
        val thrown = assertFailsWith<IllegalStateException> {
            runApplication(RavenHost(headless = true)) {
                Window(onCloseRequest = {}) {
                    Label("label")
                    error("boom")
                }
            }
        }
        assertEquals("boom", thrown.message)
    }

    @Test
    fun anExceptionInRecompositionShutsDownAndRethrows() = withRavenChecks {
        var step by mutableIntStateOf(0)
        val thrown = assertFailsWith<IllegalStateException> {
            runApplication(RavenHost(headless = true)) {
                Window(onCloseRequest = {}) {
                    Label("label")
                    LaunchedEffect(Unit) { step = 1 }
                    if (step == 1) error("boom")
                }
            }
        }
        assertEquals("boom", thrown.message)
    }

    @Test
    fun anExceptionInAnEffectShutsDownAndRethrows() = withRavenChecks {
        val thrown = assertFailsWith<IllegalStateException> {
            runApplication(RavenHost(headless = true)) {
                Window(onCloseRequest = {}) {
                    LaunchedEffect(Unit) { error("boom") }
                }
            }
        }
        assertEquals("boom", thrown.message)
    }

    @Test
    fun anExceptionInOnCloseRequestShutsDownAndRethrows() = withRavenChecks {
        val thrown = assertFailsWith<IllegalStateException> {
            runApplication(RavenHost(headless = true)) {
                Window(onCloseRequest = { error("boom") }) {
                    Label("label")
                    LaunchedEffect(Unit) { Rwindow_close(Rwidget_get_window(labels.single())) }
                }
            }
        }
        assertEquals("boom", thrown.message)
    }

    @Test
    fun theApplyGenerationAdvancesWithEachAppliedChange() {
        var name by mutableStateOf("a")
        withHost({ Window(onCloseRequest = {}) { Label(name) } }) {
            val before = applyGeneration
            name = "b"
            runUntilIdle()
            assertTrue(applyGeneration > before)
        }
    }

    @Test
    fun awaitIdleResumesOnceAFrameEndsIdle() {
        var name by mutableStateOf("a")
        var idle: Boolean? = null
        var appliedWhenResumed = false
        withHost({
            val host = LocalRavenHost.current
            Window(onCloseRequest = {}) {
                Label(name)
                LaunchedEffect(Unit) {
                    val before = host.applyGeneration
                    name = "b"
                    idle = host.awaitIdle()
                    appliedWhenResumed = host.applyGeneration > before
                }
            }
        }) {
            repeat(10) { if (idle == null) drawFrame() }
            assertEquals(true, idle)
            assertTrue(appliedWhenResumed)
        }
    }

    @Test
    fun awaitIdleGivesUpAfterMaxFrames() {
        // A Raven task posting itself again changes what is composed between every two frames,
        // so no frame ends idle.
        val ticker = Ticker()
        val tickerRef = StableRef.create(ticker)
        var waiting by mutableStateOf(false)
        var idle: Boolean? = null
        var frames = 0
        var startedAt = -1
        withHost({
            val host = LocalRavenHost.current
            Window(onCloseRequest = {}) {
                Label("count ${ticker.count.intValue}")
                LaunchedEffect(waiting) {
                    if (!waiting) return@LaunchedEffect
                    startedAt = frames
                    idle = host.awaitIdle(maxFrames = 5)
                }
            }
        }) {
            Rtask_post(tick, tickerRef.asCPointer())
            waiting = true
            while (idle == null && frames < 20) {
                drawFrame()
                frames++
            }
            ticker.running = false
            drawFrame()
        }
        tickerRef.dispose()
        assertEquals(false, idle)
        // (the frame it started in, five that end busy, then the next frame's tasks resume it)
        assertTrue(frames - startedAt in 6..7, "gave up after ${frames - startedAt} frames")
    }

    @Test
    fun awaitIdleKeepsALiveLoopDrawing() {
        var idle: Boolean? = null
        withHost({
            val host = LocalRavenHost.current
            Window(onCloseRequest = {}) {
                Label("label")
                LaunchedEffect(Unit) { idle = host.awaitIdle() }
            }
        }) {
            // waitFrame sleeps until something wakes Raven: awaitIdle does, while it waits.
            repeat(5) { if (idle == null) check(waitFrame()) }
            assertEquals(true, idle)
        }
    }

    @Test
    fun closingClearsTheCachesAndEndsTheEpoch() = withRavenChecks {
        val host = RavenHost(headless = true)
        host.start {}
        assertEquals("first", host.caches.getOrPut("key") { "first" })
        assertEquals("first", host.caches.getOrPut("key") { "second" })
        HostEpoch.checkCurrent(host.epoch)
        host.close()

        assertEquals("third", host.caches.getOrPut("key") { "third" })
        assertFailsWith<IllegalStateException> { HostEpoch.checkCurrent(host.epoch) }
    }
}

private class Ticker {
    val count = mutableIntStateOf(0)
    var running = true
}

private val tick: CPointer<CFunction<(COpaquePointer?) -> Unit>> = staticCFunction { userData: COpaquePointer? ->
    val ticker = userData!!.asStableRef<Ticker>().get()
    if (ticker.running) {
        ticker.count.intValue += 1
        Rtask_post(tick, userData)
    }
}
