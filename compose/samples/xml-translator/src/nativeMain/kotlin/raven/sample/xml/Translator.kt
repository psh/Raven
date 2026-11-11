package raven.sample.xml

/** Generated compose-raven source, and what couldn't be translated faithfully. */
internal data class Translation(val code: String, val warnings: List<TranslationWarning>)

/** One thing the translation left out or changed. [column] is 0 when only the line is known. */
internal data class TranslationWarning(val line: Int, val message: String, val column: Int = 0) {
    override fun toString(): String = if (column > 0) "line $line, column $column: $message" else "line $line: $message"
}

/**
 * Translates a Raven XML text: parsed with [parseRavenXml] (Raven running, UI thread), then
 * [translate]d. A text that isn't well-formed gives no code and its parse error as the warning.
 */
internal fun translate(xml: String): Translation {
    val root = try {
        parseRavenXml(xml)
    } catch (e: XmlParseException) {
        return Translation("", listOf(TranslationWarning(e.line, e.reason, e.column)))
    }
    return translate(root)
}

/**
 * Translates a Raven XML tree into one compose-raven file: its imports, `@Composable fun
 * TranslatedUi()` with the hoisted state at the top, then `main()` when the root is `<Window>`.
 * Nothing is refused: what has no equivalent becomes a TODO or a comment, and a warning.
 */
internal fun translate(root: XmlElement): Translation = Translator().run(root)

/** The tags of the phase 1 vocabulary, with the aliases of `docs/XML.md`. */
private enum class Tag(vararg val names: String) {
    Window("Window"),
    VBox("VBox", "VerticalBox"),
    HBox("HBox", "HorizontalBox"),
    Overlay("Overlay"),
    ScrollBox("ScrollBox"),
    Splitter("Splitter"),
    Label("Label", "Text", "TextBlock"),
    Button("Button"),
    Checkbox("Checkbox"),
    TextInput("TextInput", "TextBox", "EditableText"),
    TextEditor("TextEditor", "TextArea"),
    Slider("Slider"),
    ProgressBar("ProgressBar"),
    Separator("Separator"),
    Spacer("Spacer");

    companion object {
        // (SizeBox's alias Box is Raven's SizeBox, not Overlay: it isn't in the vocabulary)
        private val byName = entries.flatMap { tag -> tag.names.map { it.lowercase() to tag } }.toMap()

        fun of(name: String): Tag? = byName[name.lowercase()]
    }
}

/** What the parent does with a child's slot, which decides the child's Modifier. */
private enum class Parent { Column, Row, Box, VerticalScroll, HorizontalScroll, Splitter, Window, None }

private val actionNames = arrayOf("Action", "OnClicked", "OnChanged", "OnCommitted", "OnActivated")

private val imports = mapOf(
    "Composable" to "androidx.compose.runtime",
    "getValue" to "androidx.compose.runtime",
    "setValue" to "androidx.compose.runtime",
    "mutableStateOf" to "androidx.compose.runtime",
    "remember" to "androidx.compose.runtime",
    "Button" to "raven.compose.foundation",
    "Checkbox" to "raven.compose.foundation",
    "CodeEditor" to "raven.compose.foundation",
    "HorizontalDivider" to "raven.compose.foundation",
    "LinearProgressIndicator" to "raven.compose.foundation",
    "OutlinedButton" to "raven.compose.foundation",
    "RadioButton" to "raven.compose.foundation",
    "Slider" to "raven.compose.foundation",
    "Switch" to "raven.compose.foundation",
    "Text" to "raven.compose.foundation",
    "TextButton" to "raven.compose.foundation",
    "TextField" to "raven.compose.foundation",
    "VerticalDivider" to "raven.compose.foundation",
    "Arrangement" to "raven.compose.foundation.layout",
    "Box" to "raven.compose.foundation.layout",
    "Column" to "raven.compose.foundation.layout",
    "HorizontalScroll" to "raven.compose.foundation.layout",
    "HorizontalSplitter" to "raven.compose.foundation.layout",
    "Row" to "raven.compose.foundation.layout",
    "Spacer" to "raven.compose.foundation.layout",
    "VerticalScroll" to "raven.compose.foundation.layout",
    "VerticalSplitter" to "raven.compose.foundation.layout",
    "fillMaxHeight" to "raven.compose.foundation.layout",
    "fillMaxSize" to "raven.compose.foundation.layout",
    "fillMaxWidth" to "raven.compose.foundation.layout",
    "height" to "raven.compose.foundation.layout",
    "heightIn" to "raven.compose.foundation.layout",
    "padding" to "raven.compose.foundation.layout",
    "size" to "raven.compose.foundation.layout",
    "width" to "raven.compose.foundation.layout",
    "widthIn" to "raven.compose.foundation.layout",
    "Alignment" to "raven.compose.ui",
    "Modifier" to "raven.compose.ui",
    "singleWindowApplication" to "raven.compose.ui",
    "tooltip" to "raven.compose.ui",
    "dp" to "raven.compose.ui.unit",
    "sp" to "raven.compose.ui.unit",
)

/** One run of the translator: the state, the imports and the warnings it collects. */
private class Translator {
    private val out = CodeWriter()
    private val state = mutableListOf<String>()
    private val stateNames = mutableSetOf<String>()
    private val used = mutableSetOf<String>()
    private val warnings = mutableListOf<TranslationWarning>()

    fun run(root: XmlElement): Translation {
        use("Composable")
        var main: String? = null
        if (Tag.of(root.tag) == Tag.Window) {
            main = window(root)
        } else {
            element(root, Parent.None, null)
        }
        val code = buildString {
            for (import in used.map { "${imports.getValue(it)}.$it" }.sorted()) append("import $import\n")
            append("\n@Composable\nfun TranslatedUi() {\n")
            for (declaration in state) append("    $declaration\n")
            for (line in out.lines) append(if (line.isEmpty()) "\n" else "    $line\n")
            append("}\n")
            if (main != null) append("\n$main")
        }
        return Translation(code, warnings.toList())
    }

    // ---- the window ----

    /** Emits the window's content, and returns `main()`. */
    private fun window(window: XmlElement): String {
        val attrs = Attrs(window)
        val args = listOfNotNull(
            attrs["Title"]?.let { "title = ${kotlinString(it)}" },
            attrs["Width"]?.let { attrs.int("Width", it)?.let { width -> "width = $width" } },
            attrs["Height"]?.let { attrs.int("Height", it)?.let { height -> "height = $height" } },
        )
        attrs.reportUnused(window, "singleWindowApplication")
        var content = false
        for (child in window.children) {
            when {
                child.isComment -> comment(child)
                child.tag.equals("Shortcut", ignoreCase = true) || child.tag.equals("Island", ignoreCase = true) ||
                    child.tag.equals("Widget", ignoreCase = true) -> todo(child, "<${child.tag}> has no composable yet")
                content -> todo(child, "a window has one content widget; <${child.tag}> is a second one")
                else -> {
                    content = true
                    element(child, Parent.Window, null)
                }
            }
        }
        use("singleWindowApplication")
        return "fun main() = singleWindowApplication(${args.joinToString(", ")}) {\n    TranslatedUi()\n}\n"
            .replace("singleWindowApplication() {", "singleWindowApplication {")
    }

    // ---- elements ----

    /** Emits [element] as the child of [parent]; [radio] is its place when it is a radio button. */
    private fun element(element: XmlElement, parent: Parent, radio: RadioChoice?) {
        if (element.isComment) return comment(element)
        val tag = Tag.of(element.tag) ?: return todo(element, "<${element.tag}> has no composable yet")
        if (tag == Tag.Window) return todo(element, "<Window> is only a root")
        val attrs = Attrs(element)
        if (parent == Parent.Window && !windowContentFills(attrs)) {
            // The window places its content by alignment, which needs a BoxScope.
            use("Box", "Modifier", "fillMaxSize")
            out.block("Box(Modifier.fillMaxSize())") { widget(element, tag, attrs, Parent.Box, radio) }
        } else {
            widget(element, tag, attrs, parent, radio)
        }
    }

    private fun widget(element: XmlElement, tag: Tag, attrs: Attrs, parent: Parent, radio: RadioChoice?) {
        val modifier = modifier(element, attrs, parent)
        when (tag) {
            Tag.VBox -> container(element, attrs, "Column", modifier, spacing(attrs, "verticalArrangement"), Parent.Column)
            Tag.HBox -> container(element, attrs, "Row", modifier, spacing(attrs, "horizontalArrangement"), Parent.Row)
            Tag.Overlay -> container(element, attrs, "Box", modifier, null, Parent.Box)
            Tag.ScrollBox -> {
                val horizontal = orientation(attrs, vertical = true) == "horizontal"
                if (attrs["ScrollBoth"]?.let { attrs.bool("ScrollBoth", it) } == true) {
                    warn(element, "ScrollBoth: scrolling both ways has no equivalent; it scrolls one way")
                }
                if (horizontal) {
                    container(element, attrs, "HorizontalScroll", modifier, spacing(attrs, "horizontalArrangement"), Parent.HorizontalScroll)
                } else {
                    container(element, attrs, "VerticalScroll", modifier, spacing(attrs, "verticalArrangement"), Parent.VerticalScroll)
                }
            }
            Tag.Splitter -> {
                val name = if (orientation(attrs, vertical = false) == "vertical") "VerticalSplitter" else "HorizontalSplitter"
                container(element, attrs, name, modifier, null, Parent.Splitter)
            }
            Tag.Label -> {
                val args = mutableListOf(kotlinString(attrs["Text"] ?: element.text))
                modifier?.let { args += "modifier = $it" }
                attrs["TextSize", "FontSize"]?.let { size -> attrs.number("TextSize", size)?.let { args += "fontSize = ${number(it)}.sp".also { use("sp") } } }
                leaf(element, attrs, "Text", args)
            }
            Tag.Button -> button(element, attrs, modifier)
            Tag.Checkbox -> checkbox(element, attrs, modifier, radio)
            Tag.TextInput -> {
                val name = state(attrs, "textField", kotlinString(attrs["Text"] ?: element.text))
                val args = mutableListOf("value = $name", "onValueChange = { $name = it }")
                modifier?.let { args += "modifier = $it" }
                enabled(attrs)?.let { args += it }
                attrs["ReadOnly"]?.let { if (attrs.bool("ReadOnly", it) == true) args += "readOnly = true" }
                attrs["Placeholder"]?.let { args += "placeholder = ${kotlinString(it)}" }
                action(attrs)?.let { warn(element, "the action \"$it\" fires on Enter, which TextField doesn't report") }
                leaf(element, attrs, "TextField", args)
            }
            Tag.TextEditor -> {
                val name = state(attrs, "codeEditor", kotlinString(attrs["Text"] ?: element.text))
                val args = mutableListOf("value = $name", "onValueChange = { $name = it }")
                modifier?.let { args += "modifier = $it" }
                attrs["Language"]?.let { if (!it.equals("None", ignoreCase = true)) args += "language = ${kotlinString(it)}" }
                attrs["ReadOnly"]?.let { if (attrs.bool("ReadOnly", it) == true) args += "readOnly = true" }
                attrs["LineNumbers"]?.let { if (attrs.bool("LineNumbers", it) == false) args += "lineNumbers = false" }
                leaf(element, attrs, "CodeEditor", args)
            }
            Tag.Slider -> slider(element, attrs, modifier)
            Tag.ProgressBar -> {
                val progress = attrs["Value"]?.let { attrs.number("Value", it) } ?: 0f
                val args = mutableListOf("progress = ${float(progress)}")
                modifier?.let { args += "modifier = $it" }
                leaf(element, attrs, "LinearProgressIndicator", args)
            }
            Tag.Separator -> {
                val name = if (orientation(attrs, vertical = false) == "vertical") "VerticalDivider" else "HorizontalDivider"
                leaf(element, attrs, name, listOfNotNull(modifier))
            }
            Tag.Spacer -> leaf(element, attrs, "Spacer", listOf(modifier ?: "Modifier".also { use("Modifier") }))
            Tag.Window -> error("handled by the caller")
        }
    }

    private fun container(element: XmlElement, attrs: Attrs, name: String, modifier: String?, arrangement: String?, inside: Parent) {
        val args = listOfNotNull(modifier, arrangement)
        val comment = attrs.reportUnused(element, name)
        use(name)
        val children = element.children
        val radios = children.withIndex().filter { (_, child) -> isRadio(child) }.map { it.index }
        val group = if (radios.isEmpty()) null else radioGroup(radios.map { children[it] })
        out.block(if (args.isEmpty()) name else call(name, args), comment) {
            children.forEachIndexed { index, child ->
                element(child, inside, group?.let { RadioChoice(it, radios.indexOf(index)) }?.takeIf { it.index >= 0 })
            }
        }
    }

    private fun leaf(element: XmlElement, attrs: Attrs, name: String, args: List<String>, trailing: String? = null) {
        val comment = attrs.reportUnused(element, name)
        if (element.children.any { !it.isComment }) warn(element, "the children of <${element.tag}> have no equivalent and are left out")
        use(name)
        out.line(call(name, args) + (trailing?.let { " $it" } ?: ""), comment)
    }

    private fun button(element: XmlElement, attrs: Attrs, modifier: String?) {
        val name = when (attrs["Style"]?.lowercase()) {
            null, "default" -> "OutlinedButton"
            "primary" -> "Button"
            "flat" -> "TextButton"
            else -> "OutlinedButton".also { warn(element, "Style=\"${attrs["Style"]}\" is not a button style; Default is used") }
        }
        val args = mutableListOf("onClick = ${stub(action(attrs))}")
        modifier?.let { args += "modifier = $it" }
        enabled(attrs)?.let { args += it }
        use("Text")
        leaf(element, attrs, name, args, trailing = "{ Text(${kotlinString(attrs["Text"] ?: element.text)}) }")
    }

    private fun checkbox(element: XmlElement, attrs: Attrs, modifier: String?, choice: RadioChoice?) {
        val text = attrs["Text"] ?: element.text
        val style = attrs["Style"]?.lowercase()
        val action = action(attrs)
        val args = mutableListOf<String>()
        val name: String
        // (a radio with no parent panel, the root, is a group of its own)
        val radio = choice ?: if (style == "radio") RadioChoice(radioGroup(listOf(element)), 0) else null
        if (radio != null) {
            name = "RadioButton"
            args += "selected = ${radio.group} == ${radio.index}"
            args += "onClick = { ${radio.group} = ${radio.index}${action?.let { " /* $it */" } ?: ""} }"
            // The group's state holds which one is checked.
            attrs["Checked"]
        } else {
            name = when (style) {
                null, "check" -> "Checkbox"
                "switch" -> "Switch"
                else -> "Checkbox".also { warn(element, "Style=\"${attrs["Style"]}\" is not a checkbox style; Check is used") }
            }
            val checked = attrs["Checked"]?.let { attrs.bool("Checked", it) } ?: false
            val variable = state(attrs, if (name == "Switch") "switch" else "checkbox", checked.toString())
            args += "checked = $variable"
            args += "onCheckedChange = { $variable = it${action?.let { " /* $it */" } ?: ""} }"
        }
        enabled(attrs)?.let { args += it }
        if (text.isEmpty()) {
            modifier?.let { args += "modifier = $it" }
            return leaf(element, attrs, name, args)
        }
        // Raven draws the text beside the box; Compose's toggles have none, so a Row holds both.
        val comment = attrs.reportUnused(element, name)
        use("Row", "Alignment", name, "Text")
        val rowArgs = listOfNotNull(modifier, "verticalAlignment = Alignment.CenterVertically")
        out.block(call("Row", rowArgs), comment) {
            out.line(call(name, args))
            out.line("Text(${kotlinString(text)})")
        }
    }

    private fun slider(element: XmlElement, attrs: Attrs, modifier: String?) {
        val min = attrs["Min"]?.let { attrs.number("Min", it) } ?: 0f
        val max = attrs["Max"]?.let { attrs.number("Max", it) } ?: 1f
        val value = (attrs["Value"]?.let { attrs.number("Value", it) } ?: min).coerceIn(minOf(min, max), maxOf(min, max))
        val name = state(attrs, "slider", float(value))
        val args = mutableListOf("value = $name", "onValueChange = { $name = it${action(attrs)?.let { " /* $it */" } ?: ""} }")
        modifier?.let { args += "modifier = $it" }
        enabled(attrs)?.let { args += it }
        if (min != 0f || max != 1f) args += "valueRange = ${float(min)}..${float(max)}"
        attrs["Step"]?.let { attrs.number("Step", it) }?.takeIf { it > 0f }?.let { step ->
            // Compose counts the values between the ends; Raven gives the distance between two.
            val positions = (max - min) / step
            val count = kotlin.math.round(positions)
            if (count >= 2f && kotlin.math.abs(positions - count) < 1e-4f) {
                args += "steps = ${count.toInt() - 1}"
            } else {
                warn(element, "Step=\"${attrs["Step"]}\" doesn't divide the range into steps Slider can count; it slides freely")
            }
        }
        if (orientation(attrs, vertical = false) == "vertical") warn(element, "Slider is horizontal only")
        leaf(element, attrs, "Slider", args)
    }

    // ---- layout ----

    /** The Modifier for an element's slot and size in [parent], or null when it needs none. */
    private fun modifier(element: XmlElement, attrs: Attrs, parent: Parent): String? {
        val parts = mutableListOf<String>()
        val h = attrs["Slot.HAlign"]?.let { attrs.choice("Slot.HAlign", it, "fill", "left", "center", "right") } ?: "fill"
        val v = attrs["Slot.VAlign"]?.let { attrs.choice("Slot.VAlign", it, "fill", "top", "center", "bottom") } ?: "fill"
        val auto = attrs["Slot.Auto"]?.let { attrs.bool("Slot.Auto", it) } == true
        val fill = attrs["Slot.Fill"]?.let { attrs.number("Slot.Fill", it) }?.takeIf { it > 0f && !auto }
        when (parent) {
            Parent.Column, Parent.VerticalScroll -> {
                parts += horizontal(h)
                if (fill != null && parent == Parent.Column) parts += "weight(${float(fill)})"
            }
            Parent.Row, Parent.HorizontalScroll -> {
                parts += vertical(v)
                if (fill != null && parent == Parent.Row) parts += "weight(${float(fill)})"
            }
            Parent.Box, Parent.Window -> parts += both(h, v)
            Parent.Splitter -> if (fill != null) parts += "weight(${float(fill)})"
            Parent.None -> {
                if (h != "fill" || v != "fill" || fill != null) warn(element, "the root's slot has no parent to place it; Slot attributes are left out")
            }
        }
        attrs["Slot.Padding"]?.let { attrs.margin("Slot.Padding", it) }?.let { parts += padding(it) }
        val width = attrs["Width"]?.let { attrs.number("Width", it) }
        val height = attrs["Height"]?.let { attrs.number("Height", it) }
        when {
            width != null && height != null -> parts += "size(${dp(width)}, ${dp(height)})".also { use("size") }
            width != null -> parts += "width(${dp(width)})".also { use("width") }
            height != null -> parts += "height(${dp(height)})".also { use("height") }
        }
        limits(attrs, "MinWidth", "MaxWidth")?.let { parts += "widthIn($it)".also { use("widthIn") } }
        limits(attrs, "MinHeight", "MaxHeight")?.let { parts += "heightIn($it)".also { use("heightIn") } }
        attrs["Tooltip"]?.let { parts += "tooltip(${kotlinString(it)})".also { use("tooltip") } }
        if (parts.isEmpty()) return null
        use("Modifier")
        return "Modifier." + parts.joinToString(".")
    }

    /** Whether the window's content fills it both ways, so it needs no Box to be aligned in. */
    private fun windowContentFills(attrs: Attrs): Boolean =
        (attrs.peek("Slot.HAlign") ?: "fill").equals("fill", ignoreCase = true) &&
            (attrs.peek("Slot.VAlign") ?: "fill").equals("fill", ignoreCase = true)

    private fun dp(value: Float): String {
        use("dp")
        return "${number(value)}.dp"
    }

    private fun horizontal(h: String): String = when (h) {
        "fill" -> "fillMaxWidth()".also { use("fillMaxWidth") }
        else -> "align(Alignment.${horizontalName(h)})".also { use("Alignment") }
    }

    private fun vertical(v: String): String = when (v) {
        "fill" -> "fillMaxHeight()".also { use("fillMaxHeight") }
        else -> "align(Alignment.${verticalName(v)})".also { use("Alignment") }
    }

    /** A Box child: align first, then fill, since the last element wins on each axis. */
    private fun both(h: String, v: String): List<String> {
        if (h == "fill" && v == "fill") return listOf("fillMaxSize()".also { use("fillMaxSize") })
        use("Alignment")
        val vName = when (v) { "center" -> "Center"; "bottom" -> "Bottom"; else -> "Top" }
        val hName = when (h) { "center" -> "Center"; "right" -> "End"; else -> "Start" }
        val alignment = if (vName == "Center" && hName == "Center") "Center" else vName + hName
        return listOfNotNull(
            "align(Alignment.$alignment)",
            "fillMaxWidth()".takeIf { h == "fill" }?.also { use("fillMaxWidth") },
            "fillMaxHeight()".takeIf { v == "fill" }?.also { use("fillMaxHeight") },
        )
    }

    private fun horizontalName(h: String) = when (h) { "left" -> "Start"; "center" -> "CenterHorizontally"; else -> "End" }

    private fun verticalName(v: String) = when (v) { "top" -> "Top"; "center" -> "CenterVertically"; else -> "Bottom" }

    private fun padding(margin: List<Float>): String {
        use("padding")
        return when (margin.size) {
            1 -> "padding(${dp(margin[0])})"
            2 -> "padding(horizontal = ${dp(margin[0])}, vertical = ${dp(margin[1])})"
            else -> "padding(start = ${dp(margin[0])}, top = ${dp(margin[1])}, end = ${dp(margin[2])}, bottom = ${dp(margin[3])})"
        }
    }

    private fun limits(attrs: Attrs, minName: String, maxName: String): String? {
        val min = attrs[minName]?.let { attrs.number(minName, it) }
        val max = attrs[maxName]?.let { attrs.number(maxName, it) }
        return listOfNotNull(min?.let { "min = ${dp(it)}" }, max?.let { "max = ${dp(it)}" }).joinToString(", ").ifEmpty { null }
    }

    private fun spacing(attrs: Attrs, parameter: String): String? =
        attrs["Spacing"]?.let { attrs.number("Spacing", it) }?.takeIf { it != 0f }?.let {
            use("Arrangement")
            "$parameter = Arrangement.spacedBy(${dp(it)})"
        }

    private fun orientation(attrs: Attrs, vertical: Boolean): String =
        attrs["Orientation"]?.let { attrs.choice("Orientation", it, "horizontal", "vertical") } ?: if (vertical) "vertical" else "horizontal"

    // ---- state and actions ----

    /** A radio button's place: the name of its group's state, and its index in the group. */
    private class RadioChoice(val group: String, val index: Int)

    private fun isRadio(element: XmlElement) =
        Tag.of(element.tag) == Tag.Checkbox && element.attributes.entries.any { (name, value) ->
            name.equals("Style", ignoreCase = true) && value.equals("Radio", ignoreCase = true)
        }

    /**
     * One Int state for the radio buttons of one parent, which Raven keeps exclusive: the index of
     * the checked one, in document order, or -1. Returns its name.
     */
    private fun radioGroup(radios: List<XmlElement>): String {
        val checked = radios.indexOfFirst { radio ->
            radio.attributes.entries.any { (name, value) -> name.equals("Checked", ignoreCase = true) && value.trim().lowercase() in trueWords }
        }
        val name = uniqueName("radioGroup", numbered = true)
        state += "var $name by remember { mutableStateOf($checked) }"
        use("getValue", "setValue", "mutableStateOf", "remember")
        return name
    }

    private fun state(attrs: Attrs, base: String, initial: String): String {
        val name = uniqueName(attrs["Name", "Id"]?.let(::identifier) ?: base, numbered = attrs["Name", "Id"] == null)
        state += "var $name by remember { mutableStateOf($initial) }"
        use("getValue", "setValue", "mutableStateOf", "remember")
        return name
    }

    private fun uniqueName(base: String, numbered: Boolean): String {
        var n = 1
        var name = if (numbered) "$base$n" else base
        while (!stateNames.add(name)) name = "$base${++n}"
        return name
    }

    private fun action(attrs: Attrs): String? = attrs.get(*actionNames)

    private fun stub(action: String?): String = if (action == null) "{}" else "{ /* ${action.replace("*/", "* /")} */ }"

    private fun enabled(attrs: Attrs): String? =
        attrs["Enabled"]?.let { attrs.bool("Enabled", it) }?.takeIf { !it }?.let { "enabled = false" }

    // ---- output helpers ----

    private fun comment(element: XmlElement) {
        element.text.trim().lines().forEach { out.line("// ${it.trim()}".trimEnd()) }
    }

    private fun todo(element: XmlElement, message: String) {
        out.line("// TODO(raven-xml): $message")
        warn(element, message)
    }

    private fun warn(element: XmlElement, message: String) {
        warnings += TranslationWarning(element.line, message)
    }

    private fun use(vararg symbols: String) {
        used.addAll(symbols)
    }

    private fun call(name: String, args: List<String>): String {
        val oneLine = "$name(${args.joinToString(", ")})"
        if ((out.depth + 1) * 4 + oneLine.length <= 100) return oneLine
        return "$name(\n" + args.joinToString("") { "    $it,\n" } + ")"
    }

    // ---- attributes ----

    /**
     * The attributes of one element, by case-insensitive name. Each one read is used; [reportUnused]
     * warns about the rest. A `{...}` expression is not translated yet, and is reported at once.
     */
    private inner class Attrs(private val element: XmlElement) {
        private val byName = element.attributes.entries.associateBy { it.key.lowercase() }
        private val read = mutableSetOf<String>()
        private val expressions = mutableSetOf<String>()
        private val notes = mutableListOf<String>()

        init {
            for ((name, value) in element.attributes) {
                if ('{' in value && '}' in value) {
                    expressions += name.lowercase()
                    note(name, value, "expressions are not translated yet")
                }
            }
        }

        /** The first of [names] that is written, marking every one of them read. An expression reads as absent. */
        operator fun get(vararg names: String): String? {
            var found: String? = null
            for (name in names) {
                val key = name.lowercase()
                read += key
                if (found == null && key !in expressions) found = byName[key]?.value
            }
            return found
        }

        fun peek(name: String): String? = byName[name.lowercase()]?.takeIf { it.key.lowercase() !in expressions }?.value

        fun number(name: String, value: String): Float? = value.trim().toFloatOrNull() ?: null.also { note(name, value, "not a number") }

        fun int(name: String, value: String): Int? = number(name, value)?.toInt()

        fun bool(name: String, value: String): Boolean? = when (value.trim().lowercase()) {
            in trueWords -> true
            in falseWords -> false
            else -> null.also { note(name, value, "not a boolean") }
        }

        fun choice(name: String, value: String, vararg choices: String): String? =
            value.trim().lowercase().takeIf { it in choices } ?: null.also { note(name, value, "not one of ${choices.joinToString()}") }

        fun margin(name: String, value: String): List<Float>? {
            val parts = value.split(',').map { it.trim().toFloatOrNull() }
            return if (parts.size in setOf(1, 2, 4) && parts.all { it != null }) parts.map { it!! } else null.also { note(name, value, "not a margin") }
        }

        private fun note(name: String, value: String, why: String) {
            notes += "$name=\"$value\": $why"
            warn(element, "$name=\"$value\": $why")
        }

        /** Warns about each attribute nothing read, and returns the trailing comment for all of them. */
        fun reportUnused(element: XmlElement, composable: String): String? {
            for ((name, value) in element.attributes) {
                val key = name.lowercase()
                if (key in read || key in expressions || key == "name" || key == "id" || key in ignoredSlots) continue
                read += key
                notes += "$name=\"$value\""
                warn(element, "$name=\"$value\" has no equivalent on $composable")
            }
            return if (notes.isEmpty()) null else "raven-xml: " + notes.joinToString("; ") + " left out"
        }
    }
}

// Slot attributes that the parent ignores in Raven too, so leaving them out changes nothing.
private val ignoredSlots = setOf("slot.halign", "slot.valign", "slot.fill", "slot.auto")

private val trueWords = setOf("true", "yes", "1")
private val falseWords = setOf("false", "no", "0")

/** Lines of code, indented 4 spaces a level. A trailing comment goes after the first line. */
private class CodeWriter {
    val lines = mutableListOf<String>()
    var depth = 0

    fun line(code: String, comment: String? = null) {
        val first = code.lines()
        first.forEachIndexed { i, text ->
            val withComment = if (i == 0 && comment != null) "$text // $comment" else text
            lines += "    ".repeat(depth) + withComment
        }
    }

    fun block(header: String, comment: String? = null, body: () -> Unit) {
        line("$header {", comment)
        val start = lines.size
        depth++
        body()
        depth--
        if (lines.size == start) {
            lines[start - 1] = lines[start - 1].replaceFirst("$header {".lines().last(), "$header {}".lines().last())
        } else {
            line("}")
        }
    }
}

/** A number as Kotlin writes it: `8` or `0.5`. */
private fun number(value: Float): String = if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e9f) value.toInt().toString() else value.toString()

/** A Float literal: `1f`, `0.5f`. */
private fun float(value: Float): String = "${number(value)}f"

private fun kotlinString(text: String): String = buildString {
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

/** A `Name` as a Kotlin identifier: `file-path` becomes `filePath`, a keyword is quoted. */
private fun identifier(name: String): String {
    val words = name.split(Regex("[^A-Za-z0-9_]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "value"
    val joined = words.first().replaceFirstChar { it.lowercase() } + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
    val safe = if (joined.first().isDigit()) "v$joined" else joined
    return if (safe in keywords) "`$safe`" else safe
}

private val keywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
)
