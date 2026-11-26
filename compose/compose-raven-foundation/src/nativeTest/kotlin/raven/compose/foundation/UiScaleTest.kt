package raven.compose.foundation

import raven.cinterop.Rget_ui_scale
import raven.cinterop.Rset_ui_scale
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_scale
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.RavenUiScale
import raven.compose.ui.platform.LocalDensity
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** RavenUiScale: the app's zoom. */
class UiScaleTest {
    @AfterTest
    fun reset() {
        // Raven starts each test at 1; only a value left pending would carry over.
        if (RavenUiScale.value != 1f) runRavenTest { RavenUiScale.value = 1f }
    }

    @Test
    fun theScaleReachesRavenAndEachWindowsDensity() = runRavenTest {
        val densities = mutableListOf<Float>()
        val labels = mutableListOf<Float>()
        setContent {
            densities += LocalDensity.current.density
            labels += RavenUiScale.value
            Box(Modifier.size(100.dp).testTag("box")) {}
        }
        val box = onNodeWithTag("box").fetchWidget()
        val monitor = Rwindow_get_scale(Rwidget_get_window(box))

        RavenUiScale.value = 2f
        frame()
        waitForIdle()
        assertEquals(2f, Rget_ui_scale())
        assertEquals(2f * monitor, Rwindow_get_scale(Rwidget_get_window(box)))
        assertEquals(2f * monitor, densities.last(), "LocalDensity followed")
        assertEquals(2f, labels.last(), "a reader of the scale recomposed")
        assertEquals(100f, widgetRect(box).width, "laid out in logical units, as before")
    }

    @Test
    fun ravensOwnChangeIsFollowed() = runRavenTest {
        setContent {}
        Rset_ui_scale(1.5f)
        frame()
        assertEquals(1.5f, RavenUiScale.value)
    }

    @Test
    fun aScaleSetBeforeTheAppStartsAppliesAtStart() {
        RavenUiScale.value = 1.25f
        runRavenTest {
            setContent {}
            assertEquals(1.25f, Rget_ui_scale())
            assertEquals(1.25f, RavenUiScale.value)
        }
        // The next app starts at Raven's own 1 again.
        runRavenTest {
            setContent {}
            assertEquals(1f, Rget_ui_scale())
            assertEquals(1f, RavenUiScale.value)
        }
    }

    @Test
    fun aScaleOutOfRavensRangeIsRefused() {
        val thrown = assertFailsWith<IllegalArgumentException> { RavenUiScale.value = 5f }
        assertEquals("the UI scale must be from 0.25 to 4, not 5.0", thrown.message)
        assertFailsWith<IllegalArgumentException> { RavenUiScale.value = 0f }
    }
}
