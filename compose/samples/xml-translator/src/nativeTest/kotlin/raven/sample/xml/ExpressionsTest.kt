package raven.sample.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExpressionsTest {
    private val vars = mapOf(
        "title" to Variable("title", KType.Str),
        "wide" to Variable("wide", KType.Bool),
        "count" to Variable("count", KType.Int),
        "ratio" to Variable("ratio", KType.Float),
    )

    private fun compile(text: String, instance: Boolean = true): String =
        compileExpression(text, { vars[it.lowercase()] }, instance) {}.let { "${it.code} : ${it.type}" }

    @Test
    fun ravensTypingIsWrittenOutInKotlin() {
        val cases = listOf(
            "Wide ? 452 : 220" to "if (wide) 452 else 220 : Int",
            // Raven's numbers are doubles: a division is never an integer one.
            "7 / 2" to "7f / 2 : Float",
            "Count % 3" to "count % 3 : Int",
            "Count + Ratio" to "count + ratio : Float",
            "(Count + 1) * 2" to "(count + 1) * 2 : Int",
            "-Count" to "-count : Int",
            // + joins texts unless both sides are numbers; a quoted text is a text.
            "Title + Count" to "title + count.toString() : Str",
            "Count + 'px'" to "count.toString() + \"px\" : Str",
            "'12' + 1" to "\"12\" + \"1\" : Str",
            "Wide + 1" to "wide.toString() + \"1\" : Str",
            "Title * 2" to "(title.toFloatOrNull() ?: 0f) * 2 : Float",
            // A comparison is numeric between numbers, and between texts otherwise.
            "Count == Ratio" to "count.toFloat() == ratio : Bool",
            "Title == 'a'" to "title == \"a\" : Bool",
            "Title < Count" to "title < count.toString() : Bool",
            // Truth: a number that isn't 0, a text that isn't empty.
            "!Wide" to "!wide : Bool",
            "Wide && Count" to "wide && (count != 0) : Bool",
            "Title || Ratio" to "title.isNotEmpty() || (ratio != 0f) : Bool",
            "TRUE" to "true : Bool",
            "count" to "count : Int",
            "Wide ? 'yes' : 0" to "if (wide) \"yes\" else \"0\" : Str",
            "Count > 1 ? Count : Ratio" to "if (count > 1) count.toFloat() else ratio : Float",
        )
        for ((expression, kotlin) in cases) assertEquals(kotlin, compile(expression), expression)
    }

    @Test
    fun functions() {
        val cases = listOf(
            // Raven rounds halves up: floor(x + 0.5), not Kotlin's round.
            "round(Ratio)" to "floor(ratio + 0.5f) : Float",
            "round(Count)" to "count : Int",
            "floor(Count / 2)" to "floor(count.toFloat() / 2) : Float",
            "ceil(Ratio)" to "ceil(ratio) : Float",
            "abs(-Ratio)" to "abs(-ratio) : Float",
            "min(Count, 2)" to "minOf(count, 2) : Int",
            "max(Count, Ratio)" to "maxOf(count.toFloat(), ratio) : Float",
            "len(Title) > 3" to "title.length > 3 : Bool",
            "upper(Title)" to "title.uppercase() : Str",
            "lower(Count)" to "count.toString().lowercase() : Str",
            "empty(Title)" to "title.isEmpty() : Bool",
            "default(Title, 'none')" to "title.ifEmpty { \"none\" } : Str",
            // (only a text can be empty)
            "default(Count, 5)" to "count : Int",
        )
        for ((expression, kotlin) in cases) assertEquals(kotlin, compile(expression), expression)
    }

    @Test
    fun whatRavenRefusesIsAnError() {
        val cases = listOf(
            "Count >" to "something is missing",
            "missing" to "'missing' is not a parameter here",
            "foo(1)" to "unknown function, or wrong number of arguments",
            "round(1, 2)" to "unknown function, or wrong number of arguments",
            "'open" to "a text is not closed",
            "1 == 2 == 3" to "something is left after the end",
            "Wide ? 1" to "expected ':'",
            "(1 + 2" to "expected ')'",
            "@" to "unexpected character",
        )
        for ((expression, message) in cases) {
            assertEquals(message, assertFailsWith<ExpressionException>(expression) { compile(expression) }.message, expression)
        }
    }

    @Test
    fun instanceIsDefinedOnlyInsideAWidget() {
        assertFailsWith<InstanceException> { compile("'row' + Instance") }
        assertEquals("'Instance' is not a parameter here", assertFailsWith<ExpressionException> { compile("Instance", instance = false) }.message)
    }

    @Test
    fun valuesSplitIntoTextAndExpressions() {
        assertEquals(listOf(Segment.Text("a{b}c")), segments("a{{b}}c"))
        assertEquals(listOf(Segment.Expression("x")), segments("{x}"))
        assertEquals(listOf(Segment.Text("pre "), Segment.Expression("x"), Segment.Text(" post")), segments("pre {x} post"))
        // (a quoted brace doesn't close the expression)
        assertEquals(listOf(Segment.Expression("'}'")), segments("{'}'}"))
        assertEquals(
            "'{' without its '}' (write \"{{\" for a brace)",
            assertFailsWith<ExpressionException> { segments("{oops") }.message,
        )
    }
}
