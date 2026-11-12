---
status: closed
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: Paul Hawke
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

## Resolution

All of them come into phase 2, so `notepad` has no editor gaps. Each is a thin wrapper over
Raven's TextEditor, and `notepad` needs every one. New ticket:
[CodeEditorState, completions, markers and languages](../0081-codeeditorstate-completions-markers-and-languages.md).

- **Commands and the cursor**: `rememberCodeEditorState()`, passed as `CodeEditor(state = ...)`,
  in the manner of `ScrollState`: no text, only `undo()`, `redo()`, `selectAll()`,
  `showCompletions()`, and a read-only zero-based `cursor: TextPosition`, polled each frame because
  Raven has no cursor callback. `CodeEditor` stays controlled by `value`. `canUndo`/`canRedo` and
  `insertText` stay out; nothing uses them.
- **Word wrap**: `CodeEditor(wordWrap = false)`.
- **Completions**: `completions: ((prefix) -> List<Completion>)?`, synchronous inside Raven's
  callback. A difference from Compose, in spec section 12.
- **Markers are controlled**: `markers: List<EditorMarker>`, put back after each edit, since Raven
  drops a marker whose line is edited and an equal list from a rescan would never restore it.
  A marker on an edited line keeps its columns until the app sends a new list.
- **Languages**: `rememberCodeEditorLanguage(CodeEditorLanguage(...))` registers once per host and
  returns the name; `CodeEditorLanguage` mirrors `RLanguageDesc`, its flags a
  `Set<LanguageFlag>`. `codeEditorLanguageForFile(path)` wraps `Rlanguage_for_file`.

`notepad` then rescans its notes 0.4 s after typing pauses (a `LaunchedEffect` keyed on the text),
and shows the cursor from `state.cursor`. Spec section 8 has the new subsection.
