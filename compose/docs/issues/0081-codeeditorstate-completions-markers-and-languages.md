---
status: open
---

# CodeEditorState, completions, markers and languages

`CodeEditor`'s editing extras for the `notepad` port: commands and the cursor through
`CodeEditorState`, word wrap, completions, controlled markers, and languages the app registers.
Spec: [8: CodeEditor's editing extras](../spec.md#phase-2-codeeditors-editing-extras). Decided in:
[CodeEditor's editing extras](archive/0075-codeeditor-editing-extras.md).

**Depends on:** [ComboBox, SpinBox and codeEditorLanguages](archive/0063-combobox-spinbox-and-codeeditorlanguages.md)
(`codeEditorLanguages()`, which registered languages join).

## Deliverables

- [ ] `rememberCodeEditorState()` and `CodeEditor(state = ...)`: `undo()`, `redo()`, `selectAll()`,
      `showCompletions()` and `insertText(text)` (`Rtext_editor_insert_text`, decided in
      [Window extras the samples use](archive/0076-window-extras-the-samples-use.md)), doing
      nothing on a state not attached to an editor.
- [ ] `CodeEditorState.cursor: TextPosition(line, column)`, zero-based snapshot state refreshed each
      frame from `Rtext_editor_get_cursor`; readers recompose only when it moves.
- [ ] `CodeEditor(wordWrap = false)`.
- [ ] `CodeEditor(completions = ...)` with `Completion(text, detail)`, called inside Raven's
      completion callback through the C-boundary catch, results added with
      `Rtext_editor_add_completion`.
- [ ] `CodeEditor(markers = ...)` with `EditorMarker` and `MarkerKind`, controlled: applied when the
      list changes, and put back after each edit that made Raven drop one.
- [ ] `rememberCodeEditorLanguage(CodeEditorLanguage(...)): String`, registering once per host, with
      `CodeEditorLanguage` mirroring `RLanguageDesc` and `LanguageFlag`; and
      `codeEditorLanguageForFile(path): String?`, failing outside the composition.
- [ ] Tests: undo after typing restores the text and calls `onValueChange`; `insertText` types at the
      cursor and can be undone; the cursor follows a
      typed character; completions from the lambda are offered for a prefix; a marker on an edited
      line comes back after the edit; a registered language is listed by `codeEditorLanguages()`
      and chosen by `codeEditorLanguageForFile("x.ini")`.
