// The harness's setContent opens an opaque window, so the test opens its own with RavenHost.start.
@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_BACKDROP_BLUR
import raven.cinterop.R_BACKDROP_MICA
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_backdrop
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Window
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayTest {
    /** Hosts [Overlay] as `main` does: in a transparent window whose opacity it controls. */
    private fun RavenTestScope.setOverlay(onClose: () -> Unit = {}) {
        var opacity by mutableFloatStateOf(1f)
        host.start {
            Window(onCloseRequest = {}, title = "Test", transparent = true, opacity = opacity) {
                Overlay(opacity, onOpacityChange = { opacity = it }, onClose = onClose)
            }
        }
        waitForIdle()
    }

    private fun RavenTestScope.window() = Rwidget_get_window(onNodeWithTag("backdrop").fetchWidget())

    @Test
    fun choosingABackdropReachesTheWindow() = runRavenTest {
        setOverlay()
        onNodeWithTag("backdrop").assertSelectedIndex(1)
        assertEquals(R_BACKDROP_BLUR, Rwindow_get_backdrop(window()))
        onNodeWithText("obtained: the desktop, blurred").assertExists()

        onNodeWithTag("backdrop").performSelection(3)
        waitForIdle()
        onNodeWithTag("backdrop").assertSelectedIndex(3)
        assertEquals(R_BACKDROP_MICA, Rwindow_get_backdrop(window()))
        onNodeWithText("obtained: mica").assertExists()
    }

    @Test
    fun aSeeThroughWindowExplainsWhatShowsThrough() = runRavenTest {
        // The headless window system gives every window that asks a see-through one.
        setOverlay()
        onNodeWithText("what is behind it shows", substring = true).assertExists()
        onNodeWithText("did not give a window that can be seen through", substring = true).assertDoesNotExist()
    }

    @Test
    fun anOpaqueWindowExplainsThePlainBackground() = runRavenTest {
        // setContent's window isn't transparent, as when the window system refuses one.
        setContent { Overlay(opacity = 1f, onOpacityChange = {}, onClose = {}) }
        onNodeWithText("did not give a window that can be seen through", substring = true).assertExists()
        onNodeWithText("what is behind it shows", substring = true).assertDoesNotExist()
        onNodeWithText("obtained: nothing (not available here)").assertExists()
    }

    @Test
    fun closeCallsOnClose() = runRavenTest {
        var closes = 0
        setOverlay(onClose = { closes++ })
        onNodeWithText("Close").performClick()
        assertEquals(1, closes)
    }
}
