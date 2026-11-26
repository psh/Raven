package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_TEXT_INPUT
import raven.cinterop.Rid
import raven.cinterop.Rtext_input_get_insets
import raven.cinterop.Rtext_input_get_text
import raven.cinterop.Rtext_input_is_password
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_is_focused
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.ui.Modifier
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.text.input.PasswordVisualTransformation
import raven.compose.ui.text.input.VisualTransformation
import raven.compose.ui.unit.dp
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals

/** TextField's leadingIcon and trailingIcon. */
class TextFieldIconsTest {
    private fun insets(field: Rid): Pair<Float, Float> = memScoped {
        val start = alloc<FloatVar>()
        val end = alloc<FloatVar>()
        Rtext_input_get_insets(field, start.ptr, end.ptr)
        start.value to end.value
    }

    private fun RavenTestScope.field(): Rid = onNodeWithTag("field").fetchWidget()

    @Test
    fun aFieldWithNoIconsHasNoInsets() = runRavenTest {
        setContent { TextField("", {}, Modifier.testTag("field")) }
        assertEquals(0f to 0f, insets(field()))
    }

    @Test
    fun theInsetsFollowTheIconsWidths() = runRavenTest {
        var wide by mutableStateOf(false)
        setContent {
            TextField(
                "", {}, Modifier.testTag("field"),
                leadingIcon = { Box(Modifier.size(16.dp)) {} },
                trailingIcon = { Box(Modifier.size(if (wide) 40.dp else 24.dp)) {} },
            )
        }
        frame()
        assertEquals(16f + 8f to 24f + 8f, insets(field()), "each icon and the gaps around it")
        wide = true
        waitForIdle()
        frame()
        assertEquals(16f + 8f to 40f + 8f, insets(field()))
    }

    @Test
    fun theTagAndTheTypingReachTheFieldAndTheLayoutTheBoxAround() = runRavenTest {
        var text by mutableStateOf("")
        setContent {
            TextField(text, { text = it }, Modifier.width(300.dp).testTag("field"), trailingIcon = { Box(Modifier.size(20.dp)) {} })
        }
        assertEquals(R_WIDGET_TEXT_INPUT, Rwidget_get_type(field()))
        onNodeWithTag("field").performTextInput("hi")
        assertEquals("hi", text)
        assertEquals(300f, widgetRect(field()).width, "the field fills the box the width went to")
    }

    @Test
    fun aShowHideButtonTogglesThePasswordAndLeavesTheFocusInTheField() = runRavenTest {
        var text by mutableStateOf("")
        var hidden by mutableStateOf(true)
        setContent {
            TextField(
                text, { text = it }, Modifier.width(300.dp).testTag("field"),
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = { TextButton(onClick = { hidden = !hidden }, Modifier.testTag("eye")) { Text("Show") } },
            )
        }
        onNodeWithTag("field").performTextInput("ab")
        assertEquals(R_TRUE, Rtext_input_is_password(field()))

        onNodeWithTag("eye").performClick()
        waitForIdle()
        assertEquals(R_FALSE, Rtext_input_is_password(field()), "shown")
        assertEquals(R_TRUE, Rwidget_is_focused(field()), "the field kept the focus")
        onNodeWithTag("field").performTextInput("c")
        assertEquals("abc", Rtext_input_get_text(field()).toRavenString(), "the typing goes on where it was")
    }

    @Test
    fun anIconThatIsOnlyAnImageLetsTheClickThroughToTheField() = runRavenTest {
        setContent {
            TextField("", {}, Modifier.testTag("field"), leadingIcon = { Box(Modifier.size(20.dp).testTag("glass")) {} })
        }
        onNodeWithTag("glass").performClick()
        assertEquals(R_TRUE, Rwidget_is_focused(field()))
    }
}
