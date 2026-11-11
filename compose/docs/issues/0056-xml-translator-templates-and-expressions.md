---
status: open
---

# XML translator: templates and expressions

Translate `<Widget>` declarations into top-level composables, `<Children>` into slot lambdas, the
whole `{...}` expression language into Kotlin, and `If`/`Repeat`/`ForEach` into Kotlin control
flow. Spec: [`samples/xml-translator`](../spec.md#samplesxml-translator) (Templates).

**Depends on:** [XML translator: mapping the phase 1 vocabulary](archive/0055-xml-translator-mapping.md).

## Deliverables

- [ ] `<Widget Name="Card" ...>` becomes `@Composable fun Card(modifier, params..., content)` before
      `TranslatedUi()`, with lowerCamelCase parameters typed from their defaults (`Boolean`,
      `Float`, `Int`, `String`) and `remember` state inside. A `<Widgets>` root emits only
      functions.
- [ ] `<Children/>` becomes the trailing `content`, and `<Children Name="x"/>` a named slot. At the
      use site, `Into="x"` children go to `x = { }`, and layout attributes become the call's
      `modifier`, which the function passes to its root.
- [ ] The `{...}` expression language, typed by the target attribute
      (`Width="{Wide ? 452 : 220}"` becomes `Modifier.width((if (wide) 452 else 220).dp)`);
      interpolated text becomes string templates; `{Instance}` becomes a comment.
- [ ] `If`/`<If>`, `<Repeat>` and `<ForEach>` become `if`, `repeat(n) { i -> }` and
      `listOf(...).forEachIndexed { n, x -> }`. `<Include>` stays a TODO plus a warning.
- [ ] Unit tests for each construct, and for an expression that fails to parse.
