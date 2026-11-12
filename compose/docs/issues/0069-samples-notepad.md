---
status: open
---

# samples/notepad

Port Raven's `examples/notepad`: a text editor under a custom header bar with File, Edit and
View menus, a language ComboBox, and a status line. Spec: [10. Samples](../spec.md#10-samples),
[8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar).

**Depends on:** [Menus and KeyShortcut](archive/0061-menus-and-keyshortcut.md),
[HeaderBar](archive/0062-headerbar.md),
[ComboBox, SpinBox and codeEditorLanguages](archive/0063-combobox-spinbox-and-codeeditorlanguages.md),
[RavenTheme and theme files](archive/0065-raventheme-and-theme-files.md) (the View menu's looks),
[Native message and file dialogs](0080-native-message-and-file-dialogs.md),
[CodeEditorState, completions, markers and languages](0081-codeeditorstate-completions-markers-and-languages.md),
[Files and text dropped on a window](0082-files-and-text-dropped-on-a-window.md),
[Window: WindowState and its parameters](archive/0086-window-windowstate-and-its-parameters.md).

## Deliverables

- [ ] `samples/notepad` as a `singleWindowApplication(customHeader = true)`, 980 by 640 through
      its `WindowState`, with the
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
- [ ] A file dropped on the window opens (after "Discard them?" when there are unsaved changes),
      and dropped text is typed at the cursor with `CodeEditorState.insertText`, through
      `Window(onExternalDrop)`.
- [ ] A path given on the command line opens at start: `main(args)` passes it to the composable.
- [ ] The C example's minimum window size, as `minimumSize`.
- [ ] Runs from Gradle like `samples/hello`, documented in `compose/README.md`.
- [ ] A headless test: typing marks the document modified in the status line, choosing a
      language reaches the editor, and a dropped text is inserted (`performExternalDrop`).

## Gaps

- **Phase 3:** headless tests of the dialog flows, with the dialogs' `Dialog`/`FileBrowser` default
  ([Notepad's file dialogs and discard prompt](archive/0074-notepad-file-dialogs-and-discard-prompt.md)).

Everything else of the C example is in phase 2, settled by
[compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md).
