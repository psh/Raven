package raven.compose.mcp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import raven.mcp.McpCall
import raven.mcp.McpHints
import raven.mcp.McpLogLevel
import raven.mcp.McpScreenshots
import raven.mcp.McpToolSpec
import raven.mcp.RavenMcp

/** What a tool says about itself, as `RMcpToolHint`: each one a claim the app makes on purpose. */
public enum class McpHint(internal val bit: Int) {
    /** Changes nothing. */
    ReadOnly(McpHints.READ_ONLY),

    /** Changes only by adding: never deletes or overwrites. */
    NotDestructive(McpHints.NOT_DESTRUCTIVE),

    /** Calling it again with the same arguments does nothing more. */
    Idempotent(McpHints.IDEMPOTENT),

    /** Touches nothing outside the app: no network, no other programs. */
    ClosedWorld(McpHints.CLOSED_WORLD),
}

/** A reply richer than a text: what an `McpTool`'s handler may return instead of a `String`. */
public sealed class McpReply {
    /** A text, as returning a `String` gives. */
    public class Text(public val text: String) : McpReply()

    /** JSON, given to the agent as its text. */
    public class Json(public val json: JsonElement) : McpReply()

    /** An image, as PNG bytes. */
    public class Image(public val png: ByteArray) : McpReply()
}

/**
 * Publishes a tool while it is composed, with arguments of the `@Serializable` type [A]: its input
 * schema is derived from [A]'s descriptor, with descriptions from `@McpDescription`, and a call's
 * arguments arrive deserialized. [handler] runs on the UI thread, as a coroutine the agent can
 * cancel, and returns the reply: a `String`, or an [McpReply]. What it throws is the tool's
 * failure, with its message. A call always runs the latest [handler] composed.
 *
 * Two `McpTool`s of one name: the one composed last is published, and the one before comes back
 * when it leaves. In an app started without a server, it does nothing.
 */
@Composable
public inline fun <reified A> McpTool(
    name: String,
    description: String,
    title: String? = null,
    hints: Set<McpHint> = emptySet(),
    noinline handler: suspend (A) -> Any,
) {
    McpTool(name, description, serializer<A>(), title, hints, handler)
}

/** An `McpTool` whose arguments [serializer] reads, for a type `serializer<A>()` can't name. */
@Composable
public fun <A> McpTool(
    name: String,
    description: String,
    serializer: KSerializer<A>,
    title: String? = null,
    hints: Set<McpHint> = emptySet(),
    handler: suspend (A) -> Any,
) {
    val schema = remember(serializer) { McpSchema.of(serializer.descriptor) }
    val latest = rememberUpdatedState(handler)
    PublishedTool(McpToolSpec(name, description, title, schema, hintBits(hints)), latest) { json, run ->
        val arguments = try {
            ArgumentsJson.decodeFromString(serializer, json)
        } catch (e: SerializationException) {
            throw McpToolFailure(e.message ?: "the arguments don't match the tool's schema")
        } catch (e: IllegalArgumentException) {
            throw McpToolFailure(e.message ?: "the arguments don't match the tool's schema")
        }
        run(arguments)
    }
}

/**
 * An `McpTool` that takes its arguments as they came, a JSON object, with [inputSchema] (a JSON
 * Schema object, or null for none) saying what they are: for what a type can't describe.
 */
@Composable
public fun McpTool(
    name: String,
    description: String,
    title: String? = null,
    hints: Set<McpHint> = emptySet(),
    inputSchema: String? = null,
    handler: suspend (JsonObject) -> Any,
) {
    val latest = rememberUpdatedState(handler)
    PublishedTool(McpToolSpec(name, description, title, inputSchema, hintBits(hints)), latest) { json, run ->
        val arguments = try {
            ArgumentsJson.parseToJsonElement(json) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: throw McpToolFailure("the arguments are not a JSON object")
        run(arguments)
    }
}

/** Registers [spec] while composed; a call decodes its arguments with [decode], then runs the latest [handler]. */
@Composable
private fun <A> PublishedTool(
    spec: McpToolSpec,
    handler: State<suspend (A) -> Any>,
    decode: suspend (json: String, run: suspend (A) -> Any) -> Any,
) {
    val registry = LocalMcpRegistry.current ?: return
    DisposableEffect(registry, spec.name, spec.description, spec.title, spec.inputSchemaJson, spec.hints) {
        val entry = registry.add(spec) { json -> decode(json) { handler.value(it) } }
        onDispose { registry.remove(entry) }
    }
}

private fun hintBits(hints: Set<McpHint>): Int = hints.fold(0) { bits, hint -> bits or hint.bit }

/** How a call's arguments are read: as the schema says, and nothing else. */
internal val ArgumentsJson = Json { explicitNulls = false }

/** A failure the tool expects (arguments that don't fit, a node not found): the call fails with the message, unlogged. */
internal class McpToolFailure(message: String) : Exception(message)

/**
 * The tools the composition publishes, a stack per name: the one composed last is the one
 * published, and when it leaves, the one under it is published again. Calls run as coroutines of
 * [scope], on the UI thread. UI thread only.
 */
internal class McpRegistry(private val scope: CoroutineScope) {
    class Entry(val spec: McpToolSpec, val run: suspend (argumentsJson: String) -> Any)

    private val stacks = HashMap<String, ArrayList<Entry>>()

    fun add(spec: McpToolSpec, run: suspend (argumentsJson: String) -> Any): Entry {
        val entry = Entry(spec, run)
        val stack = stacks.getOrPut(spec.name) { ArrayList() }
        stack += entry
        if (stack.size > 1) {
            RavenMcp.log(McpLogLevel.Warning, "${spec.name} is composed ${stack.size} times: the last one composed answers")
        }
        publish(entry)
        return entry
    }

    fun remove(entry: Entry) {
        val stack = stacks[entry.spec.name] ?: return
        val wasPublished = stack.lastOrNull() === entry
        stack.remove(entry)
        when {
            stack.isEmpty() -> {
                stacks.remove(entry.spec.name)
                RavenMcp.removeTool(entry.spec.name)
            }
            wasPublished -> publish(stack.last())
        }
    }

    /** Forgets every tool: the server has stopped and withdrawn them. */
    fun clear() {
        stacks.clear()
    }

    private fun publish(entry: Entry) {
        RavenMcp.addTool(entry.spec) { call -> run(call, entry) }
    }

    /**
     * Runs a call of [entry]'s tool as a coroutine, and replies with what it returns, or fails with
     * what it throws. The agent cancelling the call cancels the coroutine; every call is replied to.
     */
    fun run(call: McpCall, entry: Entry) {
        val job = scope.launch {
            try {
                reply(call, entry.run(call.argumentsJson))
            } catch (e: CancellationException) {
                call.fail(if (call.isCancelled) "the call was cancelled" else "the tool went away")
                throw e
            } catch (e: Throwable) {
                if (e !is McpToolFailure) RavenMcp.log(McpLogLevel.Error, "the tool ${call.tool} threw: $e")
                call.fail(e.message ?: e.toString())
            }
        }
        call.invokeOnCancel { job.cancel() }
    }
}

/** Replies to [call] with what a handler returned. */
internal fun reply(call: McpCall, result: Any) {
    when (result) {
        is String -> call.reply(result)
        is McpReply.Text -> call.reply(result.text)
        is McpReply.Json -> call.reply(result.json.toString())
        is McpReply.Image -> call.replyJson(McpScreenshots.replyJson(result.png))
        else -> call.reply(result.toString())
    }
}
