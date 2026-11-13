package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_ROLE_BUTTON
import raven.cinterop.R_ROLE_HEADING
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_ROLE_TEXT
import raven.cinterop.R_ROLE_TOGGLE_BUTTON
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_accessible_name
import raven.cinterop.Rwidget_get_accessible_role
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.FlowRow
import raven.compose.foundation.layout.Row
import raven.compose.ui.Modifier
import raven.compose.ui.semantics.AccessibleRole
import raven.compose.ui.semantics.Role
import raven.compose.ui.semantics.semantics
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticsTest {
    private fun nameOf(widget: Rid) = Rwidget_get_accessible_name(widget).toRavenString()

    private fun RavenTestScope.nameOfTag(tag: String) = nameOf(onNodeWithTag(tag).fetchWidget())

    @Test
    fun aContentDescriptionNamesTheWidgetUntilItIsDropped() = runRavenTest {
        var described by mutableStateOf(true)
        setContent {
            Button(onClick = {}, Modifier.testTag("play").then(if (described) Modifier.semantics { contentDescription = "Play" } else Modifier)) {
                Text("▶")
            }
        }
        onNodeWithContentDescription("Play").assertExists()
        assertEquals("Play", nameOfTag("play"))

        described = false
        waitForIdle()
        onNodeWithContentDescription("Play").assertDoesNotExist()
        assertEquals("▶", nameOfTag("play"), "Raven's own name, from the content")
    }

    @Test
    fun rolesReachRaven() = runRavenTest {
        setContent {
            Column {
                Box(Modifier.testTag("button").semantics { role = Role.Button }) {}
                Box(Modifier.testTag("switch").semantics { role = Role.Switch }) {}
                Text("Settings", Modifier.testTag("heading").semantics { heading() })
                Text("Plain", Modifier.testTag("plain"))
                Box(Modifier.testTag("extension").semantics { role = Role.Button; accessibleRole = AccessibleRole.None }) {}
            }
        }
        assertEquals(R_ROLE_BUTTON, Rwidget_get_accessible_role(onNodeWithTag("button").fetchWidget()))
        assertEquals(R_ROLE_TOGGLE_BUTTON, Rwidget_get_accessible_role(onNodeWithTag("switch").fetchWidget()))
        assertEquals(R_ROLE_HEADING, Rwidget_get_accessible_role(onNodeWithTag("heading").fetchWidget()))
        assertEquals(R_ROLE_TEXT, Rwidget_get_accessible_role(onNodeWithTag("plain").fetchWidget()))
        assertEquals(R_ROLE_NONE, Rwidget_get_accessible_role(onNodeWithTag("extension").fetchWidget()), "accessibleRole wins over role")
    }

    @Test
    fun aDroppedRoleIsRavensOwnAgain() = runRavenTest {
        var heading by mutableStateOf(true)
        setContent { Text("Settings", Modifier.testTag("text").then(if (heading) Modifier.semantics { heading() } else Modifier)) }
        val text = onNodeWithTag("text").fetchWidget()
        assertEquals(R_ROLE_HEADING, Rwidget_get_accessible_role(text))

        heading = false
        waitForIdle()
        assertEquals(R_ROLE_TEXT, Rwidget_get_accessible_role(text))
    }

    @Test
    fun aHiddenTextNamesNothing() = runRavenTest {
        var hidden by mutableStateOf(true)
        setContent {
            Row {
                Text("Volume", if (hidden) Modifier.semantics { hideFromAccessibility() } else Modifier)
                Slider(0.5f, onValueChange = {}, Modifier.testTag("slider"))
            }
        }
        assertEquals("", nameOfTag("slider"), "Raven passes over the hidden Text")

        hidden = false
        waitForIdle()
        assertEquals("Volume", nameOfTag("slider"))
    }

    @Test
    fun anInputInARowIsLabelledByTheTextAfterIt() = runRavenTest {
        setContent {
            Column {
                Row {
                    Checkbox(checked = false, onCheckedChange = {}, Modifier.testTag("after"))
                    Text("Wi-Fi")
                }
                FlowRow {
                    Switch(checked = false, onCheckedChange = {}, Modifier.testTag("flow"))
                    Text("Bluetooth")
                }
                Row {
                    Text("Before")
                    Checkbox(checked = false, onCheckedChange = {}, Modifier.testTag("before"))
                    Text("After")
                }
                Row {
                    Checkbox(checked = false, onCheckedChange = {}, Modifier.testTag("described").semantics { contentDescription = "Own" })
                    Text("Ignored")
                }
            }
        }
        assertEquals("Wi-Fi", nameOfTag("after"))
        assertEquals("Bluetooth", nameOfTag("flow"))
        assertEquals("Before", nameOfTag("before"), "the Text before names it, as Raven does by itself")
        assertEquals("Own", nameOfTag("described"))
    }

    @Test
    fun aTextBelowAnInputInAColumnIsNoLabel() = runRavenTest {
        setContent {
            Column {
                Checkbox(checked = false, onCheckedChange = {}, Modifier.testTag("input"))
                Text("Below")
            }
        }
        assertEquals("", nameOfTag("input"))
    }

    @Test
    fun theLabelFollowsTheRowsChildrenAndTheDescription() = runRavenTest {
        var textAfter by mutableStateOf(true)
        var textBefore by mutableStateOf(false)
        var described by mutableStateOf(false)
        setContent {
            Row {
                if (textBefore) Text("First")
                Checkbox(
                    checked = false, onCheckedChange = {},
                    Modifier.testTag("input").then(if (described) Modifier.semantics { contentDescription = "Own" } else Modifier),
                )
                if (textAfter) Text("Wi-Fi")
            }
        }
        assertEquals("Wi-Fi", nameOfTag("input"))

        // A Text before it takes over; once it goes, the Text after is the label again.
        textBefore = true
        waitForIdle()
        assertEquals("First", nameOfTag("input"))
        textBefore = false
        waitForIdle()
        assertEquals("Wi-Fi", nameOfTag("input"))

        described = true
        waitForIdle()
        assertEquals("Own", nameOfTag("input"))
        described = false
        waitForIdle()
        assertEquals("Wi-Fi", nameOfTag("input"))

        // The Text after leaves the composition: no label is left pointing at it.
        textAfter = false
        waitForIdle()
        assertEquals("", nameOfTag("input"))
    }
}
