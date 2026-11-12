---
status: closed
---

# CodeEditor placeholder

`CodeEditor(placeholder: String = "")` over `Rtext_editor_set_placeholder`, as `TextField` already
has, for `samples/notepad`'s "Type here, open a file (Ctrl+O), or drop one from the file manager."
Spec: [8: CodeEditor's editing extras](../spec.md#phase-2-codeeditors-editing-extras). Decided on
2026-11-12, from the gap the notepad port reported.

**Depends on:** nothing open.

## Deliverables

- [x] `CodeEditor(placeholder = "")`, set only when it changes.
- [x] `samples/notepad` passes the C example's placeholder, and its ticket's gap note is corrected.
- [x] Tests: the placeholder reaches the editor and follows a change.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 293 tests, 1 of them new in foundation's
`CodeEditorExtrasTest`.

- **`CodeEditor(placeholder = "")`**, in `CodeEditor.kt`: bound through an `EditorPlaceholder`
  property, as `TextField`'s `Placeholder` is, so it is set when the editor is created and again
  only when it changes. The KDoc says it is shown, dimmed, while the text is empty.
- **Testing it**: Raven has no getter for an editor's placeholder, nor does the editor's
  accessible name read it. The property calls `Rtext_editor_set_placeholder` through an internal
  `editorPlaceholderSetter`, which the test wraps with a recorder that passes every call on, as
  `backgroundBlurTarget` is for BackgroundBlur. A Raven getter would let a later test read it back.
- **`samples/notepad`** passes the C example's "Type here, open a file (Ctrl+O), or drop one from
  the file manager.", and the gap note in [samples/notepad](0069-samples-notepad.md) says this
  ticket filled it.
- **Tests**: the placeholder reaches the editor when it is composed; a text change sets it no
  more; a new placeholder is set once.
