@file:OptIn(ExperimentalNativeApi::class)

package raven.compose.mcp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import raven.compose.ui.ApplicationScope
import raven.compose.ui.DragData
import raven.compose.ui.LocalRavenHost
import raven.compose.ui.WindowState
import raven.compose.ui.graphics.Color
import raven.compose.ui.unit.DpSize
import raven.mcp.McpLogLevel
import raven.mcp.McpServerConfig
import raven.mcp.RavenMcp
import kotlin.experimental.ExperimentalNativeApi

/**
 * The settings of an app's Raven MCP server, as `RMcpConfig`: what agents see it as, where it
 * listens, and whether it offers [the UI tools][uiTools]. Give it to `application(mcp = …)`.
 */
public class McpConfig(
    /** The app's name, as agents see it, and its token file's. Null: the program's name. */
    public val name: String? = null,
    /** The app's version, as agents see it. Null: "0". */
    public val version: String? = null,
    /** A hint to the agent about how to use the app's tools. */
    public val instructions: String? = null,
    /** Loopback only, with no fallback. 0: 8765. */
    public val port: Int = 0,
    /** Null: the app's stored token, in `~/.raven/mcp/<name>.json`. "": none, logged as a warning. */
    public val token: String? = null,
    /** Offers `ui.*`, the tools that see and drive the app's UI over Compose semantics. */
    public val uiTools: Boolean = false,
    /** Where Raven MCP's messages go, from any thread. Null: nowhere, except errors, to stderr. */
    public val log: ((McpLogLevel, String) -> Unit)? = null,
    /** On the UI thread, as an agent connects, with the name it gave. */
    public val onAgentConnected: ((client: String) -> Unit)? = null,
) {
    public companion object {
        /**
         * The settings the environment asks for, for an app that offers Raven MCP to developers
         * only: RAVEN_MCP=1 turns it on, RAVEN_MCP_PORT sets the port, RAVEN_MCP_UI=1 the UI
         * tools. Null unless RAVEN_MCP=1, so `application(mcp = McpConfig.fromEnvironment())`
         * starts nothing otherwise.
         */
        public fun fromEnvironment(base: McpConfig = McpConfig()): McpConfig? {
            val server = McpServerConfig.fromEnvironment(McpServerConfig(port = base.port, uiTools = base.uiTools)) ?: return null
            return McpConfig(
                base.name, base.version, base.instructions, server.port, base.token, server.uiTools, base.log, base.onAgentConnected,
            )
        }
    }
}

/**
 * Runs [content] as `raven.compose.ui.application` does, with a Raven MCP server started from
 * [mcp] for as long as the application runs; with null, none, and every `McpTool` does nothing.
 */
public fun application(
    mcp: McpConfig?,
    followSystemAccessibility: Boolean = true,
    liveReload: Boolean = Platform.isDebugBinary,
    resourceRoot: String? = null,
    content: @Composable ApplicationScope.() -> Unit,
) {
    raven.compose.ui.application(followSystemAccessibility, liveReload, resourceRoot) {
        val scope = this
        McpServer(mcp) { scope.content() }
    }
}

/** `raven.compose.ui.singleWindowApplication`, with a Raven MCP server started from [mcp]. */
public fun singleWindowApplication(
    mcp: McpConfig?,
    state: WindowState = WindowState(),
    visible: Boolean = true,
    title: String = "Untitled",
    customHeader: Boolean = false,
    transparent: Boolean = false,
    undecorated: Boolean = false,
    resizable: Boolean = true,
    minimumSize: DpSize? = null,
    background: Color = Color.Unspecified,
    opacity: Float = 1f,
    onExternalDrop: ((DragData) -> Unit)? = null,
    followSystemAccessibility: Boolean = true,
    liveReload: Boolean = Platform.isDebugBinary,
    resourceRoot: String? = null,
    content: @Composable () -> Unit,
) {
    raven.compose.ui.singleWindowApplication(
        state = state, visible = visible, title = title, customHeader = customHeader, transparent = transparent,
        undecorated = undecorated, resizable = resizable, minimumSize = minimumSize, background = background,
        opacity = opacity, onExternalDrop = onExternalDrop, followSystemAccessibility = followSystemAccessibility,
        liveReload = liveReload, resourceRoot = resourceRoot,
    ) {
        McpServer(mcp, content)
    }
}

/**
 * Serves Raven MCP from [config] while it is composed, with the `McpTool`s of [content] as its
 * tools: what the `mcp` overloads of `application` wrap the content in, and what a test hosts. It
 * starts the server as it enters the composition, from the config it has then, and stops it,
 * withdrawing every tool, as it leaves, before Raven shuts down. Only [McpConfig.onAgentConnected]
 * follows recomposition. With null it only composes [content].
 */
@Composable
public fun McpServer(config: McpConfig?, content: @Composable () -> Unit) {
    if (config == null) return content()
    val host = LocalRavenHost.current
    val scope = rememberCoroutineScope()
    val registry = remember { McpRegistry(scope) }
    val agents = remember { mutableStateListOf<String>() }
    val onAgentConnected = rememberUpdatedState(config.onAgentConnected)
    DisposableEffect(Unit) {
        if (config.uiTools) UiTools.addAll(registry, host)
        val started = RavenMcp.start(
            McpServerConfig(
                name = config.name, version = config.version, instructions = config.instructions, port = config.port,
                token = config.token, log = config.log,
                // compose-raven's UI tools are its own, over Compose semantics: never the driver's
                uiTools = false,
                onSession = { connected, client ->
                    if (connected) {
                        agents += client
                        onAgentConnected.value?.invoke(client)
                    } else {
                        agents.remove(client)
                    }
                },
            ),
        )
        if (!started) RavenMcp.log(McpLogLevel.Error, "Raven MCP didn't start: its tools stay unpublished")
        onDispose {
            RavenMcp.stop()
            RavenMcp.removeAllTools()
            registry.clear()
            agents.clear()
        }
    }
    CompositionLocalProvider(LocalMcpRegistry provides registry, LocalMcpAgents provides agents, content = content)
}

/** The registry of the server composed above, or null in an app started without one. */
internal val LocalMcpRegistry = staticCompositionLocalOf<McpRegistry?> { null }

/** The agents connected now, by the names they gave, or null with no server. */
internal val LocalMcpAgents = staticCompositionLocalOf<List<String>?> { null }
