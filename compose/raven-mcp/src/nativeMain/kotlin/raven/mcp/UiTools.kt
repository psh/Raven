@file:OptIn(ExperimentalForeignApi::class)

package raven.mcp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject
import raven.mcp.cinterop.RAccessibleInfo
import raven.mcp.cinterop.R_INVALID_ID
import raven.mcp.cinterop.R_TRUE
import raven.mcp.cinterop.Raccessible_click
import raven.mcp.cinterop.Raccessible_count
import raven.mcp.cinterop.Raccessible_describe
import raven.mcp.cinterop.Raccessible_find
import raven.mcp.cinterop.Raccessible_get
import raven.mcp.cinterop.Raccessible_get_child
import raven.mcp.cinterop.Raccessible_get_child_count
import raven.mcp.cinterop.Raccessible_get_last_error
import raven.mcp.cinterop.Raccessible_get_parent
import raven.mcp.cinterop.Raccessible_get_role_name
import raven.mcp.cinterop.Raccessible_press_key
import raven.mcp.cinterop.Raccessible_type
import raven.mcp.cinterop.Rimage_encode_png
import raven.mcp.cinterop.Rpixels_free
import raven.mcp.cinterop.Rwindow_capture
import raven.mcp.cinterop.Rwindow_get
import raven.mcp.cinterop.Rwindow_get_count
import raven.mcp.cinterop.Rwindow_get_title
import raven.mcp.cinterop.Rwindow_is_active
import platform.posix.size_tVar
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The UI tools, ui.* (compose/docs/mcp.md, "The UI tools"), over Raven's UI driver
 * (raven_driver.h): published while a server started with uiTools runs. A compose-raven app adds
 * its own set over Compose semantics instead. UI-thread only.
 */
internal object UiTools {
    /** True while the UI tools are meant to be published: a tool of the app's that hid one gives it back. */
    var on: Boolean = false

    private class UiTool(val spec: McpToolSpec, val run: (McpCall, JsonObject) -> Unit)

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
                schema(
                    "window" to prop("string", "A window's title; every open window when left out"),
                    "under" to prop("string", "Only what is inside the one accessible this selector finds"),
                    "depth" to prop("integer", "How many levels below the start to show"),
                ),
                READ_ONLY,
            ),
        ) { call, args -> tree(call, args) },
        UiTool(
            McpToolSpec(
                "ui.find",
                "Reads the one accessible a selector finds: role, name, what it shows, value, states and bounds. $SELECTORS",
                "Find in the UI",
                schema("selector" to prop("string", "Which accessible"), required = listOf("selector")),
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
                schema(
                    "selector" to prop("string", "What to click"),
                    "snapshot" to prop("boolean", "Reply with the outline after the click (true when left out)"),
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
                schema(
                    "selector" to prop("string", "What to type into"),
                    "text" to prop("string", "The text to type"),
                    "snapshot" to prop("boolean", "Reply with the outline after typing (true when left out)"),
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
                schema(
                    "key" to prop("string", "The key: a letter, a digit or one of Enter Tab Escape Backspace Delete Insert Space Home End PageUp PageDown Up Down Left Right F1-F12, after ctrl+, shift+, alt+ or meta+ (cmd+)"),
                    "selector" to prop("string", "What to press it on; what has the focus when left out"),
                    "snapshot" to prop("boolean", "Reply with the outline after the key (true when left out)"),
                    required = listOf("key"),
                ),
            ),
        ) { call, args -> key(call, args) },
        UiTool(
            McpToolSpec(
                "ui.screenshot",
                "The window as it is drawn, as a PNG, in device pixels, once the app is idle. Nothing outside the app's window is captured.",
                "Screenshot",
                schema("window" to prop("string", "A window's title; the focused window when left out")),
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

    private fun withArguments(call: McpCall, run: (McpCall, JsonObject) -> Unit) {
        val args = try {
            McpJsonArguments.parse(call.argumentsJson)
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

    private fun tree(call: McpCall, args: JsonObject) {
        val windows = windowsNamed(args.string("window")) ?: return call.fail(noWindow(args.string("window")!!))
        val depth = args.int("depth")
        val under = args.string("under")
        val roots = if (under != null) {
            val found = findOne(windows, under) ?: return call.fail(lastError())
            listOf(found)
        } else {
            windows.map { rootOf(it) }.filter { it != 0uL }
        }
        call.reply(Outline.of(roots, depth))
    }

    private fun find(call: McpCall, args: JsonObject) {
        val selector = args.requireString("selector")
        val found = findOne(allWindows(), selector) ?: return call.fail(lastError())
        val info = Described.of(found) ?: return call.fail("the accessible is gone")
        call.reply(info.details())
    }

    private fun click(call: McpCall, args: JsonObject) {
        val selector = args.requireString("selector")
        val target = findOne(allWindows(), selector) ?: return call.fail(lastError())
        val window = Described.of(target)?.window
        if (Raccessible_click(target) != R_TRUE) return call.fail(lastError())
        replyAfter(call, args, window, "clicked ${Described.of(target)?.words() ?: selector}")
    }

    private fun type(call: McpCall, args: JsonObject) {
        val selector = args.requireString("selector")
        val text = args.requireString("text")
        val target = findOne(allWindows(), selector) ?: return call.fail(lastError())
        val window = Described.of(target)?.window
        if (Raccessible_type(target, text) != R_TRUE) return call.fail(lastError())
        replyAfter(call, args, window, "typed into ${Described.of(target)?.words() ?: selector}")
    }

    private fun key(call: McpCall, args: JsonObject) {
        val name = args.requireString("key")
        val (code, modifiers) = Keys.parse(name) ?: return call.fail(
            "\"$name\" is not a key: a letter, a digit or a key's name (Enter, Tab, Escape, ...), after ctrl+, shift+, alt+ or meta+",
        )
        val selector = args.string("selector")
        val target = if (selector != null) {
            findOne(allWindows(), selector) ?: return call.fail(lastError())
        } else {
            focused() ?: return call.fail("no window is open")
        }
        val window = Described.of(target)?.window
        if (Raccessible_press_key(target, code, modifiers) != R_TRUE) return call.fail(lastError())
        replyAfter(call, args, window, "pressed $name on ${Described.of(target)?.words() ?: "the focus"}")
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun screenshot(call: McpCall, args: JsonObject) {
        val title = args.string("window")
        val window = (if (title != null) windowsNamed(title)?.firstOrNull() else activeWindow())
            ?: return call.fail(if (title != null) noWindow(title) else "no window is open")
        whenIdle(call.tool) {
            val png = capturePng(window) ?: return@whenIdle call.fail(
                "the window can't be captured: the app renders nothing (the null backend) or its renderer can't read frames back",
            )
            val result = buildJsonObject {
                put("content", buildJsonArray {
                    addJsonObject {
                        put("type", "image")
                        put("data", Base64.encode(png))
                        put("mimeType", "image/png")
                    }
                })
            }
            call.replyJson(result.toString())
        }
    }

    /** Waits until idle, then replies with the outline of [window] (every window when it closed), or [done]. */
    private fun replyAfter(call: McpCall, args: JsonObject, window: UInt?, done: String) {
        val snapshot = args.boolean("snapshot") ?: true
        whenIdle(call.tool) { idle ->
            val busy = if (idle) "" else "The app was still busy after ${IDLE_SECONDS.toInt()} s.\n"
            if (!snapshot) return@whenIdle call.reply(busy + done)
            val open = allWindows()
            val roots = (if (window != null && window in open) listOf(window) else open).map { rootOf(it) }.filter { it != 0uL }
            call.reply(busy + Outline.of(roots, null))
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
            .mapNotNull { Described.of(it)?.window }
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

    private fun capturePng(window: UInt): ByteArray? = memScoped {
        val width = alloc<IntVar>()
        val height = alloc<IntVar>()
        val pixels = Rwindow_capture(window, width.ptr, height.ptr) ?: return null
        try {
            val w = width.value
            val h = height.value
            if (w <= 0 || h <= 0) return null
            // A window seen through is captured premultiplied: PNG wants straight alpha.
            for (i in 0 until w * h) {
                val a = pixels[i * 4 + 3].toInt()
                if (a == 255) continue
                for (c in 0 until 3) {
                    val v = pixels[i * 4 + c].toInt()
                    pixels[i * 4 + c] = (if (a == 0) 0 else minOf(255, (v * 255 + a / 2) / a)).toUByte()
                }
            }
            val size = alloc<size_tVar>()
            val png = Rimage_encode_png(pixels, w, h, size.ptr) ?: return null
            try {
                png.readBytes(size.value.toInt())
            } finally {
                Rpixels_free(png)
            }
        } finally {
            Rpixels_free(pixels)
        }
    }

    // ---------------------------------------------------------------------------------------------
    //  Schemas and arguments
    // ---------------------------------------------------------------------------------------------

    private fun prop(type: String, description: String) = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun schema(vararg properties: Pair<String, JsonObject>, required: List<String> = emptyList()): String =
        buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties.toMap()))
            if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }.toString()

    private fun JsonObject.primitive(name: String): JsonPrimitive? {
        val value: JsonElement = this[name] ?: return null
        if (value is kotlinx.serialization.json.JsonNull) return null
        return value as? JsonPrimitive ?: throw IllegalArgumentException("$name must be a ${expected(name)}")
    }

    private fun JsonObject.string(name: String): String? {
        val value = primitive(name) ?: return null
        if (!value.isString) throw IllegalArgumentException("$name must be a string")
        return value.content
    }

    private fun JsonObject.requireString(name: String): String = string(name) ?: throw IllegalArgumentException("$name is required")

    private fun JsonObject.int(name: String): Int? {
        val value = primitive(name) ?: return null
        val number = value.takeIf { !it.isString }?.intOrNull ?: throw IllegalArgumentException("$name must be a whole number")
        if (number < 0) throw IllegalArgumentException("$name must be 0 or more")
        return number
    }

    private fun JsonObject.boolean(name: String): Boolean? {
        val value = primitive(name) ?: return null
        return value.takeIf { !it.isString }?.booleanOrNull ?: throw IllegalArgumentException("$name must be true or false")
    }

    private fun expected(name: String): String = when (name) {
        "depth" -> "whole number"
        "snapshot" -> "boolean"
        else -> "string"
    }
}

/** A call's arguments, as a JSON object. */
internal object McpJsonArguments {
    fun parse(json: String): JsonObject {
        val parsed = try {
            io.modelcontextprotocol.kotlin.sdk.types.McpJson.parseToJsonElement(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("the arguments are not JSON")
        }
        return parsed as? JsonObject ?: throw IllegalArgumentException("the arguments are not a JSON object")
    }
}

/** An accessible, read with Raccessible_describe: its texts copied, since the driver reuses them. */
internal class Described private constructor(
    val role: String,
    val name: String,
    val description: String,
    val value: String,
    val number: Double,
    val minimum: Double,
    val maximum: Double,
    val states: UInt,
    val x: Float, val y: Float, val width: Float, val height: Float,
    val window: UInt,
) {
    /** What it shows, as the driver's text= reads it: a field's value, else its name. */
    val shown: String get() = if (role in VALUE_ROLES) value else name

    fun has(state: UInt): Boolean = states and state != 0u

    /** As the driver's messages name it: role=button name="Save". */
    fun words(): String = if (name.isEmpty()) "role=$role" else "role=$role name=${quote(name)}"

    /** ui.find's reply. */
    fun details(): String = buildString {
        appendLine(words())
        if (role in VALUE_ROLES) appendLine("text: ${quote(value)}")
        else if (value.isNotEmpty()) appendLine("value: ${quote(value)}")
        if (has(States.HAS_RANGE)) appendLine("number: ${format(number)} (from ${format(minimum)} to ${format(maximum)})")
        if (description.isNotEmpty()) appendLine("description: ${quote(description)}")
        val named = States.NAMES.filter { has(it.first) }.map { it.second }
        appendLine("states: ${named.ifEmpty { listOf("none") }.joinToString(" ")}")
        val title = Rwindow_get_title(window)?.toKString() ?: ""
        append("bounds: x=${format(x)} y=${format(y)} width=${format(width)} height=${format(height)}, logical units in the window ${quote(title)}")
    }

    companion object {
        private val VALUE_ROLES = setOf("text-field", "document", "combo-box", "spin-button")

        fun of(accessible: ULong): Described? = memScoped {
            val info = alloc<RAccessibleInfo>()
            if (Raccessible_describe(accessible, info.ptr) != R_TRUE) return null
            Described(
                role = Raccessible_get_role_name(info.role)?.toKString() ?: "",
                name = info.name?.toKString() ?: "",
                description = info.description?.toKString() ?: "",
                value = info.value?.toKString() ?: "",
                number = info.number, minimum = info.minimum, maximum = info.maximum,
                states = info.states,
                x = info.x, y = info.y, width = info.width, height = info.height,
                window = info.window,
            )
        }

        fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private fun format(value: Double): String =
            if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

        private fun format(value: Float): String = format(value.toDouble())
    }
}

/** RAccessibleState, as raven_driver.h has it. */
internal object States {
    val FOCUSED: UInt = 1u shl 1
    val DISABLED: UInt = 1u shl 2
    val CHECKED: UInt = 1u shl 3
    val EXPANDED: UInt = 1u shl 5
    val EXPANDABLE: UInt = 1u shl 6
    val SELECTED: UInt = 1u shl 7
    val HAS_RANGE: UInt = 1u shl 12

    /** The states a selector names, in the order the outline shows them. */
    val NAMES: List<Pair<UInt, String>> = listOf(
        FOCUSED to "focused", CHECKED to "checked", SELECTED to "selected", EXPANDED to "expanded", DISABLED to "disabled",
    )
}

/**
 * The outline ui.tree and the actions reply with: one line per accessible, indented under its
 * parent, each line a selector (compose/docs/mcp.md, "The outline"). At most [MAX_LINES] lines.
 */
internal object Outline {
    const val MAX_LINES: Int = 500

    fun of(roots: List<ULong>, depth: Int?): String {
        val lines = ArrayList<String>()
        var left = 0
        fun walk(accessible: ULong, level: Int) {
            val info = Described.of(accessible) ?: return
            if (lines.size < MAX_LINES) lines += "  ".repeat(level) + "- " + line(info) else left++
            if (depth != null && level >= depth) return
            // (a closed menu's items are there to be found, not to be read)
            if (info.has(States.EXPANDABLE) && !info.has(States.EXPANDED)) return
            val children = Raccessible_get_child_count(accessible)
            for (i in 0 until children) walk(Raccessible_get_child(accessible, i), level + 1)
        }
        for (root in roots) walk(root, 0)
        if (lines.isEmpty()) return "no window is open"
        if (left > 0) lines += "- … $left more accessibles left out: narrow it with under (a selector) or depth"
        return lines.joinToString("\n")
    }

    private fun line(info: Described): String = buildString {
        append(info.role)
        if (info.name.isNotEmpty()) append(' ').append(Described.quote(clip(info.name)))
        if (info.shown != info.name && info.shown.isNotEmpty()) append(" text=").append(Described.quote(clip(info.shown)))
        val states = States.NAMES.filter { info.has(it.first) }.map { it.second }
        if (states.isNotEmpty()) append(" [").append(states.joinToString(" ")).append(']')
    }

    private fun clip(text: String): String {
        val line = text.substringBefore('\n')
        return if (line.length <= 80 && line.length == text.length) text else line.take(80) + "…"
    }
}

/** Key names as raven_driver.h's callers and Raven's shortcuts write them: "Enter", "ctrl+s", "shift+Tab". */
internal object Keys {
    private val SHIFT = 1u shl 0
    private val CTRL = 1u shl 1
    private val ALT = 1u shl 2
    private val SUPER = 1u shl 3

    // RKey's values (raven_window.h): letters, digits and punctuation are their ASCII codes.
    private val NAMED: Map<String, UInt> = buildMap {
        put("space", 32u); put("escape", 256u); put("esc", 256u); put("enter", 257u); put("return", 257u)
        put("tab", 258u); put("backspace", 259u); put("insert", 260u); put("delete", 261u); put("del", 261u)
        put("right", 262u); put("left", 263u); put("down", 264u); put("up", 265u)
        put("arrowright", 262u); put("arrowleft", 263u); put("arrowdown", 264u); put("arrowup", 265u)
        put("pageup", 266u); put("pagedown", 267u); put("home", 268u); put("end", 269u)
        for (n in 1..12) put("f$n", (272 + n).toUInt())
    }
    private val PUNCTUATION = "',-./;=[\\]`"

    /** The key and its RModifier values, or null for a name that isn't a key. */
    fun parse(text: String): Pair<UInt, UInt>? {
        var modifiers = 0u
        var key: UInt? = null
        val parts = text.split('+')
        for ((i, part) in parts.withIndex()) {
            val word = part.trim().lowercase()
            val last = i == parts.size - 1
            when {
                !last && (word == "ctrl" || word == "control") -> modifiers = modifiers or CTRL
                !last && word == "shift" -> modifiers = modifiers or SHIFT
                !last && (word == "alt" || word == "option") -> modifiers = modifiers or ALT
                !last && (word == "meta" || word == "cmd" || word == "command" || word == "super" || word == "win") ->
                    modifiers = modifiers or SUPER
                last -> key = keyOf(part.trim())
                else -> return null
            }
        }
        return key?.let { it to modifiers }
    }

    private fun keyOf(name: String): UInt? {
        NAMED[name.lowercase()]?.let { return it }
        if (name.length != 1) return null
        val c = name[0].uppercaseChar()
        return when {
            c in 'A'..'Z' || c in '0'..'9' || c in PUNCTUATION -> c.code.toUInt()
            else -> null
        }
    }
}
