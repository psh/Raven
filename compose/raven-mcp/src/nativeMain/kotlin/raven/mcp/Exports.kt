@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package raven.mcp

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import platform.posix.getenv
import cnames.structs.RMcpCall
import raven.mcp.cinterop.RMcpConfig
import raven.mcp.cinterop.RMcpToolCallback
import raven.mcp.cinterop.RMcpToolDesc
import raven.mcp.cinterop.R_FALSE
import raven.mcp.cinterop.R_SEVERITY_ERROR
import raven.mcp.cinterop.R_SEVERITY_WARNING
import raven.mcp.cinterop.R_SEVERITY_WEAK_WARNING
import raven.mcp.cinterop.R_TRUE
import kotlin.experimental.ExperimentalNativeApi

// The C API of include/raven/raven_mcp.h, over RavenMcp. Only conversion lives here.

private val urlBytes = HashMap<String, CPointer<ByteVar>>()

@CName("Rmcp_start")
public fun rmcpStart(config: CPointer<RMcpConfig>?): Int {
    val c = config?.pointed
    val log = c?.log
    val logUser = c?.log_user_data
    val onSession = c?.on_session
    val sessionUser = c?.session_user_data
    val settings = McpServerConfig(
        name = c?.name?.toKString(),
        version = c?.version?.toKString(),
        instructions = c?.instructions?.toKString(),
        port = c?.port ?: 0,
        token = c?.token?.toKString(),
        uiTools = (c?.ui_tools ?: R_FALSE) == R_TRUE,
        log = log?.let { callback ->
            { level: McpLogLevel, message: String ->
                val severity = when (level) {
                    McpLogLevel.Info -> R_SEVERITY_WEAK_WARNING
                    McpLogLevel.Warning -> R_SEVERITY_WARNING
                    McpLogLevel.Error -> R_SEVERITY_ERROR
                }
                memScoped { callback(severity, message.cstr.ptr, logUser) }
            }
        },
        onSession = onSession?.let { callback ->
            { connected: Boolean, client: String ->
                memScoped { callback(if (connected) R_TRUE else R_FALSE, client.cstr.ptr, sessionUser) }
            }
        },
    )
    return if (RavenMcp.start(settings)) R_TRUE else R_FALSE
}

@CName("Rmcp_stop")
public fun rmcpStop(): Unit = RavenMcp.stop()

@CName("Rmcp_get_url")
public fun rmcpGetUrl(): CPointer<ByteVar>? {
    val url = RavenMcp.url ?: return null
    // Kept for the life of the process, one copy per address, so the pointer stays valid.
    return urlBytes.getOrPut(url) { strdup(url) }
}

@CName("Rmcp_config_from_environment")
public fun rmcpConfigFromEnvironment(config: CPointer<RMcpConfig>?): Int {
    if (getenv("RAVEN_MCP")?.toKString() != "1") return R_FALSE
    val c = config?.pointed ?: return R_TRUE
    getenv("RAVEN_MCP_PORT")?.toKString()?.toIntOrNull()?.let { c.port = it }
    if (getenv("RAVEN_MCP_UI")?.toKString() == "1") c.ui_tools = R_TRUE
    return R_TRUE
}

@CName("Rmcp_tool_add")
public fun rmcpToolAdd(desc: CPointer<RMcpToolDesc>?, callback: RMcpToolCallback?, userData: COpaquePointer?): Int {
    val d = desc?.pointed ?: return refused("Rmcp_tool_add with no description")
    val name = d.name?.toKString() ?: return refused("Rmcp_tool_add with no name")
    if (callback == null) return refused("Rmcp_tool_add($name) with no function")
    val spec = McpToolSpec(
        name = name,
        description = d.description?.toKString(),
        title = d.title?.toKString(),
        inputSchemaJson = d.input_schema_json?.toKString(),
        hints = d.hints.toInt(),
    )
    val added = RavenMcp.addTool(spec) { call ->
        memScoped {
            callback(call.id.toCPointer<RMcpCall>(), call.tool.cstr.ptr, call.argumentsJson.cstr.ptr, userData)
        }
    }
    return if (added) R_TRUE else R_FALSE
}

@CName("Rmcp_tool_remove")
public fun rmcpToolRemove(name: CPointer<ByteVar>?) {
    RavenMcp.removeTool(name?.toKString() ?: return)
}

@CName("Rmcp_tool_remove_all")
public fun rmcpToolRemoveAll(): Unit = RavenMcp.removeAllTools()

@CName("Rmcp_expose_actions")
public fun rmcpExposeActions(prefix: CPointer<ByteVar>?): Unit = ExposedActions.exposeAll(prefix?.toKString())

@CName("Rmcp_expose_action")
public fun rmcpExposeAction(name: CPointer<ByteVar>?, description: CPointer<ByteVar>?, hints: UInt): Int {
    val action = name?.toKString() ?: return refused("Rmcp_expose_action with no name")
    return if (ExposedActions.exposeOne(action, description?.toKString(), hints.toInt())) R_TRUE else R_FALSE
}

@CName("Rmcp_call_reply")
public fun rmcpCallReply(call: CPointer<RMcpCall>?, text: CPointer<ByteVar>?) {
    callOf(call)?.reply(text?.toKString() ?: "")
}

@CName("Rmcp_call_reply_json")
public fun rmcpCallReplyJson(call: CPointer<RMcpCall>?, json: CPointer<ByteVar>?) {
    callOf(call)?.replyJson(json?.toKString() ?: "")
}

@CName("Rmcp_call_fail")
public fun rmcpCallFail(call: CPointer<RMcpCall>?, message: CPointer<ByteVar>?) {
    callOf(call)?.fail(message?.toKString() ?: "the tool failed")
}

@CName("Rmcp_call_is_cancelled")
public fun rmcpCallIsCancelled(call: CPointer<RMcpCall>?): Int =
    if (callOf(call)?.isCancelled == true) R_TRUE else R_FALSE

/** The open call a handle names; null, logged, for one already replied to. */
private fun callOf(handle: CPointer<RMcpCall>?): McpCall? {
    val call = handle?.let { McpCalls.find(it.toLong()) }
    if (call == null) McpLog.warn("a call was replied to twice, or after it ended; the reply is ignored")
    return call
}

private fun refused(reason: String): Int {
    McpLog.error(reason)
    return R_FALSE
}

private fun strdup(text: String): CPointer<ByteVar> = platform.posix.strdup(text)!!
