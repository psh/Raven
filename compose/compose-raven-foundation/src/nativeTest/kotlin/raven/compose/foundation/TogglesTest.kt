package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_TRUE
import raven.cinterop.Rcheckbox_is_checked
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.ui.Modifier
import raven.compose.ui.testTag
import kotlin.test.Test
import kotlin.test.assertEquals

class TogglesTest {
    @Test
    fun aCheckboxWhoseAppIgnoresTheChangeReverts() = runRavenTest {
        var changes = 0
        setContent { Checkbox(checked = false, onCheckedChange = { changes++ }, Modifier.testTag("box")) }
        onNodeWithTag("box").performClick()
        onNodeWithTag("box").assertIsNotChecked()
        assertEquals(1, changes)
    }

    @Test
    fun aCheckboxWhoseAppTakesTheChangeStaysChecked() = runRavenTest {
        var checked by mutableStateOf(false)
        setContent {
            Row {
                Checkbox(checked = checked, onCheckedChange = { checked = it }, Modifier.testTag("box"))
                Text("Remember me")
            }
        }
        val box = onNodeWithTag("box").fetchWidget()
        onNodeWithTag("box").performClick()
        // Shown at once, then kept: the app's state now agrees, so nothing is written back.
        assertEquals(R_TRUE, Rcheckbox_is_checked(box))
        frame()
        assertEquals(R_TRUE, Rcheckbox_is_checked(box))
        assertEquals(true, checked)
        onNodeWithTag("box").assertIsChecked()

        onNodeWithTag("box").performClick()
        onNodeWithTag("box").assertIsNotChecked()
        assertEquals(false, checked)
    }

    @Test
    fun aSwitchIsControlledToo() = runRavenTest {
        var on by mutableStateOf(true)
        setContent { Switch(checked = on, onCheckedChange = { on = it }, Modifier.testTag("switch")) }
        onNodeWithTag("switch").assertIsChecked()
        onNodeWithTag("switch").performClick()
        onNodeWithTag("switch").assertIsNotChecked()
        assertEquals(false, on)
        on = true
        onNodeWithTag("switch").assertIsChecked()
    }

    @Test
    fun aDisabledCheckboxIgnoresClicks() = runRavenTest {
        var changes = 0
        setContent { Checkbox(checked = false, onCheckedChange = { changes++ }, Modifier.testTag("box"), enabled = false) }
        onNodeWithTag("box").performClick()
        onNodeWithTag("box").assertIsNotChecked()
        assertEquals(0, changes)
    }

    private val options = listOf("a", "b", "c")

    /** A radio group showing [selected], whose clicks go to [onSelect]. */
    private fun RavenTestScope.radioGroup(selected: () -> String, onSelect: (String) -> Unit) = setContent {
        val current = selected()
        Column {
            for (option in options) {
                RadioButton(selected = option == current, onClick = { onSelect(option) }, Modifier.testTag(option))
            }
        }
    }

    private fun RavenTestScope.assertSelected(option: String) {
        for (other in options) {
            if (other == option) onNodeWithTag(other).assertIsChecked() else onNodeWithTag(other).assertIsNotChecked()
        }
    }

    @Test
    fun aRadioGroupFollowsTheAppsChoice() = runRavenTest {
        var selected by mutableStateOf("a")
        val clicks = mutableListOf<String>()
        radioGroup({ selected }) {
            clicks += it
            selected = it
        }
        assertSelected("a")
        onNodeWithTag("b").performClick()
        assertSelected("b")
        assertEquals(listOf("b"), clicks, "only the radio that became checked calls back")
        onNodeWithTag("b").performClick()
        assertEquals(listOf("b"), clicks, "a checked radio can't be clicked off")
    }

    @Test
    fun aRadioGroupKeepsTheAppsChoiceWhenTheAppIgnoresTheClick() = runRavenTest {
        radioGroup({ "a" }) {}
        onNodeWithTag("b").performClick()
        assertSelected("a")
    }

    @Test
    fun aRadioGroupShowsADifferentChoiceThanTheOneClicked() = runRavenTest {
        var selected by mutableStateOf("a")
        radioGroup({ selected }) { selected = "c" }
        onNodeWithTag("b").performClick()
        assertSelected("c")
    }
}
