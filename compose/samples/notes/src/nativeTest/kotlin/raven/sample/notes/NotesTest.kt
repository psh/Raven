package raven.sample.notes

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import raven.compose.mcp.McpConfig
import raven.compose.mcp.McpServer
import raven.compose.mcp.McpTestClient
import raven.compose.mcp.whileDrawing
import raven.compose.ui.Window
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PORT = 18791
private const val TOKEN = "notes-test"

/** The notepad as an agent drives it: its own notes.* tools, and the UI tools. */
class NotesTest {
    private fun RavenTestScope.start(uiTools: Boolean = false) = setApplicationContent {
        McpServer(McpConfig(name = "notes", port = PORT, token = TOKEN, uiTools = uiTools)) {
            Window(onCloseRequest = {}, title = "Notes") { NotesApp() }
        }
    }

    private fun RavenTestScope.call(tool: String, arguments: JsonObject = JsonObject(emptyMap())): JsonObject =
        whileDrawing(::frame) { McpTestClient(PORT, TOKEN).connect().call(tool, arguments) }

    private fun RavenTestScope.text(tool: String, arguments: JsonObject = JsonObject(emptyMap())): String {
        val result = call(tool, arguments)
        assertTrue(result["isError"]?.jsonPrimitive?.boolean != true, result.toString())
        return result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
    }

    private fun RavenTestScope.failure(tool: String, arguments: JsonObject = JsonObject(emptyMap())): String {
        val result = call(tool, arguments)
        assertEquals(true, result["isError"]?.jsonPrimitive?.boolean, result.toString())
        return result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
    }

    private fun args(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
        for ((key, value) in pairs) when (value) {
            is String -> put(key, value)
            is Int -> put(key, value)
            is Boolean -> put(key, value)
        }
    }

    @Test
    fun anAgentAddsListsAndDeletesNotes() = runRavenTest {
        start()
        val tools = whileDrawing(::frame) { McpTestClient(PORT, TOKEN).connect().tools() }
        assertEquals(listOf("notes.add", "notes.delete", "notes.list"), tools.map { it.jsonObject["name"]!!.jsonPrimitive.content }.sorted())

        assertEquals("no notes", text("notes.list"))
        assertEquals("added note 0", text("notes.add", args("text" to "buy milk")))
        assertEquals("added note 1", text("notes.add", args("text" to "call Oscar")))
        assertEquals("0: buy milk\n1: call Oscar", text("notes.list"))
        onNodeWithTag("count").assertTextEquals("2 notes")

        assertEquals("deleted \"buy milk\"", text("notes.delete", args("index" to 0)))
        assertEquals("there is no note 5", failure("notes.delete", args("index" to 5)))
        assertEquals("a note needs some text", failure("notes.add", args("text" to " ")))
        assertEquals("0: call Oscar", text("notes.list"))
        onNodeWithTag("count").assertTextEquals("1 note")
    }

    @Test
    fun anAgentDrivesTheNotepadWithTheUiTools() = runRavenTest {
        start(uiTools = true)
        text("notes.add", args("text" to "buy milk"))
        assertEquals(
            listOf(
                "- window \"Notes\"",
                "  - text-field \"New note\" tag=\"draft\"",
                "  - button \"Add\" tag=\"add\"",
                "  - tag=\"notes\"",
                "    - tag=\"note-0\"",
                "      - text \"buy milk\"",
                "      - button \"Delete\" tag=\"delete-0\"",
                "  - text \"1 note\" tag=\"count\"",
            ),
            // (the last line is the agent marker, with this client's name)
            text("ui.tree").lines().dropLast(1),
        )
        assertEquals("- tag=\"notes\"\n  - tag=\"note-0\"", text("ui.tree", args("under" to "tag=notes", "depth" to 1)))

        val typed = text("ui.type", args("selector" to "tag=draft", "text" to "call Oscar")).lines()
        assertTrue("  - text-field \"New note\" text=\"call Oscar\" [focused] tag=\"draft\"" in typed, typed.toString())
        val added = text("ui.key", args("key" to "Enter")).lines()
        assertTrue("      - text \"call Oscar\"" in added, added.toString())
        onNodeWithTag("count").assertTextEquals("2 notes")

        assertEquals("clicked role=button name=\"Delete\"", text("ui.click", args("selector" to "tag=delete-0", "snapshot" to false)))
        assertEquals("0: call Oscar", text("notes.list"))

        val found = text("ui.find", args("selector" to "role=button name=Add")).lines()
        assertEquals("role=button name=\"Add\"", found.first())
        assertEquals("tag: \"add\"", found.last())

        assertEquals("2 nodes with role=button, expected one", failure("ui.click", args("selector" to "role=button")))
        assertEquals("no node with text=Save", failure("ui.find", args("selector" to "text=Save")))
        assertTrue("use tag=" in failure("ui.find", args("selector" to "widget=add")))
        assertTrue("\"ctrl+banana\" is not a key" in failure("ui.key", args("key" to "ctrl+banana")))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun aScreenshotIsAPng() = runRavenTest {
        start(uiTools = true)
        val image = call("ui.screenshot")["content"]!!.jsonArray[0].jsonObject
        assertEquals("image/png", image["mimeType"]!!.jsonPrimitive.content)
        val png = Base64.decode(image["data"]!!.jsonPrimitive.content)
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), png.take(4).map { it.toInt() and 0xFF })
    }
}
