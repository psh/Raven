@file:OptIn(ExperimentalForeignApi::class)

package raven.mcp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import raven.mcp.cinterop.R_TRUE
import raven.mcp.cinterop.Raccessible_click
import raven.mcp.cinterop.Raccessible_count
import raven.mcp.cinterop.Raccessible_find
import raven.mcp.cinterop.Raccessible_get
import raven.mcp.cinterop.Raccessible_get_last_error
import raven.mcp.cinterop.Raccessible_get_parent
import raven.mcp.cinterop.Raccessible_press_key
import raven.mcp.cinterop.Raccessible_type
import raven.mcp.cinterop.Rwindow_get
import raven.mcp.cinterop.Rwindow_get_count
import raven.mcp.cinterop.Rwindow_get_title
import raven.mcp.cinterop.Rwindow_is_active

/**
 * The UI tools, ui.* (compose/docs/mcp.md, "The UI tools"), over Raven's UI driver
 * (raven_driver.h): published while a server started with uiTools runs. A compose-raven app adds
 * its own set over Compose semantics instead. UI-thread only.
 */
internal object UiTools {
    /** True while the UI tools are meant to be published: a tool of the app's that hid one gives it back. */
    var on: Boolean = false

    private class UiTool(val spec: McpToolSpec, val run: (McpCall, McpArguments) -> Unit)

    private const val SELECTORS =
        "A selector is words that must all hold: role=button, name=\"Save\" (exactly), name~=\"sav\" (contains, any case), " +
            "text=\"buy milk\" (what it shows), widget=save_button (its Name in XML), the states checked selected " +
            "expanded focused disabled enabled, and [n] for the n-th match from 0."
    private const val SNAPSHOT = "Waits until the app is idle, then replies with the window's outline (snapshot: false skips it)."
    private const val READ_ONLY = McpHints.READ_ONLY or McpHints.CLOSED_WORLD

    private val all: List<UiTool> = listOf(
        UiTool(
            McpToolSpec(
                "ui.tree",
                "The app's windows as an outline of what a user perceives: one line per accessible, with its role, " +
                    "name in quotes, what it shows and its states, each line usable as a selector. Narrow it with " +
                    "under (a selector) and depth. $SELECTORS",
                "Outline the UI",
                McpSchemas.objectOf(
                    "window" to McpSchemas.property("string", "A window's title; every open window when left out"),
                    "under" to McpSchemas.property("string", "Only what is inside the one accessible this selector finds"),
                    "depth" to McpSchemas.property("integer", "How many levels below the start to show"),
                ),
                READ_ONLY,
            ),
        ) { call, args -> tree(call, args) },
        UiTool(
            McpToolSpec(
                "ui.find",
                "Reads the one accessible a selector finds: role, name, what it shows, value, states and bounds. $SELECTORS",
                "Find in the UI",
                McpSchemas.objectOf("selector" to McpSchemas.property("string", "Which accessible"), required = listOf("selector")),
                READ_ONLY,
            ),
        ) { call, args -> find(call, args) },
        UiTool(
            McpToolSpec(
                "ui.click",
                "Clicks the middle of the one accessible a selector finds with the mouse, as a user would: a row is " +
                    "scrolled to, a menu item reached through its menus, and a disabled one can't be clicked. " +
                    "$SNAPSHOT $SELECTORS",
                "Click",
                McpSchemas.objectOf(
                    "selector" to McpSchemas.property("string", "What to click"),
                    "snapshot" to McpSchemas.property("boolean", "Reply with the outline after the click (true when left out)"),
                    required = listOf("selector"),
                ),
            ),
        ) { call, args -> click(call, args) },
        UiTool(
            McpToolSpec(
                "ui.type",
                "Gives the one accessible a selector finds the keyboard focus, then types a text into it as the " +
                    "keyboard would. $SNAPSHOT $SELECTORS",
                "Type",
                McpSchemas.objectOf(
                    "selector" to McpSchemas.property("string", "What to type into"),
                    "text" to McpSchemas.property("string", "The text to type"),
                    "snapshot" to McpSchemas.property("boolean", "Reply with the outline after typing (true when left out)"),
                    required = listOf("selector", "text"),
                ),
            ),
        ) { call, args -> type(call, args) },
        UiTool(
            McpToolSpec(
                "ui.key",
                "Presses and releases a key, with modifiers: \"Enter\", \"ctrl+s\", \"shift+Tab\", \"cmd+z\". It goes to " +
                    "the accessible a selector finds, focused first, else to what has the focus. $SNAPSHOT $SELECTORS",
                "Press a key",
                McpSchemas.objectOf(
                    "key" to McpSchemas.property("string", "The key: a letter, a digit or one of Enter Tab Escape Backspace Delete Insert Space Home End PageUp PageDown Up Down Left Right F1-F12, after ctrl+, shift+, alt+ or meta+ (cmd+)"),
                    "selector" to McpSchemas.property("string", "What to press it on; what has the focus when left out"),
                    "snapshot" to McpSchemas.property("boolean", "Reply with the outline after the key (true when left out)"),
                    required = listOf("key"),
                ),
            ),
        ) { call, args -> key(call, args) },
        UiTool(
            McpToolSpec(
                "ui.screenshot",
                "The window as it is drawn, as a PNG, in device pixels, once the app is idle. Nothing outside the app's window is captured.",
                "Screenshot",
                McpSchemas.objectOf("window" to McpSchemas.property("string", "A window's title; the focused window when left out")),
                READ_ONLY,
            ),
        ) { call, args -> screenshot(call, args) },
    )

    /** Publishes every UI tool. One whose name the app already took is left, and logged. */
    fun addAll() {
        on = true
        for (tool in all) add(tool.spec.name)
    }

    /** Publishes the UI tool called [name], if there is one, unless its name is taken. */
    fun add(name: String) {
        val tool = all.firstOrNull { it.spec.name == name } ?: return
        val added = RavenMcp.addOwnTool(tool.spec, RavenMcp.Origin.Ui) { call -> withArguments(call, tool.run) }
        if (!added) McpLog.warn("${tool.spec.name} is the app's own tool, so the UI tool of that name isn't published")
    }

    private fun withArguments(call: McpCall, run: (McpCall, McpArguments) -> Unit) {
        val args = try {
            McpArguments.parse(call.argumentsJson)
        } catch (e: IllegalArgumentException) {
            return call.fail(e.message ?: "the arguments are not a JSON object")
        }
        try {
            run(call, args)
        } catch (e: IllegalArgumentException) {
            call.fail(e.message ?: "invalid arguments")
        }
    }

    // ---------------------------------------------------------------------------------------------
    //  The tools
    // ---------------------------------------------------------------------------------------------

    private fun tree(call: McpCall, args: McpArguments) {
        val windows = windowsNamed(args.string("window")) ?: return call.fail(noWindow(args.string("window")!!))
        val depth = args.int("depth")
        val under = args.string("under")
        val roots = if (under != null) {
            val found = findOne(windows, under) ?: return call.fail(lastError())
            listOf(found)
        } else {
            windows.map { rootOf(it) }.filter { it != 0uL }
        }
        call.reply(McpOutline.of(roots, depth))
    }

    private fun find(call: McpCall, args: McpArguments) {
        val selector = args.requireString("selector")
        val found = findOne(allWindows(), selector) ?: return call.fail(lastError())
        val info = McpAccessible.of(found) ?: return call.fail("the accessible is gone")
        call.reply(info.details())
    }

    private fun click(call: McpCall, args: McpArguments) {
        val selector = args.requireString("selector")
        val target = findOne(allWindows(), selector) ?: return call.fail(lastError())
        val window = McpAccessible.of(target)?.window
        if (Raccessible_click(target) != R_TRUE) return call.fail(lastError())
        replyAfter(call, args, window, "clicked ${McpAccessible.of(target)?.words() ?: selector}")
    }

    private fun type(call: McpCall, args: McpArguments) {
        val selector = args.requireString("selector")
        val text = args.requireString("text")
        val target = findOne(allWindows(), selector) ?: return call.fail(lastError())
        val window = McpAccessible.of(target)?.window
        if (Raccessible_type(target, text) != R_TRUE) return call.fail(lastError())
        replyAfter(call, args, window, "typed into ${McpAccessible.of(target)?.words() ?: selector}")
    }

    private fun key(call: McpCall, args: McpArguments) {
        val name = args.requireString("key")
        val (code, modifiers) = McpKeys.parse(name) ?: return call.fail(
            "\"$name\" is not a key: a letter, a digit or a key's name (Enter, Tab, Escape, ...), after ctrl+, shift+, alt+ or meta+",
        )
        val selector = args.string("selector")
        val target = if (selector != null) {
            findOne(allWindows(), selector) ?: return call.fail(lastError())
        } else {
            focused() ?: return call.fail("no window is open")
        }
        val window = McpAccessible.of(target)?.window
        if (Raccessible_press_key(target, code, modifiers) != R_TRUE) return call.fail(lastError())
        replyAfter(call, args, window, "pressed $name on ${McpAccessible.of(target)?.words() ?: "the focus"}")
    }

    private fun screenshot(call: McpCall, args: McpArguments) {
        val title = args.string("window")
        val window = (if (title != null) windowsNamed(title)?.firstOrNull() else activeWindow())
            ?: return call.fail(if (title != null) noWindow(title) else "no window is open")
        whenIdle(call.tool) {
            val png = McpScreenshots.capture(window) ?: return@whenIdle call.fail(
                "the window can't be captured: the app renders nothing (the null backend) or its renderer can't read frames back",
            )
            call.replyJson(McpScreenshots.replyJson(png))
        }
    }

    /** Waits until idle, then replies with the outline of [window] (every window when it closed), or [done]. */
    private fun replyAfter(call: McpCall, args: McpArguments, window: UInt?, done: String) {
        val snapshot = args.boolean("snapshot") ?: true
        whenIdle(call.tool) { idle ->
            val busy = if (idle) "" else "The app was still busy after ${IDLE_SECONDS.toInt()} s.\n"
            if (!snapshot) return@whenIdle call.reply(busy + done)
            val open = allWindows()
            val roots = (if (window != null && window in open) listOf(window) else open).map { rootOf(it) }.filter { it != 0uL }
            call.reply(busy + McpOutline.of(roots, null))
        }
    }

    // ---------------------------------------------------------------------------------------------
    //  Windows and finding
    // ---------------------------------------------------------------------------------------------

    /** The windows that are shown, as the driver searches them: the roots of its tree. */
    private fun allWindows(): List<UInt> {
        val count = Raccessible_count(0u, "role=window")
        return (0 until count).map { Raccessible_get(0u, "role=window", it) }
            .filter { it != 0uL && Raccessible_get_parent(it) == 0uL }
            .mapNotNull { McpAccessible.of(it)?.window }
            .distinct()
    }

    /** Every shown window for null; else those with [title], or null when there is none. */
    private fun windowsNamed(title: String?): List<UInt>? {
        val shown = allWindows()
        if (title == null) return shown
        return shown.filter { Rwindow_get_title(it)?.toKString() == title }.ifEmpty { null }
    }

    private fun noWindow(title: String): String {
        val titles = allWindows().map { "\"" + (Rwindow_get_title(it)?.toKString() ?: "") + "\"" }
        return if (titles.isEmpty()) "no window is open" else "no window titled \"$title\"; the open ones: ${titles.joinToString(", ")}"
    }

    /** The focused window, else the first one shown. */
    private fun activeWindow(): UInt? {
        val windows = (0 until Rwindow_get_count()).map { Rwindow_get(it) }.filter { it in allWindows() }
        return windows.firstOrNull { Rwindow_is_active(it) == R_TRUE } ?: windows.firstOrNull()
    }

    /** What has the keyboard focus in the active window, else that window. */
    private fun focused(): ULong? {
        val window = activeWindow() ?: return null
        val count = Raccessible_count(window, "focused")
        val deepest = if (count > 0) Raccessible_get(window, "focused", count - 1) else 0uL
        return if (deepest != 0uL) deepest else rootOf(window)
    }

    /** The one accessible [selector] finds in [windows]; null, with the driver's message, otherwise. */
    private fun findOne(windows: List<UInt>, selector: String): ULong? {
        if (windows.size == 1) return Raccessible_find(windows[0], selector).takeIf { it != 0uL }
        return Raccessible_find(0u, selector).takeIf { it != 0uL }
    }

    private fun rootOf(window: UInt): ULong = raven.mcp.cinterop.Raccessible_get_root(window)

    private fun lastError(): String = Raccessible_get_last_error()?.toKString()?.ifEmpty { null } ?: "the UI driver failed"

}
