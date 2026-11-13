@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Risland_get_notch
import raven.cinterop.Risland_is_expanded
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwindow_get_island
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.padding
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class IslandTest {
    /** Opens one window whose island is [island], with nothing in the window itself. */
    private fun RavenTestScope.setIsland(island: (@Composable () -> Unit)?) {
        host.start { Window(onCloseRequest = {}, title = "Test", island = island) {} }
        waitForIdle()
    }

    private fun isExpanded(island: Rid) = Risland_is_expanded(island) == R_TRUE

    /** Moves the pointer onto the middle of [widget], as the user would. */
    private fun hover(widget: Rid) {
        val rect = widgetRect(widget)
        Rwindow_inject_mouse_move(Rwidget_get_window(widget), rect.x + rect.width / 2, rect.y + rect.height / 2)
    }

    @Composable
    private fun TestIsland(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier.testTag("island"),
        expandOnHover: Boolean = true,
        expandOnClick: Boolean = true,
        notch: Boolean = false,
    ) = Island(
        expanded, onExpandedChange, modifier, expandOnHover = expandOnHover, expandOnClick = expandOnClick, notch = notch,
        folded = { Text("Now") },
        unfolded = { Text("Playing") },
    )

    @Test
    fun aWindowsIslandFloatsAtItsTopInItsOwnComposition() = runRavenTest {
        setIsland { TestIsland(expanded = false, onExpandedChange = {}, Modifier.testTag("island").padding(top = 8.dp), notch = true) }
        val island = onNodeWithTag("island").fetchWidget()
        val window = Rwidget_get_window(island)
        assertEquals(island, Rwindow_get_island(window))
        assertEquals(2, Rwidget_get_child_count(island), "the folded and the unfolded content")
        assertEquals(8f, widgetRect(island).y, "its top padding is its distance from the top edge")
        assertEquals(R_TRUE, Risland_get_notch(island))
        onNodeWithText("Now").assertExists()
        onNodeWithText("Playing").assertExists()
    }

    @Test
    fun aRefusedExpansionReverts() = runRavenTest {
        val changes = mutableListOf<Boolean>()
        setIsland { TestIsland(expanded = false, onExpandedChange = { changes += it }) }
        val island = onNodeWithTag("island").fetchWidget()
        hover(island)
        waitForIdle()
        assertEquals(listOf(true), changes)
        assertEquals(false, isExpanded(island), "the app kept it folded")
    }

    @Test
    fun aHoverExpansionReportsBack() = runRavenTest {
        var expanded by mutableStateOf(false)
        setIsland { TestIsland(expanded = expanded, onExpandedChange = { expanded = it }) }
        val island = onNodeWithTag("island").fetchWidget()
        hover(island)
        waitForIdle()
        assertEquals(true, expanded)
        assertEquals(true, isExpanded(island))

        // The app folds it back, as a LaunchedEffect with a delay would.
        expanded = false
        waitForIdle()
        assertEquals(false, isExpanded(island))
    }

    @Test
    fun aClickExpansionReportsBack() = runRavenTest {
        var expanded by mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        setIsland {
            TestIsland(expanded = expanded, onExpandedChange = { changes += it; expanded = it }, expandOnHover = false)
        }
        val island = onNodeWithTag("island").fetchWidget()
        hover(island)
        waitForIdle()
        assertEquals(emptyList(), changes, "the pointer alone doesn't unfold it")

        onNodeWithTag("island").performClick()
        waitForIdle()
        assertEquals(true, isExpanded(island))
        onNodeWithTag("island").performClick()
        waitForIdle()
        assertEquals(false, isExpanded(island))
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun anIslandWithoutClickExpansionIgnoresAClick() = runRavenTest {
        val changes = mutableListOf<Boolean>()
        setIsland { TestIsland(expanded = false, onExpandedChange = { changes += it }, expandOnHover = false, expandOnClick = false) }
        onNodeWithTag("island").performClick()
        waitForIdle()
        assertEquals(emptyList(), changes)
    }

    @Test
    fun theWindowsIslandComesAndGoesWithTheComposition() = runRavenTest {
        var shown by mutableStateOf(true)
        var expanded by mutableStateOf(true)
        host.start {
            Window(
                onCloseRequest = {}, title = "Test",
                island = if (shown) ({ TestIsland(expanded = expanded, onExpandedChange = {}) }) else null,
            ) { Text("Content") }
        }
        waitForIdle()
        val first = onNodeWithTag("island").fetchWidget()
        val window = Rwidget_get_window(first)
        assertEquals(true, isExpanded(first))

        // A change recomposes the island's own composition.
        expanded = false
        waitForIdle()
        assertEquals(false, isExpanded(first))

        shown = false
        waitForIdle()
        assertEquals(R_INVALID_ID, Rwindow_get_island(window))
        assertEquals(R_FALSE, Rwidget_is_valid(first))
        onNodeWithTag("island").assertDoesNotExist()
        onNodeWithText("Content").assertExists()

        shown = true
        waitForIdle()
        val second = onNodeWithTag("island").fetchWidget()
        assertEquals(second, Rwindow_get_island(window))
        // The window closes at shutdown with its island, which is disposed first.
    }

    @Test
    fun anIslandElsewhereIsAWidgetInTheLayout() = runRavenTest {
        setContent {
            Column {
                TestIsland(expanded = false, onExpandedChange = {})
                Text("Below")
            }
        }
        val island = onNodeWithTag("island").fetchWidget()
        assertEquals(R_INVALID_ID, Rwindow_get_island(Rwidget_get_window(island)))
        assertEquals(true, widgetRect(onNodeWithText("Below").fetchWidget()).y > widgetRect(island).y)
    }
}
