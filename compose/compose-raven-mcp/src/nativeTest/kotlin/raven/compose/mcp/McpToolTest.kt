package raven.compose.mcp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import raven.compose.foundation.Text
import raven.compose.ui.Window
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.mcp.RavenMcp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PORT = 18790
private const val TOKEN = "test-token"

@Serializable
enum class Colour { Red, Green }

@Serializable
@McpDescription("A note to add")
data class AddNote(
    @McpDescription("The note's text") val text: String,
    val pinned: Boolean = false,
    val colour: Colour? = null,
    val tags: List<String> = emptyList(),
)

/** McpTool, as an app uses it, called by an agent over HTTP. */
class McpToolTest {
    private val config = McpConfig(name = "mcp-test", port = PORT, token = TOKEN)

    private fun RavenTestScope.client(): McpTestClient = whileDrawing(::frame) { McpTestClient(PORT, TOKEN).connect() }

    private fun RavenTestScope.call(tool: String, arguments: JsonObject = JsonObject(emptyMap())): JsonObject =
        whileDrawing(::frame) { McpTestClient(PORT, TOKEN).connect().call(tool, arguments) }

    private fun RavenTestScope.tools(): Map<String, JsonObject> =
        whileDrawing(::frame) { McpTestClient(PORT, TOKEN).connect().tools() }
            .associate { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject }

    private fun JsonObject.text(): String = this["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

    private fun JsonObject.isError(): Boolean = this["isError"]?.jsonPrimitive?.boolean == true

    @Test
    fun aTypedToolDerivesItsSchemaAndRepliesWithItsResult() = runRavenTest {
        val added = mutableListOf<AddNote>()
        setApplicationContent {
            McpServer(config) {
                Window(onCloseRequest = {}, title = "Notes") {
                    McpTool<AddNote>("notes.add", "Add a note", hints = setOf(McpHint.NotDestructive, McpHint.ClosedWorld)) { args ->
                        added += args
                        "added ${args.text}"
                    }
                }
            }
        }
        val tool = tools()["notes.add"]!!
        val schema = tool["inputSchema"]!!.jsonObject
        val properties = schema["properties"]!!.jsonObject
        assertEquals("The note's text", properties["text"]!!.jsonObject["description"]!!.jsonPrimitive.content)
        assertEquals("string", properties["text"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("boolean", properties["pinned"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("Red", "Green"), properties["colour"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("array", properties["tags"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("text"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(false, tool["annotations"]!!.jsonObject["destructiveHint"]!!.jsonPrimitive.boolean)

        val reply = call("notes.add", buildJsonObject {
            put("text", "buy milk")
            put("colour", "Green")
            putJsonArray("tags") { add(JsonPrimitive("shop")) }
        })
        assertEquals("added buy milk", reply.text())
        assertEquals(listOf(AddNote("buy milk", colour = Colour.Green, tags = listOf("shop"))), added)

        val bad = call("notes.add", buildJsonObject { put("pinned", true) })
        assertTrue(bad.isError())
        assertTrue("text" in bad.text(), bad.text())
    }

    @Test
    fun failuresRepliesAndTheUntypedTool() = runRavenTest {
        setApplicationContent {
            McpServer(config) {
                Window(onCloseRequest = {}) {
                    McpTool("fails", "Fails") { require(false) { "there is no note 3" } }
                    McpTool("json", "Replies JSON") { McpReply.Json(buildJsonObject { put("count", 2) }) }
                    McpTool("echo", "Echoes", inputSchema = """{"type":"object","properties":{"word":{"type":"string"}}}""") { args ->
                        args["word"]!!.jsonPrimitive.content
                    }
                }
            }
        }
        val failed = call("fails")
        assertTrue(failed.isError())
        assertEquals("there is no note 3", failed.text())
        assertEquals("""{"count":2}""", call("json").text())
        assertEquals("hi", call("echo", buildJsonObject { put("word", "hi") }).text())
        assertEquals("string", tools()["echo"]!!["inputSchema"]!!.jsonObject["properties"]!!.jsonObject["word"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolsFollowTheCompositionAndTheLastComposedWins() = runRavenTest {
        var screen by mutableStateOf(true)
        var second by mutableStateOf(false)
        var greeting by mutableStateOf("hello")
        setApplicationContent {
            McpServer(config) {
                Window(onCloseRequest = {}) {
                    McpTool("greet", "Greets") { "first: $greeting" }
                    if (second) McpTool("greet", "Greets too") { "second" }
                    if (screen) McpTool("screen.only", "Only on this screen") { "here" }
                }
            }
        }
        assertEquals(setOf("greet", "screen.only"), tools().keys)
        assertEquals("first: hello", call("greet").text())
        greeting = "hi"   // the latest handler answers, with today's state
        waitForIdle()
        assertEquals("first: hi", call("greet").text())

        second = true
        waitForIdle()
        assertEquals("second", call("greet").text())
        second = false   // the one before comes back
        waitForIdle()
        assertEquals("first: hi", call("greet").text())

        screen = false
        waitForIdle()
        assertEquals(setOf("greet"), tools().keys)
    }

    @Test
    fun stoppingTheServerCancelsACallInProgress() = runRavenTest {
        var started = false
        var cancelled = false
        setApplicationContent {
            McpServer(config) {
                Window(onCloseRequest = {}) {
                    McpTool("slow", "Never finishes") {
                        started = true
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    }
                }
            }
        }
        val client = client()
        val reply = whileDrawing({
            frame()
            if (started && !cancelled) RavenMcp.stop()
        }) { client.call("slow") }
        assertTrue(reply.isError())
        assertEquals("the server stopped", reply.text())
        waitForIdle()
        assertTrue(cancelled)
    }

    @Test
    fun theStatusShowsAConnectedAgent() = runRavenTest {
        val connected = mutableListOf<String>()
        setApplicationContent {
            McpServer(McpConfig(name = "mcp-test", port = PORT, token = TOKEN, onAgentConnected = { connected += it })) {
                Window(onCloseRequest = {}) { McpStatus() }
            }
        }
        onAllNodesWithText("connected", substring = true).assertCountEquals(0)
        client()
        // (the server tells the UI thread through a Raven task, which only a frame runs)
        repeat(100) { if (connected.isEmpty()) frame() }
        assertEquals(listOf("test-agent"), connected)
        onNodeWithText("● test-agent connected").assertExists()
    }

    @Test
    fun recomposingTheServerKeepsItsTools() = runRavenTest {
        var generation by mutableStateOf(0)
        setApplicationContent {
            // A new, equal config at each recomposition: the server started once goes on.
            McpServer(McpConfig(name = "mcp-test-$generation".take(8), port = PORT, token = TOKEN)) {
                Window(onCloseRequest = {}) { McpTool("still", "Still here") { "yes" } }
            }
        }
        generation++
        waitForIdle()
        assertEquals("yes", call("still").text())
    }

    @Test
    fun withoutAServerAToolDoesNothing() = runRavenTest {
        var called = false
        setApplicationContent {
            McpServer(null) {
                Window(onCloseRequest = {}) {
                    McpTool("nothing", "Not published") { called = true; "" }
                    Text("still here")
                }
            }
        }
        onNodeWithText("still here").assertExists()
        assertFalse(called)
    }
}
