---
status: open
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: null
---

# CodeEditor's editing extras

## Question

`notepad` uses parts of Raven's TextEditor that `CodeEditor` doesn't cover anywhere in the spec:
undo and redo, select all, word wrap, completions (`Rtext_editor_add_completion`, the completion
callback, `show_completions`), TODO/FIXME markers, the cursor position for the status line, a
language the app registers (`Rlanguage_register`), and choosing a language from a file name
(`Rlanguage_for_file`). For each: what is its compose-raven shape (parameters, a state holder like
Compose's `TextFieldState`, functions on a remembered object), and is it phase 2 or a named later
phase? Read `examples/notepad/main.c` and `include/raven/` for what Raven offers.
