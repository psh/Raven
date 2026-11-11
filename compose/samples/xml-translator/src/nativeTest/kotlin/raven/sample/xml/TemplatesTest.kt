package raven.sample.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemplatesTest {
    @Test
    fun theFieldOfXmlMdBecomesAFunction() {
        val window = el(
            "Window", "Title" to "Form", line = 1,
            children = listOf(
                el(
                    "Widget", "Name" to "Field", "Label" to "Name", "Hint" to "", "Wide" to "false", line = 2,
                    children = listOf(
                        el(
                            "HBox", "Spacing" to "8", line = 3,
                            children = listOf(
                                el("Label", "Text" to "{Label}:", "Width" to "{Wide ? 140 : 90}", line = 4),
                                el("TextInput", "Placeholder" to "{Hint}", "Slot.Fill" to "1", line = 5),
                                el("Children", line = 6),
                            ),
                        ),
                    ),
                ),
                el(
                    "VBox", "Spacing" to "8", line = 9,
                    children = listOf(
                        el("Field", "Label" to "Email", "Hint" to "you@example.com", "Name" to "email", line = 10),
                        el("Field", "Label" to "Password", "Wide" to "true", line = 11, children = listOf(el("Button", "Text" to "Show", line = 11))),
                    ),
                ),
            ),
        )
        val translation = translate(window)
        assertEquals(
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import raven.compose.foundation.OutlinedButton
            import raven.compose.foundation.Text
            import raven.compose.foundation.TextField
            import raven.compose.foundation.layout.Arrangement
            import raven.compose.foundation.layout.Column
            import raven.compose.foundation.layout.Row
            import raven.compose.foundation.layout.RowScope
            import raven.compose.foundation.layout.fillMaxHeight
            import raven.compose.foundation.layout.fillMaxSize
            import raven.compose.foundation.layout.fillMaxWidth
            import raven.compose.foundation.layout.width
            import raven.compose.ui.Modifier
            import raven.compose.ui.singleWindowApplication
            import raven.compose.ui.unit.dp

            @Composable
            fun Field(
                modifier: Modifier = Modifier,
                label: String = "Name",
                hint: String = "",
                wide: Boolean = false,
                content: @Composable RowScope.() -> Unit = {},
            ) {
                var textField1 by remember { mutableStateOf("") }
                Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${'$'}{label}:", modifier = Modifier.fillMaxHeight().width((if (wide) 140 else 90).dp))
                    TextField(
                        value = textField1,
                        onValueChange = { textField1 = it },
                        modifier = Modifier.fillMaxHeight().weight(1f),
                        placeholder = hint,
                    )
                    content()
                }
            }

            @Composable
            fun TranslatedUi() {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(modifier = Modifier.fillMaxWidth(), label = "Email", hint = "you@example.com")
                    Field(modifier = Modifier.fillMaxWidth(), label = "Password", wide = true) {
                        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxHeight()) { Text("Show") }
                    }
                }
            }

            fun main() = singleWindowApplication(title = "Form") {
                TranslatedUi()
            }

            """.trimIndent(),
            translation.code,
        )
        assertEquals(emptyList(), translation.warnings)
    }

    @Test
    fun parametersAreTypedFromTheirDefaults() {
        val translation = translate(
            el(
                "VBox",
                children = listOf(
                    widget(
                        "Gauge", "Shown" to "TRUE", "Level" to "0.5", "Steps" to "4", "Unit" to "%", "Ready" to "yes",
                        body = el("Label", "Text" to "{Level}{Unit}"),
                    ),
                    el("Gauge", "Shown" to "no", "Level" to "1", "Steps" to "2.5", "Unit" to "px", "Ready" to "no", line = 7),
                    el("Gauge", "Shown" to "maybe", line = 8),
                ),
            ),
        )
        assertTrue(
            "fun Gauge(\n    modifier: Modifier = Modifier,\n    shown: Boolean = true,\n    level: Float = 0.5f,\n    steps: Int = 4,\n" +
                "    unit: String = \"%\",\n    ready: String = \"yes\",\n) {" in translation.code,
            translation.code,
        )
        assertEquals(
            """
            Column {
                Gauge(
                    modifier = Modifier.fillMaxWidth(),
                    shown = false,
                    level = 1f,
                    steps = 2,
                    unit = "px",
                    ready = "no",
                )
                Gauge(modifier = Modifier.fillMaxWidth()) // raven-xml: Shown="maybe": not a boolean left out
            }
            """.trimIndent(),
            body(translation),
        )
        assertEquals(
            listOf("line 7: Steps=\"2.5\": not an integer; 2 is used", "line 8: Shown=\"maybe\": not a boolean"),
            translation.warnings.map { it.toString() },
        )
    }

    @Test
    fun childrenGoToSlotsByInto() {
        val translation = translate(
            el(
                "Overlay",
                children = listOf(
                    widget(
                        "Card", "Title" to "Untitled",
                        body = el(
                            "VBox",
                            children = listOf(
                                el("Label", "Text" to "{Title}"),
                                el("HBox", children = listOf(el("Children"))),
                                el("Children", "Name" to "footer"),
                            ),
                        ),
                    ),
                    el(
                        "Card", "Title" to "Hello",
                        children = listOf(
                            el("Button", "Text" to "OK", "Into" to "Footer", "Slot.HAlign" to "Right"),
                            el("Label", "Text" to "Body", "Slot.Fill" to "1"),
                            el("Label", "Text" to "Lost", "Into" to "header", line = 9),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(
            "fun Card(\n    modifier: Modifier = Modifier,\n    title: String = \"Untitled\",\n" +
                "    footer: @Composable ColumnScope.() -> Unit = {},\n    content: @Composable RowScope.() -> Unit = {},\n) {" in translation.code,
            translation.code,
        )
        assertTrue("    Column(modifier) {\n        Text(title, modifier = Modifier.fillMaxWidth())\n        Row(Modifier.fillMaxWidth()) {\n            content()\n        }\n        footer()\n" in translation.code, translation.code)
        assertEquals(
            """
            Box {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    title = "Hello",
                    footer = {
                        OutlinedButton(onClick = {}, modifier = Modifier.align(Alignment.End)) { Text("OK") }
                    },
                ) {
                    Text("Body", modifier = Modifier.fillMaxHeight().weight(1f))
                }
            }
            """.trimIndent(),
            body(translation),
        )
        assertEquals(listOf("line 9: <Card> has no <Children Name=\"header\"/>; <Label> is left out"), translation.warnings.map { it.toString() })
    }

    @Test
    fun theCallPlacesTheRootAndWins() {
        val translation = translate(
            el(
                "VBox",
                children = listOf(
                    widget(
                        "Tile", "Wide" to "false",
                        body = el("Label", "Slot.Padding" to "8", "Width" to "{Wide ? 200 : 100}", "Height" to "40", "Slot.HAlign" to "Left", text = "tile"),
                    ),
                    el("Tile"),
                    el("Tile", "Slot.HAlign" to "Fill", "Height" to "60", "Tooltip" to "a tile", "Material" to "Glass", line = 6),
                ),
            ),
        )
        // Written-out placing goes to each call; what depends on parameters stays in the function, before the call's.
        assertTrue("    Text(\"tile\", modifier = Modifier.width((if (wide) 200 else 100).dp).then(modifier))\n" in translation.code, translation.code)
        assertEquals(
            """
            Column {
                Tile(modifier = Modifier.align(Alignment.Start).padding(8.dp).height(40.dp))
                Tile(modifier = Modifier.fillMaxWidth().padding(8.dp).height(60.dp).tooltip("a tile")) // raven-xml: Material="Glass" left out
            }
            """.trimIndent(),
            body(translation),
        )
        assertEquals(listOf("line 6: Material=\"Glass\" has no equivalent on Tile"), translation.warnings.map { it.toString() })
    }

    @Test
    fun theTextInsideACallIsItsTextParameter() {
        val body = body(
            el(
                "VBox",
                children = listOf(
                    widget("Chip", "Text" to "", body = el("Button", "Text" to "{Text}")),
                    el("Chip", "Slot.HAlign" to "Center", text = "Tag"),
                ),
            ),
        )
        assertEquals(
            """
            Column {
                Chip(modifier = Modifier.align(Alignment.CenterHorizontally), text = "Tag")
            }
            """.trimIndent(),
            body,
        )
    }

    @Test
    fun aWidgetsRootHasOnlyFunctions() {
        val translation = translate(el("Widgets", children = listOf(widget("Line", body = el("Separator")))))
        assertEquals(
            """
            import androidx.compose.runtime.Composable
            import raven.compose.foundation.HorizontalDivider
            import raven.compose.ui.Modifier

            @Composable
            fun Line(modifier: Modifier = Modifier) {
                HorizontalDivider(modifier)
            }

            """.trimIndent(),
            translation.code,
        )
    }

    @Test
    fun ifBecomesKotlinOrIsDecidedWhenWrittenOut() {
        val body = body(
            el(
                "VBox",
                children = listOf(
                    el("Label", "If" to "false", text = "never"),
                    el("Label", "If" to "{x}", text = "outside a template, {x} is a text, and true"),
                    el(
                        "Repeat", "Count" to "2", "Var" to "i",
                        children = listOf(
                            el("Label", "If" to "{i == 1}", text = "second"),
                            el("If", "Test" to "{i > 0 && i < 5}", children = listOf(el("Separator"))),
                            el("If", "Test" to "0", children = listOf(el("Separator"))),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            """
            Column {
                // raven-xml: <Label> left out by If="false"
                Text("outside a template, {x} is a text, and true", modifier = Modifier.fillMaxWidth())
                repeat(2) { i ->
                    if (i == 1) {
                        Text("second", modifier = Modifier.fillMaxWidth())
                    }
                    if ((i > 0) && (i < 5)) {
                        HorizontalDivider(Modifier.fillMaxWidth())
                    }
                    // raven-xml: <If> left out by Test="0"
                }
            }
            """.trimIndent(),
            body,
        )
    }

    @Test
    fun loopsKeepTheirStateInTheirBody() {
        val body = body(
            widget(
                "List", "Items" to "a;b", "Count" to "2",
                body = el(
                    "VBox",
                    children = listOf(
                        el("ForEach", "Items" to "red|green|", "Var" to "color", "Index" to "n", children = listOf(el("Label", "Text" to "{n}: {color}"))),
                        el("ForEach", "Items" to "{Items}", "Separator" to ";", children = listOf(el("Label", "Text" to "{Item} at {Index}"))),
                        el("ForEach", "Items" to "", children = listOf(el("Label"))),
                        el("Repeat", "Count" to "{Count * 2}", children = listOf(el("Checkbox", "Text" to "Row {Index + 1}", "Checked" to "{Index % 2 == 0}"))),
                    ),
                ),
            ),
            function = "List",
        )
        assertEquals(
            """
            Column(modifier) {
                listOf("red", "green").forEachIndexed { n, color ->
                    Text("${'$'}{n}: ${'$'}{color}", modifier = Modifier.fillMaxWidth())
                }
                items.split(';').forEachIndexed { index, item ->
                    Text("${'$'}{item} at ${'$'}{index}", modifier = Modifier.fillMaxWidth())
                }
                emptyList<String>().forEachIndexed { index2, item2 ->
                    Text("", modifier = Modifier.fillMaxWidth())
                }
                repeat(count * 2) { index3 ->
                    var checkbox1 by remember { mutableStateOf((index3 % 2) == 0) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checkbox1, onCheckedChange = { checkbox1 = it })
                        Text("Row ${'$'}{index3 + 1}")
                    }
                }
            }
            """.trimIndent(),
            body,
        )
    }

    @Test
    fun aLoopOutsideATemplateComputesItsBraces() {
        assertEquals(
            """
            Column {
                repeat(3) { index ->
                    Text("Line ${'$'}{index + 1} of {Total}", modifier = Modifier.fillMaxWidth())
                }
            }
            """.trimIndent(),
            body(el("VBox", children = listOf(el("Repeat", "Count" to "3", children = listOf(el("Label", text = "Line {Index + 1} of {{Total}}")))))),
        )
    }

    @Test
    fun anExpressionThatFailsToParseIsATodo() {
        val translation = translate(
            el(
                "Widgets",
                children = listOf(
                    widget(
                        "Bad", "Size" to "1",
                        body = el("VBox", children = listOf(el("Label", "Text" to "{Size +}", "Width" to "{Missing}", line = 3), el("Label", "Text" to "{oops", line = 4))),
                    ),
                ),
            ),
        )
        assertEquals(
            """
            Column(modifier) {
                // TODO(raven-xml): Text="{Size +}": something is missing
                // TODO(raven-xml): Width="{Missing}": 'Missing' is not a parameter here
                Text("", modifier = Modifier.fillMaxWidth())
                // TODO(raven-xml): Text="{oops": '{' without its '}' (write "{{" for a brace)
                Text("", modifier = Modifier.fillMaxWidth())
            }
            """.trimIndent(),
            body(translation, function = "Bad"),
        )
        assertEquals(
            listOf(
                // (warnings come in the order the attributes are read: placing first)
                "line 3: Width=\"{Missing}\": in an expression: 'Missing' is not a parameter here",
                "line 3: Text=\"{Size +}\": in an expression: something is missing",
                "line 4: Text=\"{oops\": in an expression: '{' without its '}' (write \"{{\" for a brace)",
            ),
            translation.warnings.map { it.toString() },
        )
    }

    @Test
    fun instanceBecomesAComment() {
        val translation = translate(el("Widgets", children = listOf(widget("Row1", body = el("Label", "Text" to "row-{Instance}")))))
        assertTrue("Text(\"\", modifier = modifier) // raven-xml: Text=\"row-{Instance}\": {Instance} has no equivalent left out" in translation.code, translation.code)
        assertEquals(emptyList(), translation.warnings)
    }

    @Test
    fun whatATemplateCantDoIsAWarning() {
        val translation = translate(
            el(
                "VBox",
                children = listOf(
                    widget(
                        "Loop", "Primary" to "false", line = 2,
                        body = el("VBox", children = listOf(el("Button", "Style" to "{Primary ? 'Primary' : 'Default'}", text = "Go", line = 3), el("Loop", line = 4))),
                    ),
                    el("Children", line = 6),
                    el("Include", "Source" to "part.xml", line = 7),
                    widget("Text", body = el("Label"), line = 8),
                    widget("", body = el("Label"), line = 9),
                    el("Widget", "Name" to "Empty", line = 10),
                ),
            ),
        )
        assertTrue("OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(\"Go\") } // raven-xml: Style: an expression can't choose this; the default is used left out" in translation.code, translation.code)
        assertEquals(
            listOf(
                "line 8: <Widget Name=\"Text\">: this tag already exists",
                "line 9: <Widget> needs a Name: the tag it declares",
                "line 10: <Widget Name=\"Empty\"> is empty: it needs the element it stands for",
                "line 3: Style: an expression can't choose this; the default is used",
                "line 4: <Loop> uses itself, which a <Widget> can't",
                "line 6: <Children/> only means something inside a <Widget>",
                "line 7: <Include Source=\"part.xml\"> isn't translated: the file isn't read",
            ),
            translation.warnings.map { it.toString() },
        )
    }

    private fun el(
        tag: String,
        vararg attributes: Pair<String, String>,
        text: String = "",
        line: Int = 0,
        children: List<XmlElement> = emptyList(),
    ) = XmlElement(tag, mapOf(*attributes), text, line, children)

    private fun widget(name: String, vararg params: Pair<String, String>, body: XmlElement, line: Int = 0) =
        XmlElement("Widget", mapOf("Name" to name, *params), line = line, children = listOf(body))

    /** The body of `TranslatedUi()`, or of [function], unindented. */
    private fun body(root: XmlElement, function: String = "TranslatedUi"): String = body(translate(root), function)

    private fun body(translation: Translation, function: String = "TranslatedUi"): String =
        translation.code.substringAfter("fun $function(").substringAfter(") {\n").substringBefore("\n}\n").trimIndent()
}
