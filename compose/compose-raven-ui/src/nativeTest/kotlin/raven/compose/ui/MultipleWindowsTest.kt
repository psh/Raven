package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.savedState
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rwindow_focus
import raven.cinterop.Rwindow_get_content
import raven.cinterop.Rwindow_is_valid
import raven.cinterop.Rwindow_minimize
import raven.cinterop.Rwindow_restore
import raven.compose.ui.platform.FrameStats
import raven.compose.ui.platform.LocalWindowInfo
import raven.compose.ui.platform.WindowInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultipleWindowsTest {
    @Composable
    private fun Label(name: String) {
        ComposeNode<RavenNode, RavenApplier>(factory = { node(name) }, update = {})
    }

    @Test
    fun twoWindowsOpenAndCloseIndependently() {
        var showFirst by mutableStateOf(true)
        var showSecond by mutableStateOf(true)
        var secondComposedAnew = 0
        withHost({
            if (showFirst) Window(onCloseRequest = {}, title = "first") { Label("first") }
            if (showSecond) {
                Window(onCloseRequest = {}, title = "second") {
                    remember { secondComposedAnew++ }
                    Label("second")
                }
            }
            // Raven ends the loop when its last window goes.
            Window(onCloseRequest = {}, title = "keeper") {}
        }) {
            val (first, second, keeper) = windows.map { it.window }
            assertEquals(listOf("first", "second"), listOf(first, second).map { ravenChildren(Rwindow_get_content(it)).single() })

            showFirst = false
            runUntilIdle()
            assertEquals(R_FALSE, Rwindow_is_valid(first))
            assertEquals(R_TRUE, Rwindow_is_valid(second))
            assertEquals(listOf("second"), ravenChildren(Rwindow_get_content(second)), "the other window kept its content")

            showFirst = true
            runUntilIdle()
            val reopened = windows.last().window
            assertEquals(R_TRUE, Rwindow_is_valid(reopened))
            assertEquals(listOf("first"), ravenChildren(Rwindow_get_content(reopened)), "a new window, composed anew")
            assertEquals(listOf(second, keeper, reopened), windows.map { it.window }, "in the order they opened")

            showSecond = false
            runUntilIdle()
            assertEquals(R_FALSE, Rwindow_is_valid(second))
            assertEquals(R_TRUE, Rwindow_is_valid(reopened))
            assertEquals(listOf(keeper, reopened), windows.map { it.window })
        }
        assertEquals(1, secondComposedAnew, "the second window's composition lived on while the first came and went")
    }

    @Test
    fun theLifecycleFollowsShowFocusMinimiseAndRemoval() {
        var showSecond by mutableStateOf(true)
        val firstState = WindowState()
        var first: LifecycleOwner? = null
        var second: LifecycleOwner? = null
        withHost({
            Window(onCloseRequest = {}, state = firstState) { first = LocalLifecycleOwner.current }
            if (showSecond) Window(onCloseRequest = {}) { second = LocalLifecycleOwner.current }
        }) {
            val (firstWindow, secondWindow) = windows.map { it.window }
            val firstLifecycle = first!!.lifecycle
            val secondLifecycle = second!!.lifecycle
            // Shown, and headless, every window starts active.
            assertEquals(Lifecycle.State.RESUMED, firstLifecycle.currentState)
            assertEquals(Lifecycle.State.RESUMED, secondLifecycle.currentState)
            assertNotSame(first, second, "each window has its own")

            Rwindow_focus(secondWindow)
            drawFrame()
            assertEquals(Lifecycle.State.STARTED, firstLifecycle.currentState, "shown, but another window is active")
            assertEquals(Lifecycle.State.RESUMED, secondLifecycle.currentState)

            Rwindow_focus(firstWindow)
            drawFrame()
            assertEquals(Lifecycle.State.RESUMED, firstLifecycle.currentState)
            assertEquals(Lifecycle.State.STARTED, secondLifecycle.currentState)

            // Minimised by the app, then by the user.
            firstState.isMinimized = true
            runUntilIdle()
            assertEquals(Lifecycle.State.CREATED, firstLifecycle.currentState)
            firstState.isMinimized = false
            runUntilIdle()
            assertEquals(Lifecycle.State.RESUMED, firstLifecycle.currentState)
            Rwindow_minimize(firstWindow)
            drawFrame()
            assertEquals(Lifecycle.State.CREATED, firstLifecycle.currentState)
            Rwindow_restore(firstWindow)
            drawFrame()
            assertEquals(Lifecycle.State.RESUMED, firstLifecycle.currentState)

            showSecond = false
            runUntilIdle()
            assertEquals(Lifecycle.State.DESTROYED, secondLifecycle.currentState)
            assertEquals(Lifecycle.State.RESUMED, firstLifecycle.currentState)
        }
    }

    @Test
    fun theContentsObserversSeeTheWindowGoBeforeTheyAreDisposed() {
        var shown by mutableStateOf(true)
        val events = mutableListOf<String>()
        withHost({
            if (shown) {
                Window(onCloseRequest = {}) {
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    DisposableEffect(lifecycle) {
                        val observer = LifecycleEventObserver { _, event -> events += event.name }
                        lifecycle.addObserver(observer)
                        onDispose {
                            events += "disposed"
                            lifecycle.removeObserver(observer)
                        }
                    }
                }
            }
            Window(onCloseRequest = {}) {}
        }) {
            assertEquals(listOf("ON_CREATE", "ON_START", "ON_RESUME"), events)
            events.clear()
            shown = false
            runUntilIdle()
        }
        assertEquals(listOf("ON_PAUSE", "ON_STOP", "ON_DESTROY", "disposed"), events)
    }

    @Test
    fun eachWindowHasASavedStateRegistryThatPersistsNothing() {
        val owners = mutableListOf<SavedStateRegistryOwner>()
        withHost({
            Window(onCloseRequest = {}) { owners += LocalSavedStateRegistryOwner.current }
            Window(onCloseRequest = {}) { owners += LocalSavedStateRegistryOwner.current }
        }) {
            val (first, second) = owners
            assertNotSame(first, second)
            assertTrue(first.savedStateRegistry.isRestored)
            assertNull(first.savedStateRegistry.consumeRestoredStateForKey("key"), "nothing was saved before")
            // A provider can register, as lifecycle-aware libraries do.
            first.savedStateRegistry.registerSavedStateProvider("key") { savedState() }
            first.savedStateRegistry.unregisterSavedStateProvider("key")
        }
    }

    @Test
    fun frameStatsReportAFrameOnceOneIsDrawn() {
        var info: WindowInfo? = null
        val before = mutableListOf<FrameStats>()
        withHost({
            Window(onCloseRequest = {}) {
                info = LocalWindowInfo.current
                Label("label")
                // Read in the first composition, before Raven drew the window.
                if (before.isEmpty()) before += LocalWindowInfo.current.frameStats()
            }
        }) {
            assertEquals(0, before.single().widgetCount, "nothing before the first frame is drawn")
            drawFrame()
            val stats = info!!.frameStats()
            assertTrue(stats.widgetCount > 0, "the frame laid out and painted widgets: $stats")
        }
    }
}
