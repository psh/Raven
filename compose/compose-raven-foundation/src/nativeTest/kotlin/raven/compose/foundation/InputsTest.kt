package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.RKey
import raven.cinterop.R_FALSE
import raven.cinterop.R_KEY_BACKSPACE
import raven.cinterop.R_KEY_C
import raven.cinterop.R_KEY_ENTER
import raven.cinterop.R_KEY_LEFT
import raven.cinterop.R_KEY_V
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_TRUE
import raven.cinterop.Rclipboard_get_text
import raven.cinterop.Rclipboard_set_text
import raven.cinterop.Rcombo_box_get_item_count
import raven.cinterop.Rcombo_box_get_item_text
import raven.cinterop.Rcombo_box_get_selected
import raven.cinterop.Rslider_get_value
import raven.cinterop.Rspin_box_get_value
import raven.cinterop.Rtext_input_get_text
import raven.cinterop.Rtext_input_is_password
import raven.cinterop.Rtext_input_select_all
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_key
import raven.compose.foundation.text.KeyboardActions
import raven.compose.ui.Modifier
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.testTag
import raven.compose.ui.text.input.PasswordVisualTransformation
import raven.compose.ui.text.input.VisualTransformation
import raven.native.toRavenString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputsTest {
    private fun RavenTestScope.fieldText(tag: String): String = Rtext_input_get_text(onNodeWithTag(tag).fetchWidget()).toRavenString()

    private fun RavenTestScope.press(tag: String, key: RKey, modifiers: UInt = 0u) {
        val window = Rwidget_get_window(onNodeWithTag(tag).fetchWidget())
        Rwindow_inject_key(window, key, R_TRUE, modifiers)
        Rwindow_inject_key(window, key, R_FALSE, modifiers)
    }

    /** Ctrl+[key], which Raven's fields take as the command on every platform. */
    private fun RavenTestScope.command(tag: String, key: RKey) = press(tag, key, R_MOD_CTRL)

    private val defaultSink = layoutWarningSink

    @AfterTest
    fun restore() {
        layoutWarningSink = defaultSink
    }

    @Test
    fun typingReachesOnValueChange() = runRavenTest {
        var text by mutableStateOf("")
        val changes = mutableListOf<String>()
        setContent {
            TextField(text, onValueChange = {
                changes += it
                text = it
            }, Modifier.testTag("field"))
        }
        onNodeWithTag("field").performTextInput("hi")
        assertEquals(listOf("h", "hi"), changes)
        assertEquals("hi", fieldText("field"))
    }

    @Test
    fun anAppThatUpperCasesGetsItsTextWrittenBack() = runRavenTest {
        var text by mutableStateOf("")
        setContent { TextField(text, onValueChange = { text = it.uppercase() }, Modifier.testTag("field")) }
        onNodeWithTag("field").performTextInput("hi")
        assertEquals("HI", fieldText("field"))
    }

    @Test
    fun anAppThatRejectsInputReverts() = runRavenTest {
        var text by mutableStateOf("")
        setContent {
            TextField(text, onValueChange = { if (it.all(Char::isDigit)) text = it }, Modifier.testTag("field"))
        }
        onNodeWithTag("field").performTextInput("1")
        onNodeWithTag("field").performTextInput("x")
        assertEquals("1", fieldText("field"))
        onNodeWithTag("field").performTextInput("2")
        assertEquals("12", fieldText("field"))
        assertEquals("12", text)
    }

    @Test
    fun anAppThatIgnoresInputKeepsTheFieldEmpty() = runRavenTest {
        setContent { TextField("", onValueChange = {}, Modifier.testTag("field")) }
        onNodeWithTag("field").performTextInput("abc")
        assertEquals("", fieldText("field"))
    }

    @Test
    fun acceptedTypingLeavesTheCaretWhereItIs() = runRavenTest {
        var text by mutableStateOf("")
        setContent { TextField(text, onValueChange = { text = it }, Modifier.testTag("field")) }
        onNodeWithTag("field").performTextInput("ac")
        press("field", R_KEY_LEFT)
        onNodeWithTag("field").performTextInput("b")
        onNodeWithTag("field").performTextInput("b")
        assertEquals("abbc", fieldText("field"))
    }

    @Test
    fun rewrittenTextMovesTheCaretToTheEnd() = runRavenTest {
        var text by mutableStateOf("")
        setContent { TextField(text, onValueChange = { text = it.uppercase() }, Modifier.testTag("field")) }
        onNodeWithTag("field").performTextInput("ac")
        press("field", R_KEY_LEFT)
        onNodeWithTag("field").performTextInput("b")
        onNodeWithTag("field").performTextInput("d")
        assertEquals("ABCD", fieldText("field"))
    }

    @Test
    fun theAppCanReplaceTheText() = runRavenTest {
        var text by mutableStateOf("draft")
        setContent { TextField(text, onValueChange = { text = it }, Modifier.testTag("field"), placeholder = "Name") }
        assertEquals("draft", fieldText("field"))
        text = ""
        assertEquals("", fieldText("field"))
    }

    @Test
    fun aPasswordFieldHidesItsTextAndRefusesToCopyIt() = runRavenTest {
        var text by mutableStateOf("")
        var hidden by mutableStateOf(true)
        setContent {
            TextField(
                text, onValueChange = { text = it }, Modifier.testTag("field"),
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
        val widget = onNodeWithTag("field").fetchWidget()
        assertEquals(R_TRUE, Rtext_input_is_password(widget))
        onNodeWithTag("field").performTextInput("secret")
        assertEquals("secret", text, "the app still gets the text")

        Rclipboard_set_text("before")
        Rtext_input_select_all(widget)
        command("field", R_KEY_C)
        assertEquals("before", Rclipboard_get_text().toRavenString(), "nothing copied")

        hidden = false
        assertEquals(R_FALSE, Rtext_input_is_password(onNodeWithTag("field").fetchWidget()))
    }

    @Test
    fun anotherPasswordMaskWarnsAndIsDrawnAsABullet() = runRavenTest {
        val warnings = mutableListOf<String>()
        layoutWarningSink = { warnings += it }
        setContent { TextField("", onValueChange = {}, Modifier.testTag("field"), visualTransformation = PasswordVisualTransformation('*')) }
        assertEquals(R_TRUE, Rtext_input_is_password(onNodeWithTag("field").fetchWidget()))
        assertEquals(listOf("TextField: Raven hides a password with bullets, so the mask '*' is drawn as one"), warnings)
    }

    @Test
    fun maxLengthCutsTyping() = runRavenTest {
        var text by mutableStateOf("")
        val changes = mutableListOf<String>()
        setContent {
            TextField(text, onValueChange = {
                changes += it
                text = it
            }, Modifier.testTag("field"), maxLength = 3)
        }
        onNodeWithTag("field").performTextInput("abcde")
        assertEquals("abc", fieldText("field"))
        assertEquals(listOf("a", "ab", "abc"), changes, "a key past the limit changes nothing")
    }

    @Test
    fun maxLengthCountsCharactersNotUtf16Units() = runRavenTest {
        var text by mutableStateOf("")
        setContent { TextField(text, onValueChange = { text = it }, Modifier.testTag("field"), maxLength = 2) }
        onNodeWithTag("field").performTextInput("😀é!")
        assertEquals("😀é", text)
    }

    @Test
    fun aPasteIsCutToMaxLengthWithTheCaretAfterIt() = runRavenTest {
        var text by mutableStateOf("")
        setContent { TextField(text, onValueChange = { text = it }, Modifier.testTag("field"), maxLength = 5) }
        onNodeWithTag("field").performTextInput("ab")
        press("field", R_KEY_LEFT)
        Rclipboard_set_text("XYZW")
        command("field", R_KEY_V)
        assertEquals("aXYZb", text, "three of the four fit")
        // The caret is after the pasted text, not at the end: Backspace deletes the Z.
        press("field", R_KEY_BACKSPACE)
        assertEquals("aXYb", text)
    }

    @Test
    fun aValueLongerThanMaxLengthIsShownWhole() = runRavenTest {
        var text by mutableStateOf("abcdef")
        setContent { TextField(text, onValueChange = { text = it }, Modifier.testTag("field"), maxLength = 3) }
        assertEquals("abcdef", fieldText("field"))
        onNodeWithTag("field").performTextInput("g")
        assertEquals("abcdef", text, "no room to type")
        press("field", R_KEY_BACKSPACE)
        assertEquals("abcde", text, "deleting still works")
    }

    @Test
    fun maxLengthMustBePositive() = runRavenTest {
        val thrown = assertFailsWith<IllegalArgumentException> {
            setContent { TextField("", onValueChange = {}, maxLength = 0) }
        }
        assertEquals("a TextField's maxLength must be positive, not 0", thrown.message)
    }

    @Test
    fun enterCallsOnDone() = runRavenTest {
        var text by mutableStateOf("")
        val done = mutableListOf<String>()
        var second by mutableStateOf(false)
        setContent {
            TextField(
                text, onValueChange = { text = it }, Modifier.testTag("field"),
                keyboardActions = KeyboardActions(onDone = { done += if (second) "second: $text" else "first: $text" }),
            )
        }
        onNodeWithTag("field").performTextInput("hi")
        press("field", R_KEY_ENTER)
        assertEquals(listOf("first: hi"), done)
        assertEquals("hi", text, "Enter doesn't change the text")

        second = true
        press("field", R_KEY_ENTER)
        assertEquals(listOf("first: hi", "second: hi"), done, "the latest onDone")
    }

    @Test
    fun enterWithoutOnDoneDoesNothing() = runRavenTest {
        var text by mutableStateOf("")
        setContent { TextField(text, onValueChange = { text = it }, Modifier.testTag("field")) }
        onNodeWithTag("field").performTextInput("hi")
        press("field", R_KEY_ENTER)
        assertEquals("hi", text)
    }

    private fun RavenTestScope.sliderValue(tag: String): Float = Rslider_get_value(onNodeWithTag(tag).fetchWidget())

    @Test
    fun aSliderFollowsTheApp() = runRavenTest {
        var value by mutableStateOf(0f)
        setContent { Slider(value, onValueChange = { value = it }, Modifier.testTag("slider"), valueRange = 0f..100f) }
        onNodeWithTag("slider").performClick()
        assertEquals(50f, value, 1f)
        assertEquals(value, sliderValue("slider"))
        value = 20f
        assertEquals(20f, sliderValue("slider"))
    }

    @Test
    fun aSliderClampsToAnAppImposedRange() = runRavenTest {
        var value by mutableStateOf(0f)
        setContent {
            Slider(value, onValueChange = { value = it.coerceAtMost(30f) }, Modifier.testTag("slider"), valueRange = 0f..100f)
        }
        onNodeWithTag("slider").performClick()
        assertEquals(30f, value)
        assertEquals(30f, sliderValue("slider"))
    }

    @Test
    fun aSliderWhoseAppIgnoresTheChangeReverts() = runRavenTest {
        setContent { Slider(0.25f, onValueChange = {}, Modifier.testTag("slider")) }
        onNodeWithTag("slider").performClick()
        assertEquals(0.25f, sliderValue("slider"))
    }

    @Test
    fun aSliderShowsAValueOutsideItsRangeAtTheNearestEnd() = runRavenTest {
        setContent { Slider(5f, onValueChange = {}, Modifier.testTag("slider")) }
        assertEquals(1f, sliderValue("slider"))
    }

    @Test
    fun aSliderWithStepsSnapsToThem() = runRavenTest {
        // Two steps between 0 and 90 leave 0, 30, 60 and 90.
        setContent { Slider(40f, onValueChange = {}, Modifier.testTag("slider"), valueRange = 0f..90f, steps = 2) }
        assertEquals(30f, sliderValue("slider"), 0.001f)
    }

    private val fruit = listOf("Apple", "Banana", "Cherry")

    @Test
    fun aComboBoxFollowsTheApp() = runRavenTest {
        var selected by mutableStateOf(-1)
        val changes = mutableListOf<Int>()
        setContent {
            ComboBox(fruit, selected, onSelectedIndexChange = {
                changes += it
                selected = it
            }, Modifier.testTag("combo"))
        }
        onNodeWithTag("combo").assertSelectedIndex(-1)
        val combo = onNodeWithTag("combo").fetchWidget()
        onNodeWithTag("combo").performSelection(2)
        // Shown at once, then kept: the app's state now agrees, so nothing is written back.
        assertEquals(2, Rcombo_box_get_selected(combo))
        frame()
        assertEquals(2, Rcombo_box_get_selected(combo))
        assertEquals(listOf(2), changes)
        onNodeWithTag("combo").assertSelectedIndex(2)
        selected = 0
        onNodeWithTag("combo").assertSelectedIndex(0)
    }

    @Test
    fun aComboBoxWhoseAppIgnoresTheSelectionReverts() = runRavenTest {
        val changes = mutableListOf<Int>()
        setContent { ComboBox(fruit, 1, onSelectedIndexChange = { changes += it }, Modifier.testTag("combo")) }
        onNodeWithTag("combo").performSelection(2)
        onNodeWithTag("combo").assertSelectedIndex(1)
        assertEquals(listOf(2), changes)
    }

    @Test
    fun newItemsKeepTheSelectedIndex() = runRavenTest {
        var items by mutableStateOf(fruit)
        setContent { ComboBox(items, 1, onSelectedIndexChange = {}, Modifier.testTag("combo")) }
        onNodeWithTag("combo").assertSelectedIndex(1)
        items = listOf("Red", "Yellow", "Green", "Blue")
        onNodeWithTag("combo").assertSelectedIndex(1)
        val combo = onNodeWithTag("combo").fetchWidget()
        assertEquals(4, Rcombo_box_get_item_count(combo))
        assertEquals("Yellow", Rcombo_box_get_item_text(combo, 1).toRavenString())
    }

    @Test
    fun aSpinBoxFollowsTheApp() = runRavenTest {
        var value by mutableStateOf(10.0)
        val changes = mutableListOf<Double>()
        setContent {
            SpinBox(value, onValueChange = {
                changes += it
                value = it
            }, Modifier.testTag("spin"))
        }
        onNodeWithTag("spin").assertValueEquals(10.0)
        val spin = onNodeWithTag("spin").fetchWidget()
        onNodeWithTag("spin").performValueInput(42.5)
        // Shown at once, then kept, as an accepted selection is.
        assertEquals(42.5, Rspin_box_get_value(spin))
        frame()
        assertEquals(42.5, Rspin_box_get_value(spin))
        assertEquals(listOf(42.5), changes)
        value = 7.0
        onNodeWithTag("spin").assertValueEquals(7.0)
    }

    @Test
    fun aSpinBoxWhoseAppIgnoresTheValueReverts() = runRavenTest {
        setContent { SpinBox(10.0, onValueChange = {}, Modifier.testTag("spin")) }
        onNodeWithTag("spin").performValueInput(42.0)
        onNodeWithTag("spin").assertValueEquals(10.0)
    }

    @Test
    fun aSpinBoxKeepsToItsRange() = runRavenTest {
        var value by mutableStateOf(10.0)
        setContent { SpinBox(value, onValueChange = { value = it }, Modifier.testTag("spin"), valueRange = 0.0..50.0) }
        onNodeWithTag("spin").performValueInput(500.0)
        assertEquals(50.0, value)
        onNodeWithTag("spin").assertValueEquals(50.0)
    }

    @Test
    fun anIntSpinBoxRounds() = runRavenTest {
        var value by mutableStateOf(1)
        val changes = mutableListOf<Int>()
        setContent {
            SpinBox(value, onValueChange = {
                changes += it
                value = it
            }, Modifier.testTag("spin"))
        }
        onNodeWithTag("spin").performValueInput(2.6)
        assertEquals(listOf(3), changes)
        // The fraction typed is written back as the whole number the app took.
        onNodeWithTag("spin").assertValueEquals(3.0)
    }

    @Test
    fun anIntSpinBoxWhoseAppIgnoresTheValueReverts() = runRavenTest {
        setContent { SpinBox(5, onValueChange = {}, Modifier.testTag("spin"), valueRange = 0..10) }
        onNodeWithTag("spin").performValueInput(8.0)
        onNodeWithTag("spin").assertValueEquals(5.0)
    }
}
