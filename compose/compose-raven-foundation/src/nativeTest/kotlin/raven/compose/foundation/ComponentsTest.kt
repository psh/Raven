package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_BUTTON
import raven.cinterop.Rid
import raven.cinterop.Rprogress_bar_get_value
import raven.cinterop.Rwidget_get_color
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_is_enabled
import raven.cinterop.Rwidget_is_valid
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.width
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import raven.native.CallbackRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentsTest {
    /** The Counter from the spec's overview. */
    @Composable
    private fun Counter() {
        var count by remember { mutableIntStateOf(0) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clicked $count times")
            Button(onClick = { count++ }) { Text("Add") }
        }
    }

    private fun rect(widget: Rid): List<Float> = memScoped {
        val values = List(4) { alloc<FloatVar>() }
        Rwidget_get_rect(widget, values[0].ptr, values[1].ptr, values[2].ptr, values[3].ptr)
        values.map { it.value }
    }

    @Test
    fun theOverviewsCounterCounts() = runRavenTest {
        setContent { Counter() }
        onNodeWithText("Clicked 0 times").assertExists()
        onNodeWithText("Add").performClick()
        onNodeWithText("Clicked 1 times").assertExists()
        onNodeWithText("Add").performClick()
        onNodeWithText("Clicked 2 times").assertExists()
    }

    @Test
    fun aButtonsContentHoldsSeveralChildrenCentredInARow() = runRavenTest {
        var clicks = 0
        setContent {
            Button(onClick = { clicks++ }, Modifier.width(200.dp).testTag("button")) {
                Text("Save")
                Spacer(Modifier.width(4.dp))
                Text("all")
            }
        }
        val button = onNodeWithTag("button").fetchWidget()
        val save = onNodeWithText("Save").fetchWidget()
        val all = onNodeWithText("all").fetchWidget()
        val panel = Rwidget_get_parent(save)
        assertEquals(panel, Rwidget_get_parent(all))
        assertEquals(button, Rwidget_get_parent(panel))

        val (saveX, saveY, saveWidth) = rect(save)
        val (allX, allY) = rect(all)
        assertEquals(saveX + saveWidth + 4f, allX, "laid out in a row")
        assertEquals(saveY, allY)
        val (buttonX, _, buttonWidth) = rect(button)
        val (panelX, _, panelWidth) = rect(panel)
        assertEquals(buttonX + buttonWidth / 2, panelX + panelWidth / 2, "centred in the button")

        onNodeWithText("all").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun aDisabledButtonIgnoresClicks() = runRavenTest {
        var enabled by mutableStateOf(false)
        var clicks = 0
        setContent {
            OutlinedButton(onClick = { clicks++ }, enabled = enabled, modifier = Modifier.testTag("button")) { Text("Go") }
        }
        assertEquals(R_FALSE, Rwidget_is_enabled(onNodeWithTag("button").fetchWidget()))
        onNodeWithText("Go").performClick()
        assertEquals(0, clicks)

        enabled = true
        assertEquals(R_TRUE, Rwidget_is_enabled(onNodeWithTag("button").fetchWidget()))
        onNodeWithText("Go").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun removingAButtonReleasesItsWidgets() = runRavenTest {
        var shown by mutableStateOf(true)
        var clicks = 0
        setContent {
            Column {
                if (shown) TextButton(onClick = { clicks++ }, Modifier.testTag("button")) { Text("Remove me") }
                Text("Stays")
            }
        }
        onNodeWithText("Remove me").performClick()
        assertEquals(1, clicks)
        val button = onNodeWithTag("button").fetchWidget()
        val referencesBefore = CallbackRegistry.liveCount

        shown = false
        onNodeWithTag("button").assertDoesNotExist()
        onNodeWithText("Remove me").assertDoesNotExist()
        onNodeWithText("Stays").assertExists()
        assertEquals(R_FALSE, Rwidget_is_valid(button))
        assertEquals(referencesBefore - 3, CallbackRegistry.liveCount, "the button, its content panel and its Text")
    }

    @Test
    fun dividersSpacersAndProgress() = runRavenTest {
        setContent {
            Column(Modifier.width(200.dp)) {
                HorizontalDivider(Modifier.testTag("line"))
                Spacer(Modifier.height(12.dp).testTag("gap"))
                LinearProgressIndicator(0.25f, Modifier.testTag("progress"))
                Row(Modifier.height(50.dp)) { VerticalDivider(Modifier.testTag("upright")) }
            }
        }
        assertEquals(200f, rect(onNodeWithTag("line").fetchWidget())[2])
        assertEquals(12f, rect(onNodeWithTag("gap").fetchWidget())[3])
        assertEquals(0.25f, Rprogress_bar_get_value(onNodeWithTag("progress").fetchWidget()))
        assertEquals(50f, rect(onNodeWithTag("upright").fetchWidget())[3])
    }

    @Test
    fun aButtonIsFoundByTheNameRavenGivesIt() = runRavenTest {
        setContent { Button(onClick = {}) { Text("Add") } }
        assertEquals(R_WIDGET_BUTTON, Rwidget_get_type(onNodeWithContentDescription("Add").fetchWidget()))
        onNodeWithContentDescription("dd", substring = true).assertExists()
        onNodeWithContentDescription("Remove").assertDoesNotExist()
        assertTrue(onNodeWithText("A", substring = true).fetchWidget() != onNodeWithContentDescription("Add").fetchWidget())
    }

    @Test
    fun aTextFollowsItsColourAndUnspecifiedRestoresTheThemes() = runRavenTest {
        var color by mutableStateOf(Color.Unspecified)
        setContent { Text("Tinted", color = color) }
        val label = onNodeWithText("Tinted").fetchWidget()
        fun shown() = ravenColor { r, g, b, a -> Rwidget_get_color(label, R_COLOR_TEXT, r, g, b, a) }
        val themes = shown()

        color = Color.Red
        waitForIdle()
        assertEquals(Color.Red, shown())
        color = Color(0x803B82F6)
        waitForIdle()
        assertEquals(Color(0x803B82F6), shown())
        color = Color.Unspecified
        waitForIdle()
        assertEquals(themes, shown())
    }
}
