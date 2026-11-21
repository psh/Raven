package raven.mcp

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.application.serverConfig
import io.ktor.server.response.respondText
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.posix.getenv
import raven.mcp.cinterop.RAVEN_VERSION_MAJOR
import raven.mcp.cinterop.RAVEN_VERSION_MINOR
import raven.mcp.cinterop.R_TRUE
import raven.mcp.cinterop.Rget_time
import raven.mcp.cinterop.Rget_version
import raven.mcp.cinterop.Ris_initialized
import raven.mcp.cinterop.Ris_ui_thread
import raven.mcp.cinterop.Rtask_post
import raven.mcp.cinterop.rmcp_monotonic_seconds
import raven.mcp.cinterop.rmcp_port_is_free
import raven.mcp.cinterop.rmcp_program_name

/** The server's settings, as `RMcpConfig`. */
public class McpServerConfig(
    public val name: String? = null,
    public val version: String? = null,
    public val instructions: String? = null,
    /** 0: [McpServerConfig.DEFAULT_PORT]. Loopback only, with no fallback. */
    public val port: Int = 0,
    /** null: the app's stored token. "": none, logged as a warning. */
    public val token: String? = null,
    public val uiTools: Boolean = false,
    public val log: ((McpLogLevel, String) -> Unit)? = null,
    /** On the UI thread, as agents connect (true) and disconnect, with the client's name. */
    public val onSession: ((connected: Boolean, client: String) -> Unit)? = null,
) {
    public companion object {
        public const val DEFAULT_PORT: Int = 8765

        /**
         * The settings the environment asks for, for an app that offers Raven MCP to developers
         * only: RAVEN_MCP=1 turns it on, RAVEN_MCP_PORT sets the port, RAVEN_MCP_UI=1 the UI tools.
         * Null unless RAVEN_MCP=1.
         */
        @OptIn(ExperimentalForeignApi::class)
        public fun fromEnvironment(base: McpServerConfig = McpServerConfig()): McpServerConfig? {
            if (getenv("RAVEN_MCP")?.toKString() != "1") return null
            return McpServerConfig(
                name = base.name, version = base.version, instructions = base.instructions,
                port = getenv("RAVEN_MCP_PORT")?.toKString()?.toIntOrNull() ?: base.port,
                token = base.token,
                uiTools = base.uiTools || getenv("RAVEN_MCP_UI")?.toKString() == "1",
                log = base.log, onSession = base.onSession,
            )
        }
    }
}

/** What a tool says about itself, as `RMcpToolHint`: each one a claim the app makes on purpose. */
public object McpHints {
    public const val READ_ONLY: Int = 1 shl 0
    public const val NOT_DESTRUCTIVE: Int = 1 shl 1
    public const val IDEMPOTENT: Int = 1 shl 2
    public const val CLOSED_WORLD: Int = 1 shl 3
}

/** A tool, as `RMcpToolDesc`. */
public class McpToolSpec(
    public val name: String,
    public val description: String? = null,
    public val title: String? = null,
    /** A JSON Schema object; null: no arguments. */
    public val inputSchemaJson: String? = null,
    public val hints: Int = 0,
)

/**
 * Raven MCP: the server, the registry of tools and the calls (compose/docs/mcp.md). The C API in
 * Exports.kt and compose-raven both go through it. Everything but [McpCall]'s replies is UI-thread
 * only, as Raven is.
 */
public object RavenMcp {
    /** Who published a tool. The app's own tools win over the other two. */
    internal enum class Origin {
        App,
        /** Rmcp_expose_action(s). */
        Action,
        /** The UI tools, while a server started with uiTools runs. */
        Ui,
    }

    private class Registration(
        val spec: McpToolSpec, val tool: Tool, val handler: (McpCall) -> Unit, val run: RavenRun, val origin: Origin,
    )

    /** When a run of Raven started, on Raven's own clock: between [earliest] and [latest]. */
    private class RavenRun(val earliest: Double, val latest: Double) {
        fun isSameAs(other: RavenRun): Boolean = earliest <= other.latest && other.earliest <= latest
    }

    private val tools = LinkedHashMap<String, Registration>()
    private var server: Server? = null
    private var http: EmbeddedServer<*, *>? = null
    private var port = 0
    private var onSession: ((Boolean, String) -> Unit)? = null
    private val sessionsSeen = HashSet<ServerSession>()

    /** The address being served, or null when stopped. */
    public var url: String? = null
        private set

    init {
        McpLog.routeKotlinLogging()
    }

    /** Starts serving the tools; false, with the reason logged, when it can't. */
    @OptIn(ExperimentalForeignApi::class)
    public fun start(config: McpServerConfig = McpServerConfig()): Boolean {
        McpLog.setSink(config.log)
        if (getenv("RAVEN_MCP")?.toKString() == "0") return refuse("RAVEN_MCP=0 in the environment: Raven MCP stays off")
        if (Ris_initialized() != R_TRUE) return refuse("Rmcp_start before Rinit")
        if (!onUiThread("Rmcp_start")) return false
        if (http != null) return refuse("the server is already running")
        if (!ravenIsRecentEnough()) return false
        if (staleTools()) return refuse("tools are left from an earlier run of Raven: remove them before Rshutdown")
        val name = config.name ?: rmcp_program_name()?.toKString() ?: "raven-app"
        val port = if (config.port == 0) McpServerConfig.DEFAULT_PORT else config.port
        val address = "http://127.0.0.1:$port/mcp"
        val token = when (config.token) {
            null -> McpTokens.storedToken(name, address) ?: return refuse("the token file can't be written")
            "" -> null.also { McpLog.warn("Raven MCP runs with no token: any local process can connect") }
            else -> config.token
        }
        val mcp = Server(
            Implementation(name = name, version = config.version ?: "0"),
            ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))),
            config.instructions ?: "",
        )
        onSession = config.onSession
        sessionsSeen.clear()
        mcp.onConnect { watchNewSessions(mcp) }
        if (rmcp_port_is_free(port) != 1) return refuse("port $port is taken: set another in the config")
        if (config.uiTools) UiTools.addAll()
        for (registration in tools.values) mcp.addTool(registration.tool, handlerOf(registration))
        val settings = serverConfig(applicationEnvironment { log = McpLog.ktor }) {
            // A failure in Ktor's coroutines is logged; uncaught, it would end the app.
            parentCoroutineContext = CoroutineExceptionHandler { _, e -> McpLog.error("the server failed: $e") }
            module {
                if (token != null) {
                    intercept(ApplicationCallPipeline.Plugins) {
                        if (call.request.headers["Authorization"] != "Bearer $token") {
                            call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                            finish()
                        }
                    }
                }
                mcpStreamableHttp { mcp }
            }
        }
        val engine = try {
            embeddedServer(CIO, settings) {
                connector { host = "127.0.0.1"; this.port = port }
                // As the probe binds: a port whose old connections are in TIME_WAIT can be served again.
                reuseAddress = true
            }.start(wait = false)
        } catch (e: Exception) {
            removeUiTools()
            return refuse("can't serve on port $port: ${e.message}")
        }
        server = mcp
        http = engine
        this.port = port
        url = address
        McpLog.info("serving $address")
        return true
    }

    /** Closes the port and every session, cancels the calls not yet replied to, and withdraws the UI tools. */
    public fun stop() {
        if (!onUiThread("Rmcp_stop")) return
        val engine = http ?: return
        McpCalls.cancelAll("the server stopped")
        removeUiTools()
        engine.stop(gracePeriodMillis = 100, timeoutMillis = 1000)
        // The port is free when Rmcp_stop returns, so the server can start again straight away.
        repeat(100) { if (rmcp_port_is_free(port) == 1) return@repeat; platform.posix.usleep(10_000u) }
        http = null
        server = null
        url = null
        McpLog.info("stopped")
    }

    /** Publishes [spec], replacing a tool of the same name. False, with the reason logged, when it can't. */
    public fun addTool(spec: McpToolSpec, handler: (McpCall) -> Unit): Boolean = register(spec, handler, Origin.App)

    /**
     * Publishes an exposed action's tool or a UI tool, unless another kind of tool has its name:
     * the app's own tools win, and an action named ui.* never replaces a UI tool.
     */
    internal fun addOwnTool(spec: McpToolSpec, origin: Origin, handler: (McpCall) -> Unit): Boolean {
        val taken = tools[spec.name]?.origin
        if (taken != null && taken != origin) return false
        return register(spec, handler, origin)
    }

    /** Withdraws a tool [origin] published; another kind of tool of that name stays. */
    internal fun removeOwnTool(name: String, origin: Origin) {
        if (tools[name]?.origin != origin) return
        tools.remove(name)
        server?.removeTool(name)
    }

    private fun removeUiTools() {
        UiTools.on = false
        for (name in tools.filterValues { it.origin == Origin.Ui }.keys.toList()) removeOwnTool(name, Origin.Ui)
    }

    private fun register(spec: McpToolSpec, handler: (McpCall) -> Unit, origin: Origin): Boolean {
        if (Ris_initialized() != R_TRUE) return refuse("Rmcp_tool_add before Rinit")
        if (!onUiThread("Rmcp_tool_add")) return false
        if (staleTools()) return refuse("tools are left from an earlier run of Raven: remove them before Rshutdown")
        if (!TOOL_NAME.matches(spec.name)) return refuse("\"${spec.name}\" is not a tool name: 1 to 128 of A-Z, a-z, 0-9, '_', '.', '-'")
        val schema = schemaOf(spec) ?: return false
        val tool = Tool(
            name = spec.name, inputSchema = schema, description = spec.description, title = spec.title,
            annotations = annotationsOf(spec.hints),
        )
        val registration = Registration(spec, tool, handler, currentRun(), origin)
        server?.let { if (tools.containsKey(spec.name)) it.removeTool(spec.name) }
        tools[spec.name] = registration
        server?.addTool(tool, handlerOf(registration))
        return true
    }

    /** Withdraws a tool. Its calls not yet given to its function fail. A UI tool or an action it hid comes back. */
    public fun removeTool(name: String) {
        if (!onUiThread("Rmcp_tool_remove")) return
        val removed = tools.remove(name) ?: return
        server?.removeTool(name)
        if (removed.origin != Origin.App) return
        if (UiTools.on) UiTools.add(name)
        ExposedActions.sync(name)
    }

    /** Withdraws every tool, the UI tools and the exposed actions included, which are forgotten. */
    public fun removeAllTools() {
        if (!onUiThread("Rmcp_tool_remove_all")) return
        server?.removeTools(tools.keys.toList())
        tools.clear()
        UiTools.on = false
        ExposedActions.reset()
    }

    private fun handlerOf(registration: Registration): suspend io.modelcontextprotocol.kotlin.sdk.server.ClientConnection.(
        io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest,
    ) -> CallToolResult = { request ->
        val call = McpCalls.open(registration.spec.name, (request.arguments ?: JsonObject(emptyMap())).toString())
        postToUiThread { dispatch(registration, call) }
        try {
            call.result.await()
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        }
    }

    /** On the UI thread: the call reaches its tool's function, unless the tool went in the meantime. */
    private fun dispatch(registration: Registration, call: McpCall) {
        if (tools[registration.spec.name] !== registration) return call.fail("the tool was removed")
        try {
            registration.handler(call)
        } catch (e: Exception) {
            McpLog.error("the tool ${call.tool} threw: $e")
            if (McpCalls.find(call.id) != null) call.fail(e.message ?: e.toString())
        }
    }

    private fun watchNewSessions(mcp: Server) {
        for (session in mcp.sessions.values) {
            if (!sessionsSeen.add(session)) continue
            session.onInitialized {
                val client = session.clientVersion?.name ?: "an agent"
                McpLog.info("$client connected")
                onSession?.let { notify -> postToUiThread { notify(true, client) } }
            }
            session.onClose {
                val client = session.clientVersion?.name ?: "an agent"
                McpLog.info("$client disconnected")
                onSession?.let { notify -> postToUiThread { notify(false, client) } }
            }
        }
    }

    private fun schemaOf(spec: McpToolSpec): ToolSchema? {
        val json = spec.inputSchemaJson ?: return ToolSchema()
        val parsed = try {
            McpJson.parseToJsonElement(json)
        } catch (e: Exception) {
            refuse("the input schema of ${spec.name} is not JSON: ${e.message}")
            return null
        }
        if (parsed !is JsonObject) {
            refuse("the input schema of ${spec.name} is not a JSON object")
            return null
        }
        return ToolSchema(
            properties = parsed["properties"] as? JsonObject,
            required = (parsed["required"] as? JsonArray)?.map { it.jsonPrimitive.content },
            defs = parsed["\$defs"] as? JsonObject,
        )
    }

    private fun annotationsOf(hints: Int): ToolAnnotations? {
        if (hints == 0) return null
        return ToolAnnotations(
            readOnlyHint = if (hints and McpHints.READ_ONLY != 0) true else null,
            destructiveHint = if (hints and McpHints.NOT_DESTRUCTIVE != 0) false else null,
            idempotentHint = if (hints and McpHints.IDEMPOTENT != 0) true else null,
            openWorldHint = if (hints and McpHints.CLOSED_WORLD != 0) false else null,
        )
    }

    /**
     * Which run of Raven this is: when Rinit started it, as "now - Rget_time()". Raven's clock is read
     * between two of ours, so the start is known to within that gap, and two runs are the same only
     * when the gaps overlap. A run that follows Rshutdown starts later than that whole gap.
     */
    private fun currentRun(): RavenRun {
        val before = rmcp_monotonic_seconds()
        val sinceInit = Rget_time()
        val after = rmcp_monotonic_seconds()
        return RavenRun(before - sinceInit, after - sinceInit)
    }

    private fun staleTools(): Boolean {
        if (tools.isEmpty()) return false
        val now = currentRun()
        return tools.values.any { !it.run.isSameAs(now) }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ravenIsRecentEnough(): Boolean {
        val running = Rget_version()?.toKString().orEmpty().split('.').mapNotNull { it.toIntOrNull() }
        val major = running.getOrElse(0) { 0 }
        val minor = running.getOrElse(1) { 0 }
        if (major < RAVEN_VERSION_MAJOR || (major == RAVEN_VERSION_MAJOR && minor < RAVEN_VERSION_MINOR)) {
            return refuse("Raven $major.$minor is older than the $RAVEN_VERSION_MAJOR.$RAVEN_VERSION_MINOR Raven MCP was built against")
        }
        return true
    }

    private fun onUiThread(function: String): Boolean {
        if (Ris_ui_thread() == R_TRUE) return true
        return refuse("$function called away from Raven's UI thread")
    }

    private fun refuse(reason: String): Boolean {
        McpLog.error(reason)
        return false
    }

    private val TOOL_NAME = Regex("[A-Za-z0-9_.-]{1,128}")
}

/** Runs [block] on Raven's UI thread, through Rtask_post. Thread-safe. */
@OptIn(ExperimentalForeignApi::class)
internal fun postToUiThread(block: () -> Unit) {
    Rtask_post(runPosted, StableRef.create(block).asCPointer())
}

@OptIn(ExperimentalForeignApi::class)
private val runPosted = staticCFunction { userData: COpaquePointer? ->
    val ref = userData!!.asStableRef<() -> Unit>()
    val block = ref.get()
    ref.dispose()
    block()
}
