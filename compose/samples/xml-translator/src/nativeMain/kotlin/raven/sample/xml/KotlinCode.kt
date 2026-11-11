package raven.sample.xml

/**
 * Lines of code, indented 4 spaces a level from [depth]. A trailing comment goes after the first
 * line of what is written.
 */
internal class CodeWriter(var depth: Int = 0) {
    val lines = mutableListOf<String>()

    fun line(code: String, comment: String? = null) {
        code.lines().forEachIndexed { i, text ->
            lines += "    ".repeat(depth) + if (i == 0 && comment != null) "$text // $comment" else text
        }
    }

    /** `header {`, the body one level in, then `}`; or `header {}` when the body wrote nothing. */
    fun block(header: String, comment: String? = null, body: () -> Unit) {
        line("$header {", comment)
        val start = lines.size
        depth++
        body()
        depth--
        if (lines.size == start) {
            val opening = "$header {".lines().last()
            lines[start - 1] = lines[start - 1].replaceFirst(opening, "$opening}")
        } else {
            line("}")
        }
    }

    /** Lines written elsewhere, already indented. */
    fun addAll(written: List<String>) {
        lines += written
    }

    /** Inserts [codeLines] at [index], at this writer's [depth] then. */
    fun insert(index: Int, depthThen: Int, codeLines: List<String>) {
        lines.addAll(index, codeLines.map { "    ".repeat(depthThen) + it })
    }
}

/** A number as Kotlin writes it: `8` or `0.5`. */
internal fun number(value: Float): String =
    if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e9f) value.toInt().toString() else value.toString()

/** A Float literal: `1f`, `0.5f`. */
internal fun float(value: Float): String = "${number(value)}f"

internal fun kotlinString(text: String): String = buildString {
    append('"')
    for (c in text) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
    append('"')
}

/** A name as a lowerCamelCase Kotlin identifier: `file-path` becomes `filePath`, a keyword is quoted. */
internal fun identifier(name: String): String {
    val joined = words(name).let { words ->
        if (words.isEmpty()) return "value"
        words.first().replaceFirstChar { it.lowercase() } + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
    }
    val safe = if (joined.first().isDigit()) "v$joined" else joined
    return if (safe in keywords) "`$safe`" else safe
}

/** A name as a PascalCase Kotlin type or function name: `my-card` becomes `MyCard`. */
internal fun pascalCase(name: String): String {
    val joined = words(name).joinToString("") { it.replaceFirstChar(Char::uppercase) }.ifEmpty { "Widget" }
    return if (joined.first().isDigit()) "W$joined" else joined
}

private fun words(name: String) = name.split(Regex("[^A-Za-z0-9_]+")).filter { it.isNotEmpty() }

private val keywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
)
