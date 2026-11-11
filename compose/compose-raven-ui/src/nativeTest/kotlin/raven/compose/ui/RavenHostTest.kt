@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
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

    /** Starts a headless host with [content], runs [block] once it is idle, then closes it. */
    private fun host(content: @Composable () -> Unit, block: RavenHost.() -> Unit = {}) = withRavenChecks {
        val host = RavenHost(headless = true)
        try {
            host.start(content)
            host.runUntilIdle()
            host.block()
        } finally {
            host.close()
        }
        host.error?.let { throw it }
    }

    private fun RavenHost.runUntilIdle() {
        repeat(100) {
            if (isIdle()) return
            check(frame()) { "Raven quit" }
        }
        error("not idle after 100 frames")
    }

    @Test
    fun aWindowsContentGoesUnderItsOverlayRoot() = host({
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
        host({
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
        host({ Window(onCloseRequest = { requests++ }) { Label("label") } }) {
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
        host({ Window(onCloseRequest = {}) { Label(name) } }) {
            val before = applyGeneration
            name = "b"
            runUntilIdle()
            assertTrue(applyGeneration > before)
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
