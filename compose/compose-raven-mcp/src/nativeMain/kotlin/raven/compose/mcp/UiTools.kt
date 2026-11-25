package raven.compose.mcp

import kotlinx.cinterop.toKString
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_title
import raven.cinterop.Rwindow_inject_key
import raven.cinterop.Rwindow_is_active
import raven.compose.ui.RavenHost
import raven.compose.ui.RavenNode
import raven.compose.ui.test.FoundNode
import raven.compose.ui.test.NodeException
import raven.compose.ui.test.findNode
import raven.compose.ui.test.nodeMatcher
import raven.compose.ui.test.widgetRect
import raven.mcp.McpAccessible
import raven.mcp.McpAccessibleStates
import raven.mcp.McpArguments
import raven.mcp.McpHints
import raven.mcp.McpKeys
import raven.mcp.McpOutline
import raven.mcp.McpSchemas
import raven.mcp.McpScreenshots
import raven.mcp.McpToolSpec

/**
 * compose-raven's UI tools, ui.* (compose/docs/mcp.md, "The UI tools"): the contract of the Raven
 * MCP library's, over the composition's nodes. A node is found by a selector, with `tag=` for
 * `Modifier.testTag`; its role, name and states are what Raven tells screen readers about its
 * widget, read as the library's tools read them, so the outlines look alike. Actions are
 * compose-raven-ui-test's, real input, and wait in `RavenHost.awaitIdle`.
 */
internal object UiTools {
    private const val IDLE_FRAMES = 120

    private const val SELECTORS =
        "A selector is words that must all hold: role=button, name=\"Save\" (exactly), name~=\"sav\" (contains, any case), " +
            "text=\"buy milk\" (what it shows), tag=add (its Modifier.testTag), the states checked selected expanded " +
            "focused disabled enabled, and [n] for the n-th match from 0."
    private const val SNAPSHOT = "Waits until the app is idle, then replies with the window's outline (snapshot: false skips it)."
    private const val READ_ONLY = McpHints.READ_ONLY or McpHints.CLOSED_WORLD

    private class UiTool(val spec: McpToolSpec, val run: suspend (RavenHost, McpArguments) -> Any)

    private val all: List<UiTool> = listOf(
        UiTool(
            McpToolSpec(
                "ui.tree",
                "The app's windows as an outline of what a user perceives: one line per node, with its role, name in " +
                    "quotes, what it shows, its states and its tag, each line usable as a selector. Narrow it with " +
                    "under (a selector) and depth. $SELECTORS",
                "Outline the UI",
                McpSchemas.objectOf(
                    "window" to McpSchemas.property("string", "A window's title; every open window when left out"),
                    "under" to McpSchemas.property("string", "Only what is inside the one node this selector finds"),
                    "depth" to McpSchemas.property("integer", "How many levels below the start to show"),
                ),
                READ_ONLY,
            ),
        ) { host, args -> tree(host, args) },
        UiTool(
            McpToolSpec(
                "ui.find",
                "Reads the one node a selector finds: role, name, what it shows, value, states, tag and bounds. $SELECTORS",
                "Find in the UI",
                McpSchemas.objectOf("selector" to McpSchemas.property("string", "Which node"), required = listOf("selector")),
                READ_ONLY,
            ),
        ) { host, args -> find(host, args) },
        UiTool(
            McpToolSpec(
                "ui.click",
                "Clicks the middle of the one node a selector finds with the mouse, as a user would: a menu item is " +
                    "reached through its menus, and a disabled node can't be clicked. $SNAPSHOT $SELECTORS",
                "Click",
                McpSchemas.objectOf(
                    "selector" to McpSchemas.property("string", "What to click"),
                    "snapshot" to McpSchemas.property("boolean", "Reply with the outline after the click (true when left out)"),
                    required = listOf("selector"),
                ),
            ),
        ) { host, args -> click(host, args) },
        UiTool(
            McpToolSpec(
                "ui.type",
                "Clicks the one node a selector finds unless it has the keyboard focus, then types a text into it as the " +
                    "keyboard would. $SNAPSHOT $SELECTORS",
                "Type",
                McpSchemas.objectOf(
                    "selector" to McpSchemas.property("string", "What to type into"),
                    "text" to McpSchemas.property("string", "The text to type"),
                    "snapshot" to McpSchemas.property("boolean", "Reply with the outline after typing (true when left out)"),
                    required = listOf("selector", "text"),
                ),
            ),
        ) { host, args -> type(host, args) },
        UiTool(
            McpToolSpec(
                "ui.key",
                "Presses and releases a key, with modifiers: \"Enter\", \"ctrl+s\", \"shift+Tab\", \"cmd+z\". It goes to " +
                    "the node a selector finds, focused first, else to what has the focus. $SNAPSHOT $SELECTORS",
                "Press a key",
                McpSchemas.objectOf(
                    "key" to McpSchemas.property("string", "The key: a letter, a digit or one of Enter Tab Escape Backspace Delete Insert Space Home End PageUp PageDown Up Down Left Right F1-F12, after ctrl+, shift+, alt+ or meta+ (cmd+)"),
                    "selector" to McpSchemas.property("string", "What to press it on; what has the focus when left out"),
                    "snapshot" to McpSchemas.property("boolean", "Reply with the outline after the key (true when left out)"),
                    required = listOf("key"),
                ),
            ),
        ) { host, args -> key(host, args) },
        UiTool(
            McpToolSpec(
                "ui.screenshot",
                "The window as it is drawn, as a PNG, in device pixels, once the app is idle. Nothing outside the app's window is captured.",
                "Screenshot",
                McpSchemas.objectOf("window" to McpSchemas.property("string", "A window's title; the focused window when left out")),
                READ_ONLY,
            ),
        ) { host, args -> screenshot(host, args) },
    )

    /** Adds every UI tool to [registry], under what the app composes: an `McpTool` of the same name wins. */
    fun addAll(registry: McpRegistry, host: RavenHost) {
        for (tool in all) {
            registry.add(tool.spec) { json ->
                try {
                    tool.run(host, McpArguments.parse(json))
                } catch (e: NodeException) {
                    throw McpToolFailure(e.message ?: "the UI tools failed")
                } catch (e: IllegalArgumentException) {
                    throw McpToolFailure(e.message ?: "invalid arguments")
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    //  The tools
    // ---------------------------------------------------------------------------------------------

    private fun tree(host: RavenHost, args: McpArguments): String {
        val title = args.string("window")
        val windows = windowsNamed(host, title)
        val depth = args.int("depth")
        val under = args.string("under")
        if (under != null) {
            val found = find(host, Selector.parse(under), windows.keys)
            return outline(listOf(found.node), depth)
        }
        val outline = McpOutline()
        for ((window, roots) in windows) {
            outline.add(0, McpAccessible.of(window.toULong())?.line() ?: "window ${McpAccessible.quote(titleOf(window))}")
            if (depth == 0) continue
            for (root in roots) walk(outline, root, 1, depth)
        }
        return outline.toString()
    }

    private fun find(host: RavenHost, args: McpArguments): String {
        val found = find(host, Selector.parse(args.requireString("selector")))
        val info = describe(found.node)
        val tag = found.node.testTag?.let { "\ntag: ${McpAccessible.quote(it)}" } ?: ""
        if (info != null) return info.details() + tag
        val rect = widgetRect(found.widget)
        return "a node with no role of its own: it lays out what it holds$tag\n" +
            "bounds: x=${rect.x} y=${rect.y} width=${rect.width} height=${rect.height}, logical units in the window " +
            McpAccessible.quote(titleOf(Rwidget_get_window(found.widget)))
    }

    private suspend fun click(host: RavenHost, args: McpArguments): String {
        val found = find(host, Selector.parse(args.requireString("selector")))
        if (!found.fetchEnabled()) throw NodeException("${words(found)} is disabled")
        val window = Rwidget_get_window(found.widget)
        val done = "clicked ${words(found)}"
        found.performClick()
        return after(host, args, window, done)
    }

    private suspend fun type(host: RavenHost, args: McpArguments): String {
        val found = find(host, Selector.parse(args.requireString("selector")))
        val text = args.requireString("text")
        if (!found.fetchEnabled()) throw NodeException("${words(found)} is disabled")
        val window = Rwidget_get_window(found.widget)
        val done = "typed into ${words(found)}"
        found.performTextInput(text)
        return after(host, args, window, done)
    }

    private suspend fun key(host: RavenHost, args: McpArguments): String {
        val name = args.requireString("key")
        val (code, modifiers) = McpKeys.parse(name) ?: throw IllegalArgumentException(
            "\"$name\" is not a key: a letter, a digit or a key's name (Enter, Tab, Escape, ...), after ctrl+, shift+, alt+ or meta+",
        )
        val selector = args.string("selector")
        val window: UInt
        val done: String
        if (selector != null) {
            val found = find(host, Selector.parse(selector))
            if (!found.fetchEnabled()) throw NodeException("${words(found)} is disabled")
            Rwidget_focus(found.widget)
            window = Rwidget_get_window(found.widget)
            done = "pressed $name on ${words(found)}"
        } else {
            window = activeWindow(host) ?: throw NodeException("no window is open")
            done = "pressed $name"
        }
        Rwindow_inject_key(window, code, R_TRUE, modifiers)
        Rwindow_inject_key(window, code, R_FALSE, modifiers)
        return after(host, args, window, done)
    }

    private suspend fun screenshot(host: RavenHost, args: McpArguments): McpReply {
        val title = args.string("window")
        val window = (if (title != null) windowsNamed(host, title).keys.first() else activeWindow(host))
            ?: throw NodeException("no window is open")
        host.awaitIdle(IDLE_FRAMES)
        val png = McpScreenshots.capture(window) ?: throw NodeException(
            "the window can't be captured: the app renders nothing (the null backend) or its renderer can't read frames back",
        )
        return McpReply.Image(png)
    }

    /** Waits until idle, then replies with the outline of [window] (every window when it closed), or [done]. */
    private suspend fun after(host: RavenHost, args: McpArguments, window: UInt, done: String): String {
        val snapshot = args.boolean("snapshot") ?: true
        val idle = host.awaitIdle(IDLE_FRAMES)
        val busy = if (idle) "" else "The app was still busy after $IDLE_FRAMES frames.\n"
        if (!snapshot) return busy + done
        val windows = windowsOf(host)
        val shown = windows[window]?.let { mapOf(window to it) } ?: windows
        val outline = McpOutline()
        for ((id, roots) in shown) {
            outline.add(0, McpAccessible.of(id.toULong())?.line() ?: "window ${McpAccessible.quote(titleOf(id))}")
            for (root in roots) walk(outline, root, 1, null)
        }
        return busy + outline
    }

    // ---------------------------------------------------------------------------------------------
    //  Nodes, windows and the outline
    // ---------------------------------------------------------------------------------------------

    /** The node's widget as screen readers see it; null for a node that only lays others out. */
    fun describe(node: RavenNode): McpAccessible? = if (node.isContentNode) null else McpAccessible.of(node.widget.toULong())

    private fun words(found: FoundNode): String = describe(found.node)?.words() ?: found.selector

    private fun find(host: RavenHost, selector: Selector, windows: Collection<UInt>? = null): FoundNode {
        val found = host.findNode(selector.matcher, selector.index)
        if (windows != null && Rwidget_get_window(found.widget) !in windows) {
            throw NodeException("no node with ${selector.matcher.description} in that window")
        }
        return found
    }

    /** The open windows, each with the roots of what it shows: its content, its island, its dialogs. */
    private fun windowsOf(host: RavenHost): LinkedHashMap<UInt, MutableList<RavenNode>> {
        val windows = LinkedHashMap<UInt, MutableList<RavenNode>>()
        for (root in host.windowRoots + host.islandRoots + host.dialogRoots) {
            windows.getOrPut(Rwidget_get_window(root.widget)) { ArrayList() } += root
        }
        return windows
    }

    /** Every window for null; else those titled [title], or a NodeException naming the open ones. */
    private fun windowsNamed(host: RavenHost, title: String?): Map<UInt, List<RavenNode>> {
        val windows = windowsOf(host)
        if (title == null) return windows
        val named = windows.filterKeys { titleOf(it) == title }
        if (named.isNotEmpty()) return named
        val titles = windows.keys.joinToString(", ") { McpAccessible.quote(titleOf(it)) }
        throw NodeException(if (windows.isEmpty()) "no window is open" else "no window titled \"$title\"; the open ones: $titles")
    }

    /** The focused window, else the first one open. */
    private fun activeWindow(host: RavenHost): UInt? {
        val windows = windowsOf(host).keys
        return windows.firstOrNull { Rwindow_is_active(it) == R_TRUE } ?: windows.firstOrNull()
    }

    private fun titleOf(window: UInt): String = Rwindow_get_title(window)?.toKString() ?: ""

    private fun outline(roots: List<RavenNode>, depth: Int?): String {
        val outline = McpOutline()
        for (root in roots) walk(outline, root, 0, depth)
        return outline.toString()
    }

    /**
     * Adds [node]'s line at [level] and its children's below it. A node with neither a role nor a
     * tag adds no line, and its children take its place; [depth] counts the levels below the
     * start, as lines show them.
     */
    private fun walk(outline: McpOutline, node: RavenNode, level: Int, depth: Int?, start: Int = level) {
        val info = describe(node)
        val tag = node.testTag?.let { "tag=" + McpAccessible.quote(it) }
        val next = if (info != null || tag != null) {
            outline.add(level, listOfNotNull(info?.line(), tag).joinToString(" "))
            if (depth != null && level - start >= depth) return
            // (a closed menu's items are there to be found, not to be read)
            if (info != null && info.has(McpAccessibleStates.EXPANDABLE) && !info.has(McpAccessibleStates.EXPANDED)) return
            level + 1
        } else {
            level
        }
        for (child in node.children) walk(outline, child, next, depth, start)
    }
}

/**
 * A selector, as the UI tools take one: `role=button name="Add"`, `tag=add`, `text="buy milk"
 * [1]`. Its [matcher] accepts the nodes every word holds for; [index] is its `[n]`.
 */
/** One word of a selector: whether a node, with what Raven tells screen readers of it, holds for it. */
private typealias Test = (RavenNode, McpAccessible?) -> Boolean

internal class Selector private constructor(val matcher: raven.compose.ui.test.NodeMatcher, val index: Int?) {
    companion object {
        private val STATES = mapOf(
            "focused" to McpAccessibleStates.FOCUSED, "checked" to McpAccessibleStates.CHECKED,
            "selected" to McpAccessibleStates.SELECTED, "expanded" to McpAccessibleStates.EXPANDED,
            "disabled" to McpAccessibleStates.DISABLED,
        )

        /** Parses [text]; an IllegalArgumentException says what doesn't parse. */
        fun parse(text: String): Selector {
            val tests = ArrayList<Test>()
            val words = ArrayList<String>()
            var index: Int? = null
            var i = 0
            fun fail(why: String): Nothing = throw IllegalArgumentException("the selector $text doesn't parse: $why")
            while (i < text.length) {
                if (text[i] == ' ') { i++; continue }
                if (text[i] == '[') {
                    val end = text.indexOf(']', i)
                    index = text.substring(i + 1, if (end < 0) text.length else end).toIntOrNull()?.takeIf { it >= 0 }
                        ?: fail("[n] takes a number from 0")
                    i = end + 1
                    continue
                }
                val start = i
                while (i < text.length && text[i] != ' ' && text[i] != '=' && text[i] != '~') i++
                val key = text.substring(start, i)
                if (i >= text.length || text[i] == ' ') {
                    val state = STATES[key]
                    when {
                        state != null -> tests += { _, info -> info?.has(state) == true }
                        key == "enabled" -> tests += { _, info -> info != null && !info.has(McpAccessibleStates.DISABLED) }
                        else -> fail("\"$key\" is no state: checked selected expanded focused disabled enabled")
                    }
                    words += key
                    continue
                }
                val contains = text[i] == '~'
                if (contains) i++
                if (i >= text.length || text[i] != '=') fail("$key~ wants =")
                i++
                val value = StringBuilder()
                if (i < text.length && text[i] == '"') {
                    i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\' && i + 1 < text.length) i++
                        value.append(text[i++])
                    }
                    if (i >= text.length) fail("a quote isn't closed")
                    i++
                } else {
                    while (i < text.length && text[i] != ' ') value.append(text[i++])
                }
                val v = value.toString()
                if (contains && key != "name") fail("only name takes ~=")
                val test: Test = when (key) {
                    "role" -> { _, info -> info?.role == v }
                    "name" -> if (contains) { _, info -> info != null && info.name.contains(v, ignoreCase = true) } else { _, info -> info?.name == v }
                    "text" -> { _, info -> info?.shown == v }
                    "tag" -> { node, _ -> node.testTag == v }
                    "widget" -> fail("widget= names a widget of Raven's XML; in a compose-raven app, use tag= (Modifier.testTag)")
                    else -> fail("\"$key\" is not a selector's key: role, name, name~, text, tag")
                }
                tests += test
                // (quoted as the driver's messages quote it: when it must be)
                val written = if (v.isEmpty() || v.any { it == ' ' || it == '"' || it == '\\' }) McpAccessible.quote(v) else v
                words += if (contains) "$key~=$written" else "$key=$written"
            }
            if (tests.isEmpty()) fail("it says nothing to match")
            val matcher = nodeMatcher(words.joinToString(" ")) { node ->
                val info = UiTools.describe(node)
                tests.all { it(node, info) }
            }
            return Selector(matcher, index)
        }
    }
}
