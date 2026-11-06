---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0009, 0011]
assignee: Paul Hawke
---

# XML to Compose translator sample

## Question

Raven can describe a UI in XML (`docs/XML.md`, `raven_xml.h`). What does a translator from a Raven XML document to equivalent compose-raven Kotlin source look like, and does it ship as the sample app (XML pasted into the left pane, generated Compose code shown on the right)? Decide which XML elements and attributes it covers (tied to the v1 component vocabulary and to how slots map to `Modifier`), what it does with constructs that have no composable (comment, warn, or refuse), how it parses (Raven's own XML parser through `raven-native`, or a Kotlin parser), and whether it is a sample-only tool or a reusable module.

## Resolution

- **Location and parsing**: the translator lives in `sample` and parses with Raven's own parser through a **private cinterop**. `ravenxml.def` uses `package = raven.sample.xml.cinterop` and a `headerFilter` of `raven_xml.h`, plus `Rinit_ex`/`Rshutdown`/`RInitDesc` from `raven_core.h` for tests. It has **no `staticLibraries`**, because `libraven.a` already comes through `raven-native`'s klib and bundling it twice would give duplicate symbols. The rest of `sample` still depends only on `compose-raven-foundation` ([Layering over cinterop](0006-cinterop-layering.md)). The goal is to extract this into a shared module once it has proved useful (Translator as a tool fog).
- **Parse adapter**: one internal `parseRavenXml(text)` calls `Rxml_document_parse` (which needs Raven running, on the UI thread). It copies the result into a plain Kotlin tree `XmlElement(tag, attributes, text, line, children)`, keeping comments and line numbers, then destroys the document. Only this function touches Raven, so extraction later is just moving it.
- **Translator**: `translate(xml: String): Translation(code, warnings)`, working on the `XmlElement` tree. Tag and attribute names match case-insensitively, through a Kotlin alias table taken from `docs/XML.md` (`VerticalBox` is `VBox`, `TextBox` is `TextInput`, ...). The code does not rely on `Rxml_node_get_tag` returning canonical names.
- **Mapping** (exactly the [v1 component vocabulary](0011-v1-component-vocabulary.md)): `Window` (Title, Width, Height) becomes `fun main() = ravenApplication(...)`. `VBox`/`HBox`/`Overlay` become `Column`/`Row`/`Box`. `ScrollBox` becomes `VerticalScroll`/`HorizontalScroll`, and `Splitter` becomes `HorizontalSplitter`/`VerticalSplitter`. `Label` becomes `Text`. `Button` by `Style` becomes `Button`/`OutlinedButton`/`TextButton { Text(...) }`. `Checkbox` by `Style` becomes `Checkbox`/`Switch`/`RadioButton`, each in a `Row` with a `Text`. `TextInput` becomes `TextField`, `TextEditor` becomes `CodeEditor`, `Slider` stays `Slider`, `ProgressBar` becomes `LinearProgressIndicator`, `Separator` becomes `Horizontal`/`VerticalDivider`, and `Spacer` stays `Spacer`. `Slot.*`, `Width`/`Height` and `Min*`/`Max*` become Modifiers, and `Spacing` becomes `Arrangement.spacedBy`.
- **Faithful output**: wherever the XML relies on Raven's `Fill` default, explicit `fillMax*` modifiers are added, so the result lays out the same ([Layout and Modifiers](0009-layout-and-modifiers.md)).
- **State and actions**: each input gets a hoisted `var <name> by remember { mutableStateOf(<initial>) }`. The name comes from `Name` or is generated (`checkbox1`), and the initial value comes from `Checked`/`Value`/`Text`. The input is wired controlled (`checked = name, onCheckedChange = { name = it }`). Actions become stub lambdas, for example `onClick = { /* file.save */ }`.
- **Unsupported input** is never refused. An element with no composable becomes `// TODO(raven-xml): <ListView Name="files"> has no composable yet` in place, plus a warning with line and column. An attribute with no equivalent (`Material`, `Color.*`, `Appear`, ...) becomes a warning plus a trailing comment. Templates (`<Widget>`, `If`, `Repeat`, `ForEach`, `Include`) are handled the same way in v1: [Templates in the XML translator](0016-xml-translator-templates.md).
- **Output shape**: one compilable file containing imports, then `@Composable fun TranslatedUi()` with the hoisted state at the top, then `main()` when the root is `<Window>`. Any other root gives the function alone. The indent is 4 spaces.
- **Sample UI**: a `HorizontalSplitter`. The left pane is `CodeEditor(xml, language = "XML")`, seeded with the example from `XML.md`. The right pane is `CodeEditor(code, readOnly = true, language = "Java")`: Raven has no Kotlin highlighting yet, which is documented ([Kotlin highlighting in CodeEditor](0017-kotlin-highlighting.md)). A `Text` below shows the warnings. Translation reruns on every change, with no button and no debounce.
- **Tests**: most tests build `XmlElement` trees and check the generated code, as plain unit tests. A few headless smoke tests (`Rinit_ex` with the null backend through the private cinterop) cover `parseRavenXml`.
