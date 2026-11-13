package raven.compose.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.Rwidget_get_rect
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Column, Row, Box and FlowRow, read back from Raven's layout. */
class LayoutsTest {
    /** The laid-out bounds of the widget tagged [tag], as x, y, width, height. */
    private fun RavenTestScope.rect(tag: String): List<Float> = memScoped {
        val widget = onNodeWithTag(tag).fetchWidget()
        val values = List(4) { alloc<FloatVar>() }
        Rwidget_get_rect(widget, values[0].ptr, values[1].ptr, values[2].ptr, values[3].ptr)
        values.map { it.value }
    }

    @Test
    fun aColumnStacksItsChildrenFromTheTopStart() = runRavenTest {
        setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(100.dp, 20.dp).testTag("a"))
                Box(Modifier.size(50.dp, 30.dp).testTag("b"))
            }
        }
        assertEquals(listOf(0f, 0f, 100f, 20f), rect("a"))
        assertEquals(listOf(0f, 28f, 50f, 30f), rect("b"))
    }

    @Test
    fun aColumnAlignsItsChildrenUnlessTheyAlignThemselves() = runRavenTest {
        setContent {
            Column(Modifier.width(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(100.dp, 20.dp).testTag("centred"))
                Box(Modifier.size(100.dp, 20.dp).align(Alignment.End).testTag("end"))
                Box(Modifier.height(20.dp).fillMaxWidth().testTag("filled"))
            }
        }
        assertEquals(listOf(50f, 0f, 100f, 20f), rect("centred"))
        assertEquals(listOf(100f, 20f, 100f, 20f), rect("end"))
        assertEquals(listOf(0f, 40f, 200f, 20f), rect("filled"))
    }

    @Test
    fun aRowSharesTheWidthLeftByWeight() = runRavenTest {
        setContent {
            Row(Modifier.width(300.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.size(50.dp, 40.dp).testTag("fixed"))
                Box(Modifier.weight(2f).height(10.dp).testTag("two"))
                Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.Top).testTag("one"))
            }
        }
        assertEquals(listOf(0f, 0f, 50f, 40f), rect("fixed"))
        assertEquals(listOf(50f, 30f, 500f / 3, 10f), rect("two"))
        assertEquals(listOf(50f + 500f / 3, 0f, 250f / 3, 10f), rect("one"))
    }

    @Test
    fun aBoxPlacesItsChildrenByItsAlignmentUnlessTheyAlignThemselves() = runRavenTest {
        setContent {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(100.dp).testTag("centred"))
                Box(Modifier.size(20.dp).align(Alignment.BottomEnd).testTag("corner"))
            }
        }
        assertEquals(listOf(50f, 50f, 100f, 100f), rect("centred"))
        assertEquals(listOf(180f, 180f, 20f, 20f), rect("corner"))
    }

    @Test
    fun paddingsAddUpOutsideTheWidget() = runRavenTest {
        setContent {
            Column {
                Box(Modifier.padding(10.dp).padding(start = 5.dp).size(20.dp).testTag("padded"))
                Box(Modifier.padding(horizontal = 1.dp, vertical = 2.dp).size(20.dp).testTag("next"))
            }
        }
        assertEquals(listOf(15f, 10f, 20f, 20f), rect("padded"))
        assertEquals(listOf(1f, 42f, 20f, 20f), rect("next"))
    }

    @Test
    fun anOffsetMovesAWidgetWithoutMovingItsNeighbours() = runRavenTest {
        var offset by mutableStateOf(0.dp)
        setContent {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(20.dp).testTag("before"))
                Box(Modifier.padding(start = 5.dp).offset(x = offset, y = offset).offset(x = 1.dp).size(20.dp).testTag("moved"))
                Box(Modifier.size(20.dp).testTag("after"))
            }
        }
        assertEquals(listOf(36f, 0f, 20f, 20f), rect("moved"))
        assertEquals(listOf(65f, 0f, 20f, 20f), rect("after"))

        offset = 7.dp
        waitForIdle()
        assertEquals(listOf(0f, 0f, 20f, 20f), rect("before"))
        assertEquals(listOf(43f, 7f, 20f, 20f), rect("moved"))
        assertEquals(listOf(65f, 0f, 20f, 20f), rect("after"))

        // Up and to the left, past the start of the Row.
        offset = (-50).dp
        waitForIdle()
        assertEquals(listOf(-14f, -50f, 20f, 20f), rect("moved"))
        assertEquals(listOf(65f, 0f, 20f, 20f), rect("after"))
    }

    @Test
    fun anOffsetHangsAnAlignedWidgetOverTheCorner() = runRavenTest {
        setContent {
            Box(Modifier.size(100.dp)) {
                Box(Modifier.size(20.dp).align(Alignment.BottomEnd).offset(10.dp, 8.dp).testTag("badge"))
            }
        }
        assertEquals(listOf(90f, 88f, 20f, 20f), rect("badge"))
    }

    @Test
    fun aNegativePaddingIsAnError() {
        assertFailsWith<IllegalArgumentException> { Modifier.padding(start = (-1).dp) }
    }

    @Test
    fun sizeBoundsAndFillMaxSize() = runRavenTest {
        setContent {
            Box(Modifier.fillMaxSize().testTag("window")) {
                Box(Modifier.widthIn(min = 40.dp).heightIn(min = 10.dp).testTag("bounded"))
            }
        }
        assertEquals(listOf(0f, 0f, 800f, 600f), rect("window"))
        assertEquals(listOf(0f, 0f, 40f, 10f), rect("bounded"))
    }

    @Test
    fun aChangedModifierIsLaidOutAgain() = runRavenTest {
        var wide by mutableStateOf(false)
        setContent {
            Column(Modifier.width(200.dp)) {
                Box(Modifier.height(10.dp).then(if (wide) Modifier.fillMaxWidth() else Modifier.width(30.dp)).testTag("box"))
            }
        }
        assertEquals(listOf(0f, 0f, 30f, 10f), rect("box"))
        wide = true
        waitForIdle()
        assertEquals(listOf(0f, 0f, 200f, 10f), rect("box"))
    }

    @Test
    fun aFlowRowWrapsOntoASecondLineWhenNarrow() = runRavenTest {
        var width by mutableStateOf(200.dp)
        setContent {
            FlowRow(Modifier.width(width), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(40.dp, 20.dp).testTag("a"))
                Box(Modifier.size(40.dp, 30.dp).testTag("b"))
                Box(Modifier.size(40.dp, 20.dp).testTag("c"))
            }
        }
        assertEquals(listOf(0f, 0f, 40f, 20f), rect("a"))
        assertEquals(listOf(48f, 0f, 40f, 30f), rect("b"))
        assertEquals(listOf(96f, 0f, 40f, 20f), rect("c"))

        // Too narrow for the third: it starts a line under the tallest of the first, the same gap down.
        width = 100.dp
        waitForIdle()
        assertEquals(listOf(48f, 0f, 40f, 30f), rect("b"))
        assertEquals(listOf(0f, 38f, 40f, 20f), rect("c"))
    }
}
