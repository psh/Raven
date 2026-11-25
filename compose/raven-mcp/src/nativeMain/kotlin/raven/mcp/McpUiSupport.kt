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
import kotlinx.cinterop.value
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.cinterop.toKString
import platform.posix.size_tVar
import raven.mcp.cinterop.RAccessibleInfo
import raven.mcp.cinterop.R_TRUE
import raven.mcp.cinterop.Raccessible_describe
import raven.mcp.cinterop.Raccessible_get_child
import raven.mcp.cinterop.Raccessible_get_child_count
import raven.mcp.cinterop.Raccessible_get_role_name
import raven.mcp.cinterop.Rwindow_get_title
import raven.mcp.cinterop.Rimage_encode_png
import raven.mcp.cinterop.Rpixels_free
import raven.mcp.cinterop.Rwindow_capture
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// What the UI tools of the Raven MCP library (UiTools.kt) and compose-raven's share: the arguments,
// the schemas, the key names and the screenshots, so both read and answer agents alike.

/**
 * A call's arguments, read with the UI tools' messages: `selector is required`, `depth must be a
 * whole number`. Each read throws IllegalArgumentException with the message for the agent.
 */
public class McpArguments private constructor(private val json: JsonObject) {
    public fun string(name: String): String? {
        val value = primitive(name, "a string") ?: return null
        if (!value.isString) throw IllegalArgumentException("$name must be a string")
        return value.content
    }

    public fun requireString(name: String): String = string(name) ?: throw IllegalArgumentException("$name is required")

    /** A whole number, 0 or more. */
    public fun int(name: String): Int? {
        val value = primitive(name, "a whole number") ?: return null
        val number = value.takeIf { !it.isString }?.intOrNull ?: throw IllegalArgumentException("$name must be a whole number")
        if (number < 0) throw IllegalArgumentException("$name must be 0 or more")
        return number
    }

    public fun boolean(name: String): Boolean? {
        val value = primitive(name, "true or false") ?: return null
        return value.takeIf { !it.isString }?.booleanOrNull ?: throw IllegalArgumentException("$name must be true or false")
    }

    private fun primitive(name: String, expected: String): JsonPrimitive? {
        val value: JsonElement = json[name] ?: return null
        if (value is JsonNull) return null
        return value as? JsonPrimitive ?: throw IllegalArgumentException("$name must be $expected")
    }

    public companion object {
        /** The arguments of a call, given as a JSON object. */
        public fun parse(argumentsJson: String): McpArguments {
            val parsed = try {
                io.modelcontextprotocol.kotlin.sdk.types.McpJson.parseToJsonElement(argumentsJson)
            } catch (e: Exception) {
                throw IllegalArgumentException("the arguments are not JSON")
            }
            return McpArguments(parsed as? JsonObject ?: throw IllegalArgumentException("the arguments are not a JSON object"))
        }
    }
}

/** Input schemas, as JSON Schema objects for McpToolSpec.inputSchemaJson. */
public object McpSchemas {
    public fun property(type: String, description: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    public fun objectOf(vararg properties: Pair<String, JsonObject>, required: List<String> = emptyList()): String =
        buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties.toMap()))
            if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }.toString()
}

/** A window as it is drawn, as PNG, for ui.screenshot. UI-thread only. */
public object McpScreenshots {
    /** The window's last frame, in device pixels; null when the window can't be captured. */
    public fun capture(window: UInt): ByteArray? = memScoped {
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

    /** A CallToolResult with [png] as its one image, for McpCall.replyJson. */
    @OptIn(ExperimentalEncodingApi::class)
    public fun replyJson(png: ByteArray): String = buildJsonObject {
        put("content", buildJsonArray {
            addJsonObject {
                put("type", "image")
                put("data", Base64.encode(png))
                put("mimeType", "image/png")
            }
        })
    }.toString()
}

/**
 * An accessible, read with Raccessible_describe: its texts copied, since the driver reuses them.
 * A widget's accessible is its id; one that only lays others out has none. Shared with
 * compose-raven's UI tools, which read their nodes' widgets with it.
 */
public class McpAccessible private constructor(
    public val role: String,
    public val name: String,
    public val description: String,
    public val value: String,
    public val number: Double,
    public val minimum: Double,
    public val maximum: Double,
    public val states: UInt,
    public val x: Float, public val y: Float, public val width: Float, public val height: Float,
    public val window: UInt,
) {
    /** What it shows, as the driver's text= reads it: a field's value, else its name. */
    public val shown: String get() = if (role in VALUE_ROLES) value else name

    public fun has(state: UInt): Boolean = states and state != 0u

    /** As the driver's messages name it: role=button name="Save". */
    public fun words(): String = if (name.isEmpty()) "role=$role" else "role=$role name=${quote(name)}"

    /** Its line of the outline, with no indent: `button "Add" [disabled]`. */
    public fun line(): String = buildString {
        append(role)
        if (name.isNotEmpty()) append(' ').append(quote(clip(name)))
        if (shown != name && shown.isNotEmpty()) append(" text=").append(quote(clip(shown)))
        val named = McpAccessibleStates.NAMES.filter { has(it.first) }.map { it.second }
        if (named.isNotEmpty()) append(" [").append(named.joinToString(" ")).append(']')
    }

    /** ui.find's reply. */
    public fun details(): String = buildString {
        appendLine(words())
        if (role in VALUE_ROLES) appendLine("text: ${quote(value)}")
        else if (value.isNotEmpty()) appendLine("value: ${quote(value)}")
        if (has(McpAccessibleStates.HAS_RANGE)) appendLine("number: ${format(number)} (from ${format(minimum)} to ${format(maximum)})")
        if (description.isNotEmpty()) appendLine("description: ${quote(description)}")
        val named = McpAccessibleStates.NAMES.filter { has(it.first) }.map { it.second }
        appendLine("states: ${named.ifEmpty { listOf("none") }.joinToString(" ")}")
        val title = Rwindow_get_title(window)?.toKString() ?: ""
        append("bounds: x=${format(x)} y=${format(y)} width=${format(width)} height=${format(height)}, logical units in the window ${quote(title)}")
    }

    public companion object {
        private val VALUE_ROLES = setOf("text-field", "document", "combo-box", "spin-button")

        /** The accessible, read now; null when there is none. */
        public fun of(accessible: ULong): McpAccessible? = memScoped {
            val info = alloc<RAccessibleInfo>()
            if (Raccessible_describe(accessible, info.ptr) != R_TRUE) return null
            McpAccessible(
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

        /** [text] in quotes, as a selector writes a value. */
        public fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private fun format(value: Double): String =
            if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

        private fun format(value: Float): String = format(value.toDouble())

        private fun clip(text: String): String {
            val line = text.substringBefore('\n')
            return if (line.length <= 80 && line.length == text.length) text else line.take(80) + "…"
        }
    }
}

/** RAccessibleState, as raven_driver.h has it. */
public object McpAccessibleStates {
    public val FOCUSED: UInt = 1u shl 1
    public val DISABLED: UInt = 1u shl 2
    public val CHECKED: UInt = 1u shl 3
    public val EXPANDED: UInt = 1u shl 5
    public val EXPANDABLE: UInt = 1u shl 6
    public val SELECTED: UInt = 1u shl 7
    public val HAS_RANGE: UInt = 1u shl 12

    /** The states a selector names, in the order the outline shows them. */
    public val NAMES: List<Pair<UInt, String>> = listOf(
        FOCUSED to "focused", CHECKED to "checked", SELECTED to "selected", EXPANDED to "expanded", DISABLED to "disabled",
    )
}

/**
 * The outline ui.tree and the actions reply with: one line per accessible, indented under its
 * parent, each line a selector (compose/docs/mcp.md, "The outline"). At most [MAX_LINES] lines;
 * past them it counts what it leaves out. compose-raven's UI tools add their nodes' lines to one.
 */
public class McpOutline {
    private val lines = ArrayList<String>()
    private var left = 0

    /** Adds [line] at [level] of indent, or counts it as left out past [MAX_LINES]. */
    public fun add(level: Int, line: String) {
        if (lines.size < MAX_LINES) lines += "  ".repeat(level) + "- " + line else left++
    }

    override fun toString(): String {
        if (lines.isEmpty()) return "no window is open"
        val all = if (left > 0) lines + "- … $left more accessibles left out: narrow it with under (a selector) or depth" else lines
        return all.joinToString("\n")
    }

    public companion object {
        public const val MAX_LINES: Int = 500

        /** The outline of the driver's tree under [roots], [depth] levels down (all, for null). */
        public fun of(roots: List<ULong>, depth: Int?): String {
            val outline = McpOutline()
            fun walk(accessible: ULong, level: Int) {
                val info = McpAccessible.of(accessible) ?: return
                outline.add(level, info.line())
                if (depth != null && level >= depth) return
                // (a closed menu's items are there to be found, not to be read)
                if (info.has(McpAccessibleStates.EXPANDABLE) && !info.has(McpAccessibleStates.EXPANDED)) return
                val children = Raccessible_get_child_count(accessible)
                for (i in 0 until children) walk(Raccessible_get_child(accessible, i), level + 1)
            }
            for (root in roots) walk(root, 0)
            return outline.toString()
        }
    }
}

/**
 * Key names as the UI tools take them, and Raven's shortcuts write them: "Enter", "ctrl+s",
 * "shift+Tab". Shared with compose-raven's UI tools.
 */
public object McpKeys {
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

    /** The key (an RKey value) and its RModifier values, or null for a name that isn't a key. */
    public fun parse(text: String): Pair<UInt, UInt>? {
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
