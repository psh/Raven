package raven.compose.mcp

import androidx.compose.runtime.Composable
import raven.compose.foundation.Text
import raven.compose.ui.Modifier

/**
 * A small marker saying an agent is connected, wherever the app places it: "● claude-code connected",
 * with the agent's name, or how many there are. Nothing while no agent is, or in an app without a server.
 */
@Composable
public fun McpStatus(modifier: Modifier = Modifier) {
    val names = LocalMcpAgents.current?.distinct().orEmpty()
    if (names.isEmpty()) return
    Text(if (names.size == 1) "● ${names.single()} connected" else "● ${names.size} agents connected", modifier)
}
