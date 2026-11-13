package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.Rwidget_get_child_count
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.testTag
import raven.compose.ui.unit.DpOffset
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** The menus opened from code: `ContextMenuArea` on a right click, `DropdownMenu` by its state. */
class PopupMenusTest {
    @Test
    fun aRightClickOpensTheContextMenuAndAnEntryRuns() = runRavenTest {
        val chosen = mutableListOf<String>()
        setContent {
            ContextMenuArea(items = { listOf(ContextMenuItem("Copy") { chosen += "Copy" }, ContextMenuItem("Paste") { chosen += "Paste" }) }) {
                Box(Modifier.size(80.dp).testTag("area")) {}
            }
        }
        assertEquals(emptyList(), openMenus())
        onNodeWithTag("area").performRightClick()
        assertEquals(1, openMenus().size)
        clickOpenMenuItem("Paste")
        assertEquals(listOf("Paste"), chosen)
        waitForIdle()
        assertEquals(emptyList(), openMenus())
    }

    @Test
    fun theContextMenuShowsTheItemsOfTheMomentItOpens() = runRavenTest {
        var canPaste = false
        val chosen = mutableListOf<String>()
        setContent {
            ContextMenuArea(items = { listOfNotNull(ContextMenuItem("Copy") {}, if (canPaste) ContextMenuItem("Paste") { chosen += "Paste" } else null) }) {
                Box(Modifier.size(80.dp).testTag("area")) {}
            }
        }
        onNodeWithTag("area").performRightClick()
        assertFails { clickOpenMenuItem("Paste") }
        // The menu opened over the area: Escape closes it, so the next click reaches the area.
        pressKey(Key.Escape)
        canPaste = true
        onNodeWithTag("area").performRightClick()
        clickOpenMenuItem("Paste")
        assertEquals(listOf("Paste"), chosen)
    }

    @Test
    fun aLeftClickOrADisabledAreaOpensNoMenu() = runRavenTest {
        var enabled by mutableStateOf(true)
        setContent {
            ContextMenuArea(items = { listOf(ContextMenuItem("Copy") {}) }, enabled = enabled) {
                Box(Modifier.size(80.dp).testTag("area")) {}
            }
        }
        onNodeWithTag("area").performClick()
        assertEquals(emptyList(), openMenus())
        enabled = false
        onNodeWithTag("area").performRightClick()
        assertEquals(emptyList(), openMenus())
    }

    @Test
    fun aDropdownMenuOpensUnderItsParentAndAnEntryRuns() = runRavenTest {
        var lossless by mutableStateOf(false)
        setContent {
            val state = rememberDropdownMenuState()
            Box(Modifier.testTag("anchor")) {
                Button(onClick = { state.open() }) { Text("More") }
                DropdownMenu(state, offset = DpOffset(10.dp, 4.dp)) {
                    CheckboxItem("Lossless audio", checked = lossless, onCheckedChange = { lossless = it })
                    Separator()
                    Item("About", onClick = {})
                }
            }
        }
        // Closed, it can't be chosen.
        assertFails { onNodeWithText("Lossless audio").performClick() }

        onNodeWithText("More").performClick()
        val menu = openMenus().single()
        val anchor = widgetRect(onNodeWithTag("anchor").fetchWidget())
        val opened = widgetRect(menu)
        assertEquals(anchor.x + 10f, opened.x)
        assertEquals(anchor.y + anchor.height + 4f, opened.y)

        onNodeWithText("Lossless audio").performClick()
        assertTrue(lossless)
        onNodeWithText("Lossless audio").assertIsChecked()
    }

    @Test
    fun aDropdownMenuTakesNoRoomWhereItIsComposed() = runRavenTest {
        setContent {
            Row(Modifier.testTag("row")) {
                Text("a")
                DropdownMenu(rememberDropdownMenuState()) { Item("Hidden", onClick = {}) }
                Text("b")
            }
        }
        assertEquals(2, Rwidget_get_child_count(onNodeWithTag("row").fetchWidget()))
        onNodeWithText("Hidden").assertExists()
    }

    @Test
    fun aDropdownMenusShortcutWorksBeforeItEverOpens() = runRavenTest {
        var quits = 0
        setContent {
            Box {
                Text("Player")
                DropdownMenu(rememberDropdownMenuState()) { Item("Quit", onClick = { quits++ }, shortcut = KeyShortcut(Key.Q, ctrl = true)) }
            }
        }
        pressKey(Key.Q, ctrl = true)
        waitForIdle()
        assertEquals(1, quits)
    }
}
