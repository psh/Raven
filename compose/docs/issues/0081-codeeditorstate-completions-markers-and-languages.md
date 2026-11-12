---
status: closed
---

# CodeEditorState, completions, markers and languages

`CodeEditor`'s editing extras for the `notepad` port: commands and the cursor through
`CodeEditorState`, word wrap, completions, controlled markers, and languages the app registers.
Spec: [8: CodeEditor's editing extras](../spec.md#phase-2-codeeditors-editing-extras). Decided in:
[CodeEditor's editing extras](archive/0075-codeeditor-editing-extras.md).

**Depends on:** [ComboBox, SpinBox and codeEditorLanguages](archive/0063-combobox-spinbox-and-codeeditorlanguages.md)
(`codeEditorLanguages()`, which registered languages join).

## Deliverables

- [x] `rememberCodeEditorState()` and `CodeEditor(state = ...)`: `undo()`, `redo()`, `selectAll()`,
      `showCompletions()` and `insertText(text)` (`Rtext_editor_insert_text`, decided in
      [Window extras the samples use](archive/0076-window-extras-the-samples-use.md)), doing
      nothing on a state not attached to an editor.
- [x] `CodeEditorState.cursor: TextPosition(line, column)`, zero-based snapshot state refreshed each
      frame from `Rtext_editor_get_cursor`; readers recompose only when it moves.
- [x] `CodeEditor(wordWrap = false)`.
- [x] `CodeEditor(completions = ...)` with `Completion(text, detail)`, called inside Raven's
      completion callback through the C-boundary catch, results added with
      `Rtext_editor_add_completion`.
- [x] `CodeEditor(markers = ...)` with `EditorMarker` and `MarkerKind`, controlled: applied when the
      list changes, and put back after each edit that made Raven drop one.
- [x] `rememberCodeEditorLanguage(CodeEditorLanguage(...)): String`, registering once per host, with
      `CodeEditorLanguage` mirroring `RLanguageDesc` and `LanguageFlag`; and
      `codeEditorLanguageForFile(path): String?`, failing outside the composition.
- [x] Tests: undo after typing restores the text and calls `onValueChange`; `insertText` types at the
      cursor and can be undone; the cursor follows a
      typed character; completions from the lambda are offered for a prefix; a marker on an edited
      line comes back after the edit; a registered language is listed by `codeEditorLanguages()`
      and chosen by `codeEditorLanguageForFile("x.ini")`.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 211 tests, 84 of them in foundation.

- **Binding layer** (a self-contained section at the end of `Binding.kt`):
  - `WidgetFollower` (`attach`, `follow`, `detach`), bound with `bind(follower)`. A node holds one
    at most; the host keeps the followed nodes and calls `follow` once a frame in its frame
    callback, after the windows follow Raven and before the frame drains and recomposes, so a
    state the follower writes is recomposed in the same frame. Releasing the node detaches it.
    This is how the cursor is polled, since Raven has no cursor callback.
  - `TextEditorCompletion`, an event over `Rtext_editor_set_completion_callback` whose lambda gets
    the editor and the prefix, run through `dispatchEvent`, so the C-boundary catch covers it.
  - `rememberPerHost(key) { }`, which keeps a value in the host's caches: registered languages
    are remembered there, so a language registers once per run of Raven.
- **`CodeEditorState`**, `rememberCodeEditorState()` and `TextPosition` are in `CodeEditor.kt`.
  The state holds no text, only the editor it is attached to: `undo()`, `redo()`, `selectAll()`,
  `showCompletions()` and `insertText(text)` call Raven when attached and do nothing otherwise.
  `cursor` is a `mutableStateOf`, written only when `Rtext_editor_get_cursor` differs, so its
  readers recompose only when it moves. An internal `EditorFollower`, remembered by each
  `CodeEditor`, attaches the state, and moves the editor to a new state when `state` changes. A
  state detaches only from the editor it still points at, so a state handed to another editor
  stays with it.
- **`wordWrap`** is a property over `Rtext_editor_set_word_wrap`.
- **`completions`**: each `Completion(text, detail)` goes to `Rtext_editor_add_completion`; Raven
  leaves out those that don't start with the prefix and lists the app's first.
- **`markers`** (`EditorMarker`, `MarkerKind`) are controlled. The composed list is shown again
  (cleared and re-added) after every edit, in the change callback before `onValueChange`; after
  the app writes the text (`Rtext_editor_set_text` replaces every line, so Raven drops every
  marker); and, as a safety net, at the start of a frame when Raven's marker count differs from
  the list, which catches the end-of-frame write-back of a rejected edit.
- **Languages**: `CodeEditorLanguage` mirrors every field of `RLanguageDesc`, and `LanguageFlag`
  mirrors `RLanguageFlags`. `rememberCodeEditorLanguage` registers in the composition, so
  `codeEditorLanguages()` read after it lists it. `codeEditorLanguageForFile(path)` checks the UI
  thread as `codeEditorLanguages()` does.
- **Readings of the spec**:
  - The word lists of `CodeEditorLanguage` (`extensions`, `keywords`, `types`, `constants`,
    `multilineStrings`) are `List<String>`, joined with `;` or spaces for Raven, rather than
    Raven's separated strings: "Kotlin names" read as Kotlin types too.
  - This ticket says markers come back "after each edit that made Raven drop one"; the spec says
    the node puts the composed list back after each edit. It is put back after every edit, which
    satisfies both: a marker below an edit, which Raven moves, goes back to the line the app
    composed until the app sends a new list.
- **Tests** (`CodeEditorExtrasTest`, and one in `BindingTest`):
  - undo after typing restores the text and calls `onValueChange`, and redo brings it back;
  - `insertText` types at the cursor and can be undone; `selectAll` then `insertText` replaces
    everything; commands on a state whose editor left do nothing;
  - the cursor follows a typed character and an inserted line break, and frames where it doesn't
    move recompose nothing;
  - completions from the lambda get the prefix and are offered, and Enter takes one; an empty
    list offers nothing;
  - a marker on an edited line is back as soon as the edit returns, and after selecting all and
    typing; markers come back after a rejected edit; a new list replaces the old;
  - a registered INI language is named, listed by `codeEditorLanguages()` and chosen by
    `codeEditorLanguageForFile("x.ini")`; an unknown extension gives null; the function fails
    outside the composition;
  - a `WidgetFollower` attaches, follows each frame, and detaches when its node leaves.

  Without the restore in the change callback, the edited-line marker test fails.
