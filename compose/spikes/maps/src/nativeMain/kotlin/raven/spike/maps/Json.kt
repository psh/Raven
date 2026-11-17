package raven.spike.maps

/**
 * Reads JSON into plain values: a `Map<String, Any?>`, a `List<Any?>`, a `String`, a `Double`, a
 * `Boolean` or null. Enough for a style file; no dependency for a spike.
 */
fun parseJson(text: String): Any? = JsonReader(text).run {
    val value = value()
    skipSpace()
    require(pos == text.length) { "JSON: text after the value at $pos" }
    value
}

private class JsonReader(val text: String) {
    var pos = 0

    fun skipSpace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    fun value(): Any? {
        skipSpace()
        require(pos < text.length) { "JSON: unexpected end" }
        return when (val c = text[pos]) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> word("true", true)
            'f' -> word("false", false)
            'n' -> word("null", null)
            else -> if (c == '-' || c.isDigit()) number() else error("JSON: unexpected '$c' at $pos")
        }
    }

    private fun word(word: String, value: Any?): Any? {
        require(text.startsWith(word, pos)) { "JSON: expected $word at $pos" }
        pos += word.length
        return value
    }

    private fun obj(): Map<String, Any?> {
        pos++
        val map = LinkedHashMap<String, Any?>()
        skipSpace()
        if (text[pos] == '}') return map.also { pos++ }
        while (true) {
            skipSpace()
            val key = string()
            skipSpace()
            require(text[pos] == ':') { "JSON: expected ':' at $pos" }
            pos++
            map[key] = value()
            skipSpace()
            when (text[pos++]) {
                ',' -> continue
                '}' -> return map
                else -> error("JSON: expected ',' or '}' at ${pos - 1}")
            }
        }
    }

    private fun array(): List<Any?> {
        pos++
        val list = ArrayList<Any?>()
        skipSpace()
        if (text[pos] == ']') return list.also { pos++ }
        while (true) {
            list += value()
            skipSpace()
            when (text[pos++]) {
                ',' -> continue
                ']' -> return list
                else -> error("JSON: expected ',' or ']' at ${pos - 1}")
            }
        }
    }

    private fun string(): String {
        require(text[pos] == '"') { "JSON: expected a string at $pos" }
        pos++
        val out = StringBuilder()
        while (true) {
            val c = text[pos++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> when (val e = text[pos++]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'u' -> out.append(text.substring(pos, pos + 4).toInt(16).toChar()).also { pos += 4 }
                    else -> out.append(e)
                }
                else -> out.append(c)
            }
        }
    }

    private fun number(): Double {
        val start = pos
        while (pos < text.length && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
        return text.substring(start, pos).toDouble()
    }
}
