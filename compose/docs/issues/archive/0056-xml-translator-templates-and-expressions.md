---
status: closed
---

# XML translator: templates and expressions

Translate `<Widget>` declarations into top-level composables, `<Children>` into slot lambdas, the
whole `{...}` expression language into Kotlin, and `If`/`Repeat`/`ForEach` into Kotlin control
flow. Spec: [`samples/xml-translator`](../../spec.md#samplesxml-translator) (Templates).

**Depends on:** [XML translator: mapping the phase 1 vocabulary](0055-xml-translator-mapping.md).

## Deliverables

- [x] `<Widget Name="Card" ...>` becomes `@Composable fun Card(modifier, params..., content)` before
      `TranslatedUi()`, with lowerCamelCase parameters typed from their defaults (`Boolean`,
      `Float`, `Int`, `String`) and `remember` state inside. A `<Widgets>` root emits only
      functions.
- [x] `<Children/>` becomes the trailing `content`, and `<Children Name="x"/>` a named slot. At the
      use site, `Into="x"` children go to `x = { }`, and layout attributes become the call's
      `modifier`, which the function passes to its root.
- [x] The `{...}` expression language, typed by the target attribute
      (`Width="{Wide ? 452 : 220}"` becomes `Modifier.width((if (wide) 452 else 220).dp)`);
      interpolated text becomes string templates; `{Instance}` becomes a comment.
- [x] `If`/`<If>`, `<Repeat>` and `<ForEach>` become `if`, `repeat(n) { i -> }` and
      `listOf(...).forEachIndexed { n, x -> }`. `<Include>` stays a TODO plus a warning.
- [x] Unit tests for each construct, and for an expression that fails to parse.

## Resolution

Done on macOS. 40 tests pass in `samples/xml-translator`: 12 `TemplatesTest`, 5 `ExpressionsTest`,
15 `TranslatorTest` and 8 headless. Every translation in `TemplatesTest` and `TranslatorTest` (27
files) was also compiled once against the current foundation, by hand; 0057's examples check makes
that permanent.

- **Expressions** (`Expressions.kt`) follow Raven's own parser in `src/xml/xml_components.c`: the
  same grammar and precedence, the same functions and the same error messages. Raven's values are
  texts that are also numbers, so each expression gets a static type, and its conversions are
  written out (spec section 10 lists them). The two differences left are in the spec: a `Float`
  prints as `1.0`, and a text "0" or "false" is true.
- **Where braces count**: only inside a `<Widget>`, `<Repeat>` or `<ForEach>`, as in Raven. This
  corrects 0055, which warned about braces everywhere: outside those, `Text="{Title}"` is the text
  `{Title}`.
- **Functions**: one per `<Widget>`, typed parameters from the defaults, `remember` state inside,
  and a slot per `<Children>` typed with the scope of the panel it sits in, so children given at the
  call are placed as there. The call places the root (its written-out layout overridden by the
  use's, against the use's parent); the root keeps only what is computed from parameters, then
  `.then(modifier)`.
- **Control flow**: `If` and `<If>` become `if`, decided by the translator when written out;
  `<Repeat>` and `<ForEach>` become `repeat` and `forEachIndexed`, with Raven's default variable
  names and its way of splitting a written-out list. Inputs in a loop declare their state in its
  body.
- **Refused as in Raven**: a reserved or library tag name, an empty `<Widget>`, a widget used inside
  itself, `<Children/>` outside a `<Widget>`, children for a slot that doesn't exist. Each is a
  warning or a TODO.
- **Code**: `KotlinCode.kt` now holds `CodeWriter` and the Kotlin literal and identifier helpers,
  which both files use.

