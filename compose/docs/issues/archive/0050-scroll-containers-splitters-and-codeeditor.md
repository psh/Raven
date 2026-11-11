---
status: closed
---

# Scroll containers, splitters and CodeEditor

The last of the phase 1 vocabulary, needed by the `xml-translator` sample: `VerticalScroll`,
`HorizontalScroll`, `HorizontalSplitter`, `VerticalSplitter` and a controlled `CodeEditor`. Spec:
[8. Component vocabulary](../../spec.md#8-component-vocabulary).

**Depends on:** [Text, buttons and the first finders](0047-text-buttons-and-first-finders.md).

## Deliverables

- [x] `VerticalScroll { }` and `HorizontalScroll { }` over ScrollBox, scrolling a Column or Row.
- [x] `HorizontalSplitter { }` and `VerticalSplitter { }` over Splitter. A child's
      `Modifier.weight` is its relative pane size.
- [x] `CodeEditor(value, onValueChange, modifier, language: String?, readOnly, lineNumbers)` over
      TextEditor, controlled. Its change callback carries no text, so the node reads
      `Rtext_editor_get_text`. `language` goes to `Rtext_editor_set_language`, and `"Kotlin"` works
      (fork change 3, already on this branch).
- [x] Tests: the editor's controlled round trip, `readOnly`, an unknown language name, splitter
      pane weights, and a scroll container with many children.

## Resolution

Done on macOS. 87 tests pass (45 in foundation, 42 in ui).

- **Layout**: `PanelLayout` gained a `MainAxis`, which says how a panel sizes its children along
  its axis. The axis itself is still `LayoutAxis`.
  - `Weighted` is Row and Column, as before.
  - `Scrolling`: each child keeps its own size. `weight` is ignored with a warning, and `fillMax*`
    on the main axis does nothing.
  - `Panes`: each child fills its pane on both axes, and its fill coefficient is its weight, 1 by
    default. Raven gives a pane with no coefficient almost no space, so the default matters.
- **Scroll containers** (`foundation.layout`): `VerticalScroll(modifier, verticalArrangement,
  horizontalAlignment)` and `HorizontalScroll(modifier, horizontalArrangement, verticalAlignment)`.
  - The ScrollBox is the panel itself, with no inner Column, because Raven's ScrollBox stacks its
    children along its orientation and takes `Rpanel_set_spacing`.
  - They use `ColumnScope` and `RowScope`, so `align` works.
- **Splitters**: `HorizontalSplitter(modifier)` and `VerticalSplitter(modifier)`, with a new
  `SplitterScope` that offers only `weight`.
  - Dragging a handle rewrites Raven's coefficients. The node sets a coefficient only when the
    resolved weight changes, so a drag survives recomposition until the app changes a weight.
- **CodeEditor** (`foundation`): `CodeEditor(value, onValueChange = {}, modifier, language = null,
  readOnly = false, lineNumbers = true)`.
  - The handler has a default, because the sample's read-only pane is written without one.
  - The binding layer gained `TextEditorText` and `TextEditorChanged`, whose callback reads
    `Rtext_editor_get_text`.
  - Writing back only on a difference also keeps the undo history: `Rtext_editor_set_text` clears
    it. Rewritten text clears it, as the KDoc and spec section 8 say.
  - **Unknown language**: Raven warns and keeps the old language, so the property clears the
    language before setting the new name. An unknown name then shows plain text. Raven's warning
    is the only report; it is a warning, not an error, so tests don't fail on it.
- **Harness**: `widgetRect(widget)` reads `Rwidget_get_rect`, and `performClick` uses it.
- **Tests**:
  - **CodeEditor**: typing round-trips character by character and keeps undo; the app can replace
    the text; upper-cased text is written back; an editor with no handler reverts; a read-only
    editor takes no typing and calls nothing; `"Kotlin"` is a language; an unknown name shows
    plain text, and `"xml"` resolves to `XML`.
  - **Scroll**: a 100 dp `VerticalScroll` with 50 Texts keeps each child's height and puts the
    last below the view. `Rwidget_scroll_into_view` then scrolls it into view. A `HorizontalScroll`
    lays children in a row, `spacedBy` apart.
  - **Splitters**: weights 1 and 3 give a 1:3 width ratio, and panes fill across; a
    `VerticalSplitter` with two unweighted panes stacks them equally.
  - **Resolve** (pure logic, ui): scroll children get auto slots and a weight warning; splitter
    panes fill, with a weight of 1 by default.

  Without the default weight of 1, the splitter-weight test and the resolve test fail.
