---
status: open
---

# CodeEditor placeholder

`CodeEditor(placeholder: String = "")` over `Rtext_editor_set_placeholder`, as `TextField` already
has, for `samples/notepad`'s "Type here, open a file (Ctrl+O), or drop one from the file manager."
Spec: [8: CodeEditor's editing extras](../spec.md#phase-2-codeeditors-editing-extras). Decided on
2026-11-12, from the gap the notepad port reported.

**Depends on:** nothing open.

## Deliverables

- [ ] `CodeEditor(placeholder = "")`, set only when it changes.
- [ ] `samples/notepad` passes the C example's placeholder, and its ticket's gap note is corrected.
- [ ] Tests: the placeholder reaches the editor and follows a change.
