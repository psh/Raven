@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.editor

import raven.cinterop.Rtext_input_get_text
import raven.cinterop.Rtree_view_get_item_count
import raven.cinterop.Rwidget_focus
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.input.key.Key
import raven.compose.ui.theme.ThemeMetric
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The editor sample, headless. The scene's time and the stats move every frame, so waiting for idle
 * holds the clock.
 */
class EditorTest {
    private val editor = Editor()
    private var quits = 0

    private fun RavenTestScope.show() {
        mainClock.autoAdvance = false
        // Rendered on the UI thread, small: on other threads it would take real time, which frames here don't.
        editor.rendering.parallel = false
        editor.rendering.divisor = 4
        host.start { EditorApp(onQuit = { quits++ }, editor = editor) }
        waitForIdle()
        mainClock.advanceTimeByFrame()
    }

    private fun RavenTestScope.outlinerCount() = Rtree_view_get_item_count(onNodeWithTag("outliner").fetchWidget())

    @Test
    fun itStartsWithThreeObjectsAndTheVersionInTheLog() = runRavenTest {
        show()
        assertEquals(listOf("Sun", "Planet", "Moon"), editor.objects.map { it.name })
        assertEquals(3, outlinerCount())
        assertEquals("Moon", editor.selected?.name, "the last one added is selected")
        assertTrue(editor.log.first().startsWith("Raven "))
        assertEquals(4, editor.log.size)
    }

    @Test
    fun objectsAreAddedSelectedAndDeleted() = runRavenTest {
        show()
        onNodeWithTag("add").performClick()
        waitForIdle()
        assertEquals(4, outlinerCount())
        assertEquals("Object 4", editor.selected?.name)
        assertEquals("Object 4", Rtext_input_get_text(onNodeWithTag("prop.name").fetchWidget()).toRavenString(), "the properties show it")

        // Selected in the outliner with the keyboard: the first row.
        val outliner = onNodeWithTag("outliner").fetchWidget()
        Rwidget_focus(outliner)
        pressKey(Key.MoveHome)
        waitForIdle()
        assertEquals("Sun", editor.selected?.name)

        // Delete, the window's shortcut, removes it.
        onNodeWithTag("delete").performClick()
        waitForIdle()
        assertEquals(listOf("Planet", "Moon", "Object 4"), editor.objects.map { it.name })
        assertEquals(3, outlinerCount())
        assertEquals(null, editor.selected)
        assertEquals("Deleted \"Sun\"", editor.log.last())

        pressKey(Key.D, ctrl = true)
        waitForIdle()
        assertEquals(4, editor.objects.size, "Ctrl+D adds one")
        assertEquals(4, outlinerCount())
    }

    @Test
    fun aPropertyReachesTheSelectedObject() = runRavenTest {
        show()
        val moon = editor.selected!!
        onNodeWithTag("prop.speed").performValueInput(-2.5)
        waitForIdle()
        assertEquals(-2.5f, moon.speed)
        onNodeWithTag("prop.color").performSelection(3)
        waitForIdle()
        assertEquals(3, moon.color)
        onNodeWithTag("prop.name").performTextInput("!")
        waitForIdle()
        assertEquals("Moon!", moon.name)
        onNodeWithTag("prop.visible").performClick()
        waitForIdle()
        assertEquals(false, moon.visible)
    }

    @Test
    fun f5PausesAndTheSceneStopsMoving() = runRavenTest {
        show()
        mainClock.advanceTimeBy(200)
        val time = editor.time
        assertTrue(time > 0.1, "playing: $time")
        pressKey(Key.F5)
        waitForIdle()
        assertEquals(false, editor.playing)
        mainClock.advanceTimeBy(500)
        assertEquals(time, editor.time, 0.02, "paused")
        assertEquals("Animation paused", editor.log.last())
    }

    @Test
    fun theScaleSetsTheThemesMetrics() {
        assertEquals(12f, CompactMetrics.getValue(ThemeMetric.FontSize))
        assertEquals(32f, ComfortableMetrics.getValue(ThemeMetric.ControlHeight))
    }
}
