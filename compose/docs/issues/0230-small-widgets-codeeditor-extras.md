---
status: open
---

# Small widgets: CodeEditor extras

Add to `CodeEditor` and its state what an editor app needs: undo and redo availability, the
selection, scrolling to a line, zoom, four editing options, and `SyntaxTheme` through
`LocalSyntaxTheme`. Spec: [8: CodeEditor's phase 5 extras](../spec.md#phase-2-codeeditors-editing-extras)
and section 12. Planned in [Small widgets: the CodeEditor extras](archive/0212-small-widgets-the-codeeditor-extras.md).

**Depends on:** [Raven: token colours per editor](0229-raven-token-colours-per-editor.md).

## Deliverables

- [ ] `CodeEditorState.canUndo`, `canRedo` and `selection: EditorSelection?`, followed once a frame; `select(start, end)`, `selectedText()`, `suspend scrollToLine(line)`; `zoom`, followed from Raven's zoom callback and set through `Rtext_editor_set_zoom`.
- [ ] `CodeEditor(zoomable = true, tabSize = 4, insertSpacesForTab = false, autoPairs = true, autoCompletion = true)`.
- [ ] `SyntaxTheme` (one `Color` per token kind, `Unspecified` meaning the theme's) and `LocalSyntaxTheme`; each `CodeEditor` applies the one around it with `Rtext_editor_set_token_color`, clearing what it no longer specifies.
- [ ] Headless tests: undo availability after an edit and an undo; a selection made by the user and by `select`, and its text; zoom set and zoomed by keys; each option reaches Raven; two editors under different `SyntaxTheme`s, and a nested provider winning.
- [ ] The spec's extras and section 12's row checked against what was built; `api-coverage.md` updated, the unbound functions with their reasons.
