---
status: open
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: null
---

# Small widgets: the CodeEditor extras

## Question

Which of `CodeEditor`'s unbound functions does an app need, and in what shape? Undo and redo
availability (`Rtext_editor_can_undo`/`_can_redo`), the selection and selected text, scrolling to
a line, zoom, tab size and auto pairs, and token colours (`Rtheme_set_token_color`). Decide which
go on `CodeEditorState` and which are parameters, and which are left out. The answer is the spec
section and its implementation tickets.
