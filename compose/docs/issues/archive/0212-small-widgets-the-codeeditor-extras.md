---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Small widgets: the CodeEditor extras

## Question

Which of `CodeEditor`'s unbound functions does an app need, and in what shape? Undo and redo
availability (`Rtext_editor_can_undo`/`_can_redo`), the selection and selected text, scrolling to
a line, zoom, tab size and auto pairs, and token colours (`Rtheme_set_token_color`). Decide which
go on `CodeEditorState` and which are parameters, and which are left out. The answer is the spec
section and its implementation tickets.

## Resolution

Resolved on 2026-11-26 in conversation. Written into spec section 8 (CodeEditor's phase 5 extras),
section 11 (change 13, to be made) and section 12, and filed as
[Raven: token colours per editor](0229-raven-token-colours-per-editor.md) and
[Small widgets: CodeEditor extras](../0230-small-widgets-codeeditor-extras.md), in that order.

- **On the state**: `canUndo`, `canRedo` and `selection: EditorSelection?` (`TextPosition`s in
  text order), followed once a frame as `cursor` is; `select(start, end)`, `selectedText()` as a
  function; `suspend scrollToLine(line)`, a jump; `zoom`, followed from Raven's callback and
  settable.
- **Parameters**: `zoomable`, `tabSize`, `insertSpacesForTab`, `autoPairs` and `autoCompletion`,
  Raven's defaults.
- **Syntax colours** (the user's call): not a part of `RavenTheme` but a `SyntaxTheme` of their own,
  provided in the tree as `LocalSyntaxTheme`, one `Color` per token kind, `Unspecified` falling back
  to the editor's Raven theme (a theme file's `<Syntax>`, then Raven's pick). Raven's token colours
  are only global, so a local needs a Raven change: colours per editor
  (`Rtext_editor_set_token_color`), applied by each `CodeEditor` from the local around it.
- **Left unbound**: the token at a position, the line count, a line, the length, `set_syntax`,
  `set_markdown` (Markdown is the `"Markdown"` language) and `is_completing`.
