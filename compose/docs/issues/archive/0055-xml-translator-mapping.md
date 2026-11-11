---
status: closed
---

# XML translator: mapping the phase 1 vocabulary

Write `translate(xml): Translation(code, warnings)` over the `XmlElement` tree for the phase 1
vocabulary: the element mapping, explicit `fillMax*` where XML relies on Raven's `Fill`, hoisted
state, stub actions, and TODOs with warnings for anything unsupported. Spec:
[`samples/xml-translator`](../../spec.md#samplesxml-translator) (Translator, Mapping, Faithful
output, State and actions, Unsupported input, Output).

**Depends on:** [XML translator: parsing](0054-xml-translator-parsing.md), for `XmlElement`.

## Deliverables

- [x] Names matched case-insensitively through an alias table taken from `docs/XML.md`.
- [x] The mapping table in the spec, including `Style` choosing between buttons and toggles, and
      `Slot.*`, sizes and `Spacing` becoming Modifiers and `Arrangement.spacedBy`.
- [x] Explicit `fillMax*` modifiers wherever the XML relies on Raven's `Fill` default.
- [x] A hoisted `var <name> by remember { mutableStateOf(...) }` per input, named from `Name` or
      generated (`checkbox1`), and stub lambdas for actions.
- [x] Unsupported elements become `// TODO(raven-xml): ...` plus a warning with its line (Raven
      gives a node no column, see [parsing](0054-xml-translator-parsing.md));
      unknown attributes become a warning plus a trailing comment.
- [x] One compilable file with 4-space indents: imports, `@Composable fun TranslatedUi()` with the
      state at the top, then `main()` when the root is `<Window>`.
- [x] Plain unit tests that build `XmlElement` trees and compare the generated code.

## Resolution

Done on macOS. 23 tests pass in `samples/xml-translator`: 15 plain translator tests and 8 headless
ones. Every expected output in the tests was also compiled against the current foundation once,
by hand; 0057's examples check makes that permanent.

- **`translate(root: XmlElement)`** (`Translator.kt`) is pure Kotlin, for the unit tests, and
  `translate(xml: String)` parses first, turning a parse error into the one warning, with its
  column. `Translation(code, warnings)` holds `TranslationWarning(line, message, column = 0)`,
  printed as `line N: ...`.
- **Names**: a `Tag` table of the phase 1 tags and their `docs/XML.md` aliases, matched
  lowercased, and attributes read case-insensitively, with `Name`/`Id`, `TextSize`/`FontSize` and
  the five action spellings. `Box` is Raven's alias for `SizeBox`, so it stays a TODO rather than
  becoming Compose's `Box`.
- **Mapping**, as the spec's table: buttons by `Style` (none and `Default` are `OutlinedButton`, as
  Raven's default style), toggles by `Style`, `ScrollBox` and `Splitter` by `Orientation`,
  `Separator` by `Orientation`, `Spacing` as `Arrangement.spacedBy`, and `Slot.Padding` (all three
  margin forms), `Width`/`Height`, `Min*`/`Max*` and `Tooltip` as Modifiers.
- **Faithful fills** follow what each parent reads in Raven, now spelled out in spec section 10:
  the cross axis of a Column or Row, both axes in a Box (align before fill), `Slot.Fill` as
  `weight` where Raven uses it, the window's content filling the window (in a `Box` when it is
  aligned instead), and no implicit fill for a widget root.
- **State**: one hoisted `var` per input, named from `Name` (lowerCamelCase, numbered on a clash,
  a keyword in backticks) or numbered by kind (`textField1`, `checkbox1`, `slider1`...). Radio
  buttons of one parent share an `Int` state. Actions are stub comments in the change lambdas.
- **Unsupported input** is never refused: an element with no composable becomes
  `// TODO(raven-xml): <Tag> has no composable yet` plus a warning with its line; an attribute
  with no equivalent, an invalid value or a `{...}` expression becomes a warning plus one trailing
  comment on the element. Comments in the XML are kept as `//` lines.
- **Output**: sorted imports of only what is used, `@Composable fun TranslatedUi()` with the
  state first, and `main()` through `singleWindowApplication(title, width, height)` for a
  `<Window>`. Calls longer than 100 columns put one argument per line, with trailing commas.

