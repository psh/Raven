@file:OptIn(ExperimentalForeignApi::class)

package raven.mcp

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import raven.mcp.cinterop.R_FALSE
import raven.mcp.cinterop.R_INVALID_ID
import raven.mcp.cinterop.R_TRUE
import raven.mcp.cinterop.Raction_fire
import raven.mcp.cinterop.Raction_get_count
import raven.mcp.cinterop.Raction_get_description
import raven.mcp.cinterop.Raction_get_name
import raven.mcp.cinterop.Raction_is_builtin
import raven.mcp.cinterop.Raction_set_list_callback
import raven.mcp.cinterop.Ris_idle
import raven.mcp.cinterop.Ris_initialized
import raven.mcp.cinterop.Ris_ui_thread
import raven.mcp.cinterop.Rwindow_get
import raven.mcp.cinterop.Rwindow_get_count
import raven.mcp.cinterop.Rwindow_is_active

/**
 * Raven's actions published as tools (Rmcp_expose_actions, Rmcp_expose_action; compose/docs/mcp.md,
 * "Raven's actions as tools"). Kept in step with binding and unbinding through Raven's list
 * callback, whose one slot it takes. An app's own tool of the same name wins while it exists.
 * UI-thread only.
 */
internal object ExposedActions {
    private class Chosen(val description: String?, val hints: Int)

    /** What Rmcp_expose_actions was given: "" for every action. */
    private val prefixes = LinkedHashSet<String>()
    private val chosen = LinkedHashMap<String, Chosen>()
    private val nudged = HashSet<String>()
    private val skipped = HashSet<String>()

    /** The frames an action's reply waits for the app to be idle, as the UI tools do. */
    private const val IDLE_FRAMES = 120

    fun exposeAll(prefix: String?) {
        if (!onUiThread("Rmcp_expose_actions")) return
        prefixes += prefix ?: ""
        listen()
        for (name in boundActions()) sync(name)
    }

    fun exposeOne(name: String, description: String?, hints: Int): Boolean {
        if (!onUiThread("Rmcp_expose_action")) return false
        if (!TOOL_NAME.matches(name)) {
            McpLog.error("\"$name\" is not a tool name: 1 to 128 of A-Z, a-z, 0-9, '_', '.', '-'")
            return false
        }
        chosen[name] = Chosen(description, hints)
        listen()
        sync(name)
        return true
    }

    /** Forgets every exposed action, and gives the list callback back (Rmcp_tool_remove_all). */
    fun reset() {
        prefixes.clear()
        chosen.clear()
        nudged.clear()
        skipped.clear()
        if (Ris_initialized() == R_TRUE && Ris_ui_thread() == R_TRUE) Raction_set_list_callback(null, null)
    }

    /** Publishes, updates or withdraws the tool for [name], as things stand now. */
    fun sync(name: String) {
        val pick = chosen[name]
        // (built-ins carry arguments in their names, or end the app: only ever exposed on purpose)
        val wanted = pick != null ||
            (prefixes.any { name.startsWith(it) } && isBound(name) && Raction_is_builtin(name) != R_TRUE)
        if (!wanted) return RavenMcp.removeActionTool(name)
        if (!TOOL_NAME.matches(name)) {
            if (skipped.add(name)) McpLog.warn("the action \"$name\" isn't a valid tool name, so it isn't exposed")
            return
        }
        val description = pick?.description ?: Raction_get_description(name)?.toKString() ?: generic(name)
        RavenMcp.addActionTool(McpToolSpec(name, description, hints = pick?.hints ?: 0)) { call -> run(call) }
    }

    /** On the UI thread: fires the action as a widget would, and replies once the app is idle. */
    private fun run(call: McpCall) {
        val name = call.tool
        if (!isBound(name) && Raction_is_builtin(name) != R_TRUE) return call.fail("nothing handles the action $name")
        Raction_fire(name, sender())
        replyWhenIdle(call, "ran $name", IDLE_FRAMES)
    }

    /** Checks Ris_idle once a frame, through Rtask_post, and replies when idle or out of frames. */
    private fun replyWhenIdle(call: McpCall, reply: String, framesLeft: Int) {
        postToUiThread {
            when {
                Ris_idle(R_FALSE) == R_TRUE -> call.reply(reply)
                framesLeft <= 1 -> {
                    McpLog.warn("${call.tool}: the app was still busy after $IDLE_FRAMES frames; replied anyway")
                    call.reply(reply)
                }
                else -> replyWhenIdle(call, reply, framesLeft - 1)
            }
        }
    }

    /** The focused window, else the first open one: what Raven's built-ins and window handlers act on. */
    private fun sender(): UInt {
        val windows = (0 until Rwindow_get_count()).map { Rwindow_get(it) }
        return windows.firstOrNull { Rwindow_is_active(it) == R_TRUE } ?: windows.firstOrNull() ?: R_INVALID_ID
    }

    private fun generic(name: String): String {
        if (nudged.add(name)) McpLog.info("the action $name has no description: Raction_set_description gives agents one to choose it by")
        return "Runs the app's action `$name`"
    }

    private fun boundActions(): List<String> = (0 until Raction_get_count()).mapNotNull { Raction_get_name(it)?.toKString() }

    private fun isBound(name: String): Boolean = boundActions().contains(name)

    /** Hears binding and unbinding. Set again each time: Rinit starts Raven with no list callback. */
    private fun listen() {
        Raction_set_list_callback(onListChanged, null)
    }

    private fun onUiThread(function: String): Boolean {
        if (Ris_ui_thread() == R_TRUE) return true
        McpLog.error("$function called away from Raven's UI thread")
        return false
    }
}

// The tool follows a frame later, so a description set right after Raction_bind is the one agents see.
private val onListChanged = staticCFunction { name: CPointer<ByteVar>?, _: Int, _: COpaquePointer? ->
    val action = name?.toKString() ?: return@staticCFunction
    postToUiThread { ExposedActions.sync(action) }
}

private val TOOL_NAME = Regex("[A-Za-z0-9_.-]{1,128}")
