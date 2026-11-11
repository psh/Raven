@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.staticCFunction
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_BUTTON
import raven.cinterop.R_WIDGET_CHECKBOX
import raven.cinterop.R_WIDGET_HBOX
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rcheckbox_set_changed_callback
import raven.cinterop.Rcheckbox_set_checked
import raven.cinterop.Rid
import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rlabel_set_text
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_type
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The binding layer, through test-only descriptors. */
class BindingTest {
    private val widgets = mutableMapOf<String, Rid>()

    /** Records the widget under a name: a property set once, when the widget is created. */
    private val Name = Property<String> { widget, name -> widgets[name] = widget }

    private var textWrites = 0
    private val CountedText = Property<String> { widget, text ->
        textWrites++
        Rlabel_set_text(widget, text)
    }

    private val TestLabel = WidgetDescriptor(R_WIDGET_LABEL)
    private val TestButton = WidgetDescriptor(R_WIDGET_BUTTON, ChildKind.Single, ContentKind.Row, events = listOf(ButtonClicked))
    private val TestCheckbox = WidgetDescriptor(R_WIDGET_CHECKBOX, events = listOf(CountedCheckboxChanged))

    @Test
    fun anUnchangedPropertyMakesNoRavenCall() {
        var text by mutableStateOf("a")
        var recompositions by mutableIntStateOf(0)
        withHost({
            Window(onCloseRequest = {}) {
                recompositions.let { RavenWidget(TestLabel, { bind("label", Name); bind(text, CountedText) }) }
            }
        }) {
            assertEquals(1, textWrites)
            recompositions++
            runUntilIdle()
            assertEquals(1, textWrites, "the same text again")

            text = "b"
            runUntilIdle()
            assertEquals(2, textWrites)
            assertEquals("b", Rlabel_get_text(widgets.getValue("label")).toRavenString())
        }
    }

    @Test
    fun aSingleChildWidgetsContentGoesIntoItsContentPanel() = withHost({
        Window(onCloseRequest = {}) {
            RavenWidget(TestButton, { bind("button", Name) }) {
                RavenWidget(TestLabel, { bind("first", Name) })
                RavenWidget(TestLabel, { bind("second", Name) })
            }
        }
    }) {
        val panel = Rwidget_get_parent(widgets.getValue("first"))
        assertEquals(R_WIDGET_HBOX, Rwidget_get_type(panel))
        assertEquals(panel, Rwidget_get_parent(widgets.getValue("second")))
        assertEquals(widgets.getValue("button"), Rwidget_get_parent(panel))
    }

    @Test
    fun anExceptionInACallbackReachesTheErrorPath() = withRavenChecks {
        val host = RavenHost(headless = true)
        try {
            host.start {
                Window(onCloseRequest = {}) {
                    RavenWidget(TestButton, { bind("button", Name); bind({ error("boom") }, ButtonClicked) })
                }
            }
            host.runUntilIdle()
            click(widgets.getValue("button"))
            assertEquals("boom", host.error?.message)
            assertFalse(host.frame(), "the error asks Raven to quit")
        } finally {
            host.close()
        }
    }

    @Test
    fun aRejectedChangeRevertsOnTheNextFrame() = checkbox(onCheckedChange = {}) { checkbox ->
        click(checkbox)
        assertEquals(R_TRUE, Rcheckbox_is_checked(checkbox), "Raven changes its own value first")
        drawFrame()
        assertEquals(R_FALSE, Rcheckbox_is_checked(checkbox))
        assertEquals(1, checkedWrites)
        assertTrue(isIdle())
    }

    @Test
    fun aChangeWithNoHandlerReverts() = checkbox(onCheckedChange = null) { checkbox ->
        click(checkbox)
        drawFrame()
        assertEquals(R_FALSE, Rcheckbox_is_checked(checkbox))
    }

    @Test
    fun aRejectedChangeRevertsWhileAnAnimationRecomposesEveryFrame() {
        var frameTime by mutableLongStateOf(0L)
        checkedWrites = 0
        withHost({
            Window(onCloseRequest = {}) {
                LaunchedEffect(Unit) {
                    while (true) withFrameNanos { frameTime = it }
                }
                RavenWidget(TestLabel, { bind(frameTime.toString(), CountedText) })
                RavenWidget(TestCheckbox, {
                    bind("checkbox", Name)
                    bind(false, CountedChecked)
                    bind({ _: Boolean -> }, CountedCheckboxChanged)
                })
            }
        }) {
            val checkbox = widgets.getValue("checkbox")
            click(checkbox)
            drawFrame()
            drawFrame()
            assertEquals(R_FALSE, Rcheckbox_is_checked(checkbox))
            assertEquals(1, checkedWrites)
        }
    }

    @Test
    fun anAcceptedChangeCausesNoWrite() {
        var checked by mutableStateOf(false)
        checkbox(checked = { checked }, onCheckedChange = { checked = it }) { checkbox ->
            click(checkbox)
            drawFrame()
            assertTrue(checked)
            assertEquals(R_TRUE, Rcheckbox_is_checked(checkbox))
            assertEquals(0, checkedWrites)
            assertTrue(isIdle())
        }
    }

    /** A checkbox that shows [checked], with its widget passed to [block]. */
    private fun checkbox(
        checked: () -> Boolean = { false },
        onCheckedChange: ((Boolean) -> Unit)?,
        block: RavenHost.(Rid) -> Unit,
    ) {
        checkedWrites = 0
        withHost({
            Window(onCloseRequest = {}) {
                val value = checked()
                RavenWidget(TestCheckbox, {
                    bind("checkbox", Name)
                    bind(value, CountedChecked)
                    bind(onCheckedChange, CountedCheckboxChanged)
                })
            }
        }) { block(widgets.getValue("checkbox")) }
    }
}

private var checkedWrites = 0

private val CountedChecked = ControlledValue(
    read = { Rcheckbox_is_checked(it) == R_TRUE },
    write = { widget, checked ->
        checkedWrites++
        Rcheckbox_set_checked(widget, if (checked) R_TRUE else R_FALSE)
    },
)

private val CountedCheckboxChanged: Event<(Boolean) -> Unit> = Event(marks = CountedChecked) { widget, userData ->
    Rcheckbox_set_changed_callback(widget, onCountedCheckboxChanged, userData)
}

private val onCountedCheckboxChanged = staticCFunction { _: Rid, checked: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, CountedCheckboxChanged) { it(checked == R_TRUE) }
}
