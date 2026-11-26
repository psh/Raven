package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_KEY_C
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_TRUE
import raven.cinterop.Rclipboard_get_text
import raven.cinterop.Rclipboard_set_text
import raven.cinterop.Rlabel_get_selectable
import raven.cinterop.Rlabel_get_selected_text
import raven.cinterop.Rlabel_select_all
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_key
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.text.selection.DisableSelection
import raven.compose.foundation.text.selection.SelectionContainer
import raven.compose.ui.Modifier
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals

/** SelectionContainer and DisableSelection. */
class SelectionTest {
    private fun RavenTestScope.selectable(tag: String): Int = Rlabel_get_selectable(onNodeWithTag(tag).fetchWidget())

    /** Focuses the label with a click, selects all of it and presses Ctrl+C. */
    private fun RavenTestScope.copyAll(tag: String) {
        onNodeWithTag(tag).performClick()
        val label = onNodeWithTag(tag).fetchWidget()
        Rlabel_select_all(label)
        val window = Rwidget_get_window(label)
        Rwindow_inject_key(window, R_KEY_C, R_TRUE, R_MOD_CTRL)
        Rwindow_inject_key(window, R_KEY_C, R_FALSE, R_MOD_CTRL)
    }

    @Test
    fun textInsideASelectionContainerIsSelectable() = runRavenTest {
        setContent {
            Column {
                Text("outside", Modifier.testTag("outside"))
                SelectionContainer {
                    Column {
                        Text("inside", Modifier.testTag("inside"))
                        MarkdownText("**bold**", Modifier.testTag("markdown"))
                        DisableSelection { Text("disabled", Modifier.testTag("disabled")) }
                    }
                }
            }
        }
        assertEquals(R_FALSE, selectable("outside"))
        assertEquals(R_TRUE, selectable("inside"))
        assertEquals(R_TRUE, selectable("markdown"), "MarkdownText too")
        assertEquals(R_FALSE, selectable("disabled"))
    }

    @Test
    fun aSelectedTextIsCopied() = runRavenTest {
        setContent { SelectionContainer { Text("copy me", Modifier.testTag("text")) } }
        Rclipboard_set_text("")
        copyAll("text")
        assertEquals("copy me", Rclipboard_get_text().toRavenString())
    }

    @Test
    fun aMarkdownTextIsCopiedWithoutItsMarks() = runRavenTest {
        setContent { SelectionContainer { MarkdownText("Some **bold** text", Modifier.testTag("text")) } }
        Rclipboard_set_text("")
        copyAll("text")
        assertEquals("Some bold text", Rclipboard_get_text().toRavenString())
    }

    @Test
    fun leavingTheContainerEndsTheSelection() = runRavenTest {
        var inside by mutableStateOf(true)
        setContent {
            if (inside) {
                SelectionContainer { Text("text", Modifier.testTag("text")) }
            } else {
                DisableSelection { Text("text", Modifier.testTag("text")) }
            }
        }
        Rlabel_select_all(onNodeWithTag("text").fetchWidget())
        assertEquals("text", Rlabel_get_selected_text(onNodeWithTag("text").fetchWidget()).toRavenString())
        inside = false
        assertEquals(R_FALSE, selectable("text"))
        assertEquals("", Rlabel_get_selected_text(onNodeWithTag("text").fetchWidget()).toRavenString())
    }

    @Test
    fun aSelectableTextTakesTheClickFromAClickableAroundIt() = runRavenTest {
        var clicks by mutableStateOf(0)
        var selectable by mutableStateOf(false)
        setContent {
            Box(Modifier.clickable { clicks++ }) {
                if (selectable) {
                    SelectionContainer { Text("text", Modifier.testTag("text")) }
                } else {
                    Text("text", Modifier.testTag("text"))
                }
            }
        }
        onNodeWithTag("text").performClick()
        assertEquals(1, clicks, "a plain Text lets the click through")
        selectable = true
        onNodeWithTag("text").performClick()
        assertEquals(1, clicks, "a selectable one takes it")
    }
}
