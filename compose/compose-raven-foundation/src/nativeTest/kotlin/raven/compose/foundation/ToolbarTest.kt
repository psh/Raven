package raven.compose.foundation

import raven.cinterop.R_ROLE_TOOLBAR
import raven.cinterop.R_WIDGET_HBOX
import raven.cinterop.R_WIDGET_SEPARATOR
import raven.cinterop.Rwidget_get_accessible_role
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_type
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.ui.Modifier
import raven.compose.ui.PaddingValues
import raven.compose.ui.Window
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Toolbar and ToolbarSeparator, read back from Raven's layout and accessibility. */
class ToolbarTest {
    @Test
    fun aToolbarHasTheToolbarRoleAndCentresItsChildrenInsideItsPadding() = runRavenTest {
        setContent {
            Toolbar(Modifier.testTag("toolbar")) {
                Box(Modifier.size(30.dp, 40.dp).testTag("tall"))
                Box(Modifier.size(20.dp).testTag("short"))
            }
        }
        val toolbar = onNodeWithTag("toolbar").fetchWidget()
        assertEquals(R_ROLE_TOOLBAR, Rwidget_get_accessible_role(toolbar))
        // The default padding is 6 by 4, and the children are 4 apart.
        assertEquals(WidgetRect(0f, 0f, 6f + 30f + 4f + 20f + 6f, 4f + 40f + 4f), widgetRect(toolbar))
        assertEquals(WidgetRect(6f, 4f, 30f, 40f), widgetRect(onNodeWithTag("tall").fetchWidget()))
        assertEquals(WidgetRect(40f, 14f, 20f, 20f), widgetRect(onNodeWithTag("short").fetchWidget()))
    }

    @Test
    fun theSeparatorSpansTheRowAndAWeightedSpacerPushesTheRestToTheEnd() = runRavenTest {
        setContent {
            Toolbar(Modifier.width(300.dp).testTag("toolbar"), contentPadding = PaddingValues(0.dp)) {
                Box(Modifier.size(20.dp, 30.dp))
                ToolbarSeparator(Modifier.testTag("separator"))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(20.dp).testTag("end"))
            }
        }
        val separator = onNodeWithTag("separator").fetchWidget()
        assertEquals(R_WIDGET_SEPARATOR, Rwidget_get_type(separator))
        val rect = widgetRect(separator)
        assertEquals(listOf(0f, 30f), listOf(rect.y, rect.height))
        assertEquals(WidgetRect(280f, 5f, 20f, 20f), widgetRect(onNodeWithTag("end").fetchWidget()))
    }

    @Test
    fun aToolbarSitsInAHeaderBar() = runRavenTest {
        host.start {
            Window(onCloseRequest = {}, title = "Test", customHeader = true) {
                Column(Modifier.fillMaxWidth()) {
                    HeaderBar(Modifier.fillMaxWidth().testTag("bar")) {
                        Toolbar(Modifier.testTag("toolbar")) {
                            Box(Modifier.size(20.dp).testTag("tool"))
                        }
                    }
                }
            }
        }
        waitForIdle()
        val bar = widgetRect(onNodeWithTag("bar").fetchWidget())
        val toolbar = onNodeWithTag("toolbar").fetchWidget()
        assertEquals(R_ROLE_TOOLBAR, Rwidget_get_accessible_role(toolbar))
        // Its children are in one HBox, inside the toolbar.
        assertEquals(1, Rwidget_get_child_count(toolbar))
        assertEquals(R_WIDGET_HBOX, Rwidget_get_type(Rwidget_get_child(toolbar, 0)))
        // Centred vertically in the bar, as the bar centres its children.
        val rect = widgetRect(toolbar)
        assertEquals(bar.y + (bar.height - rect.height) / 2, rect.y)
        val tool = widgetRect(onNodeWithTag("tool").fetchWidget())
        assertEquals(listOf(rect.x + 6f, rect.y + 4f), listOf(tool.x, tool.y))
    }
}
