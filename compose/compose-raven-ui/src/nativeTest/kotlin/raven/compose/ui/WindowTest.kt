package raven.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.value
import raven.cinterop.RBackdrop
import raven.cinterop.R_BACKDROP_BLUR
import raven.cinterop.R_BACKDROP_MICA
import raven.cinterop.R_BACKDROP_NONE
import raven.cinterop.R_BACKDROP_TRANSPARENT
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WINDOW_BORDERLESS
import raven.cinterop.R_WINDOW_CUSTOM_HEADER
import raven.cinterop.R_WINDOW_HIDDEN
import raven.cinterop.R_WINDOW_MAXIMIZED
import raven.cinterop.R_WINDOW_NO_RESIZE
import raven.cinterop.R_WINDOW_TRANSPARENT
import raven.cinterop.Rid
import raven.cinterop.Rwindow_get_backdrop
import raven.cinterop.Rwindow_get_opacity
import raven.cinterop.Rwindow_get_position
import raven.cinterop.Rwindow_get_size
import raven.cinterop.Rwindow_get_title
import raven.cinterop.Rwindow_inject_drag
import raven.cinterop.Rwindow_inject_drop
import raven.cinterop.Rwindow_is_maximized
import raven.cinterop.Rwindow_is_minimized
import raven.cinterop.Rwindow_maximize
import raven.cinterop.Rwindow_restore
import raven.cinterop.Rwindow_set_position
import raven.cinterop.Rwindow_set_size
import raven.compose.ui.graphics.Color
import raven.compose.ui.platform.LocalWindowInfo
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowTest {
    @Test
    fun theStatesSizeAndPositionReachTheWindow() {
        val state = WindowState(size = DpSize(420.dp, 220.dp), position = WindowPosition(30.dp, 40.dp))
        withHost({ Window(onCloseRequest = {}, state = state) {} }) {
            val window = windows.single().window
            assertEquals(420 to 220, sizeOf(window))
            assertEquals(30 to 40, positionOf(window))

            state.size = DpSize(500.4.dp, 299.6.dp)
            state.position = WindowPosition(50.dp, 60.dp)
            runUntilIdle()
            assertEquals(500 to 300, sizeOf(window), "rounded to whole logical units")
            assertEquals(50 to 60, positionOf(window))
        }
    }

    @Test
    fun theStateFollowsRavensResizeAndMove() {
        val state = WindowState()
        val sizesSeen = mutableListOf<DpSize>()
        withHost({ Window(onCloseRequest = {}, state = state) { sizesSeen += state.size } }) {
            val window = windows.single().window
            assertEquals(DpSize(800.dp, 600.dp), state.size)
            assertEquals(WindowPosition(0.dp, 0.dp), state.position, "the default position became the window's own")

            // As the user would, through the window system.
            Rwindow_set_size(window, 640, 480)
            Rwindow_set_position(window, 5, 6)
            drawFrame()
            runUntilIdle()
            assertEquals(DpSize(640.dp, 480.dp), state.size)
            assertEquals(WindowPosition(5.dp, 6.dp), state.position)
            assertEquals(DpSize(640.dp, 480.dp), sizesSeen.last(), "a reader recomposed")
            assertEquals(640 to 480, sizeOf(window), "nothing was written back")
        }
    }

    @Test
    fun anUnspecifiedSideKeepsTheWindowsOwn() {
        val state = WindowState(size = DpSize(500.dp, Dp.Unspecified))
        withHost({ Window(onCloseRequest = {}, state = state) {} }) {
            assertEquals(500 to 600, sizeOf(windows.single().window))
            assertEquals(DpSize(500.dp, 600.dp), state.size)
        }
    }

    @Test
    fun thePlacementReachesTheWindowAndFollowsIt() {
        val state = WindowState(placement = WindowPlacement.Maximized)
        withHost({ Window(onCloseRequest = {}, state = state) {} }) {
            val window = windows.single().window
            assertEquals(R_TRUE, Rwindow_is_maximized(window))

            state.isMinimized = true
            runUntilIdle()
            assertEquals(R_TRUE, Rwindow_is_minimized(window))

            Rwindow_restore(window)
            drawFrame()
            runUntilIdle()
            assertEquals(false, state.isMinimized)
            assertEquals(WindowPlacement.Floating, state.placement)

            Rwindow_maximize(window)
            drawFrame()
            runUntilIdle()
            assertEquals(WindowPlacement.Maximized, state.placement)

            state.placement = WindowPlacement.Floating
            runUntilIdle()
            assertEquals(R_FALSE, Rwindow_is_maximized(window))
        }
    }

    @Test
    fun theFlagsReachRavenWhenTheWindowIsCreated() {
        val calls = RecordingWindowCalls()
        withWindowCalls(calls) {
            withHost({
                Window(onCloseRequest = {}) {}
                Window(
                    onCloseRequest = {}, state = rememberWindowState(placement = WindowPlacement.Maximized),
                    customHeader = true, transparent = true, undecorated = true, resizable = false,
                ) {}
            })
        }
        assertEquals(
            listOf(
                R_WINDOW_HIDDEN,
                R_WINDOW_CUSTOM_HEADER or R_WINDOW_TRANSPARENT or R_WINDOW_BORDERLESS or R_WINDOW_NO_RESIZE or
                    R_WINDOW_MAXIMIZED or R_WINDOW_HIDDEN,
            ),
            calls.createdFlags,
        )
    }

    @Test
    fun aChangedFlagIsIgnoredWithAWarning() {
        var transparent by mutableStateOf(false)
        val calls = RecordingWindowCalls()
        val warnings = collectWarnings {
            withWindowCalls(calls) {
                withHost({ Window(onCloseRequest = {}, transparent = transparent, resizable = !transparent) {} }) {
                    transparent = true
                    runUntilIdle()
                }
            }
        }
        assertEquals(listOf("Window: transparent, resizable can't change once the window is open, so the change is ignored"), warnings)
        assertEquals(listOf(R_WINDOW_HIDDEN), calls.createdFlags)
    }

    @Test
    fun theMinimumSizeReachesRavenAndFollowsTheComposition() {
        var minimumSize by mutableStateOf<DpSize?>(DpSize(559.6.dp, 320.dp))
        val calls = RecordingWindowCalls()
        withWindowCalls(calls) {
            withHost({ Window(onCloseRequest = {}, minimumSize = minimumSize) {} }) {
                minimumSize = DpSize(600.dp, 400.dp)
                runUntilIdle()
                minimumSize = null
                runUntilIdle()
            }
            withHost({ Window(onCloseRequest = {}) {} })
        }
        assertEquals(listOf("minSize 560 x 320", "minSize 600 x 400", "minSize 0 x 0"), calls.calls)
    }

    @Test
    fun theBackgroundReachesRavenWhenSpecified() {
        var background by mutableStateOf(Color.Unspecified)
        val calls = RecordingWindowCalls()
        val warnings = collectWarnings {
            withWindowCalls(calls) {
                withHost({ Window(onCloseRequest = {}, background = background) {} }) {
                    background = Color(0xFF3B82F6)
                    runUntilIdle()
                    background = Color.Unspecified
                    runUntilIdle()
                }
            }
        }
        assertEquals(listOf("clearColor Color(0xFF3B82F6)"), calls.calls)
        assertEquals(listOf("Window: Raven can't give a window its theme's background back, so it keeps Color(0xFF3B82F6)"), warnings)
    }

    @Test
    fun theOpacityReachesTheWindow() {
        var opacity by mutableStateOf(0.5f)
        withHost({ Window(onCloseRequest = {}, opacity = opacity) {} }) {
            val window = windows.single().window
            assertEquals(0.5f, Rwindow_get_opacity(window))
            opacity = 0.75f
            runUntilIdle()
            assertEquals(0.75f, Rwindow_get_opacity(window))
        }
    }

    @Test
    fun aRefusedOpacityLeavesTheWindowOpaqueWithAWarning() {
        var opacity by mutableStateOf(0.5f)
        val warnings = collectWarnings {
            withWindowCalls(RecordingWindowCalls(refuse = true)) {
                withHost({ Window(onCloseRequest = {}, opacity = opacity) {} }) {
                    assertEquals(1f, Rwindow_get_opacity(windows.single().window))
                    opacity = 0.25f
                    runUntilIdle()
                }
            }
        }
        assertEquals(listOf("Window: the window system refused opacity 0.5, so the window stays opaque"), warnings, "once a window")
    }

    @Test
    fun theBackdropFollowsTheCompositionAndReturnsRavens() {
        var backdrop by mutableStateOf(Backdrop.Blur)
        var shown by mutableStateOf(true)
        val obtained = mutableListOf<Backdrop>()
        withHost({ Window(onCloseRequest = {}, transparent = true) { if (shown) obtained += WindowBackdrop(backdrop) } }) {
            val window = windows.single().window
            assertEquals(listOf(Backdrop.None, Backdrop.Blur), obtained, "None until the first apply")
            assertEquals(R_BACKDROP_BLUR, Rwindow_get_backdrop(window))

            backdrop = Backdrop.Mica
            runUntilIdle()
            assertEquals(Backdrop.Mica, obtained.last())
            assertEquals(R_BACKDROP_MICA, Rwindow_get_backdrop(window))

            shown = false
            runUntilIdle()
            assertEquals(R_BACKDROP_NONE, Rwindow_get_backdrop(window), "None restored")
        }
    }

    @Test
    fun aRefusedBackdropReturnsWhatRavenKeptWithAWarning() {
        var obtained = Backdrop.None
        val warnings = collectWarnings {
            withWindowCalls(RecordingWindowCalls(refuse = true)) {
                withHost({ Window(onCloseRequest = {}, transparent = true) { obtained = WindowBackdrop(Backdrop.Acrylic) } })
            }
        }
        // A transparent window starts with Raven's Transparent backdrop.
        assertEquals(Backdrop.Transparent, obtained)
        assertEquals(listOf("WindowBackdrop: the window system refused Backdrop.Acrylic"), warnings)
    }

    @Test
    fun aBackdropOnAnOpaqueWindowIsIgnoredWithAWarning() {
        var obtained = Backdrop.Blur
        var backdrop: RBackdrop = R_BACKDROP_TRANSPARENT
        val warnings = collectWarnings {
            withHost({ Window(onCloseRequest = {}) { obtained = WindowBackdrop(Backdrop.Blur) } }) {
                backdrop = Rwindow_get_backdrop(windows.single().window)
            }
        }
        assertEquals(Backdrop.None, obtained)
        assertEquals(R_BACKDROP_NONE, backdrop)
        assertEquals(listOf("WindowBackdrop: the window isn't transparent, so Backdrop.Blur is ignored"), warnings)
    }

    @Test
    fun windowInfoSaysWhetherTheWindowIsSeenThrough() {
        var opaque: Boolean? = null
        var seeThrough: Boolean? = null
        withHost({
            Window(onCloseRequest = {}) { opaque = LocalWindowInfo.current.isTransparent }
            Window(onCloseRequest = {}, transparent = true) { seeThrough = LocalWindowInfo.current.isTransparent }
        })
        assertEquals(false, opaque)
        // The headless window system gives every window that asks a see-through one.
        assertEquals(true, seeThrough)
    }

    @Test
    fun aDroppedFileListReachesTheHandler() {
        val dropped = mutableListOf<DragData>()
        withHost({ Window(onCloseRequest = {}, onExternalDrop = { dropped += it }) {} }) {
            val window = windows.single().window
            assertEquals(R_TRUE, Rwindow_inject_drag(window, 10f, 10f, R_TRUE), "accepted while dragged over")
            assertEquals(emptyList(), dropped, "not called before the drop")

            assertEquals(R_TRUE, drop(window, paths = listOf("/tmp/a.txt", "/tmp/b é.txt")))
        }
        assertEquals(listOf<DragData>(DragData.FilesList(listOf("/tmp/a.txt", "/tmp/b é.txt"))), dropped)
    }

    @Test
    fun aDroppedTextReachesTheHandler() {
        val dropped = mutableListOf<DragData>()
        withHost({ Window(onCloseRequest = {}, onExternalDrop = { dropped += it }) {} }) {
            assertEquals(R_TRUE, drop(windows.single().window, text = "Hello"))
        }
        assertEquals(listOf<DragData>(DragData.Text("Hello")), dropped)
    }

    @Test
    fun aWindowWithoutAHandlerRefusesADrop() {
        withHost({ Window(onCloseRequest = {}) {} }) {
            val window = windows.single().window
            assertEquals(R_FALSE, Rwindow_inject_drag(window, 10f, 10f, R_TRUE))
            assertEquals(R_FALSE, drop(window, paths = listOf("/tmp/a.txt")))
        }
    }

    @Test
    fun aChangedHandlerTakesEffectAtOnce() {
        val dropped = mutableListOf<String>()
        var onExternalDrop by mutableStateOf<((DragData) -> Unit)?>(null)
        withHost({ Window(onCloseRequest = {}, onExternalDrop = onExternalDrop) {} }) {
            val window = windows.single().window
            assertEquals(R_FALSE, drop(window, text = "refused"))

            onExternalDrop = { dropped += "first $it" }
            runUntilIdle()
            assertEquals(R_TRUE, drop(window, text = "one"))

            onExternalDrop = { dropped += "second $it" }
            runUntilIdle()
            assertEquals(R_TRUE, drop(window, text = "two"))

            onExternalDrop = null
            runUntilIdle()
            assertEquals(R_FALSE, drop(window, text = "refused"))
        }
        assertEquals(listOf("first Text(text=one)", "second Text(text=two)"), dropped)
    }

    @Test
    fun anExceptionInOnExternalDropReachesTheHost() = withRavenChecks {
        val host = RavenHost(headless = true)
        try {
            host.start { Window(onCloseRequest = {}, onExternalDrop = { error("boom") }) {} }
            host.runUntilIdle()
            assertNull(host.error)
            drop(host.windows.single().window, text = "Hello")
        } finally {
            host.close()
        }
        assertEquals("boom", host.error?.message)
    }

    @Test
    fun theTitleFollowsTheComposition() {
        var title by mutableStateOf("One")
        withHost({ Window(onCloseRequest = {}, title = title) {} }) {
            val window = windows.single().window
            assertEquals("One", Rwindow_get_title(window).toRavenString())
            title = "Two"
            runUntilIdle()
            assertEquals("Two", Rwindow_get_title(window).toRavenString())
        }
    }
}

/** Records what windows ask of Raven, and refuses the opacity and any backdrop but None when told to. */
private class RecordingWindowCalls(private val refuse: Boolean = false) : WindowCalls() {
    val createdFlags = mutableListOf<UInt>()
    val calls = mutableListOf<String>()

    override fun create(title: String, width: Int, height: Int, flags: UInt): Rid {
        createdFlags += flags
        return super.create(title, width, height, flags)
    }

    override fun setMinSize(window: Rid, width: Int, height: Int) {
        calls += "minSize $width x $height"
        super.setMinSize(window, width, height)
    }

    override fun setClearColor(window: Rid, color: Color) {
        calls += "clearColor $color"
        super.setClearColor(window, color)
    }

    override fun setOpacity(window: Rid, opacity: Float): Boolean = !refuse && super.setOpacity(window, opacity)

    override fun setBackdrop(window: Rid, backdrop: RBackdrop): Boolean =
        if (refuse && backdrop != R_BACKDROP_NONE) false else super.setBackdrop(window, backdrop)
}

private fun withWindowCalls(calls: WindowCalls, block: () -> Unit) {
    val previous = windowCalls
    windowCalls = calls
    try {
        block()
    } finally {
        windowCalls = previous
    }
}

private fun collectWarnings(block: () -> Unit): List<String> {
    val warnings = mutableListOf<String>()
    val sink = layoutWarningSink
    layoutWarningSink = { warnings += it }
    try {
        block()
    } finally {
        layoutWarningSink = sink
    }
    return warnings
}

/** Drops [paths] and [text] from another application at the window's top left; returns whether it took them. */
private fun drop(window: Rid, paths: List<String> = emptyList(), text: String? = null): Int = memScoped {
    Rwindow_inject_drop(window, 10f, 10f, paths.map { it.cstr.ptr }.toCValues(), paths.size, text)
}

private fun sizeOf(window: Rid): Pair<Int, Int> = memScoped {
    val width = alloc<IntVar>()
    val height = alloc<IntVar>()
    Rwindow_get_size(window, width.ptr, height.ptr)
    width.value to height.value
}

private fun positionOf(window: Rid): Pair<Int, Int> = memScoped {
    val x = alloc<IntVar>()
    val y = alloc<IntVar>()
    Rwindow_get_position(window, x.ptr, y.ptr)
    x.value to y.value
}
