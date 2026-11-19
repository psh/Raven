package raven.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference

/**
 * One use of a tool by an agent. It gets exactly one reply, from any thread, cancelled or not: the
 * reply ends it, and a second one is logged and ignored. Its [id] is the C API's `RMcpCall*`, so a
 * reply through a handle already replied to finds nothing, rather than freed memory.
 */
public class McpCall internal constructor(
    internal val id: Long,
    /** The tool's name. */
    public val tool: String,
    /** The arguments, a JSON object ("{}" when none). */
    public val argumentsJson: String,
) {
    internal val result = CompletableDeferred<CallToolResult>()
    private val cancelled = AtomicBoolean(false)

    /** True once the agent cancelled the call or the server stopped. The call still needs its reply. */
    public val isCancelled: Boolean get() = cancelled.load()

    internal fun cancel() {
        cancelled.store(true)
    }

    /** Replies with a text. */
    public fun reply(text: String): Unit = McpCalls.finish(id, CallToolResult(content = listOf(TextContent(text))))

    /** Replies with a whole MCP CallToolResult, as JSON. A result that doesn't parse fails the call instead. */
    public fun replyJson(callToolResultJson: String) {
        val result = try {
            McpJson.decodeFromString(CallToolResult.serializer(), callToolResultJson)
        } catch (e: Exception) {
            McpLog.error("the reply to $tool is not a CallToolResult: ${e.message}")
            return fail("the tool replied with a result that isn't valid")
        }
        McpCalls.finish(id, result)
    }

    /** Replies that the tool failed, with a message the agent reads (an MCP tool error). */
    public fun fail(message: String): Unit =
        McpCalls.finish(id, CallToolResult(content = listOf(TextContent(message)), isError = true))
}

/** Every call not yet replied to, by id. Thread-safe. */
internal object McpCalls {
    private val nextId = AtomicLong(1)
    private val open = AtomicReference(emptyMap<Long, McpCall>())

    fun open(tool: String, argumentsJson: String): McpCall {
        val call = McpCall(nextId.fetchAndAdd(1), tool, argumentsJson)
        update { it + (call.id to call) }
        return call
    }

    fun find(id: Long): McpCall? = open.load()[id]

    /** Ends the call with [result]. Called twice, or after it ended, it logs and does nothing. */
    fun finish(id: Long, result: CallToolResult) {
        var ended: McpCall? = null
        update { calls ->
            ended = calls[id]
            if (ended == null) calls else calls - id
        }
        val call = ended
        if (call == null) {
            McpLog.warn("a call was replied to twice, or after it ended; the reply is ignored")
            return
        }
        call.result.complete(result)
    }

    /** Marks every open call cancelled and answers its agent; each still needs its reply. */
    fun cancelAll(reason: String) {
        for (call in open.load().values) {
            call.cancel()
            call.result.complete(CallToolResult(content = listOf(TextContent(reason)), isError = true))
        }
    }

    private inline fun update(change: (Map<Long, McpCall>) -> Map<Long, McpCall>) {
        while (true) {
            val before = open.load()
            if (open.compareAndSet(before, change(before))) return
        }
    }
}
