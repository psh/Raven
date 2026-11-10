---
status: open
---

# Scroll containers, splitters and CodeEditor

The last of the phase 1 vocabulary, needed by the `xml-translator` sample: `VerticalScroll`,
`HorizontalScroll`, `HorizontalSplitter`, `VerticalSplitter` and a controlled `CodeEditor`. Spec:
[8. Component vocabulary](../spec.md#8-component-vocabulary).

**Depends on:** [Text, buttons and the first finders](0047-text-buttons-and-first-finders.md).

## Deliverables

- [ ] `VerticalScroll { }` and `HorizontalScroll { }` over ScrollBox, scrolling a Column or Row.
- [ ] `HorizontalSplitter { }` and `VerticalSplitter { }` over Splitter. A child's
      `Modifier.weight` is its relative pane size.
- [ ] `CodeEditor(value, onValueChange, modifier, language: String?, readOnly, lineNumbers)` over
      TextEditor, controlled. Its change callback carries no text, so the node reads
      `Rtext_editor_get_text`. `language` goes to `Rtext_editor_set_language`, and `"Kotlin"` works
      (fork change 3, already on this branch).
- [ ] Tests: the editor's controlled round trip, `readOnly`, an unknown language name, splitter
      pane weights, and a scroll container with many children.
