---
status: open
---

# XML translator: mapping the phase 1 vocabulary

Write `translate(xml): Translation(code, warnings)` over the `XmlElement` tree for the phase 1
vocabulary: the element mapping, explicit `fillMax*` where XML relies on Raven's `Fill`, hoisted
state, stub actions, and TODOs with warnings for anything unsupported. Spec:
[`samples/xml-translator`](../spec.md#samplesxml-translator) (Translator, Mapping, Faithful
output, State and actions, Unsupported input, Output).

**Depends on:** [XML translator: parsing](0054-xml-translator-parsing.md), for `XmlElement`.

## Deliverables

- [ ] Names matched case-insensitively through an alias table taken from `docs/XML.md`.
- [ ] The mapping table in the spec, including `Style` choosing between buttons and toggles, and
      `Slot.*`, sizes and `Spacing` becoming Modifiers and `Arrangement.spacedBy`.
- [ ] Explicit `fillMax*` modifiers wherever the XML relies on Raven's `Fill` default.
- [ ] A hoisted `var <name> by remember { mutableStateOf(...) }` per input, named from `Name` or
      generated (`checkbox1`), and stub lambdas for actions.
- [ ] Unsupported elements become `// TODO(raven-xml): ...` plus a warning with line and column;
      unknown attributes become a warning plus a trailing comment.
- [ ] One compilable file with 4-space indents: imports, `@Composable fun TranslatedUi()` with the
      state at the top, then `main()` when the root is `<Window>`.
- [ ] Plain unit tests that build `XmlElement` trees and compare the generated code.
