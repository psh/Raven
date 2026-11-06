---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0013]
assignee: Paul Hawke
---

# Templates in the XML translator

## Question

How does the XML translator handle Raven's templates, which v1 treats as unsupported? `<Widget>` declarations would become `@Composable` functions with parameters (and `<Children/>` / `Into=` would become content slots), `<Repeat>` and `<ForEach>` would become loops, `If=` / `<If>` would become `if`, and `<Include>` would be handled somehow. How much of Raven's `{...}` expression language is translated into Kotlin expressions, and what is left as a TODO?

## Resolution

The running example is `examples/components/widgets.xml` (`Badge`, `Stat`, `Card`).

- **Declarations**: each `<Widget Name="Card" Title="Untitled" Badge="" Wide="false">` becomes a top-level `@Composable fun Card(modifier: Modifier = Modifier, title: String = "Untitled", badge: String = "", wide: Boolean = false, footer: @Composable () -> Unit = {}, content: @Composable () -> Unit = {})`. These functions are emitted before `TranslatedUi()` wherever the declaration appears.
  - Parameters are renamed to lowerCamelCase.
  - Types come from the default value: `true`/`false` gives `Boolean`, a number with a dot `Float`, a whole number `Int`, anything else `String`.
  - Inputs inside a declaration get `remember` state in that function, so each use has its own state, as each Raven instance has its own widget.
  - A `<Widgets>` root produces only the functions, with no `TranslatedUi()`.
- **Slots**: `<Children/>` becomes the last parameter, `content: @Composable () -> Unit`, called where it sits (a trailing lambda at the use site). `<Children Name="footer"/>` becomes `footer: @Composable () -> Unit = {}`. Use-site children with `Into="footer"` go to `footer = { ... }`, and the rest go to the trailing lambda.
- **Use-site attributes that are not parameters**: layout ones (`Slot.*`, `Width`, `Height`, `Min*`/`Max*`) become the call's `modifier`, which the function passes to its root composable. `Name` only names hoisted state. Any other attribute that would pass through (`OnClicked`, `Material`, ...) becomes a warning.
- **Expressions**: a small parser translates the whole `{...}` language. Parameters and variables become identifiers, `a ? b : c` becomes `if (a) b else c`, and comparisons, `&&`, `||` and `!` stay as they are. `+` on text becomes a string template. `min`/`max` become `minOf`/`maxOf`, and `round`/`floor`/`ceil`/`abs` become their `kotlin.math` versions. `len(x)` becomes `x.length`, `upper`/`lower` become `uppercase()`/`lowercase()`, `empty(x)` becomes `x.isEmpty()`, and `default(v, f)` becomes `v.ifEmpty { f }`. Interpolated text (`"{Label}:"`) becomes `"$label:"`. The required type comes from the target attribute, so `Width="{Wide ? 452 : 220}"` becomes `Modifier.width((if (wide) 452 else 220).dp)`. `{Instance}` (only for unique names) becomes a comment. Anything that fails to parse becomes a TODO plus a warning.
- **Control flow**: `If="{...}"` and `<If Test="...">` become `if (...) { }`. `<Repeat Count="3" Var="i">` becomes `repeat(3) { i -> }`, and `Count` may be an expression. `<ForEach Items="a|b|c" Var="x" Index="n" Separator="|">` becomes `listOf("a", "b", "c").forEachIndexed { n, x -> }`, split at translation time. There is no `key {}`, because the lists are fixed.
- **`<Include>`**: stays a TODO plus a warning in the sample, which has no files. Following includes, one set of functions per file, belongs to the Translator as a tool fog.
- **Raven's registry**: Raven registers `<Widget>` declarations globally until shutdown, and the sample retranslates on every keystroke. The implementation first verifies that `Rxml_document_parse` registers nothing and leaves `<Widget>` unexpanded. If parsing does register them, `parseRavenXml` calls `Rxml_unregister_widget` for each declared name afterwards.
