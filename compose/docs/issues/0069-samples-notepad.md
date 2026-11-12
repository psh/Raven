---
status: open
---

# samples/notepad

Port Raven's `examples/notepad`: a text editor under a custom header bar with File, Edit and
View menus, a language ComboBox, and a status line. Spec: [10. Samples](../spec.md#10-samples),
[8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar).

**Depends on:** [Menus and KeyShortcut](0061-menus-and-keyshortcut.md),
[HeaderBar and Window(customHeader)](0062-headerbar-and-custom-header.md),
[ComboBox, SpinBox and codeEditorLanguages](0063-combobox-spinbox-and-codeeditorlanguages.md),
[RavenTheme and theme files](0065-raventheme-and-theme-files.md) (the View menu's looks).

## Deliverables

- [ ] `samples/notepad` as a `singleWindowApplication(customHeader = true)`, 980 by 640, with the
      C example's look: menus and the language ComboBox in the `HeaderBar`, a `CodeEditor` that
      fills the window, and the status line under a divider.
- [ ] Menus with their shortcuts, Line numbers as a `CheckboxItem`, and the four View looks through
      an application-level `RavenTheme`.
- [ ] The language ComboBox lists `codeEditorLanguages()` and sets the editor's `language`.
- [ ] Runs from Gradle like `samples/hello`, documented in `compose/README.md`.
- [ ] A headless test: typing marks the document modified in the status line, and choosing a
      language reaches the editor.
- [ ] Each gap below is settled by [compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md)
      before this ticket starts: brought into phase 2, or a port gap naming the phase that
      completes it.

## Gaps

Phase 2 doesn't cover these parts of the C example:

- **Phase 3:** the file dialogs and the "discard the changes?" message box (suspend message and
  file dialogs, [Multiple windows](archive/0024-multiple-windows.md)); the window's minimum size
  (`WindowState`).
- **Not in the spec:** `CodeEditor` undo, redo, select all, word wrap, completions, TODO/FIXME
  markers and its cursor position; the INI language registered by the app
  (`Rlanguage_register`) and `Rlanguage_for_file`; dropping a file on the window; opening a file
  given on the command line.
