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
[RavenTheme and theme files](0065-raventheme-and-theme-files.md) (the View menu's looks),
[Native message and file dialogs](0080-native-message-and-file-dialogs.md),
[CodeEditorState, completions, markers and languages](0081-codeeditorstate-completions-markers-and-languages.md).

## Deliverables

- [ ] `samples/notepad` as a `singleWindowApplication(customHeader = true)`, 980 by 640, with the
      C example's look: menus and the language ComboBox in the `HeaderBar`, a `CodeEditor` that
      fills the window, and the status line under a divider.
- [ ] Menus with their shortcuts, Line numbers as a `CheckboxItem`, and the four View looks through
      an application-level `RavenTheme`.
- [ ] New, Open, Save, Save as and Quit as the C example has them, through the dialogs with
      `native = true`: New, Open and Quit (Ctrl+Q and the close button) ask "Discard them?" when
      the text has unsaved changes, each in a `rememberCoroutineScope()` coroutine, and Quit then
      calls `exitApplication()`.
- [ ] The language ComboBox lists `codeEditorLanguages()`, with the INI language registered through
      `rememberCodeEditorLanguage`, and sets the editor's `language`; opening or saving a file
      picks it with `codeEditorLanguageForFile`.
- [ ] Edit's Undo, Redo, Select all and Propose words through `CodeEditorState`; View's Word wrap as
      a `CheckboxItem`; the C example's snippets, TODO and FIXME as `completions`.
- [ ] TODO/FIXME `markers` and the notes count, rescanned 0.4 s after typing pauses, and the
      status line's position from `state.cursor`.
- [ ] Runs from Gradle like `samples/hello`, documented in `compose/README.md`.
- [ ] A headless test: typing marks the document modified in the status line, and choosing a
      language reaches the editor.
- [ ] Each gap below is settled by [compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md)
      before this ticket starts: brought into phase 2, or a port gap naming the phase that
      completes it.

## Gaps

Phase 2 doesn't cover these parts of the C example:

- **Phase 3:** the window's minimum size (`WindowState`); headless tests of the dialog flows,
  with the dialogs' `Dialog`/`FileBrowser` default
  ([Notepad's file dialogs and discard prompt](archive/0074-notepad-file-dialogs-and-discard-prompt.md)).
- **Not in the spec:** dropping a file on the window; opening a file given on the command line
  ([Window extras the samples use](0076-window-extras-the-samples-use.md) settles both).
