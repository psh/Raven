package raven.compose.ui.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.compose.foundation.Button
import raven.compose.foundation.Checkbox
import raven.compose.foundation.Slider
import raven.compose.foundation.Text
import raven.compose.foundation.TextField
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.height
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyEventType
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.input.key.onKeyEvent
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The public test API, used as an app's tests use it: no Raven type, no opt-in. */
class RavenTestTest {
    @Test
    fun matchersCombineAndCollectionsCount() = runRavenTest {
        setContent {
            Column {
                Text("Apples", Modifier.testTag("fruit"))
                Text("Pears", Modifier.testTag("fruit"))
                Button(onClick = {}, Modifier.testTag("buy"), enabled = false) { Text("Buy") }
                Checkbox(checked = true, onCheckedChange = {}, Modifier.testTag("organic"))
            }
        }
        onAllNodesWithTag("fruit").assertCountEquals(2)
        onAllNodesWithTag("fruit")[1].assertTextEquals("Pears")
        onAllNodesWithTag("fruit").onFirst().assertTextEquals("Apples")
        onAllNodesWithTag("fruit").onLast().assertTextEquals("Pears")
        onNode(hasTestTag("fruit") and hasText("Pears")).assertExists()
        onAllNodes(hasText("Apples") or hasText("Pears")).assertCountEquals(2)
        onAllNodesWithText("ear", substring = true).assertCountEquals(1)

        onNodeWithTag("buy").assertIsNotEnabled().assert(isNotEnabled())
        onNodeWithTag("organic").assertIsEnabled().assert(isToggleable()).assertIsChecked()
        onAllNodes(isToggleable()).assertCountEquals(1)
        onAllNodesWithTag("fruit")[2].assertDoesNotExist()
        assertFailsWith<AssertionError> { onNodeWithTag("fruit").assertExists() }
    }

    @Test
    fun textIsReadReplacedAndTyped() = runRavenTest {
        var name by mutableStateOf("Ada")
        setContent { TextField(name, { name = it }, Modifier.testTag("name")) }
        onNodeWithTag("name").assertTextEquals("Ada")
        onNodeWithTag("name").performTextReplacement("Grace")
        onNodeWithTag("name").assertTextEquals("Grace")
        assertEquals("Grace", name)
        onNodeWithTag("name").performTextInput(" Hopper").assert(isFocused())
        assertEquals("Grace Hopper", onNodeWithTag("name").fetchText())
        onNodeWithTag("name").performTextReplacement("")
        assertEquals("", name)
    }

    @Test
    fun aKeyPressGoesToTheNode() = runRavenTest {
        var presses by mutableIntStateOf(0)
        setContent {
            TextField("", {}, Modifier.testTag("field").onKeyEvent { event -> (event.key == Key.F2 && event.type == KeyEventType.KeyDown).also { if (it) presses++ } })
        }
        onNodeWithTag("field").performKeyPress(KeyShortcut(Key.F2)).assertIsFocused()
        waitForIdle()
        assertEquals(1, presses)
    }

    @Test
    fun scrollingBringsANodeIntoViewAndValuesAreRead() = runRavenTest {
        var volume by mutableFloatStateOf(0.25f)
        setContent {
            VerticalScroll(Modifier.height(120.dp).testTag("list")) {
                Slider(volume, { volume = it }, Modifier.testTag("volume"))
                repeat(30) { Text("Row $it") }
            }
        }
        onNodeWithTag("volume").assertValueEquals(0.25).assertIsDisplayed()
        assertFailsWith<AssertionError> { onNodeWithText("Row 29").assertIsDisplayed() }
        onNodeWithTag("list").performScrollBy(dy = 10_000f)
        onNodeWithText("Row 29").assertIsDisplayed()
        assertFailsWith<AssertionError> { onNodeWithTag("volume").assertIsDisplayed() }
    }

    @Test
    fun anApplicationMakesItsOwnWindow() = runRavenTest {
        setApplicationContent { Window(onCloseRequest = {}, title = "Own") { Text("Inside") } }
        onNodeWithText("Inside").assertIsDisplayed()
    }

    @Test
    fun theLeakCheckCanBeTurnedOff() = runRavenTest(checkLeaks = false) {
        setContent { Text("Nothing leaks, but nothing is checked") }
        onNodeWithText("Nothing leaks", substring = true).assertExists()
    }
}
