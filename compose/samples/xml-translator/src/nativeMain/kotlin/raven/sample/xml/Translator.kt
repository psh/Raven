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
 * Translates a Raven XML tree into one compose-raven file: its imports, a composable function for
 * each `<Widget>`, `@Composable fun TranslatedUi()` with the hoisted state at the top (none for a
 * `<Widgets>` root), then `main()` when the root is `<Window>`. Nothing is refused: what has no
 * equivalent becomes a TODO or a comment, and a warning.
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

/** Tags of the template language and of `<Window>`, which no `<Widget>` may take as its name. */
private val reservedTags = setOf(
    "window", "widget", "widgets", "children", "repeat", "foreach", "if", "include", "strings", "shortcut", "item", "separator",
)

/**
 * What the parent does with a child's slot, which decides the child's Modifier, and the scope a
 * slot lambda placed there gets. [Passed] is a `<Widget>`'s root, which the call places.
 */
private enum class Parent(val scope: String?) {
    Column("ColumnScope"),
    Row("RowScope"),
    Box("BoxScope"),
    VerticalScroll("ColumnScope"),
    HorizontalScroll("RowScope"),
    Splitter("SplitterScope"),
    Window(null),
    Passed(null),
    None(null),
}

// The attributes that place an element, which a call to a <Widget> takes as its modifier.
private val layoutKeys = setOf(
    "slot.padding", "slot.halign", "slot.valign", "slot.fill", "slot.auto",
    "width", "height", "minwidth", "maxwidth", "minheight", "maxheight", "tooltip",
)

private val actionNames = arrayOf("Action", "OnClicked", "OnChanged", "OnCommitted", "OnActivated")

private val imports = mapOf(
    "Composable" to "androidx.compose.runtime",
    "getValue" to "androidx.compose.runtime",
    "setValue" to "androidx.compose.runtime",
    "mutableStateOf" to "androidx.compose.runtime",
    "remember" to "androidx.compose.runtime",
    "abs" to "kotlin.math",
    "ceil" to "kotlin.math",
    "floor" to "kotlin.math",
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
    "BoxScope" to "raven.compose.foundation.layout",
    "Column" to "raven.compose.foundation.layout",
    "ColumnScope" to "raven.compose.foundation.layout",
    "HorizontalScroll" to "raven.compose.foundation.layout",
    "HorizontalSplitter" to "raven.compose.foundation.layout",
    "Row" to "raven.compose.foundation.layout",
    "RowScope" to "raven.compose.foundation.layout",
    "Spacer" to "raven.compose.foundation.layout",
    "SplitterScope" to "raven.compose.foundation.layout",
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

/** A `<Widget>` declaration, as a composable function. */
private class WidgetFunction(
    val xmlName: String,
    val name: String,
    val root: XmlElement,
    val params: List<Param>,
    /** By lowercased `<Children Name>`, `""` for the plain `<Children/>`; the content slot last. */
    val slots: Map<String, Slot>,
    /** The root's own placing attributes (expressions left out), by lowercased name, with a root's root's under them. */
    val rootLayout: Map<String, Pair<String, String>>,
)

private class Param(val xmlName: String, val name: String, val type: KType, val default: String)

private class Slot(val name: String, val parent: Parent)

/** The variables `{...}` can name here; none outside a `<Widget>`, `<Repeat>` or `<ForEach>`. */
private class Scope(val parent: Scope?, val vars: Map<String, Variable>, private val function: WidgetFunction? = null) {
    val widget: WidgetFunction? get() = function ?: parent?.widget

    fun find(name: String): Variable? = vars[name.lowercase()] ?: parent?.find(name)

    fun inside(function: WidgetFunction): Boolean = this.function === function || parent?.inside(function) == true
}

/** Where state is declared: the top of a function, or of a loop's body. */
private class Holder {
    val state = mutableListOf<String>()
}

/** A radio button's place: the name of its group's state, and its index in the group. */
private class RadioChoice(val group: String, val index: Int)

/** A number read from an attribute: its value when it is written out, and its Kotlin code. */
private class Num(val constant: Float?, val expr: KExpr)

/** A truth read from an attribute: its value when it is written out, and its Kotlin code. */
private class Truth(val constant: Boolean?, val code: String)

/** An attribute value: written out, or computed. */
private sealed interface Value {
    class Literal(val text: String) : Value
    class Code(val expr: KExpr) : Value
}

/** One run of the translator: the functions, state, imports and warnings it collects. */
private class Translator {
    private val used = mutableSetOf<String>()
    private val warnings = mutableListOf<TranslationWarning>()
    private val functions = LinkedHashMap<String, WidgetFunction>()
    private var out = CodeWriter()
    private var holder = Holder()
    private var names = mutableSetOf<String>()
    private var scope: Scope? = null

    fun run(root: XmlElement): Translation {
        declare(root)
        val sections = mutableListOf<String>()
        for (function in functions.values) sections += function(function)
        var main: String? = null
        if (!root.tag.equals("Widgets", ignoreCase = true) && !root.tag.equals("Widget", ignoreCase = true)) {
            use("Composable")
            begin(Holder(), mutableSetOf(), null)
            if (Tag.of(root.tag) == Tag.Window) main = window(root) else element(root, Parent.None)
            sections += "@Composable\nfun TranslatedUi() {\n" + body() + "}\n"
        }
        main?.let { sections += it }
        val code = buildString {
            for (import in used.map { "${imports.getValue(it)}.$it" }.sorted()) append("import $import\n")
            if (sections.isNotEmpty()) append("\n" + sections.joinToString("\n"))
        }
        return Translation(code, warnings.toList())
    }

    private fun begin(holder: Holder, names: MutableSet<String>, scope: Scope?) {
        out = CodeWriter()
        this.holder = holder
        this.names = names
        this.scope = scope
    }

    /** The state, then the code, one level in. */
    private fun body(): String = buildString {
        for (declaration in holder.state) append("    $declaration\n")
        for (line in out.lines) append(if (line.isBlank()) "\n" else "    $line\n")
    }

    // ---- <Widget> declarations ----

    /** Finds the `<Widget>` declarations, in document order: each can use the ones before it. */
    private fun declare(element: XmlElement) {
        if (element.isComment) return
        if (element.tag.equals("Widget", ignoreCase = true)) return register(element)
        element.children.forEach(::declare)
    }

    private fun register(declaration: XmlElement) {
        val attributes = declaration.attributes.entries
        val xmlName = attributes.firstOrNull { it.key.equals("Name", ignoreCase = true) }?.value.orEmpty()
        if (!Regex("[A-Za-z_][A-Za-z0-9_.-]*").matches(xmlName)) {
            return warn(declaration, if (xmlName.isEmpty()) "<Widget> needs a Name: the tag it declares" else "<Widget Name=\"$xmlName\">: this can't be the name of a tag")
        }
        if (xmlName.lowercase() in reservedTags || Tag.of(xmlName) != null) return warn(declaration, "<Widget Name=\"$xmlName\">: this tag already exists")
        val body = declaration.children.filter { !it.isComment }
        val root = body.firstOrNull() ?: return warn(declaration, "<Widget Name=\"$xmlName\"> is empty: it needs the element it stands for")
        if (body.size > 1) warn(body[1], "a <Widget> stands for exactly one element; only <${root.tag}> is translated")

        var name = pascalCase(xmlName)
        if (name in imports || functions.values.any { it.name == name } || name == "TranslatedUi") name += "Widget"
        val taken = mutableSetOf("modifier", "content")
        val params = attributes.filter { !it.key.equals("Name", ignoreCase = true) }.map { (attribute, default) ->
            val type = when {
                default.equals("true", ignoreCase = true) || default.equals("false", ignoreCase = true) -> KType.Bool
                '.' in default && default.toFloatOrNull() != null -> KType.Float
                default.toIntOrNull() != null -> KType.Int
                else -> KType.Str
            }
            val code = when (type) {
                KType.Bool -> default.lowercase()
                KType.Float -> float(default.toFloat())
                KType.Int -> default.toInt().toString()
                KType.Str -> kotlinString(default)
            }
            Param(attribute, unique(identifier(attribute), taken), type, code)
        }
        val slots = LinkedHashMap<String, Slot>()
        findSlots(root, Parent.None, slots, taken)
        // The content slot goes last, so a call can give it as a trailing lambda.
        slots.remove("")?.let { slots[""] = it }
        val rootLayout = LinkedHashMap<String, Pair<String, String>>()
        functions[root.tag.lowercase()]?.let { rootLayout.putAll(it.rootLayout) }
        for ((attribute, value) in root.attributes) {
            if (attribute.lowercase() in layoutKeys && '{' !in value && '}' !in value) rootLayout[attribute.lowercase()] = attribute to value
        }
        functions[xmlName.lowercase()] = WidgetFunction(xmlName, name, root, params, slots, rootLayout)
    }

    /** The `<Children>` of a declaration, with the parent that places what they stand for. */
    private fun findSlots(element: XmlElement, parent: Parent, slots: MutableMap<String, Slot>, taken: MutableSet<String>) {
        if (element.isComment) return
        val tag = element.tag.lowercase()
        when {
            tag == "children" -> {
                val xmlName = element.attributes.entries.firstOrNull { it.key.equals("Name", ignoreCase = true) }?.value.orEmpty()
                slots.getOrPut(xmlName.lowercase()) {
                    Slot(if (xmlName.isEmpty()) "content" else unique(identifier(xmlName), taken), parent)
                }
            }
            tag == "if" || tag == "repeat" || tag == "foreach" || tag == "widgets" ->
                element.children.forEach { findSlots(it, parent, slots, taken) }
            tag == "widget" -> Unit
            Tag.of(tag) != null -> element.children.forEach { findSlots(it, childParent(element), slots, taken) }
            else -> functions[tag]?.let { function ->
                for (child in element.children) {
                    val into = child.attributes.entries.firstOrNull { it.key.equals("Into", ignoreCase = true) }?.value.orEmpty()
                    findSlots(child, function.slots[into.lowercase()]?.parent ?: Parent.None, slots, taken)
                }
            }
        }
    }

    /** What a container tag places its children as, read from its written attributes. */
    private fun childParent(element: XmlElement): Parent {
        val orientation = element.attributes.entries.firstOrNull { it.key.equals("Orientation", ignoreCase = true) }?.value?.lowercase()
        return when (Tag.of(element.tag)) {
            Tag.VBox -> Parent.Column
            Tag.HBox -> Parent.Row
            Tag.Overlay -> Parent.Box
            Tag.ScrollBox -> if (orientation == "horizontal") Parent.HorizontalScroll else Parent.VerticalScroll
            Tag.Splitter -> Parent.Splitter
            else -> Parent.None
        }
    }

    /** `@Composable fun Name(modifier, params..., slots...)` and its body. */
    private fun function(function: WidgetFunction): String {
        use("Composable", "Modifier")
        val vars = function.params.associate { it.xmlName.lowercase() to Variable(it.name, it.type) }
        val taken = (function.params.map { it.name } + function.slots.values.map { it.name } + "modifier").toMutableSet()
        begin(Holder(), taken, Scope(null, vars, function))
        element(function.root, Parent.Passed)
        val parameters = listOf("modifier: Modifier = Modifier") +
            function.params.map { "${it.name}: ${kotlinType(it.type)} = ${it.default}" } +
            function.slots.values.map { slot ->
                slot.parent.scope?.let { use(it) }
                "${slot.name}: @Composable ${slot.parent.scope?.let { "$it." } ?: ""}() -> Unit = {}"
            }
        val oneLine = "fun ${function.name}(${parameters.joinToString(", ")}) {"
        val header = if (oneLine.length <= 100) oneLine else "fun ${function.name}(\n" + parameters.joinToString("") { "    $it,\n" } + ") {"
        return "@Composable\n$header\n" + body() + "}\n"
    }

    private fun kotlinType(type: KType) = when (type) {
        KType.Bool -> "Boolean"
        KType.Int -> "Int"
        KType.Float -> "Float"
        KType.Str -> "String"
    }

    // ---- the window ----

    /** Emits the window's content, and returns `main()`. */
    private fun window(window: XmlElement): String {
        val attrs = Attrs(window)
        val args = listOfNotNull(
            attrs.raw("Title")?.let { "title = ${kotlinString(it)}" },
            attrs.num("Width")?.constant?.let { "width = ${it.toInt()}" },
            attrs.num("Height")?.constant?.let { "height = ${it.toInt()}" },
        )
        attrs.reportUnused("singleWindowApplication")
        var content = false
        for (child in window.children) {
            when {
                child.isComment -> comment(child)
                child.tag.equals("Widget", ignoreCase = true) -> Unit
                child.tag.equals("Shortcut", ignoreCase = true) || child.tag.equals("Island", ignoreCase = true) ->
                    todo(child, "<${child.tag}> has no composable yet")
                content -> todo(child, "a window has one content widget; <${child.tag}> is a second one")
                else -> {
                    content = true
                    element(child, Parent.Window)
                }
            }
        }
        use("singleWindowApplication")
        val call = if (args.isEmpty()) "singleWindowApplication" else "singleWindowApplication(${args.joinToString(", ")})"
        return "fun main() = $call {\n    TranslatedUi()\n}\n"
    }

    // ---- elements ----

    /** Emits [element] as the child of [parent]; [radio] is its place when it is a radio button. */
    private fun element(element: XmlElement, parent: Parent, radio: RadioChoice? = null) {
        if (element.isComment) return comment(element)
        val attrs = Attrs(element)
        val mark = out.lines.size
        val depth = out.depth
        // If="..." on any element but <If>, which has Test.
        val condition = if (element.tag.equals("If", ignoreCase = true)) null else attrs.condition("If")
        when {
            condition == null -> placed(element, attrs, parent, radio)
            condition.constant == false -> out.line("// raven-xml: <${element.tag}> left out by If=\"${attrs.peek("If")}\"")
            condition.constant == true -> placed(element, attrs, parent, radio)
            else -> out.block("if (${condition.code})") { placed(element, attrs, parent, radio) }
        }
        // An expression that failed to parse leaves a TODO in the element's place.
        out.insert(mark, depth, attrs.todos)
    }

    private fun placed(element: XmlElement, attrs: Attrs, parent: Parent, radio: RadioChoice?) {
        val tag = element.tag.lowercase()
        when {
            tag == "widget" -> Unit
            tag == "widgets" -> children(element.children, parent)
            tag == "children" -> slot(element, attrs)
            tag == "if" -> ifTest(element, attrs, parent)
            tag == "repeat" -> repeat(element, attrs, parent)
            tag == "foreach" -> forEach(element, attrs, parent)
            tag == "include" -> todo(element, "<Include Source=\"${attrs.peek("Source").orEmpty()}\"> isn't translated: the file isn't read")
            tag == "window" -> todo(element, "<Window> is only a root")
            Tag.of(tag) != null || functions[tag] != null -> {
                val function = functions[tag]
                if (parent == Parent.Window && !windowContentFills(attrs, function)) {
                    // The window places its content by alignment, which needs a BoxScope.
                    use("Box", "Modifier", "fillMaxSize")
                    out.block("Box(Modifier.fillMaxSize())") { emit(element, attrs, Parent.Box, radio, function) }
                } else {
                    emit(element, attrs, parent, radio, function)
                }
            }
            else -> todo(element, "<${element.tag}> has no composable yet")
        }
    }

    private fun emit(element: XmlElement, attrs: Attrs, parent: Parent, radio: RadioChoice?, function: WidgetFunction?) {
        if (function != null) call(element, attrs, function, parent) else widget(element, Tag.of(element.tag)!!, attrs, parent, radio)
    }

    /** Emits [children] into [parent], giving the radio buttons among them one shared state. */
    private fun children(children: List<XmlElement>, parent: Parent) {
        val radios = children.withIndex().filter { (_, child) -> isRadio(child) }.map { it.index }
        val group = if (radios.isEmpty()) null else radioGroup(radios.map { children[it] })
        children.forEachIndexed { index, child ->
            val place = radios.indexOf(index)
            element(child, parent, if (group != null && place >= 0) RadioChoice(group, place) else null)
        }
    }

    private fun widget(element: XmlElement, tag: Tag, attrs: Attrs, parent: Parent, radio: RadioChoice?) {
        val modifier = modifier(attrs, parent)
        when (tag) {
            Tag.VBox -> container(element, attrs, "Column", modifier, spacing(attrs, "verticalArrangement"), Parent.Column)
            Tag.HBox -> container(element, attrs, "Row", modifier, spacing(attrs, "horizontalArrangement"), Parent.Row)
            Tag.Overlay -> container(element, attrs, "Box", modifier, null, Parent.Box)
            Tag.ScrollBox -> {
                val horizontal = orientation(attrs, vertical = true) == "horizontal"
                if (attrs.truth("ScrollBoth")?.constant == true) warn(element, "ScrollBoth: scrolling both ways has no equivalent; it scrolls one way")
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
                val args = mutableListOf(attrs.text())
                modifier?.let { args += "modifier = $it" }
                attrs.num("TextSize", "FontSize")?.let { args += "fontSize = ${sp(it)}" }
                leaf(element, attrs, "Text", args)
            }
            Tag.Button -> button(element, attrs, modifier)
            Tag.Checkbox -> checkbox(element, attrs, modifier, radio)
            Tag.TextInput -> {
                val name = state(attrs, "textField", attrs.text())
                val args = mutableListOf("value = $name", "onValueChange = { $name = it }")
                modifier?.let { args += "modifier = $it" }
                enabled(attrs)?.let { args += it }
                attrs.truth("ReadOnly")?.let { flag("readOnly", it, unless = false)?.let { args += it } }
                attrs.str("Placeholder")?.let { args += "placeholder = $it" }
                action(attrs)?.let { warn(element, "the action \"$it\" fires on Enter, which TextField doesn't report") }
                leaf(element, attrs, "TextField", args)
            }
            Tag.TextEditor -> {
                val name = state(attrs, "codeEditor", attrs.text())
                val args = mutableListOf("value = $name", "onValueChange = { $name = it }")
                modifier?.let { args += "modifier = $it" }
                val language = attrs.value("Language")
                if (language !is Value.Literal || !language.text.equals("None", ignoreCase = true)) {
                    attrs.str(language)?.let { args += "language = $it" }
                }
                attrs.truth("ReadOnly")?.let { flag("readOnly", it, unless = false)?.let { args += it } }
                attrs.truth("LineNumbers")?.let { flag("lineNumbers", it, unless = true)?.let { args += it } }
                leaf(element, attrs, "CodeEditor", args)
            }
            Tag.Slider -> slider(element, attrs, modifier)
            Tag.ProgressBar -> {
                val args = mutableListOf("progress = ${attrs.num("Value")?.let(::kotlinFloat) ?: "0f"}")
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
        val comment = attrs.reportUnused(name)
        use(name)
        out.block(if (args.isEmpty()) name else call(name, args), comment) { children(element.children, inside) }
    }

    private fun leaf(element: XmlElement, attrs: Attrs, name: String, args: List<String>, trailing: String? = null) {
        val comment = attrs.reportUnused(name)
        if (element.children.any { !it.isComment }) warn(element, "the children of <${element.tag}> have no equivalent and are left out")
        use(name)
        out.line(call(name, args) + (trailing?.let { " $it" } ?: ""), comment)
    }

    private fun button(element: XmlElement, attrs: Attrs, modifier: String?) {
        val style = attrs.choice("Style", "default", "primary", "flat")
        val name = when (style) {
            "primary" -> "Button"
            "flat" -> "TextButton"
            else -> "OutlinedButton"
        }
        val args = mutableListOf("onClick = ${stub(action(attrs))}")
        modifier?.let { args += "modifier = $it" }
        enabled(attrs)?.let { args += it }
        use("Text")
        leaf(element, attrs, name, args, trailing = "{ Text(${attrs.text()}) }")
    }

    private fun checkbox(element: XmlElement, attrs: Attrs, modifier: String?, choice: RadioChoice?) {
        val text = attrs.text()
        val style = attrs.choice("Style", "check", "switch", "radio")
        val action = action(attrs)
        val args = mutableListOf<String>()
        val name: String
        // (a radio with no panel around it, the root, is a group of its own)
        val radio = choice ?: if (style == "radio") RadioChoice(radioGroup(listOf(element)), 0) else null
        if (radio != null) {
            name = "RadioButton"
            args += "selected = ${radio.group} == ${radio.index}"
            args += "onClick = { ${radio.group} = ${radio.index}${action?.let { " /* $it */" } ?: ""} }"
            // The group's state holds which one is checked, from the written Checked values.
            attrs.value("Checked")?.let { if (it is Value.Code) attrs.note("Checked", "a radio button's Checked from an expression isn't translated") }
        } else {
            name = if (style == "switch") "Switch" else "Checkbox"
            val checked = attrs.truth("Checked")
            val variable = state(attrs, if (name == "Switch") "switch" else "checkbox", checked?.code ?: "false")
            args += "checked = $variable"
            args += "onCheckedChange = { $variable = it${action?.let { " /* $it */" } ?: ""} }"
        }
        enabled(attrs)?.let { args += it }
        if (text == "\"\"") {
            modifier?.let { args += "modifier = $it" }
            return leaf(element, attrs, name, args)
        }
        // Raven draws the text beside the box; Compose's toggles have none, so a Row holds both.
        val comment = attrs.reportUnused(name)
        use("Row", "Alignment", name, "Text")
        val rowArgs = listOfNotNull(modifier, "verticalAlignment = Alignment.CenterVertically")
        out.block(call("Row", rowArgs), comment) {
            out.line(call(name, args))
            out.line("Text($text)")
        }
    }

    private fun slider(element: XmlElement, attrs: Attrs, modifier: String?) {
        val min = attrs.num("Min")
        val max = attrs.num("Max")
        val value = attrs.num("Value")
        val low = min?.constant ?: 0f
        val high = max?.constant ?: 1f
        val initial = when {
            value == null -> min?.let(::kotlinFloat) ?: "0f"
            value.constant != null && (min == null || min.constant != null) && (max == null || max.constant != null) ->
                float(value.constant.coerceIn(minOf(low, high), maxOf(low, high)))
            else -> kotlinFloat(value)
        }
        val name = state(attrs, "slider", initial)
        val args = mutableListOf("value = $name", "onValueChange = { $name = it${action(attrs)?.let { " /* $it */" } ?: ""} }")
        modifier?.let { args += "modifier = $it" }
        enabled(attrs)?.let { args += it }
        if (min?.constant != 0f && min != null || max?.constant != 1f && max != null) {
            args += "valueRange = ${min?.let(::kotlinFloat) ?: "0f"}..${max?.let(::kotlinFloat) ?: "1f"}"
        }
        attrs.num("Step")?.let { step ->
            val constant = step.constant
            if (constant != null && constant <= 0f) return@let
            // Compose counts the values between the ends; Raven gives the distance between two.
            val positions = if (constant != null && (min == null || min.constant != null) && (max == null || max.constant != null)) (high - low) / constant else null
            val count = positions?.let { kotlin.math.round(it) }
            if (positions != null && count != null && count >= 2f && kotlin.math.abs(positions - count) < 1e-4f) {
                args += "steps = ${count.toInt() - 1}"
            } else {
                warn(element, "Step=\"${attrs.peek("Step")}\" doesn't divide the range into steps Slider can count; it slides freely")
            }
        }
        if (orientation(attrs, vertical = false) == "vertical") warn(element, "Slider is horizontal only")
        leaf(element, attrs, "Slider", args)
    }

    // ---- templates ----

    /** A call to a `<Widget>`'s function: parameters, the modifier for its root, and its slots. */
    private fun call(element: XmlElement, attrs: Attrs, function: WidgetFunction, parent: Parent) {
        if (scope?.inside(function) == true) return todo(element, "<${element.tag}> uses itself, which a <Widget> can't")
        val args = mutableListOf<String>()
        val modifier = if (parent == Parent.Passed) {
            modifier(attrs, parent)
        } else {
            // The root's own placing attributes, overridden by those written here, placed in this parent.
            val merged = LinkedHashMap(function.rootLayout)
            for ((attribute, value) in element.attributes) {
                if (attribute.lowercase() in layoutKeys) merged[attribute.lowercase()] = attribute to value
            }
            val layout = Attrs(XmlElement(element.tag, merged.values.toMap(), line = element.line))
            modifier(layout, parent).also { attrs.absorb(layout, merged.keys) }
        }
        modifier?.let { args += "modifier = $it" }
        for (param in function.params) {
            val value = attrs.value(param.xmlName) ?: continue
            attrs.typed(param.xmlName, value, param.type)?.let { args += "${param.name} = $it" }
        }
        // <Card>some text</Card>: the text is the Text parameter.
        if (element.text.isNotEmpty() && attrs.peek("Text") == null) {
            val param = function.params.firstOrNull { it.xmlName.equals("Text", ignoreCase = true) }
            if (param != null) {
                attrs.typed("Text", attrs.parse("Text", element.text) ?: Value.Literal(""), param.type)?.let { args += "${param.name} = $it" }
            } else {
                attrs.note("Text", "the text inside <${element.tag}> has no parameter to go to")
            }
        }
        val comment = attrs.reportUnused(function.name)
        val groups = LinkedHashMap<String, MutableList<XmlElement>>()
        for (child in element.children) {
            val into = if (child.isComment) "" else child.attributes.entries.firstOrNull { it.key.equals("Into", ignoreCase = true) }?.value.orEmpty()
            if (function.slots[into.lowercase()] == null) {
                if (!child.isComment) warn(child, "<${function.xmlName}> has no ${if (into.isEmpty()) "<Children/>" else "<Children Name=\"$into\"/>"}; <${child.tag}> is left out")
                continue
            }
            groups.getOrPut(into.lowercase()) { mutableListOf() } += child
        }
        val named = groups.filterKeys { it.isNotEmpty() }
        val content = groups[""]
        if (named.isEmpty()) {
            val header = call(function.name, args)
            if (content == null) out.line(header, comment) else out.block(header, comment) { children(content, function.slots.getValue("").parent) }
            return
        }
        out.line("${function.name}(", comment)
        out.depth++
        args.forEach { out.line("$it,") }
        for ((key, kids) in named) {
            val slot = function.slots.getValue(key)
            out.line("${slot.name} = {")
            out.depth++
            children(kids, slot.parent)
            out.depth--
            out.line("},")
        }
        out.depth--
        if (content == null) out.line(")") else out.block(")") { children(content, function.slots.getValue("").parent) }
    }

    /** `<Children/>` inside a `<Widget>`: a call to its slot. */
    private fun slot(element: XmlElement, attrs: Attrs) {
        val function = scope?.widget ?: return todo(element, "<Children/> only means something inside a <Widget>")
        val slot = function.slots[attrs.raw("Name").orEmpty().lowercase()] ?: return
        if (element.children.any { !it.isComment }) warn(element, "the default content of <Children> is left out")
        out.line("${slot.name}()")
    }

    /** `<If Test="...">`: its content, when the test is true. */
    private fun ifTest(element: XmlElement, attrs: Attrs, parent: Parent) {
        val test = attrs.condition("Test") ?: return todo(element, "<If> needs Test=\"...\"")
        when (test.constant) {
            true -> children(element.children, parent)
            false -> out.line("// raven-xml: <If> left out by Test=\"${attrs.peek("Test")}\"")
            null -> out.block("if (${test.code})") { children(element.children, parent) }
        }
    }

    /** `<Repeat Count="3" Var="i">`: `repeat(3) { i -> }`. */
    private fun repeat(element: XmlElement, attrs: Attrs, parent: Parent) {
        val count = attrs.num("Count") ?: return todo(element, "<Repeat> needs Count=\"...\"")
        val times = when {
            count.constant == null -> count.expr.to(KType.Int).code
            count.constant > 10000f -> "10000".also { warn(element, "<Repeat Count=\"${attrs.peek("Count")}\">: limited to 10000") }
            else -> count.constant.toInt().toString()
        }
        val variable = attrs.raw("Var").orEmpty().ifEmpty { "Index" }
        val name = unique(identifier(variable), names)
        loop(element, parent, "repeat($times) { $name ->", mapOf(variable.lowercase() to Variable(name, KType.Int)))
    }

    /** `<ForEach Items="a|b" Var="x" Index="n">`: `listOf("a", "b").forEachIndexed { n, x -> }`. */
    private fun forEach(element: XmlElement, attrs: Attrs, parent: Parent) {
        val separator = attrs.raw("Separator")?.firstOrNull() ?: '|'
        val items = when (val value = attrs.value("Items") ?: return todo(element, "<ForEach> needs Items=\"a|b|c\"")) {
            is Value.Literal -> {
                // As Raven splits them: an empty last item is no item.
                val list = mutableListOf<String>()
                var rest = value.text
                while (rest.isNotEmpty()) {
                    list += rest.substringBefore(separator)
                    rest = if (separator in rest) rest.substringAfter(separator) else ""
                }
                if (list.isEmpty()) "emptyList<String>()" else "listOf(${list.joinToString(", ") { kotlinString(it) }})"
            }
            is Value.Code -> "${value.expr.to(KType.Str).parenthesized}.split(${charLiteral(separator)})"
        }
        val itemVar = attrs.raw("Var").orEmpty().ifEmpty { "Item" }
        val indexVar = attrs.raw("Index").orEmpty().ifEmpty { "Index" }
        val item = unique(identifier(itemVar), names)
        val index = unique(identifier(indexVar), names)
        val vars = mapOf(itemVar.lowercase() to Variable(item, KType.Str), indexVar.lowercase() to Variable(index, KType.Int))
        loop(element, parent, "$items.forEachIndexed { $index, $item ->", vars)
    }

    /** A loop: its body has the loop's variables, and declares its own state, one per iteration. */
    private fun loop(element: XmlElement, parent: Parent, header: String, vars: Map<String, Variable>) {
        val outer = out
        val outerHolder = holder
        val outerScope = scope
        out = CodeWriter(outer.depth + 1)
        holder = Holder()
        scope = Scope(outerScope, vars)
        children(element.children, parent)
        val inner = out
        val innerHolder = holder
        out = outer
        holder = outerHolder
        scope = outerScope
        if (inner.lines.isEmpty() && innerHolder.state.isEmpty()) return out.line("$header }")
        out.line(header)
        out.addAll(innerHolder.state.map { "    ".repeat(out.depth + 1) + it })
        out.addAll(inner.lines)
        out.line("}")
    }

    // ---- layout ----

    /**
     * The Modifier for an element's slot and size in [parent], or null when it needs none. A
     * `<Widget>`'s root takes the call's `modifier` last, so what the call places wins.
     */
    private fun modifier(attrs: Attrs, parent: Parent): String? {
        val parts = mutableListOf<String>()
        if (parent == Parent.Passed) {
            // What is written out is placed by each call; only what depends on parameters is here.
            for (key in layoutKeys) {
                val raw = attrs.peek(key) ?: continue
                if ('{' !in raw && '}' !in raw) attrs.raw(key)
                else if (key.startsWith("slot.")) attrs.note(key, "a <Widget>'s root can't be placed by an expression").also { attrs.raw(key) }
            }
            sizes(attrs, parts, computedOnly = true)
            use("Modifier")
            return if (parts.isEmpty()) "modifier" else "Modifier.${parts.joinToString(".")}.then(modifier)"
        }
        val h = attrs.choice("Slot.HAlign", "fill", "left", "center", "right") ?: "fill"
        val v = attrs.choice("Slot.VAlign", "fill", "top", "center", "bottom") ?: "fill"
        val auto = attrs.truth("Slot.Auto")?.constant == true
        val fill = attrs.num("Slot.Fill")?.takeIf { !auto && (it.constant == null || it.constant > 0f) }
        when (parent) {
            Parent.Column, Parent.VerticalScroll -> {
                parts += horizontal(h)
                if (fill != null && parent == Parent.Column) parts += "weight(${kotlinFloat(fill)})"
            }
            Parent.Row, Parent.HorizontalScroll -> {
                parts += vertical(v)
                if (fill != null && parent == Parent.Row) parts += "weight(${kotlinFloat(fill)})"
            }
            Parent.Box, Parent.Window -> parts += both(h, v)
            Parent.Splitter -> if (fill != null) parts += "weight(${kotlinFloat(fill)})"
            Parent.None -> if (h != "fill" || v != "fill" || fill != null) {
                attrs.warn("the root's slot has no parent to place it; Slot attributes are left out")
            }
            Parent.Passed -> error("handled above")
        }
        attrs.margin("Slot.Padding")?.let { parts += padding(it) }
        sizes(attrs, parts)
        if (parts.isEmpty()) return null
        use("Modifier")
        return "Modifier." + parts.joinToString(".")
    }

    /** Size, limits and tooltip; inside a `<Widget>`'s root, only those computed from parameters. */
    private fun sizes(attrs: Attrs, parts: MutableList<String>, computedOnly: Boolean = false) {
        fun num(name: String) = if (computedOnly && !attrs.computed(name)) null else attrs.num(name)
        val width = num("Width")
        val height = num("Height")
        when {
            width != null && height != null -> parts += "size(${dp(width)}, ${dp(height)})".also { use("size") }
            width != null -> parts += "width(${dp(width)})".also { use("width") }
            height != null -> parts += "height(${dp(height)})".also { use("height") }
        }
        limits(::num, "MinWidth", "MaxWidth")?.let { parts += "widthIn($it)".also { use("widthIn") } }
        limits(::num, "MinHeight", "MaxHeight")?.let { parts += "heightIn($it)".also { use("heightIn") } }
        if (!computedOnly || attrs.computed("Tooltip")) attrs.str("Tooltip")?.let { parts += "tooltip($it)".also { use("tooltip") } }
    }

    /** Whether the window's content fills it both ways, so it needs no Box to be aligned in. */
    private fun windowContentFills(attrs: Attrs, function: WidgetFunction?): Boolean {
        fun align(key: String) = attrs.peek(key) ?: function?.rootLayout?.get(key.lowercase())?.second ?: "fill"
        return align("Slot.HAlign").equals("fill", ignoreCase = true) && align("Slot.VAlign").equals("fill", ignoreCase = true)
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

    private fun padding(margin: List<Num>): String {
        use("padding")
        return when (margin.size) {
            1 -> "padding(${dp(margin[0])})"
            2 -> "padding(horizontal = ${dp(margin[0])}, vertical = ${dp(margin[1])})"
            else -> "padding(start = ${dp(margin[0])}, top = ${dp(margin[1])}, end = ${dp(margin[2])}, bottom = ${dp(margin[3])})"
        }
    }

    private fun limits(num: (String) -> Num?, minName: String, maxName: String): String? {
        val min = num(minName)
        val max = num(maxName)
        return listOfNotNull(min?.let { "min = ${dp(it)}" }, max?.let { "max = ${dp(it)}" }).joinToString(", ").ifEmpty { null }
    }

    private fun spacing(attrs: Attrs, parameter: String): String? =
        attrs.num("Spacing")?.takeIf { it.constant != 0f }?.let {
            use("Arrangement")
            "$parameter = Arrangement.spacedBy(${dp(it)})"
        }

    private fun orientation(attrs: Attrs, vertical: Boolean): String =
        attrs.choice("Orientation", "horizontal", "vertical") ?: if (vertical) "vertical" else "horizontal"

    private fun dp(value: Num): String {
        use("dp")
        return value.constant?.let { "${number(it)}.dp" } ?: "${value.expr.parenthesized}.dp"
    }

    private fun sp(value: Num): String {
        use("sp")
        return value.constant?.let { "${number(it)}.sp" } ?: "${value.expr.parenthesized}.sp"
    }

    private fun kotlinFloat(value: Num): String = value.constant?.let(::float) ?: value.expr.to(KType.Float).code

    // ---- state and actions ----

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
            radio.attributes.entries.any { (name, value) -> name.equals("Checked", ignoreCase = true) && ravenTruth(value) }
        }
        val name = unique("radioGroup", names, numbered = true)
        holder.state += "var $name by remember { mutableStateOf($checked) }"
        use("getValue", "setValue", "mutableStateOf", "remember")
        return name
    }

    private fun state(attrs: Attrs, base: String, initial: String): String {
        val written = attrs.raw("Name", "Id")
        val name = unique(written?.let(::identifier) ?: base, names, numbered = written == null)
        holder.state += "var $name by remember { mutableStateOf($initial) }"
        use("getValue", "setValue", "mutableStateOf", "remember")
        return name
    }

    private fun action(attrs: Attrs): String? = attrs.raw(*actionNames)

    private fun stub(action: String?): String = if (action == null) "{}" else "{ /* ${action.replace("*/", "* /")} */ }"

    private fun enabled(attrs: Attrs): String? = attrs.truth("Enabled")?.let { flag("enabled", it, unless = true) }

    /** `name = value`, or null when the value is Compose's default ([unless]). */
    private fun flag(name: String, value: Truth, unless: Boolean): String? = when (value.constant) {
        unless -> null
        null -> "$name = ${value.code}"
        else -> "$name = ${value.constant}"
    }

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
     * warns about the rest. Inside a `<Widget>` or a loop, `{...}` is computed; elsewhere braces are
     * ordinary characters, as in Raven.
     */
    private inner class Attrs(private val element: XmlElement) {
        private val byName = element.attributes.entries.associateBy { it.key.lowercase() }
        private val read = mutableSetOf<String>()
        private val notes = mutableListOf<String>()
        private val scope = this@Translator.scope

        private val failed = mutableListOf<Pair<String, String>>()

        /** TODO lines for expressions that failed to parse, in the order of the attributes, to go in the element's place. */
        val todos: List<String>
            get() {
                val order = element.attributes.keys.map { it.lowercase() }
                return failed.sortedBy { (name, _) -> order.indexOf(name.lowercase()).let { if (it < 0) order.size else it } }.map { it.second }
            }

        fun peek(name: String): String? = byName[name.lowercase()]?.value

        /** Whether [name] is written with braces where they are computed. */
        fun computed(name: String): Boolean = scope != null && peek(name)?.let { '{' in it || '}' in it } == true

        /** The first of [names] that is written, as written, marking every one of them read. */
        fun raw(vararg names: String): String? {
            var found: String? = null
            for (name in names) {
                read += name.lowercase()
                if (found == null) found = byName[name.lowercase()]?.value
            }
            return found
        }

        /** The first of [names] that is written, computed. Null when absent, or when it couldn't be. */
        fun value(vararg names: String): Value? {
            val name = names.firstOrNull { byName.containsKey(it.lowercase()) } ?: return null.also { names.forEach { read += it.lowercase() } }
            return parse(byName.getValue(name.lowercase()).key, raw(*names)!!)
        }

        /** [text] read as the value of [name]. */
        fun parse(name: String, text: String): Value? {
            val scope = scope
            if (scope == null || ('{' !in text && '}' !in text)) return Value.Literal(text)
            return try {
                val pieces = segments(text)
                val compiled = pieces.map { piece ->
                    when (piece) {
                        is Segment.Text -> piece.text to null
                        is Segment.Expression -> null to compileExpression(piece.source, scope::find, scope.widget != null, ::use)
                    }
                }
                when {
                    compiled.all { it.second == null } -> Value.Literal(compiled.joinToString("") { it.first.orEmpty() })
                    compiled.size == 1 -> Value.Code(compiled.single().second!!)
                    else -> Value.Code(KExpr(stringTemplate(compiled), KType.Str, atomic = true))
                }
            } catch (e: ExpressionException) {
                failed += name to "// TODO(raven-xml): $name=\"$text\": ${e.message}"
                warn("$name=\"$text\": in an expression: ${e.message}")
                null
            } catch (e: InstanceException) {
                notes += "$name=\"$text\": {Instance} has no equivalent"
                null
            }
        }

        /** The text of the element: its Text attribute, or what is written inside it. */
        fun text(): String = str("Text") ?: str(parse("Text", element.text)) ?: "\"\""

        fun str(vararg names: String): String? = str(value(*names))

        fun str(value: Value?): String? = when (value) {
            null -> null
            is Value.Literal -> kotlinString(value.text)
            is Value.Code -> value.expr.to(KType.Str).code
        }

        fun num(vararg names: String): Num? = when (val value = value(*names)) {
            null -> null
            is Value.Literal -> value.text.trim().toFloatOrNull()?.let { constant ->
                val integral = '.' !in value.text && constant == kotlin.math.floor(constant)
                Num(constant, KExpr(if (integral) constant.toInt().toString() else float(constant), if (integral) KType.Int else KType.Float, atomic = true))
            } ?: null.also { invalid(names.first(), value.text, "not a number") }
            is Value.Code -> Num(null, value.expr.toNumber())
        }

        fun truth(name: String): Truth? = when (val value = value(name)) {
            null -> null
            is Value.Literal -> when (value.text.trim().lowercase()) {
                in trueWords -> Truth(true, "true")
                in falseWords -> Truth(false, "false")
                else -> null.also { invalid(name, value.text, "not a boolean") }
            }
            is Value.Code -> Truth(null, value.expr.toBool().code)
        }

        /** A condition, read as Raven reads If and Test: anything but "", 0 and false is true. */
        fun condition(name: String): Truth? = when (val value = value(name)) {
            null -> if (byName.containsKey(name.lowercase())) Truth(false, "false") else null
            is Value.Literal -> ravenTruth(value.text).let { Truth(it, it.toString()) }
            is Value.Code -> Truth(null, value.expr.toBool().code)
        }

        fun choice(name: String, vararg choices: String): String? = when (val value = value(name)) {
            null -> null
            is Value.Literal -> value.text.trim().lowercase().takeIf { it in choices }
                ?: null.also { invalid(name, value.text, "not one of ${choices.joinToString()}") }
            is Value.Code -> null.also { note(name, "an expression can't choose this; the default is used") }
        }

        fun margin(name: String): List<Num>? = when (val value = value(name)) {
            null -> null
            is Value.Literal -> {
                val parts = value.text.split(',').map { it.trim().toFloatOrNull() }
                if (parts.size in setOf(1, 2, 4) && parts.all { it != null }) {
                    parts.map { Num(it, KExpr(number(it!!), KType.Int, atomic = true)) }
                } else {
                    null.also { invalid(name, value.text, "not a margin") }
                }
            }
            is Value.Code -> if (value.expr.type == KType.Str) null.also { note(name, "a margin made of text and expressions isn't translated") } else listOf(Num(null, value.expr.toNumber()))
        }

        /** [value] as a parameter of [type]. */
        fun typed(name: String, value: Value, type: KType): String? = when (value) {
            is Value.Code -> value.expr.to(type).code
            is Value.Literal -> when (type) {
                KType.Str -> kotlinString(value.text)
                KType.Bool -> when (value.text.trim().lowercase()) {
                    in trueWords -> "true"
                    in falseWords -> "false"
                    else -> null.also { invalid(name, value.text, "not a boolean") }
                }
                KType.Int -> value.text.trim().toFloatOrNull()?.let { number ->
                    if (number != kotlin.math.floor(number)) warn("${byName[name.lowercase()]?.key ?: name}=\"${value.text}\": not an integer; ${number.toInt()} is used")
                    number.toInt().toString()
                } ?: null.also { invalid(name, value.text, "not a number") }
                KType.Float -> value.text.trim().toFloatOrNull()?.let(::float) ?: null.also { invalid(name, value.text, "not a number") }
            }
        }

        /** Takes the notes and TODOs of [other], an Attrs read for this element, and marks [keys] read. */
        fun absorb(other: Attrs, keys: Collection<String>) {
            notes += other.notes
            failed += other.failed
            read += keys
        }

        /** A value that can't be used: left out, with a warning. */
        fun invalid(name: String, value: String, why: String) {
            val shown = byName[name.lowercase()]?.key ?: name
            notes += "$shown=\"$value\": $why"
            warn("$shown=\"$value\": $why")
        }

        fun note(name: String, why: String) {
            val shown = byName[name.lowercase()]?.key ?: name
            notes += "$shown: $why"
            warn("$shown: $why")
        }

        fun warn(message: String) = this@Translator.warn(element, message)

        /** Warns about each attribute nothing read, and returns the trailing comment for all of them. */
        fun reportUnused(composable: String): String? {
            for ((name, value) in element.attributes) {
                val key = name.lowercase()
                if (key in read || key in quietKeys) continue
                read += key
                notes += "$name=\"$value\""
                warn("$name=\"$value\" has no equivalent on $composable")
            }
            return if (notes.isEmpty()) null else "raven-xml: " + notes.joinToString("; ") + " left out"
        }
    }
}

// Read elsewhere (If, Into) or with no meaning in Compose (Name), or ignored by the parent in Raven too.
private val quietKeys = setOf("name", "id", "if", "into", "slot.halign", "slot.valign", "slot.fill", "slot.auto")

private val trueWords = setOf("true", "yes", "1")
private val falseWords = setOf("false", "no", "0")

/** Raven's truth of a text: a number that isn't 0, or a text that isn't empty or "false". */
private fun ravenTruth(text: String): Boolean {
    text.takeIf { it.isNotEmpty() && it[0] != ' ' }?.toDoubleOrNull()?.let { return it != 0.0 }
    return text.isNotEmpty() && !text.equals("false", ignoreCase = true)
}

private fun charLiteral(c: Char): String = when (c) {
    '\\' -> "'\\\\'"
    '\'' -> "'\\''"
    else -> "'$c'"
}

/** [base], numbered when [numbered] or when it is taken already, and marked taken. */
private fun unique(base: String, taken: MutableSet<String>, numbered: Boolean = false): String {
    var n = 1
    var name = if (numbered) "$base$n" else base
    while (!taken.add(name)) name = "$base${++n}"
    return name
}
