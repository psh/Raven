package raven.compose.ui.semantics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rid
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.Window
import raven.compose.ui.runUntilIdle
import raven.compose.ui.withHost
import kotlin.test.Test
import kotlin.test.assertEquals

// What Raven doesn't let a test read back (a widget's description, an announcement) is checked
// where compose-raven makes the call.

@OptIn(InternalComposeRavenApi::class)
class SemanticsCallsTest {
    private val TestLabel = WidgetDescriptor("TestLabel", R_WIDGET_LABEL)

    private class RecordingCalls : AccessibilityCalls() {
        val descriptions = mutableListOf<String?>()
        val announcements = mutableListOf<Pair<String, Boolean>>()

        override fun setDescription(widget: Rid, description: String?) {
            descriptions += description
            super.setDescription(widget, description)
        }

        override fun announce(text: String, assertive: Boolean) {
            announcements += text to assertive
            super.announce(text, assertive)
        }
    }

    private fun withCalls(block: (RecordingCalls) -> Unit) {
        val previous = accessibilityCalls
        val calls = RecordingCalls()
        accessibilityCalls = calls
        try {
            block(calls)
        } finally {
            accessibilityCalls = previous
        }
    }

    @Test
    fun anAccessibleDescriptionIsGivenAndTakenBack() = withCalls { calls ->
        var description by mutableStateOf<String?>("Plays the album")
        withHost({
            Window(onCloseRequest = {}) {
                RavenWidget(TestLabel, description?.let { Modifier.semantics { accessibleDescription = it } } ?: Modifier, {})
            }
        }) {
            assertEquals(listOf<String?>("Plays the album"), calls.descriptions)
            description = null
            runUntilIdle()
            assertEquals(listOf("Plays the album", null), calls.descriptions)
        }
    }

    @Test
    fun anAnnouncementReachesRaven() = withCalls { calls ->
        withHost({ Window(onCloseRequest = {}) {} }) {
            announceForAccessibility("Saved")
            announceForAccessibility("Disk full", assertive = true)
        }
        assertEquals(listOf("Saved" to false, "Disk full" to true), calls.announcements)
    }
}
