---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0004]
assignee: Paul Hawke
---

# v1 component vocabulary

## Question

Which Raven widgets get composables in v1, and what are they called? Keep it slim: the smallest set that makes a real small tool (window, layout containers, label, button, text input, checkbox...?).

## Resolution

- **Scope**: v1 is what the [XML to Compose translator sample](0013-xml-to-compose-translator.md) and a basic settings form need, and nothing more. Porting Raven's own examples is the next step: [Port the Raven samples to Compose](0014-port-raven-samples.md).
- **Basic controls**, with Compose names and parameter shapes (map Notes: favour Compose conventions):
  - `Text(text, modifier, textAlign, softWrap)` uses Label. `color` and `fontSize` wait for the Theming fog.
  - `Button(onClick, modifier, enabled) { content }` uses Button with `R_BUTTON_PRIMARY` (like Compose's filled button). `OutlinedButton` uses `R_BUTTON_DEFAULT` and `TextButton` uses `R_BUTTON_FLAT`. Each takes a content slot (a single-child parent, see [Node model and Applier](0007-node-model-and-applier.md)) and never calls `Rbutton_set_text`.
  - `Checkbox(checked, onCheckedChange)`, `Switch(checked, onCheckedChange)` and `RadioButton(selected, onClick)` use Checkbox with styles `CHECK`, `SWITCH` and `RADIO`. Raven's built-in checkbox text is not used: a label is a separate `Text` in a `Row`, as in Compose. Raven unchecks sibling radios itself, and the controlled check ([Events and state](0010-events-and-state.md)) makes the app's state decide.
  - `TextField(value, onValueChange, modifier, placeholder: String, singleLine = true)` uses TextInput, single line only. The `String` placeholder rather than a slot is a documented difference.
  - `Slider(value, onValueChange, valueRange, steps)` uses Slider. `LinearProgressIndicator(progress)` uses ProgressBar. `HorizontalDivider`/`VerticalDivider` use Separator. `Spacer(modifier)` uses Spacer.
- **Scrolling**: `VerticalScroll { }` and `HorizontalScroll { }` are containers over ScrollBox that behave like a scrolling Column or Row. This is a documented exception to the Compose-conventions preference (`Modifier.verticalScroll` would break one node per widget).
- **compose-raven names where Compose has none**:
  - `CodeEditor(value, onValueChange, modifier, language: String?, readOnly, lineNumbers)` uses TextEditor. It is a controlled input, and its change callback carries no text, so the node reads `Rtext_editor_get_text`. Raven has built-in syntax only for XML and C, so `language` is a name for `Rtext_editor_set_language`. Kotlin highlighting for the sample is left to the translator ticket.
  - `HorizontalSplitter { }` and `VerticalSplitter { }` use Splitter. A child's `Modifier.weight` is its relative pane size (Raven's fill coefficient).
- **Not in v1** (Wider vocabulary fog): lists and trees (virtual, callback-driven), ComboBox, SpinBox, Image, TabView, Expander, Grid, menus and the header bar, docking, and the specialist widgets.
