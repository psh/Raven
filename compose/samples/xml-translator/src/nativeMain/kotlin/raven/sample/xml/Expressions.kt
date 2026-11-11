package raven.sample.xml

// Raven's {...} expression language (src/xml/xml_components.c), compiled to typed Kotlin. Raven's
// values are texts that are also numbers when they read as one; Kotlin's are static, so each
// expression gets a type, and Raven's conversions are written out where it needs them.

/** The static type of a translated expression. */
internal enum class KType { Bool, Int, Float, Str }

/** A Kotlin expression and its type. [atomic] when it needs no parentheses as an operand. */
internal class KExpr(val code: String, val type: KType, val atomic: Boolean = false) {
    val parenthesized: String get() = if (atomic) code else "($code)"
    val numeric: Boolean get() = type == KType.Int || type == KType.Float
}

/** A variable an expression can name: a parameter of a `<Widget>`, or a loop's. */
internal class Variable(val code: String, val type: KType)

/** An expression that Raven itself would refuse; the message is Raven's. */
internal class ExpressionException(message: String) : Exception(message)

/** An expression that names `{Instance}`, which has no Kotlin equivalent. */
internal class InstanceException : Exception("{Instance} has no equivalent")

/**
 * Compiles one expression, the text between two braces. [find] looks a name up (case-insensitively),
 * [instance] says whether `Instance` is defined here (inside a `<Widget>`), and [use] records the
 * imports it needs (`floor`, `ceil`, `abs`).
 */
internal fun compileExpression(
    text: String,
    find: (String) -> Variable?,
    instance: Boolean,
    use: (String) -> Unit,
): KExpr = ExpressionParser(text, find, instance, use).parse()

/** Raven's grammar, with its precedence: `?:`, `||`, `&&`, one comparison, `+ -`, `* / %`, `! -`. */
private class ExpressionParser(
    private val text: String,
    private val find: (String) -> Variable?,
    private val instance: Boolean,
    private val use: (String) -> Unit,
) {
    private var pos = 0
    private var depth = 0

    fun parse(): KExpr {
        val value = expression()
        space()
        if (pos < text.length) fail("something is left after the end")
        return value
    }

    private fun expression(): KExpr {
        if (++depth > 64) fail("nested too deeply")
        val condition = or()
        val result = if (take("?")) {
            val yes = expression()
            if (!take(":")) fail("expected ':'")
            val no = expression()
            val type = unify(yes.type, no.type)
            KExpr("if (${condition.toBool().code}) ${yes.to(type).code} else ${no.to(type).code}", type)
        } else {
            condition
        }
        depth--
        return result
    }

    private fun or(): KExpr {
        var left = and()
        while (take("||")) left = KExpr("${left.toBool().parenthesized} || ${and().toBool().parenthesized}", KType.Bool)
        return left
    }

    private fun and(): KExpr {
        var left = compare()
        while (take("&&")) left = KExpr("${left.toBool().parenthesized} && ${compare().toBool().parenthesized}", KType.Bool)
        return left
    }

    private fun compare(): KExpr {
        val left = sum()
        val op = listOf("==", "!=", "<=", ">=", "<", ">").firstOrNull { take(it) } ?: return left
        val right = sum()
        val (a, b) = when {
            left.numeric && right.numeric && left.type != right.type -> left.to(KType.Float) to right.to(KType.Float)
            left.type == right.type -> left to right
            else -> left.to(KType.Str) to right.to(KType.Str)
        }
        return KExpr("${a.parenthesized} $op ${b.parenthesized}", KType.Bool)
    }

    private fun sum(): KExpr {
        var left = product()
        while (true) {
            space()
            val op = text.getOrNull(pos)?.takeIf { it == '+' || it == '-' } ?: return left
            pos++
            val right = product()
            left = if (op == '+' && !(left.numeric && right.numeric)) {
                // Raven joins the texts unless both are numbers.
                KExpr("${left.to(KType.Str).parenthesized} + ${right.to(KType.Str).parenthesized}", KType.Str)
            } else {
                arithmetic(left, op, right)
            }
        }
    }

    private fun product(): KExpr {
        var left = unary()
        while (true) {
            space()
            val op = text.getOrNull(pos)?.takeIf { it == '*' || it == '/' || it == '%' } ?: return left
            pos++
            left = arithmetic(left, op, unary())
        }
    }

    private fun arithmetic(left: KExpr, op: Char, right: KExpr): KExpr {
        val a = left.toNumber()
        val b = right.toNumber()
        // Raven's numbers are doubles: a division is never an integer one.
        if (op == '/') return KExpr("${a.to(KType.Float).parenthesized} / ${b.parenthesized}", KType.Float)
        val type = if (a.type == KType.Int && b.type == KType.Int) KType.Int else KType.Float
        return KExpr("${a.parenthesized} $op ${b.parenthesized}", type)
    }

    private fun unary(): KExpr {
        space()
        val c = text.getOrNull(pos)
        if (c == '!' && text.getOrNull(pos + 1) != '=') {
            pos++
            if (++depth > 64) fail("nested too deeply")
            val value = unary().toBool()
            depth--
            return KExpr("!${value.parenthesized}", KType.Bool, atomic = true)
        }
        if (c == '-') {
            pos++
            if (++depth > 64) fail("nested too deeply")
            val value = unary().toNumber()
            depth--
            return KExpr("-${value.parenthesized}", value.type, atomic = true)
        }
        return primary()
    }

    private fun primary(): KExpr {
        space()
        val c = text.getOrNull(pos) ?: fail("something is missing")
        when {
            c == '(' -> {
                pos++
                val value = expression()
                if (!take(")")) fail("expected ')'")
                return value
            }
            c == '\'' || c == '"' -> {
                val close = text.indexOf(c, pos + 1)
                if (close < 0) fail("a text is not closed")
                val value = text.substring(pos + 1, close)
                pos = close + 1
                // (a quoted text is a text, even when it reads as a number)
                return KExpr(kotlinString(value), KType.Str, atomic = true)
            }
            c.isDigit() || c == '.' -> return number()
            c.isLetter() || c == '_' -> {
                val start = pos
                while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_' || text[pos] == '.')) pos++
                val name = text.substring(start, pos)
                space()
                if (text.getOrNull(pos) == '(') {
                    pos++
                    return call(name, arguments())
                }
                if (name.equals("true", ignoreCase = true)) return KExpr("true", KType.Bool, atomic = true)
                if (name.equals("false", ignoreCase = true)) return KExpr("false", KType.Bool, atomic = true)
                find(name)?.let { return KExpr(it.code, it.type, atomic = true) }
                if (instance && name.equals("Instance", ignoreCase = true)) throw InstanceException()
                fail("'$name' is not a parameter here")
            }
            else -> fail("unexpected character")
        }
    }

    private fun number(): KExpr {
        val match = Regex("""\d*\.?\d*(?:[eE][+-]?\d+)?""").matchAt(text, pos)?.value.orEmpty()
        val value = match.toDoubleOrNull() ?: fail("a number is wrong")
        pos += match.length
        val integral = '.' !in match && 'e' !in match.lowercase() && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()
        return if (integral) KExpr(value.toInt().toString(), KType.Int, atomic = true) else KExpr(float(value.toFloat()), KType.Float, atomic = true)
    }

    private fun arguments(): List<KExpr> {
        val args = mutableListOf<KExpr>()
        if (take(")")) return args
        do {
            if (args.size == 4) fail("too many arguments")
            args += expression()
        } while (take(","))
        if (!take(")")) fail("expected ')'")
        return args
    }

    private fun call(name: String, args: List<KExpr>): KExpr {
        val function = name.lowercase()
        val one = args.singleOrNull()
        if ((function == "min" || function == "max") && args.size == 2) {
            val type = if (args.all { it.type == KType.Int }) KType.Int else KType.Float
            return KExpr("${function}Of(${args[0].toNumber().to(type).code}, ${args[1].toNumber().to(type).code})", type, atomic = true)
        }
        if (function == "default" && args.size == 2) {
            // (only a text can be empty: anything else is its own value)
            return if (args[0].type != KType.Str) args[0] else {
                KExpr("${args[0].parenthesized}.ifEmpty { ${args[1].to(KType.Str).code} }", KType.Str, atomic = true)
            }
        }
        if (one == null) fail("unknown function, or wrong number of arguments")
        val text = { one.to(KType.Str).parenthesized }
        return when (function) {
            "round" -> rounded(one) { "floor(${it.code} + 0.5f)".also { use("floor") } }
            "floor" -> rounded(one) { "floor(${it.code})".also { use("floor") } }
            "ceil" -> rounded(one) { "ceil(${it.code})".also { use("ceil") } }
            "abs" -> one.toNumber().let { use("abs"); KExpr("abs(${it.code})", it.type, atomic = true) }
            "len" -> KExpr("${text()}.length", KType.Int, atomic = true)
            "upper" -> KExpr("${text()}.uppercase()", KType.Str, atomic = true)
            "lower" -> KExpr("${text()}.lowercase()", KType.Str, atomic = true)
            "empty" -> KExpr("${text()}.isEmpty()", KType.Bool, atomic = true)
            else -> fail("unknown function, or wrong number of arguments")
        }
    }

    /** An integer is its own floor, ceiling and rounding; anything else is rounded as a Float. */
    private fun rounded(value: KExpr, round: (KExpr) -> String): KExpr {
        val number = value.toNumber()
        return if (number.type == KType.Int) number else KExpr(round(number.to(KType.Float)), KType.Float, atomic = true)
    }

    private fun unify(a: KType, b: KType): KType = when {
        a == b -> a
        (a == KType.Int || a == KType.Float) && (b == KType.Int || b == KType.Float) -> KType.Float
        else -> KType.Str
    }

    private fun space() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun take(token: String): Boolean {
        space()
        if (!text.startsWith(token, pos)) return false
        pos += token.length
        return true
    }

    private fun fail(message: String): Nothing = throw ExpressionException(message)
}

/** A number, as Raven reads one: a Bool is 1 or 0, a text its number or 0. */
internal fun KExpr.toNumber(): KExpr = when (type) {
    KType.Int, KType.Float -> this
    KType.Bool -> KExpr("if ($code) 1 else 0", KType.Int)
    KType.Str -> KExpr("$parenthesized.toFloatOrNull() ?: 0f", KType.Float)
}

/** A truth, as Raven reads one: a number that isn't 0, a text that isn't empty. */
internal fun KExpr.toBool(): KExpr = when (type) {
    KType.Bool -> this
    KType.Int -> KExpr("$parenthesized != 0", KType.Bool)
    KType.Float -> KExpr("$parenthesized != 0f", KType.Bool)
    KType.Str -> KExpr("$parenthesized.isNotEmpty()", KType.Bool, atomic = true)
}

/** This value as [target]. */
internal fun KExpr.to(target: KType): KExpr = when {
    type == target -> this
    // (a number or truth written out is written out as text too: 1 becomes "1")
    target == KType.Str && Regex("""-?\d+|-?\d+(\.\d+)?(E-?\d+)?f|true|false""").matches(code) ->
        KExpr(kotlinString(code.removeSuffix("f")), KType.Str, atomic = true)
    target == KType.Str -> KExpr("$parenthesized.toString()", KType.Str, atomic = true)
    target == KType.Bool -> toBool()
    target == KType.Float -> {
        val number = toNumber()
        when {
            number.type == KType.Float -> number
            number.atomic && number.code.toIntOrNull() != null -> KExpr("${number.code}f", KType.Float, atomic = true)
            else -> KExpr("${number.parenthesized}.toFloat()", KType.Float, atomic = true)
        }
    }
    else -> {
        val number = toNumber()
        if (number.type == KType.Int) number else KExpr("${number.parenthesized}.toInt()", KType.Int, atomic = true)
    }
}

/** A piece of an attribute value: literal text, or the text of one `{...}` expression. */
internal sealed interface Segment {
    data class Text(val text: String) : Segment
    data class Expression(val source: String) : Segment
}

/**
 * Splits an attribute value as Raven does where expressions are computed: `{{` and `}}` are
 * braces, and a quoted `}` doesn't close an expression.
 *
 * @throws ExpressionException for a `{` without its `}`.
 */
internal fun segments(value: String): List<Segment> {
    val out = mutableListOf<Segment>()
    val literal = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            (c == '{' || c == '}') && value.getOrNull(i + 1) == c -> {
                literal.append(c)
                i += 2
            }
            c == '{' -> {
                var close = i + 1
                var quote: Char? = null
                while (close < value.length && (quote != null || value[close] != '}')) {
                    if (quote != null && value[close] == quote) quote = null
                    else if (quote == null && (value[close] == '\'' || value[close] == '"')) quote = value[close]
                    close++
                }
                if (close >= value.length) throw ExpressionException("'{' without its '}' (write \"{{\" for a brace)")
                if (literal.isNotEmpty()) out += Segment.Text(literal.toString()).also { literal.clear() }
                out += Segment.Expression(value.substring(i + 1, close))
                i = close + 1
            }
            else -> {
                literal.append(c)
                i++
            }
        }
    }
    if (literal.isNotEmpty() || out.isEmpty()) out += Segment.Text(literal.toString())
    return out
}

/** A string template of literal texts and expressions, each expression as `${...}`. */
internal fun stringTemplate(parts: List<Pair<String?, KExpr?>>): String = buildString {
    append('"')
    for ((text, expression) in parts) {
        if (text != null) append(kotlinString(text).removeSurrounding("\""))
        // (a template turns any value into its text)
        if (expression != null) append("\${${expression.code}}")
    }
    append('"')
}
