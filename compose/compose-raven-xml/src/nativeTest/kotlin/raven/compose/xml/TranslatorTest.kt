package raven.compose.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslatorTest {
    @Test
    fun theExampleOfXmlMdTranslatesToOneFile() {
        val window = el(
            "Window", "Title" to "Tool", "Width" to "800", "Height" to "500", line = 1,
            children = listOf(
                el("Shortcut", "Keys" to "Ctrl+S", "Action" to "file.save", line = 2),
                el(
                    "VBox", "Spacing" to "8", "Slot.Padding" to "12", line = 3,
                    children = listOf(
                        el("Label", "TextSize" to "18", text = "Settings", line = 4),
                        el(
                            "HBox", "Spacing" to "8", line = 5,
                            children = listOf(
                                el("TextInput", "Name" to "path", "Placeholder" to "Folder...", "Slot.Fill" to "1", line = 6),
                                el("Button", "Text" to "Save", "Style" to "Primary", "OnClicked" to "file.save", line = 7),
                            ),
                        ),
                        el("ListView", "Name" to "files", "Slot.Fill" to "1", line = 8),
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
            import raven.compose.foundation.Button
            import raven.compose.foundation.Text
            import raven.compose.foundation.TextField
            import raven.compose.foundation.layout.Arrangement
            import raven.compose.foundation.layout.Column
            import raven.compose.foundation.layout.Row
            import raven.compose.foundation.layout.fillMaxHeight
            import raven.compose.foundation.layout.fillMaxSize
            import raven.compose.foundation.layout.fillMaxWidth
            import raven.compose.foundation.layout.padding
            import raven.compose.ui.Modifier
            import raven.compose.ui.WindowState
            import raven.compose.ui.singleWindowApplication
            import raven.compose.ui.unit.DpSize
            import raven.compose.ui.unit.dp
            import raven.compose.ui.unit.sp

            @Composable
            fun TranslatedUi() {
                var path by remember { mutableStateOf("") }
                // TODO(raven-xml): <Shortcut> has no composable yet
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Settings", modifier = Modifier.fillMaxWidth(), fontSize = 18.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = path,
                            onValueChange = { path = it },
                            modifier = Modifier.fillMaxHeight().weight(1f),
                            placeholder = "Folder...",
                        )
                        Button(onClick = { /* file.save */ }, modifier = Modifier.fillMaxHeight()) { Text("Save") }
                    }
                    // TODO(raven-xml): <ListView> has no composable yet
                }
            }

            fun main() = singleWindowApplication(state = WindowState(size = DpSize(800.dp, 500.dp)), title = "Tool") {
                TranslatedUi()
            }

            """.trimIndent(),
            translation.code,
        )
        assertEquals(
            listOf("line 2: <Shortcut> has no composable yet", "line 8: <ListView> has no composable yet"),
            translation.warnings.map { it.toString() },
        )
    }

    @Test
    fun namesAreMatchedWhateverTheirCaseAndThroughAliases() {
        val body = body(
            el(
                "verticalbox", "SPACING" to "4",
                children = listOf(el("TextBlock", "fontsize" to "12", text = "a"), el("CHECKBOX", "text" to "b", "checked" to "YES")),
            ),
        )
        assertEquals(
            """
            var checkbox1 by remember { mutableStateOf(true) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("a", modifier = Modifier.fillMaxWidth(), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checkbox1, onCheckedChange = { checkbox1 = it })
                    Text("b")
                }
            }
            """.trimIndent(),
            body,
        )
    }

    @Test
    fun aButtonsStyleChoosesTheComposable() {
        val translation = translate(
            el(
                "HBox",
                children = listOf(
                    el("Button", "Text" to "a", "Slot.VAlign" to "Top"),
                    el("Button", "Text" to "b", "Style" to "Default", "Slot.VAlign" to "Top"),
                    el("Button", "Text" to "c", "Style" to "primary", "Slot.VAlign" to "Top"),
                    el("Button", "Text" to "d", "Style" to "Flat", "Enabled" to "false", "Slot.VAlign" to "Top"),
                    el("Button", "Text" to "e", "Style" to "Fancy", "Slot.VAlign" to "Top", line = 6),
                ),
            ),
        )
        assertEquals(
            """
            Row {
                OutlinedButton(onClick = {}, modifier = Modifier.align(Alignment.Top)) { Text("a") }
                OutlinedButton(onClick = {}, modifier = Modifier.align(Alignment.Top)) { Text("b") }
                Button(onClick = {}, modifier = Modifier.align(Alignment.Top)) { Text("c") }
                TextButton(onClick = {}, modifier = Modifier.align(Alignment.Top), enabled = false) { Text("d") }
                OutlinedButton(onClick = {}, modifier = Modifier.align(Alignment.Top)) { Text("e") } // raven-xml: Style="Fancy": not one of default, primary, flat left out
            }
            """.trimIndent(),
            body(translation),
        )
        assertEquals(listOf("line 6: Style=\"Fancy\": not one of default, primary, flat"), translation.warnings.map { it.toString() })
    }

    @Test
    fun togglesAreControlledByHoistedState() {
        assertEquals(
            """
            var radioGroup1 by remember { mutableStateOf(1) }
            var agree by remember { mutableStateOf(false) }
            var switch1 by remember { mutableStateOf(true) }
            Column {
                Checkbox(
                    checked = agree,
                    onCheckedChange = { agree = it /* settings.agree */ },
                    modifier = Modifier.fillMaxWidth(),
                )
                Switch(
                    checked = switch1,
                    onCheckedChange = { switch1 = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = radioGroup1 == 0, onClick = { radioGroup1 = 0 })
                    Text("Small")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = radioGroup1 == 1, onClick = { radioGroup1 = 1 /* size.large */ })
                    Text("Large")
                }
            }
            """.trimIndent(),
            body(
                el(
                    "VBox",
                    children = listOf(
                        el("Checkbox", "Name" to "agree", "OnChanged" to "settings.agree"),
                        el("Checkbox", "Style" to "Switch", "Checked" to "1"),
                        el("Checkbox", "Style" to "Radio", "Text" to "Small"),
                        el("Checkbox", "Style" to "Radio", "Text" to "Large", "Checked" to "true", "Action" to "size.large"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun inputsAndTheirInitialValues() {
        assertEquals(
            """
            var name by remember { mutableStateOf("Ada") }
            var notes by remember { mutableStateOf("line 1\nline 2 costs \$3 \"now\"") }
            var volume by remember { mutableStateOf(25f) }
            var slider1 by remember { mutableStateOf(0f) }
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    placeholder = "Name",
                )
                CodeEditor(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    language = "Kotlin",
                    lineNumbers = false,
                )
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    modifier = Modifier.fillMaxWidth(),
                    valueRange = 0f..100f,
                    steps = 19,
                )
                Slider(
                    value = slider1,
                    onValueChange = { slider1 = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                LinearProgressIndicator(progress = 0.4f, modifier = Modifier.fillMaxWidth())
            }
            """.trimIndent(),
            body(
                el(
                    "VBox",
                    children = listOf(
                        el("TextInput", "Id" to "name", "Text" to "Ada", "Placeholder" to "Name", "ReadOnly" to "true"),
                        el("TextArea", "Name" to "notes", "Language" to "Kotlin", "LineNumbers" to "false", text = "line 1\nline 2 costs \$3 \"now\""),
                        el("Slider", "Name" to "volume", "Max" to "100", "Value" to "25", "Step" to "5"),
                        el("Slider"),
                        el("ProgressBar", "Value" to "0.4"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun aTableWithColumnsAndStoredRows() {
        val translation = translate(
            el(
                "Table", "Name" to "people", "Selected" to "1", "SortColumn" to "0", "SortDescending" to "true", "GridLines" to "true",
                "SelectionMode" to "Multiple", "FrozenColumns" to "1",
                children = listOf(
                    el("Column", "Header" to "Name", "Width" to "160"),
                    el("Column", "Header" to "Age", "Width" to "60", "Align" to "Right", "Sortable" to "false"),
                    el("Column", "Header" to "City", "Fill" to "1", "Editable" to "true"),
                    el("Row", text = "Ada|36|London"),
                    el("Row", text = "Grace|85"),
                ),
            ),
        )
        assertEquals(
            """
            var people by remember { mutableStateOf(1) }
            val peopleRows = listOf(
                listOf("Ada", "36", "London"),
                listOf("Grace", "85", ""),
            )
            var peopleSort by remember { mutableStateOf<TableSort?>(TableSort(0, descending = true)) }
            val peopleShown = peopleSort?.let { sort ->
                val sorted = peopleRows.sortedWith(
                    compareBy<List<String>>(
                        { it[sort.column].toDoubleOrNull() == null },
                        { it[sort.column].toDoubleOrNull() },
                        { it[sort.column].lowercase() },
                    ),
                )
                if (sort.descending) sorted.reversed() else sorted
            } ?: peopleRows
            Table( // raven-xml: SelectionMode: Table selects one row; FrozenColumns: Table freezes no columns yet left out
                peopleShown,
                people,
                { people = it },
                sort = peopleSort,
                onSortChange = { peopleSort = it },
                gridLines = true,
            ) {
                column("Name", width = 160.dp) { it[0] }
                column("Age", width = 60.dp, align = Alignment.End, sortable = false) { it[1] }
                column("City", fill = 1f) { it[2] } // raven-xml: Editable: Table's cells aren't edited yet left out
            }
            """.trimIndent(),
            body(translation),
        )
        assertTrue("import raven.compose.foundation.TableSort" in translation.code)
        assertEquals(
            setOf("SelectionMode: Table selects one row", "FrozenColumns: Table freezes no columns yet", "Editable: Table's cells aren't edited yet"),
            translation.warnings.map { it.message }.toSet(),
        )
    }

    @Test
    fun aTableOfColumnsWrittenAsOneAttribute() {
        val code = body(translate(el("Table", "Columns" to "A|B", "ShowHeader" to "false", children = listOf(el("Row", text = "1|2")))))
        assertTrue("column(\"A\") { it[0] }" in code && "column(\"B\") { it[1] }" in code, code)
        assertTrue("headerVisible = false" in code && "mutableStateOf(-1)" in code && "mutableStateOf<TableSort?>(null)" in code, code)
    }

    @Test
    fun aTileViewsAttributesBecomeItsParameters() {
        val translation = translate(
            el("TileView", "Name" to "swatches", "TileWidth" to "124", "IconSize" to "96", "Spacing" to "12", "Layout" to "Rows", "EmptyText" to "None", "DragType" to "swatch"),
        )
        assertEquals(
            """
            var swatches by remember { mutableStateOf(emptySet<Any>()) }
            // TODO(raven-xml): <TileView>'s items are the app's to give
            TileView( // raven-xml: DragType="swatch" left out
                items = emptyList<String>(),
                key = { it },
                selection = swatches,
                onSelectionChange = { swatches = it },
                tileSize = DpSize(124.dp, 112.dp),
                iconSize = 96.dp,
                spacing = 12.dp,
                layout = TileLayout.Rows,
                emptyText = "None",
            ) { TileContent(it) }
            """.trimIndent(),
            body(translation),
        )
        assertEquals(listOf("DragType=\"swatch\" has no equivalent on TileView"), translation.warnings.map { it.message })
    }

    @Test
    fun aTreeViewsExpandedItemsAreTheInitialExpandedSet() {
        val translation = translate(
            el(
                "TreeView", "Name" to "library",
                children = listOf(
                    el("Item", "Text" to "Listen now"),
                    el(
                        "Item", "Text" to "Playlists", "Expanded" to "true",
                        children = listOf(el("Item", "Text" to "Favourites"), el("Item", "Text" to "Focus", "Icon" to "raven:folder")),
                    ),
                ),
            ),
        )
        assertEquals(
            """
            var library by remember { mutableStateOf(emptySet<Any>()) }
            var libraryExpanded by remember { mutableStateOf(setOf<Any>("1")) }
            val libraryTexts = mapOf(
                "0" to "Listen now",
                "1" to "Playlists",
                "1.0" to "Favourites",
                "1.1" to "Focus",
            )
            val libraryChildren = mapOf(
                "1" to listOf("1.0", "1.1"),
            )
            TreeView(
                listOf("0", "1"),
                { libraryChildren[it].orEmpty() },
                { libraryTexts.getValue(it) },
                { it },
                library,
                { library = it },
                libraryExpanded,
                { libraryExpanded = it },
            )
            """.trimIndent(),
            body(translation),
        )
        assertEquals(listOf("Icon: an icon is a painter given through itemIcon"), translation.warnings.map { it.message })
    }

    @Test
    fun aTextInputsPasswordMaxLengthAndActionAreTranslated() {
        val translation = translate(el("TextInput", "Password" to "true", "MaxLength" to "20", "OnCommitted" to "login.submit"))
        val code = translation.code
        assertTrue("visualTransformation = PasswordVisualTransformation()" in code, code)
        assertTrue("keyboardActions = KeyboardActions(onDone = { /* login.submit */ })" in code, code)
        assertTrue("maxLength = 20" in code, code)
        assertTrue("import raven.compose.ui.text.input.PasswordVisualTransformation" in code, code)
        assertTrue("import raven.compose.foundation.text.KeyboardActions" in code, code)
        assertEquals(emptyList(), translation.warnings.map { it.toString() }, "nothing left without an equivalent")
    }

    @Test
    fun anyActionNameOfATextInputIsItsEnter() {
        // Raven fires a text input's action on Enter, whichever name the XML gives it.
        for (name in listOf("Action", "OnChanged", "OnCommitted")) {
            val code = translate(el("TextInput", name to "go")).code
            assertTrue("keyboardActions = KeyboardActions(onDone = { /* go */ })" in code, code)
        }
    }

    @Test
    fun noPasswordAndNoLimitEmitNothing() {
        val code = translate(el("TextInput", "Password" to "false", "MaxLength" to "0")).code
        assertTrue("visualTransformation" !in code && "maxLength" !in code, code)
    }

    @Test
    fun aNegativeMaxLengthIsAWarning() {
        val translation = translate(el("TextInput", "MaxLength" to "-3", line = 5))
        assertTrue("maxLength" !in translation.code, translation.code)
        assertEquals(listOf("line 5: MaxLength=\"-3\": negative, so there is no limit"), translation.warnings.map { it.toString() })
    }

    @Test
    fun aSliderStepThatDoesntDivideTheRangeIsAWarning() {
        val translation = translate(el("Slider", "Max" to "10", "Step" to "3", line = 4))
        assertTrue("steps =" !in translation.code)
        assertEquals(
            listOf("line 4: Step=\"3\" doesn't divide the range into steps Slider can count; it slides freely"),
            translation.warnings.map { it.toString() },
        )
    }

    @Test
    fun whatXmlLeavesToFillIsFilledExplicitly() {
        assertEquals(
            """
            Box {
                Column(Modifier.fillMaxSize()) {
                    Text("fills across", modifier = Modifier.fillMaxWidth())
                    Text("centred", modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.fillMaxWidth().weight(1f))
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    Text("fills down", modifier = Modifier.fillMaxHeight())
                    Text("at the bottom", modifier = Modifier.align(Alignment.Bottom).weight(2f))
                }
                HorizontalSplitter(Modifier.align(Alignment.Center)) {
                    Text("pane")
                    Text("wider pane", modifier = Modifier.weight(3f))
                }
                VerticalScroll(Modifier.fillMaxSize()) {
                    Text("scrolls", modifier = Modifier.fillMaxWidth())
                }
            }
            """.trimIndent(),
            body(
                el(
                    "Overlay",
                    children = listOf(
                        el(
                            "VBox",
                            children = listOf(
                                el("Label", text = "fills across"),
                                el("Label", "Slot.HAlign" to "Center", text = "centred"),
                                el("Spacer", "Slot.Fill" to "1"),
                            ),
                        ),
                        el(
                            "HBox", "Slot.VAlign" to "Bottom",
                            children = listOf(
                                el("Label", text = "fills down"),
                                el("Label", "Slot.VAlign" to "Bottom", "Slot.Fill" to "2", text = "at the bottom"),
                            ),
                        ),
                        el(
                            "Splitter", "Slot.HAlign" to "Center", "Slot.VAlign" to "Center",
                            children = listOf(el("Label", text = "pane"), el("Label", "Slot.Fill" to "3", text = "wider pane")),
                        ),
                        // (a ScrollBox stacks its children each at its own size: Slot.Fill does nothing)
                        el("ScrollBox", children = listOf(el("Label", "Slot.Fill" to "1", text = "scrolls"))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun windowContentThatDoesntFillIsAlignedInABox() {
        val code = translate(el("Window", children = listOf(el("Label", "Slot.HAlign" to "Center", "Slot.VAlign" to "Center", text = "Hi")))).code
        assertTrue(
            """
                Box(Modifier.fillMaxSize()) {
                    Text("Hi", modifier = Modifier.align(Alignment.Center))
                }
            """.trimIndent().prependIndent("    ") in code,
            code,
        )
        assertTrue("fun main() = singleWindowApplication {\n    TranslatedUi()\n}" in code, code)
    }

    @Test
    fun aWindowSizeBecomesItsStateAndASideLeftOutKeepsTheDefault() {
        val code = translate(el("Window", "Width" to "640", children = listOf(el("Label", text = "Hi")))).code
        assertTrue("fun main() = singleWindowApplication(state = WindowState(size = DpSize(640.dp, Dp.Unspecified))) {" in code, code)
        assertTrue("import raven.compose.ui.unit.Dp\nimport raven.compose.ui.unit.DpSize\n" in code, code)
    }

    @Test
    fun paddingSizesAndLimitsBecomeModifiers() {
        assertEquals(
            """
            Column {
                Text("a", modifier = Modifier.fillMaxWidth().padding(8.dp))
                Text("b", modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
                Text(
                    "c",
                    modifier = Modifier.fillMaxWidth().padding(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp),
                )
                Text("d", modifier = Modifier.fillMaxWidth().size(120.dp, 0.5.dp))
                Text(
                    "e",
                    modifier = Modifier.fillMaxWidth().width(120.dp).widthIn(min = 40.dp, max = 300.dp),
                )
                Text("f", modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp).tooltip("Help"))
            }
            """.trimIndent(),
            body(
                el(
                    "VBox",
                    children = listOf(
                        el("Label", "Slot.Padding" to "8", text = "a"),
                        el("Label", "Slot.Padding" to "8, 4", text = "b"),
                        el("Label", "Slot.Padding" to "1,2,3,4", text = "c"),
                        el("Label", "Width" to "120", "Height" to "0.5", text = "d"),
                        el("Label", "Width" to "120", "MinWidth" to "40", "MaxWidth" to "300", text = "e"),
                        el("Label", "MinHeight" to "20", "Tooltip" to "Help", text = "f"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun stateNamesComeFromNameOrAreNumbered() {
        val body = body(
            el(
                "HBox",
                children = listOf(
                    el("TextInput", "Name" to "file-path"),
                    el("TextInput", "Name" to "file path"),
                    el("TextInput"),
                    el("TextInput"),
                    el("TextInput", "Name" to "class"),
                    el("TextInput", "Name" to "2nd"),
                ),
            ),
        )
        assertEquals(
            listOf("filePath", "filePath2", "textField1", "textField2", "`class`", "v2nd"),
            body.lines().filter { it.startsWith("var ") }.map { it.removePrefix("var ").substringBefore(" by") },
        )
    }

    @Test
    fun whatHasNoEquivalentIsKeptAsATodoOrAComment() {
        // (outside a <Widget> or a loop, braces are ordinary characters, as in Raven)
        val translation = translate(
            el(
                "VBox", line = 1,
                children = listOf(
                    el("ListView", "Items" to "A|B", line = 2, children = listOf(el("Item", line = 3))),
                    el("Label", "Wrap" to "true", "Color" to "#F00", text = "a", line = 4),
                    el("Label", "Text" to "{Title}", line = 5),
                    el("Include", "Source" to "part.xml", line = 6),
                    el("Box", line = 7),
                ),
            ),
        )
        assertEquals(
            """
            Column {
                // TODO(raven-xml): <ListView> has no composable yet
                Text("a", modifier = Modifier.fillMaxWidth()) // raven-xml: Wrap="true"; Color="#F00" left out
                Text("{Title}", modifier = Modifier.fillMaxWidth())
                // TODO(raven-xml): <Include Source="part.xml"> isn't translated: the file isn't read
                // TODO(raven-xml): <Box> has no composable yet
            }
            """.trimIndent(),
            body(translation),
        )
        assertEquals(
            listOf(
                "line 2: <ListView> has no composable yet",
                "line 4: Wrap=\"true\" has no equivalent on Text",
                "line 4: Color=\"#F00\" has no equivalent on Text",
                "line 6: <Include Source=\"part.xml\"> isn't translated: the file isn't read",
                "line 7: <Box> has no composable yet",
            ),
            translation.warnings.map { it.toString() },
        )
    }

    @Test
    fun anInvalidValueIsAWarning() {
        val translation = translate(el("VBox", children = listOf(el("Label", "Width" to "wide", "Slot.HAlign" to "Middle", text = "a", line = 3))))
        assertEquals(
            listOf(
                "line 3: Slot.HAlign=\"Middle\": not one of fill, left, center, right",
                "line 3: Width=\"wide\": not a number",
            ),
            translation.warnings.map { it.toString() },
        )
    }

    @Test
    fun commentsAreKept() {
        assertEquals(
            """
            Column {
                // the header
                // two lines
                Text("a", modifier = Modifier.fillMaxWidth())
            }
            """.trimIndent(),
            body(el("VBox", children = listOf(XmlElement("", text = " the header\n   two lines "), el("Label", text = "a")))),
        )
    }

    @Test
    fun aWidgetRootHasNoMainAndNoImplicitFill() {
        val translation = translate(el("HBox", "Slot.HAlign" to "Center", line = 1, children = listOf(el("Separator", "Orientation" to "Vertical"))))
        assertFalse("fun main" in translation.code)
        assertEquals(
            """
            Row {
                VerticalDivider(Modifier.fillMaxHeight())
            }
            """.trimIndent(),
            body(translation),
        )
        assertEquals(listOf("line 1: the root's slot has no parent to place it; Slot attributes are left out"), translation.warnings.map { it.toString() })
    }

    @Test
    fun onlyWhatIsUsedIsImported() {
        assertEquals(
            "import androidx.compose.runtime.Composable\nimport raven.compose.foundation.HorizontalDivider\n",
            translate(el("Separator")).code.substringBefore("\n@Composable"),
        )
    }

    private fun el(
        tag: String,
        vararg attributes: Pair<String, String>,
        text: String = "",
        line: Int = 0,
        children: List<XmlElement> = emptyList(),
    ) = XmlElement(tag, mapOf(*attributes), text, line, children)

    /** The body of `TranslatedUi()`, unindented. */
    private fun body(root: XmlElement): String = body(translate(root))

    private fun body(translation: Translation): String =
        translation.code.substringAfter("fun TranslatedUi() {\n").substringBefore("\n}\n").trimIndent()
}
