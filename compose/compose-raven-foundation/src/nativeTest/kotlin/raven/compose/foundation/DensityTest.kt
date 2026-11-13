package raven.compose.foundation

import androidx.compose.runtime.SideEffect
import raven.cinterop.Rset_ui_scale
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_scale
import raven.compose.foundation.layout.Box
import raven.compose.ui.Modifier
import raven.compose.ui.platform.LocalDensity
import raven.compose.ui.testTag
import raven.compose.ui.unit.Density
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `LocalDensity`, the window's scale. */
class DensityTest {
    @Test
    fun theDensityIsTheWindowsScaleAndFollowsIt() = runRavenTest {
        val seen = mutableListOf<Float>()
        setContent {
            val density = LocalDensity.current
            SideEffect { seen += density.density }
            Box(Modifier.testTag("box"))
        }
        val window = Rwidget_get_window(onNodeWithTag("box").fetchWidget())
        assertEquals(Rwindow_get_scale(window), seen.last())

        // Raven's interface scale doubles the window's, and the content recomposes with it.
        Rset_ui_scale(2f)
        frame()
        waitForIdle()
        assertEquals(2f * seen.first(), Rwindow_get_scale(window))
        assertEquals(Rwindow_get_scale(window), seen.last())
    }

    @Test
    fun theConversionsGoThroughTheDensity() {
        with(Density(2f)) {
            assertEquals(20f, 10.dp.toPx())
            assertEquals(5.dp, 10.toDp())
            assertEquals(1.5.dp, 3f.toDp())
        }
        assertFailsWith<IllegalArgumentException> { Density(0f) }
    }
}
