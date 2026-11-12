package raven.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rid
import raven.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `Modifier.alpha` and `clipToBounds`, through the Raven calls they make: Raven has no getter for them. */
class DrawTest {
    private val names = mutableMapOf<Rid, String>()

    /** Names the widget in the calls, once, when it is created. */
    private val Name = Property<String> { widget, name -> names[widget] = name }

    private val TestLabel = WidgetDescriptor("TestLabel", R_WIDGET_LABEL)

    // The Modifier is set before the other properties, so the widgets are named when the calls are read.
    private val recorded = mutableListOf<Pair<Rid, String>>()

    /** What the nodes asked of Raven. */
    private val calls: List<String> get() = recorded.map { (widget, call) -> call.replace("%", names[widget] ?: "?") }

    private val previousCalls = drawCalls

    @BeforeTest
    fun recordCalls() {
        drawCalls = object : DrawCalls() {
            override fun setAlpha(widget: Rid, alpha: Float) {
                recorded += widget to "alpha % $alpha"
                super.setAlpha(widget, alpha)
            }

            override fun setClipping(widget: Rid, clip: Boolean) {
                recorded += widget to "clip % $clip"
                super.setClipping(widget, clip)
            }
        }
    }

    @AfterTest
    fun restoreCalls() {
        drawCalls = previousCalls
    }

    @Test
    fun theLastAlphaInTheChainWins() {
        assertEquals(0.4f, fold(Modifier.alpha(0.8f).alpha(0.4f)).alpha)
        assertEquals(1f, fold(Modifier).alpha)
    }

    @Test
    fun anAlphaOutsideZeroToOneFails() {
        assertFailsWith<IllegalArgumentException> { Modifier.alpha(-0.1f) }
        assertFailsWith<IllegalArgumentException> { Modifier.alpha(1.5f) }
        assertFailsWith<IllegalArgumentException> { Modifier.alpha(Float.NaN) }
    }

    @Test
    fun clipToBoundsIsOffWhenLeftOut() {
        assertTrue(fold(Modifier.clipToBounds().clipToBounds()).clipToBounds)
        assertFalse(fold(Modifier).clipToBounds)
    }

    @Test
    fun aWidgetWithoutThemMakesNoCall() {
        withHost({
            Window(onCloseRequest = {}) { RavenWidget(TestLabel, Modifier, { bind("label", Name) }) }
        }) {
            assertEquals(emptyList(), calls)
        }
    }

    @Test
    fun alphaReachesRavenAndReturnsToOpaqueWhenRemoved() {
        var padding by mutableStateOf(0.dp)
        var alpha: Float? by mutableStateOf(0.6f)
        withHost({
            Window(onCloseRequest = {}) {
                val faded = alpha?.let { Modifier.alpha(it) } ?: Modifier
                RavenWidget(TestLabel, faded then PaddingElement(padding, padding, padding, padding), { bind("label", Name) })
            }
        }) {
            assertEquals(listOf("alpha label 0.6"), calls)
            recorded.clear()

            padding = 4.dp
            runUntilIdle()
            assertEquals(emptyList(), calls, "a padding change makes no call")

            alpha = 0.3f
            runUntilIdle()
            assertEquals(listOf("alpha label 0.3"), calls)
            recorded.clear()

            alpha = null
            runUntilIdle()
            assertEquals(listOf("alpha label 1.0"), calls)
        }
    }

    @Test
    fun clipToBoundsReachesRavenAndTurnsOffWhenRemoved() {
        var padding by mutableStateOf(0.dp)
        var clip by mutableStateOf(true)
        withHost({
            Window(onCloseRequest = {}) {
                val clipped = if (clip) Modifier.clipToBounds() else Modifier
                RavenWidget(TestLabel, clipped then PaddingElement(padding, padding, padding, padding), { bind("label", Name) })
            }
        }) {
            assertEquals(listOf("clip label true"), calls)
            recorded.clear()

            padding = 4.dp
            runUntilIdle()
            assertEquals(emptyList(), calls, "a padding change makes no call")

            clip = false
            runUntilIdle()
            assertEquals(listOf("clip label false"), calls)
        }
    }
}
