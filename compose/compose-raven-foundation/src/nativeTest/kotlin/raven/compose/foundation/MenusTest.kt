package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.setenv
import platform.posix.unsetenv
import raven.cinterop.R_FALSE
import raven.cinterop.R_KEY_S
import raven.cinterop.R_METRIC_ROW_HEIGHT
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_MOD_SHIFT
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rmenu_bar_is_native
import raven.cinterop.Rmenu_item_get_text
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_desired_size
import raven.cinterop.Rwidget_get_metric
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_key
import raven.compose.foundation.layout.Column
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyShortcut
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals

class MenusTest {
    @Test
    fun anItemsOnClickRuns() = runRavenTest {
        var saves = 0
        setContent {
            Column {
                MenuBar {
                    Menu("File") {
                        Item("Open", onClick = {})
                        Item("Save", onClick = { saves++ })
                    }
                }
            }
        }
        onNodeWithText("Save").performClick()
        assertEquals(1, saves)
        onNodeWithText("Save").performClick()
        assertEquals(2, saves)
    }

    @Test
    fun anItemOfASubMenuRuns() = runRavenTest {
        val opened = mutableListOf<String>()
        setContent {
            MenuBar {
                Menu("File") {
                    Item("New", onClick = {})
                    Separator()
                    Menu("Recent") {
                        Item("a.txt", onClick = { opened += "a.txt" })
                        Item("b.txt", onClick = { opened += "b.txt" })
                    }
                }
            }
        }
        onNodeWithText("b.txt").performClick()
        assertEquals(listOf("b.txt"), opened)
    }

    @Test
    fun aDisabledItemDoesNothing() = runRavenTest {
        var saves = 0
        setContent {
            MenuBar {
                Menu("File") { Item("Save", onClick = { saves++ }, enabled = false) }
            }
        }
        onNodeWithText("Save").performClick()
        assertEquals(0, saves)
    }

    @Test
    fun anItemIsEnabledAgainWhenTheAppSaysSo() = runRavenTest {
        var enabled by mutableStateOf(false)
        var saves = 0
        setContent {
            MenuBar {
                Menu("File") { Item("Save", onClick = { saves++ }, enabled = enabled) }
            }
        }
        onNodeWithText("Save").performClick()
        enabled = true
        onNodeWithText("Save").performClick()
        assertEquals(1, saves)
    }

    @Test
    fun anIgnoredCheckboxItemClickReverts() = runRavenTest {
        val changes = mutableListOf<Boolean>()
        setContent {
            MenuBar {
                Menu("View") { CheckboxItem("Word wrap", checked = false, onCheckedChange = { changes += it }) }
            }
        }
        onNodeWithText("Word wrap").assertIsNotChecked()
        onNodeWithText("Word wrap").performClick()
        onNodeWithText("Word wrap").assertIsNotChecked()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun aCheckboxItemWhoseAppTakesTheChangeStaysChecked() = runRavenTest {
        var wrap by mutableStateOf(false)
        setContent {
            MenuBar {
                Menu("View") { CheckboxItem("Word wrap", checked = wrap, onCheckedChange = { wrap = it }) }
            }
        }
        onNodeWithText("Word wrap").performClick()
        onNodeWithText("Word wrap").assertIsChecked()
        assertEquals(true, wrap)
        onNodeWithText("Word wrap").performClick()
        onNodeWithText("Word wrap").assertIsNotChecked()
        assertEquals(false, wrap)
        wrap = true
        onNodeWithText("Word wrap").assertIsChecked()
    }

    @Test
    fun aSeparatorAndAnItemInsertedInTheMiddleLandInPlace() = runRavenTest {
        var extra by mutableStateOf(false)
        val chosen = mutableListOf<String>()
        setContent {
            MenuBar {
                Menu("Edit") {
                    Item("Undo", onClick = { chosen += "Undo" })
                    if (extra) {
                        Separator()
                        Item("Paste", onClick = { chosen += "Paste" })
                    }
                    Item("Redo", onClick = { chosen += "Redo" })
                }
            }
        }
        val menu = Rwidget_get_parent(onNodeWithText("Undo").fetchWidget())
        assertEquals(listOf("Undo", "Redo"), entries(menu))
        val withoutSeparator = desiredHeight(menu)

        extra = true
        waitForIdle()
        assertEquals(listOf("Undo", "", "Paste", "Redo"), entries(menu))
        // Raven measures a separator, not a row: so it is a menu separator.
        val row = Rwidget_get_metric(menu, R_METRIC_ROW_HEIGHT) + 2f
        assertEquals(withoutSeparator + row + 7f, desiredHeight(menu))
        onNodeWithText("Paste").performClick()
        onNodeWithText("Redo").performClick()
        assertEquals(listOf("Paste", "Redo"), chosen)

        extra = false
        waitForIdle()
        assertEquals(listOf("Undo", "Redo"), entries(menu))
    }

    @Test
    fun itemsMovedByRecompositionLandInPlace() = runRavenTest {
        var names by mutableStateOf(listOf("a", "b", "c", "d"))
        setContent {
            MenuBar {
                Menu("Recent") {
                    for (name in names) key(name) { Item(name, onClick = {}) }
                }
            }
        }
        val menu = Rwidget_get_parent(onNodeWithText("a").fetchWidget())
        names = listOf("d", "b", "c", "a")
        waitForIdle()
        assertEquals(names, entries(menu))
        names = listOf("b", "c", "a")
        waitForIdle()
        assertEquals(names, entries(menu))
    }

    @Test
    fun aShortcutRunsItsItem() = runRavenTest {
        val chosen = mutableListOf<String>()
        setContent {
            MenuBar {
                Menu("File") {
                    Item("Save", onClick = { chosen += "Save" }, shortcut = KeyShortcut(Key.S, ctrl = true))
                    Item("Save as...", onClick = { chosen += "Save as" }, shortcut = KeyShortcut(Key.S, ctrl = true, shift = true))
                }
            }
        }
        val window = Rwidget_get_window(onNodeWithText("Save").fetchWidget())
        pressKey(window, R_KEY_S, R_MOD_CTRL or R_MOD_SHIFT)
        pressKey(window, R_KEY_S, R_MOD_CTRL)
        assertEquals(listOf("Save as", "Save"), chosen)
    }

    @Test
    fun aMenuBarStaysInTheWindow() = withSystemMenuBar {
        runRavenTest {
            setContent {
                MenuBar { Menu("File") { Item("Save", onClick = {}) } }
            }
            assertEquals(R_FALSE, Rmenu_bar_is_native(barOf("Save")))
        }
    }

    @Test
    fun aNativeMenuBarIsGivenToTheSystem() = withSystemMenuBar {
        runRavenTest {
            setContent {
                MenuBar(native = true) { Menu("File") { Item("Save", onClick = {}) } }
            }
            assertEquals(R_TRUE, Rmenu_bar_is_native(barOf("Save")))
        }
    }

    @Test
    fun aChangeOfNativeTakesEffectAtOnce() = withSystemMenuBar {
        runRavenTest {
            var native by mutableStateOf(false)
            setContent {
                MenuBar(native = native) { Menu("File") { Item("Save", onClick = {}) } }
            }
            val bar = barOf("Save")
            native = true
            waitForIdle()
            assertEquals(R_TRUE, Rmenu_bar_is_native(bar))
            native = false
            waitForIdle()
            assertEquals(R_FALSE, Rmenu_bar_is_native(bar))
        }
    }

    @Test
    fun aNativeMenuBarsItemsRunWhereTheSystemHasNoBar() = runRavenTest {
        var saves = 0
        setContent {
            MenuBar(native = true) {
                Menu("File") { Item("Save", onClick = { saves++ }, shortcut = KeyShortcut(Key.S, ctrl = true)) }
            }
        }
        // The headless host has no system menu bar, so Raven draws the bar in the window.
        assertEquals(R_FALSE, Rmenu_bar_is_native(barOf("Save")))
        onNodeWithText("Save").performClick()
        pressKey(Rwidget_get_window(onNodeWithText("Save").fetchWidget()), R_KEY_S, R_MOD_CTRL)
        assertEquals(2, saves)
    }

    @Test
    fun aNativeMenuBarsItemsRunWhenTheSystemShowsIt() = withSystemMenuBar {
        runRavenTest {
            var saves = 0
            setContent {
                MenuBar(native = true) {
                    Menu("File") { Item("Save", onClick = { saves++ }, shortcut = KeyShortcut(Key.S, ctrl = true)) }
                }
            }
            assertEquals(R_TRUE, Rmenu_bar_is_native(barOf("Save")))
            onNodeWithText("Save").performClick()
            pressKey(Rwidget_get_window(onNodeWithText("Save").fetchWidget()), R_KEY_S, R_MOD_CTRL)
            assertEquals(2, saves)
        }
    }

    /** The MenuBar of the bar's menu holding the item showing [text]. */
    private fun RavenTestScope.barOf(text: String): Rid =
        Rwidget_get_parent(Rwidget_get_parent(onNodeWithText(text).fetchWidget()))

    /**
     * Runs [block] with the headless host offering a system menu bar, as macOS does, so a MenuBar
     * Raven gives the system reports itself native. Raven reads this when it starts.
     */
    private fun withSystemMenuBar(block: () -> Unit) {
        setenv("RAVEN_NULL_SYSTEM_MENU_BAR", "1", 1)
        try {
            block()
        } finally {
            unsetenv("RAVEN_NULL_SYSTEM_MENU_BAR")
        }
    }

    /** The texts of a menu's entries in Raven's order, a separator's being empty. */
    private fun entries(menu: Rid): List<String> =
        List(Rwidget_get_child_count(menu)) { Rmenu_item_get_text(Rwidget_get_child(menu, it)).toRavenString() }

    private fun desiredHeight(widget: Rid): Float = memScoped {
        val width = alloc<FloatVar>()
        val height = alloc<FloatVar>()
        Rwidget_get_desired_size(widget, width.ptr, height.ptr)
        height.value
    }

    private fun pressKey(window: Rid, key: UInt, modifiers: UInt) {
        Rwindow_inject_key(window, key, R_TRUE, modifiers)
        Rwindow_inject_key(window, key, R_FALSE, modifiers)
    }
}
