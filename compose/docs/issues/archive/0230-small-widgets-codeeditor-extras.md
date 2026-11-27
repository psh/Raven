---
status: closed
---

# Small widgets: CodeEditor extras

Add to `CodeEditor` and its state what an editor app needs: undo and redo availability, the
selection, scrolling to a line, zoom, four editing options, and `SyntaxTheme` through
`LocalSyntaxTheme`. Spec: [8: CodeEditor's phase 5 extras](../../spec.md#phase-2-codeeditors-editing-extras)
and section 12. Planned in [Small widgets: the CodeEditor extras](0212-small-widgets-the-codeeditor-extras.md).

**Depends on:** [Raven: token colours per editor](0229-raven-token-colours-per-editor.md).

## Deliverables

- [x] `CodeEditorState.canUndo`, `canRedo` and `selection: EditorSelection?`, followed once a frame; `select(start, end)`, `selectedText()`, `suspend scrollToLine(line)`; `zoom`, followed from Raven's zoom callback and set through `Rtext_editor_set_zoom`.
- [x] `CodeEditor(zoomable = true, tabSize = 4, insertSpacesForTab = false, autoPairs = true, autoCompletion = true)`.
- [x] `SyntaxTheme` (one `Color` per token kind, `Unspecified` meaning the theme's) and `LocalSyntaxTheme`; each `CodeEditor` applies the one around it with `Rtext_editor_set_token_color`, clearing what it no longer specifies.
- [x] Headless tests: undo availability after an edit and an undo; a selection made by the user and by `select`, and its text; zoom set and zoomed by keys; each option reaches Raven; two editors under different `SyntaxTheme`s, and a nested provider winning.
- [x] The spec's extras and section 12's row checked against what was built; `api-coverage.md` updated, the unbound functions with their reasons.

## Resolution

Built on 2026-11-27.

- **`CodeEditorState`** gains:
  - `canUndo`, `canRedo` and `selection: EditorSelection?`, read with `cursor` once a frame. They
    are also read at once after `select` and `selectAll`, so a test or a handler sees them without
    waiting a frame.
  - `select(start, end)`, with the cursor at `end`.
  - `selectedText()`.
  - `suspend scrollToLine(line)`, Raven's jump, which puts the line a third of the way down.
  - `zoom`: coerced to 0.5 to 4, set into Raven, and followed from the new binding event
    `TextEditorZoomed`. A zoom set before the editor is shown becomes its first.
- **`CodeEditor`** gains the five parameters with Raven's defaults: `zoomable`, `tabSize` (at
  least 1), `insertSpacesForTab`, `autoPairs` and `autoCompletion`.
- **`SyntaxTheme`** (14 colours, all `Unspecified` by default) and **`LocalSyntaxTheme`**. Each
  editor clears its own colours and sets those the local specifies, so nested providers scope, and
  a colour no longer specified goes back to the theme's.
- **Tests**: 9 in `CodeEditorPhase5Test`. They cover:
  - undo and redo availability;
  - the selection by `select` (backwards too) and `selectAll`, and the user's by Shift+Right, with
    their text;
  - `scrollToLine`, seen by a click landing near the line;
  - zoom set, coerced, set before showing, and zoomed by Ctrl + '='; a non-zoomable editor
    ignoring the keys;
  - the tab size and spaces, auto pairs and auto completion, by what they do, since Raven has no
    getters for them;
  - two editors under different themes, a nested provider winning, and a colour going back to
    the theme's.

  The ui, foundation and notepad suites pass.
- **`api-coverage.md`**: 536 of 1,704 functions, `raven_text_editor.h` 40 of 94. The CodeEditor
  entry lists what is bound, what is left out on purpose and why, and what isn't bound yet.
- **Spec**: the extras updated with what was built. Section 12's row is unchanged and still right.
