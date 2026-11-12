@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_WIDGET_HEADER_BAR
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_desired_size
import raven.cinterop.Rwidget_get_type
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.ui.Alignment
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeaderBarTest {
    @Test
    fun theTitleFollowsItsParameter() = runRavenTest {
        var title by mutableStateOf<String?>("A much longer title than the window's")
        setCustomHeaderContent {
            Column {
                HeaderBar(Modifier.testTag("bar"), title = title)
                // The window's title is "Test".
                HeaderBar(Modifier.testTag("titled"), title = "Test")
            }
        }
        val bar = onNodeWithTag("bar").fetchWidget()
        val titled = onNodeWithTag("titled").fetchWidget()
        assertTrue(desiredWidth(bar) > desiredWidth(titled))

        title = null
        waitForIdle()
        assertEquals(desiredWidth(titled), desiredWidth(bar), "null shows the window's title")

        title = ""
        waitForIdle()
        assertTrue(desiredWidth(bar) < desiredWidth(titled))
    }

    @Test
    fun theButtonsFollowTheirParameter() = runRavenTest {
        var buttons by mutableStateOf(WindowButton.all())
        setCustomHeaderContent {
            Column { HeaderBar(Modifier.testTag("bar"), title = "", buttons = buttons) }
        }
        val bar = onNodeWithTag("bar").fetchWidget()
        val all = desiredWidth(bar)

        buttons = setOf(WindowButton.Close)
        waitForIdle()
        val close = desiredWidth(bar)
        assertTrue(close < all)

        buttons = emptySet()
        waitForIdle()
        assertTrue(desiredWidth(bar) < close)

        buttons = WindowButton.all()
        waitForIdle()
        assertEquals(all, desiredWidth(bar))
    }

    @Test
    fun aChildAlignedToTheEndSitsOnTheRight() = runRavenTest {
        setCustomHeaderContent {
            Column(Modifier.fillMaxWidth()) {
                HeaderBar(Modifier.fillMaxWidth()) {
                    // First in the composition, last in the bar.
                    Text("right", Modifier.align(Alignment.End))
                    Text("left")
                }
            }
        }
        val left = widgetRect(onNodeWithText("left").fetchWidget())
        val right = widgetRect(onNodeWithText("right").fetchWidget())
        assertTrue(right.x >= left.x + left.width, "$right is not on the right of $left")
    }

    @Test
    fun aMenuBarInAHeaderBarWorks() = runRavenTest {
        var saves = 0
        setCustomHeaderContent {
            Column {
                HeaderBar {
                    MenuBar {
                        Menu("File") { Item("Save", onClick = { saves++ }) }
                    }
                }
            }
        }
        onNodeWithText("Save").performClick()
        assertEquals(1, saves)
    }

    @Test
    fun aHeaderBarWithoutCustomHeaderLogsAWarning() {
        val warnings = collectWarnings {
            runRavenTest {
                setContent { HeaderBar(Modifier.testTag("bar")) }
                assertEquals(R_WIDGET_HEADER_BAR, Rwidget_get_type(onNodeWithTag("bar").fetchWidget()))
            }
        }
        assertEquals(listOf("HeaderBar: the window has no customHeader, so the system's title bar stays above it"), warnings)
    }

    @Test
    fun aHeaderBarWithCustomHeaderLogsNoWarning() {
        val warnings = collectWarnings {
            runRavenTest { setCustomHeaderContent { HeaderBar {} } }
        }
        assertEquals(emptyList(), warnings)
    }

    /** As [RavenTestScope.setContent], in a window with `customHeader`. */
    private fun RavenTestScope.setCustomHeaderContent(content: @Composable () -> Unit) {
        host.start { Window(onCloseRequest = {}, title = "Test", customHeader = true, content = content) }
        waitForIdle()
    }

    private fun desiredWidth(widget: Rid): Float = memScoped {
        val width = alloc<FloatVar>()
        val height = alloc<FloatVar>()
        Rwidget_get_desired_size(widget, width.ptr, height.ptr)
        width.value
    }

    private fun collectWarnings(block: () -> Unit): List<String> {
        val warnings = mutableListOf<String>()
        val sink = layoutWarningSink
        layoutWarningSink = { warnings += it }
        try {
            block()
        } finally {
            layoutWarningSink = sink
        }
        return warnings
    }
}
