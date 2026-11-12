package raven.compose.foundation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TRUE
import raven.cinterop.Raction_bind
import raven.cinterop.Rid
import raven.cinterop.Rlabel_get_markdown
import raven.cinterop.Rlabel_get_plain_text
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.ui.Modifier
import raven.compose.ui.platform.LocalUriHandler
import raven.compose.ui.platform.UriHandler
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MarkdownTextTest {
    @Test
    fun theMarkdownReachesTheLabel() = runRavenTest {
        setContent { MarkdownText("# Notes\n\nSome **strong** text") }
        val label = onNodeWithText("# Notes\n\nSome **strong** text").fetchWidget()
        assertEquals(R_TRUE, Rlabel_get_markdown(label))
        assertEquals("Notes\nSome strong text", Rlabel_get_plain_text(label).toRavenString())
    }

    @Test
    fun aChangedTextIsSetAgain() = runRavenTest {
        var markdown by mutableStateOf("*one*")
        setContent { MarkdownText(markdown) }
        onNodeWithText("*one*").assertExists()
        markdown = "**two**"
        val label = onNodeWithText("**two**").fetchWidget()
        assertEquals(R_TRUE, Rlabel_get_markdown(label))
        assertEquals("two", Rlabel_get_plain_text(label).toRavenString())
    }

    @Test
    fun aLinkClickReachesOnLinkClick() = runRavenTest {
        val clicked = mutableListOf<String>()
        var onLinkClick: ((String) -> Unit)? by mutableStateOf({ url: String -> clicked += "first $url" })
        setContent { MarkdownText("[Docs](https://example.com/docs)", Modifier.testTag("md"), onLinkClick) }
        clickFirstLink(onNodeWithTag("md").fetchWidget())
        assertEquals(listOf("first https://example.com/docs"), clicked)

        onLinkClick = { url -> clicked += "second $url" }
        waitForIdle()
        clickFirstLink(onNodeWithTag("md").fetchWidget())
        assertEquals(listOf("first https://example.com/docs", "second https://example.com/docs"), clicked)
    }

    @Test
    fun withoutOnLinkClickTheUriHandlerOpensTheLink() = runRavenTest {
        actionsFired.clear()
        val opened = RecordingUriHandler()
        var onLinkClick: ((String) -> Unit)? by mutableStateOf({ _: String -> actionsFired += "lambda" })
        setContent {
            CompositionLocalProvider(LocalUriHandler provides opened) {
                MarkdownText("[Run](action:markdown.test)", Modifier.testTag("md"), onLinkClick)
            }
        }
        Raction_bind("markdown.test", onAction, null)
        clickFirstLink(onNodeWithTag("md").fetchWidget())
        assertEquals(listOf("lambda"), actionsFired)
        assertEquals(emptyList(), opened.uris, "nothing reaches the handler while onLinkClick is set")

        // Raven's own handling is never used: the action: link reaches the handler, not Raven's actions.
        onLinkClick = null
        waitForIdle()
        clickFirstLink(onNodeWithTag("md").fetchWidget())
        assertEquals(listOf("lambda"), actionsFired)
        assertEquals(listOf("action:markdown.test"), opened.uris)
    }

    @Test
    fun aNestedUriHandlerWins() = runRavenTest {
        val outer = RecordingUriHandler()
        val inner = RecordingUriHandler()
        setContent {
            CompositionLocalProvider(LocalUriHandler provides outer) {
                CompositionLocalProvider(LocalUriHandler provides inner) {
                    MarkdownText("[Docs](https://example.com/docs)", Modifier.testTag("md"))
                }
            }
        }
        clickFirstLink(onNodeWithTag("md").fetchWidget())
        assertEquals(emptyList(), outer.uris)
        assertEquals(listOf("https://example.com/docs"), inner.uris)
    }

    @Test
    fun aChangedUriHandlerGetsTheNextLink() = runRavenTest {
        val first = RecordingUriHandler()
        val second = RecordingUriHandler()
        var handler: UriHandler by mutableStateOf(first)
        setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                MarkdownText("[Docs](https://example.com/docs)", Modifier.testTag("md"))
            }
        }
        clickFirstLink(onNodeWithTag("md").fetchWidget())
        handler = second
        waitForIdle()
        clickFirstLink(onNodeWithTag("md").fetchWidget())
        assertEquals(listOf("https://example.com/docs"), first.uris)
        assertEquals(listOf("https://example.com/docs"), second.uris)
    }

    @Test
    fun whatOnLinkClickThrowsEndsTheAppRatherThanCrossingIntoC() {
        val thrown = assertFailsWith<IllegalStateException> {
            runRavenTest {
                setContent { MarkdownText("[Docs](https://example.com/docs)", Modifier.testTag("md")) { error("link failed") } }
                clickFirstLink(onNodeWithTag("md").fetchWidget())
                waitForIdle()
            }
        }
        assertEquals("link failed", thrown.message)
    }

    /** Clicks a few points in from the label's top left corner, where its first link starts. */
    private fun clickFirstLink(label: Rid) {
        val rect = widgetRect(label)
        val window = Rwidget_get_window(label)
        Rwindow_inject_mouse_move(window, rect.x + 4f, rect.y + rect.height / 2)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
    }
}

private class RecordingUriHandler : UriHandler {
    val uris = mutableListOf<String>()

    override fun openUri(uri: String) {
        uris += uri
    }
}

private val actionsFired = mutableListOf<String>()

private val onAction = staticCFunction { _: Rid, action: CPointer<ByteVar>?, _: COpaquePointer? ->
    actionsFired += action?.toKString() ?: "?"
}
