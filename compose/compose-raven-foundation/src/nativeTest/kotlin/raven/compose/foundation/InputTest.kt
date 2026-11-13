package raven.compose.foundation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TRUE
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.cinterop.Rwindow_inject_mouse_wheel
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.focus.FocusManager
import raven.compose.ui.focus.FocusRequester
import raven.compose.ui.focus.focusRequester
import raven.compose.ui.focus.onFocusChanged
import raven.compose.ui.geometry.Offset
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyEvent
import raven.compose.ui.input.key.KeyEventType
import raven.compose.ui.input.key.onKeyEvent
import raven.compose.ui.input.pointer.PointerButton
import raven.compose.ui.input.pointer.PointerEvent
import raven.compose.ui.input.pointer.PointerEventType
import raven.compose.ui.input.pointer.PointerIcon
import raven.compose.ui.input.pointer.onPointerEvent
import raven.compose.ui.input.pointer.pointerHoverIcon
import raven.compose.ui.platform.LocalFocusManager
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InputTest {
    @Test
    fun aClickOnTheWidgetOrWhatItHoldsCallsOnClick() = runRavenTest {
        var clicks = 0
        setContent {
            Box(Modifier.size(80.dp).testTag("area").clickable { clicks++ }) { Text("Inside") }
        }
        onNodeWithTag("area").performClick()
        assertEquals(1, clicks)
        // The Text lets the pointer through to the Box.
        onNodeWithText("Inside").performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun aDisabledClickableDoesNothingAndLetsTheClickThrough() = runRavenTest {
        var outer = 0
        var inner = 0
        setContent {
            Box(Modifier.size(80.dp).clickable { outer++ }) {
                Box(Modifier.size(40.dp).testTag("inner").clickable(enabled = false) { inner++ }) {}
            }
        }
        onNodeWithTag("inner").performClick()
        assertEquals(0, inner)
        assertEquals(1, outer)
    }

    @Test
    fun aClickableConsumesTheClickSoItsParentDoesntSeeIt() = runRavenTest {
        var outer = 0
        var inner = 0
        setContent {
            Box(Modifier.size(80.dp).clickable { outer++ }) {
                Box(Modifier.size(40.dp).testTag("inner").clickable { inner++ }) {}
            }
        }
        onNodeWithTag("inner").performClick()
        assertEquals(1, inner)
        assertEquals(0, outer)
    }

    @Test
    fun aReleaseWithoutAPressOverTheWidgetIsNoClick() = runRavenTest {
        var clicks = 0
        setContent {
            Column {
                Box(Modifier.size(40.dp).testTag("elsewhere")) {}
                Box(Modifier.size(40.dp).testTag("area").clickable { clicks++ }) {}
            }
        }
        val from = widgetRect(onNodeWithTag("elsewhere").fetchWidget())
        val to = widgetRect(onNodeWithTag("area").fetchWidget())
        val window = Rwidget_get_window(onNodeWithTag("area").fetchWidget())
        Rwindow_inject_mouse_move(window, from.x + 5, from.y + 5)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_move(window, to.x + 5, to.y + 5)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun aDoubleClickCallsOnDoubleClickForItsSecondClick() = runRavenTest {
        val calls = mutableListOf<String>()
        setContent {
            Box(Modifier.size(80.dp).testTag("area").combinedClickable(onDoubleClick = { calls += "double" }) { calls += "click" }) {}
        }
        onNodeWithTag("area").performDoubleClick()
        assertEquals(listOf("click", "double"), calls)
    }

    @Test
    fun theHoverIsReportedAsThePointerEntersAndLeaves() = runRavenTest {
        val events = mutableListOf<PointerEventType>()
        setContent {
            Box(
                Modifier.size(80.dp).testTag("area")
                    .onPointerEvent(PointerEventType.Enter) { events += it.type }
                    .onPointerEvent(PointerEventType.Exit) { events += it.type },
            ) {}
        }
        onNodeWithTag("area").performMouseEnter()
        assertEquals(listOf(PointerEventType.Enter), events)
        onNodeWithTag("area").performMouseExit()
        assertEquals(listOf(PointerEventType.Enter, PointerEventType.Exit), events)
    }

    @Test
    fun aPointerEventHasItsButtonPositionAndScroll() = runRavenTest {
        val events = mutableListOf<PointerEvent>()
        setContent {
            Box(
                Modifier.size(80.dp).testTag("area")
                    .onPointerEvent(PointerEventType.Press) { events += it }
                    .onPointerEvent(PointerEventType.Scroll) { events += it },
            ) {}
        }
        onNodeWithTag("area").performRightClick()
        val press = events.single()
        assertEquals(PointerButton.Secondary, press.button)
        assertEquals(Offset(40f, 40f), press.position)

        Rwindow_inject_mouse_wheel(Rwidget_get_window(onNodeWithTag("area").fetchWidget()), 0f, 2f)
        val scroll = events.last()
        assertEquals(PointerEventType.Scroll, scroll.type)
        // Raven's wheel up is a scroll up, negative in Compose.
        assertEquals(Offset(0f, -2f), scroll.scrollDelta)
    }

    @Test
    fun aPointerEventGoesOnUnlessItIsConsumed() = runRavenTest {
        var consume by mutableStateOf(false)
        var clicks = 0
        setContent {
            Box(Modifier.size(80.dp).clickable { clicks++ }) {
                Box(Modifier.size(40.dp).testTag("inner").onPointerEvent(PointerEventType.Press) { if (consume) it.consume() }) {}
            }
        }
        onNodeWithTag("inner").performClick()
        assertEquals(1, clicks)
        consume = true
        onNodeWithTag("inner").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun keysBubbleFromTheFocusedWidgetToTheWindow() = runRavenTest {
        val outer = mutableListOf<Key>()
        val inner = mutableListOf<Key>()
        val window = mutableListOf<KeyEvent>()
        val requester = FocusRequester()
        host.start {
            Window(onCloseRequest = {}, onKeyEvent = { window += it; true }) {
                Column(Modifier.onKeyEvent { if (it.type == KeyEventType.KeyDown) outer += it.key; false }) {
                    Box(
                        Modifier.size(40.dp).testTag("focused").focusRequester(requester).focusable()
                            .onKeyEvent { if (it.type == KeyEventType.KeyDown) inner += it.key; it.key == Key.A },
                    ) {}
                }
                LaunchedEffect(Unit) { requester.requestFocus() }
            }
        }
        waitForIdle()
        onNodeWithTag("focused").assertIsFocused()

        // Consumed by the focused widget: nothing above it sees A.
        pressKey(Key.A)
        assertEquals(listOf(Key.A), inner)
        assertEquals(emptyList(), outer)
        assertEquals(emptyList(), window)

        // B goes up to the window, which sees it go down and up, with its modifiers.
        pressKey(Key.B, ctrl = true)
        assertEquals(listOf(Key.A, Key.B), inner)
        assertEquals(listOf(Key.B), outer)
        assertEquals(
            listOf(KeyEvent(Key.B, KeyEventType.KeyDown, isCtrlPressed = true), KeyEvent(Key.B, KeyEventType.KeyUp, isCtrlPressed = true)),
            window,
        )
    }

    @Test
    fun withNothingFocusedTheWindowSeesEveryKey() = runRavenTest {
        val window = mutableListOf<Key>()
        host.start {
            Window(onCloseRequest = {}, onKeyEvent = { if (it.type == KeyEventType.KeyDown) window += it.key; false }) {
                Text("Nothing to focus")
            }
        }
        waitForIdle()
        pressKey(Key.F5)
        pressKey(Key.Escape)
        assertEquals(listOf(Key.F5, Key.Escape), window)
    }

    @Test
    fun aFocusRequestAndClearFocusAreReported() = runRavenTest {
        val changes = mutableListOf<Boolean>()
        val requester = FocusRequester()
        lateinit var focusManager: FocusManager
        setContent {
            focusManager = LocalFocusManager.current
            Column {
                TextField(value = "", onValueChange = {}, modifier = Modifier.testTag("field").focusRequester(requester).onFocusChanged { changes += it.isFocused })
                Text("Below")
            }
        }
        onNodeWithTag("field").assertIsNotFocused()
        requester.requestFocus()
        waitForIdle()
        onNodeWithTag("field").assertIsFocused()
        assertEquals(listOf(true), changes)

        focusManager.clearFocus()
        waitForIdle()
        onNodeWithTag("field").assertIsNotFocused()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun aClickFocusesAFocusableWidget() = runRavenTest {
        setContent { Box(Modifier.size(40.dp).testTag("box").focusable()) {} }
        onNodeWithTag("box").assertIsNotFocused()
        onNodeWithTag("box").performClick()
        onNodeWithTag("box").assertIsFocused()
    }

    @Test
    fun aFocusRequesterInNoChainCantFocus() = runRavenTest {
        setContent { Text("Nothing") }
        assertFailsWith<IllegalStateException> { FocusRequester().requestFocus() }
    }

    @Test
    fun aDroppedFocusRequesterNoLongerFocusesTheWidget() = runRavenTest {
        var attached by mutableStateOf(true)
        val requester = FocusRequester()
        setContent {
            val modifier = if (attached) Modifier.focusRequester(requester) else Modifier
            Box(modifier.size(40.dp).testTag("box").focusable()) {}
        }
        attached = false
        waitForIdle()
        assertFailsWith<IllegalStateException> { requester.requestFocus() }
    }

    @Test
    fun theHoverIconsReachRavenWithoutErrors() = runRavenTest {
        var icon by mutableStateOf<PointerIcon?>(PointerIcon.Hand)
        setContent {
            val chosen = icon
            Box(Modifier.size(40.dp).testTag("box").let { if (chosen != null) it.pointerHoverIcon(chosen) else it }) {}
        }
        for (each in PointerIcon.entries) {
            icon = each
            waitForIdle()
        }
        icon = null
        waitForIdle()
        onNodeWithTag("box").assertExists()
    }

    @Test
    fun anInputHandlerThatThrowsEndsTheHostWithItsError() {
        val error = assertFailsWith<IllegalStateException> {
            runRavenTest {
                setContent { Box(Modifier.size(40.dp).testTag("box").clickable { error("clicked") }) {} }
                onNodeWithTag("box").performClick()
                frame()
            }
        }
        assertTrue("clicked" in error.message.orEmpty(), error.message)
    }

    @Test
    fun handlersAreReplacedOnRecomposition() = runRavenTest {
        var label by mutableStateOf("first")
        val seen = mutableListOf<String>()
        setContent {
            val current = remember(label) { label }
            Box(Modifier.size(40.dp).testTag("box").clickable { seen += current }) {}
        }
        onNodeWithTag("box").performClick()
        label = "second"
        waitForIdle()
        onNodeWithTag("box").performClick()
        assertEquals(listOf("first", "second"), seen)
    }
}
