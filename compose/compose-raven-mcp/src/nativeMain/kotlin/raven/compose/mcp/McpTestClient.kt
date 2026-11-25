@file:OptIn(ExperimentalForeignApi::class, ObsoleteWorkersApi::class)

package raven.compose.mcp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.close
import platform.posix.connect
import platform.posix.recv
import platform.posix.send
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.usleep
import raven.compose.ui.InternalComposeRavenApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

/**
 * An MCP client over Streamable HTTP, as an agent is, for compose-raven's own tests and the
 * samples': it initializes a session, lists tools and calls them, blocking. Never on the UI thread,
 * which must go on drawing frames for the server to answer: see [whileDrawing].
 */
@InternalComposeRavenApi
public class McpTestClient(private val port: Int, private val token: String? = null) {
    private var session: String? = null
    private var nextId = 0

    /** Opens a session, as an agent does first. */
    public fun connect(): McpTestClient = apply {
        request("initialize", buildJsonObject {
            put("protocolVersion", "2025-06-18")
            put("capabilities", JsonObject(emptyMap()))
            put("clientInfo", buildJsonObject { put("name", "test-agent"); put("version", "1") })
        })
        post(buildJsonObject { put("jsonrpc", "2.0"); put("method", "notifications/initialized") }.toString())
    }

    /** The tools the server lists now. */
    public fun tools(): JsonArray = request("tools/list", JsonObject(emptyMap()))["tools"]!!.jsonArray

    /** Calls [tool] with [arguments] and returns the CallToolResult. */
    public fun call(tool: String, arguments: JsonObject = JsonObject(emptyMap())): JsonObject =
        request("tools/call", buildJsonObject { put("name", tool); put("arguments", arguments) })

    private fun request(method: String, params: JsonObject): JsonObject {
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", ++nextId)
            put("method", method)
            put("params", params)
        }
        val reply = post(message.toString()) ?: error("no reply to $method")
        reply.jsonObject["error"]?.let { error("$method failed: $it") }
        return reply.jsonObject["result"]!!.jsonObject
    }

    /** POSTs [body] and returns the JSON the server answered, if any. */
    private fun post(body: String): JsonElement? {
        val bytes = body.encodeToByteArray()
        val head = buildString {
            append("POST /mcp HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n")
            append("Content-Type: application/json\r\nAccept: application/json, text/event-stream\r\n")
            token?.let { append("Authorization: Bearer $it\r\n") }
            session?.let { append("Mcp-Session-Id: $it\r\n") }
            append("Content-Length: ${bytes.size}\r\n\r\n")
        }
        val response = exchange(head.encodeToByteArray() + bytes).decodeToString()
        val split = response.indexOf("\r\n\r\n")
        check(split > 0) { "not an HTTP response: $response" }
        val headers = response.substring(0, split).split("\r\n")
        val status = headers.first().split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        fun header(name: String) = headers.drop(1).firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()
        header("Mcp-Session-Id")?.let { session = it }
        var content = response.substring(split + 4)
        if (header("Transfer-Encoding")?.equals("chunked", ignoreCase = true) == true) content = unchunk(content)
        check(status in 200..299) { "HTTP $status: $content" }
        // A stream gives its JSON in data: lines.
        if (header("Content-Type")?.startsWith("text/event-stream") == true) {
            content = content.lines().filter { it.startsWith("data:") }.joinToString("") { it.removePrefix("data:").trim() }
        }
        return if (content.isBlank()) null else ArgumentsJson.parseToJsonElement(content)
    }

    private fun unchunk(chunked: String): String = buildString {
        var rest = chunked
        while (true) {
            val line = rest.substringBefore("\r\n")
            val size = line.trim().toIntOrNull(16) ?: break
            if (size == 0) break
            rest = rest.substringAfter("\r\n")
            append(rest.substring(0, size))
            rest = rest.substring(size).removePrefix("\r\n")
        }
    }

    /** Sends [request] to the server and reads everything until it closes the connection. */
    private fun exchange(request: ByteArray): ByteArray = memScoped {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        check(fd >= 0) { "no socket" }
        try {
            val address = alloc<sockaddr_in>()
            address.sin_family = AF_INET.convert()
            // (network byte order, on the little-endian hosts compose-raven builds for)
            address.sin_port = ((port shr 8) or ((port and 0xFF) shl 8)).toUShort()
            address.sin_addr.s_addr = 0x0100007Fu
            check(connect(fd, address.ptr.reinterpret(), sockaddr_in.size.convert()) == 0) { "can't connect to port $port" }
            request.usePinned { send(fd, it.addressOf(0), request.size.convert(), 0) }
            val received = ArrayList<Byte>()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = buffer.usePinned { recv(fd, it.addressOf(0), buffer.size.convert(), 0) }.toInt()
                if (read <= 0) break
                for (i in 0 until read) received += buffer[i]
            }
            received.toByteArray()
        } finally {
            close(fd)
        }
    }
}

/**
 * Runs [work] on another thread while this one calls [drawFrame] (every millisecond or so), and
 * returns what it returned, or throws what it threw: how a test calls the server of an app whose
 * frames it draws itself.
 */
@InternalComposeRavenApi
public fun <T> whileDrawing(drawFrame: () -> Unit, work: () -> T): T {
    val worker = Worker.start()
    try {
        val future = worker.execute(TransferMode.SAFE, { work }) { job -> runCatching(job) }
        while (future.state == kotlin.native.concurrent.FutureState.SCHEDULED) {
            drawFrame()
            usleep(1000u)
        }
        return future.result.getOrThrow()
    } finally {
        worker.requestTermination().result
    }
}
