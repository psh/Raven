package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rid
import raven.cinterop.Rmenu_item_get_text
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_type
import raven.cinterop.R_WIDGET_MENU
import raven.compose.foundation.layout.Column
import raven.compose.ui.ApplicationScope
import raven.compose.ui.Window
import raven.compose.ui.test.runRavenTest
import raven.native.toRavenString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The badge and the Dock menu given to Raven, which the headless window system takes and shows nothing of. */
private class RecordingTaskbarCalls : ShellCalls() {
    val badges = mutableListOf<String?>()
    val dockMenus = mutableListOf<Rid>()

    override fun setBadge(text: String?): Boolean {
        badges += text
        return super.setBadge(text)
    }

    override fun setDockMenu(menu: Rid): Boolean {
        dockMenus += menu
        return super.setDockMenu(menu)
    }
}

private object TaskbarTestApplication : ApplicationScope {
    override fun exitApplication() = Unit
}

class TaskbarTest {
    private val calls = RecordingTaskbarCalls()

    @BeforeTest
    fun record() {
        shellCalls = calls
    }

    @AfterTest
    fun restore() {
        shellCalls = ShellCalls()
    }

    @Test
    fun aBadgeShowsWhileComposedTheLastOneWinning() = runRavenTest {
        var first by mutableStateOf(true)
        var second by mutableStateOf(false)
        var text by mutableStateOf("3")
        setContent {
            Column {
                if (first) TaskbarBadge(text)
                if (second) TaskbarBadge("!")
            }
        }
        assertEquals(listOf<String?>("3"), calls.badges)
        text = "4"
        waitForIdle()
        second = true
        waitForIdle()
        assertEquals(listOf<String?>("3", "4", "!"), calls.badges)
        // An earlier one's change doesn't take over.
        text = "5"
        waitForIdle()
        assertEquals("!", calls.badges.last())
        // The last one gone, the one left shows again; none once every one has left.
        second = false
        waitForIdle()
        assertEquals("5", calls.badges.last())
        first = false
        waitForIdle()
        assertEquals(listOf<String?>("3", "4", "!", "5", null), calls.badges)
    }

    @Test
    fun anEmptyBadgeShowsNone() = runRavenTest {
        setContent { TaskbarBadge("") }
        assertEquals(emptyList(), calls.badges, "no badge to begin with, so none to clear")
    }

    @Test
    fun theDockMenuHoldsItsItems() = runRavenTest {
        var shown by mutableStateOf(true)
        var extra by mutableStateOf(false)
        setApplicationContent {
            with(TaskbarTestApplication) {
                if (shown) {
                    DockMenu {
                        Item("New window", onClick = {})
                        if (extra) Item("Open recent", onClick = {})
                    }
                }
            }
            Window(onCloseRequest = {}, title = "Test") {}
        }
        val menu = calls.dockMenus.single()
        assertEquals(R_WIDGET_MENU, Rwidget_get_type(menu))
        fun items() = List(Rwidget_get_child_count(menu)) { Rmenu_item_get_text(Rwidget_get_child(menu, it)).toRavenString() }
        assertEquals(listOf("New window"), items())
        extra = true
        waitForIdle()
        assertEquals(listOf("New window", "Open recent"), items())
        shown = false
        waitForIdle()
        assertEquals(listOf(menu, R_INVALID_ID), calls.dockMenus)
    }

    @Test
    fun withTwoDockMenusTheLastComposedWins() = runRavenTest {
        var second by mutableStateOf(true)
        setApplicationContent {
            with(TaskbarTestApplication) {
                DockMenu { Item("First", onClick = {}) }
                if (second) DockMenu { Item("Second", onClick = {}) }
            }
            Window(onCloseRequest = {}, title = "Test") {}
        }
        val (first, last) = calls.dockMenus
        assertNotEquals(first, last)
        assertEquals("Second", Rmenu_item_get_text(Rwidget_get_child(last, 0)).toRavenString())
        second = false
        waitForIdle()
        assertEquals(first, calls.dockMenus.last(), "the first one is the Dock's again")
    }
}
