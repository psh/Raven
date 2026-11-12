@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.compose.ui.DragData
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Window
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalDropTest {
    @Test
    fun aDroppedFileListReachesTheHandler() = runRavenTest {
        setDropContent()
        assertTrue(performExternalDrop(paths = listOf("/tmp/a.txt", "/tmp/b.txt")))
        onNodeWithText("files /tmp/a.txt, /tmp/b.txt").assertExists()
    }

    @Test
    fun aDroppedTextReachesTheHandler() = runRavenTest {
        setDropContent()
        assertTrue(performExternalDrop(text = "Hello"))
        onNodeWithText("text Hello").assertExists()
    }

    @Test
    fun aWindowWithoutAHandlerRefusesADrop() = runRavenTest {
        setContent { Text("Nothing") }
        assertFalse(performExternalDrop(paths = listOf("/tmp/a.txt"), text = "Hello"))
    }

    /** As [RavenTestScope.setContent], in a window whose handler shows what was last dropped. */
    private fun RavenTestScope.setDropContent() {
        host.start {
            var shown by remember { mutableStateOf("nothing") }
            Window(onCloseRequest = {}, title = "Test", onExternalDrop = { shown = describe(it) }) { Text(shown) }
        }
        waitForIdle()
    }

    private fun describe(data: DragData): String = when (data) {
        is DragData.FilesList -> "files ${data.paths.joinToString()}"
        is DragData.Text -> "text ${data.text}"
    }
}
