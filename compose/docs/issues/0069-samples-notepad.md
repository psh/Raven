---
status: closed
---

# samples/notepad

Port Raven's `examples/notepad`: a text editor under a custom header bar with File, Edit and
View menus, a language ComboBox, and a status line. Spec: [10. Samples](../spec.md#10-samples),
[8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar).

**Depends on:** [Menus and KeyShortcut](archive/0061-menus-and-keyshortcut.md),
[HeaderBar](archive/0062-headerbar.md),
[ComboBox, SpinBox and codeEditorLanguages](archive/0063-combobox-spinbox-and-codeeditorlanguages.md),
[RavenTheme and theme files](archive/0065-raventheme-and-theme-files.md) (the View menu's looks),
[Native message and file dialogs](archive/0080-native-message-and-file-dialogs.md),
[CodeEditorState, completions, markers and languages](archive/0081-codeeditorstate-completions-markers-and-languages.md),
[Files and text dropped on a window](archive/0082-files-and-text-dropped-on-a-window.md),
[Window: WindowState and its parameters](archive/0086-window-windowstate-and-its-parameters.md).

## Deliverables

- [x] `samples/notepad` as a `singleWindowApplication(customHeader = true)`, 980 by 640 through
      its `WindowState`, with the
      C example's look: menus and the language ComboBox in the `HeaderBar`, a `CodeEditor` that
      fills the window, and the status line under a divider.
- [x] Menus with their shortcuts, Line numbers as a `CheckboxItem`, and the four View looks through
      an application-level `RavenTheme`.
- [x] New, Open, Save, Save as and Quit as the C example has them, through the dialogs with
      `native = true`: New, Open and Quit (Ctrl+Q and the close button) ask "Discard them?" when
      the text has unsaved changes, each in a `rememberCoroutineScope()` coroutine, and Quit then
      calls `exitApplication()`.
- [x] The language ComboBox lists `codeEditorLanguages()`, with the INI language registered through
      `rememberCodeEditorLanguage`, and sets the editor's `language`; opening or saving a file
      picks it with `codeEditorLanguageForFile`.
- [x] Edit's Undo, Redo, Select all and Propose words through `CodeEditorState`; View's Word wrap as
      a `CheckboxItem`; the C example's snippets, TODO and FIXME as `completions`.
- [x] TODO/FIXME `markers` and the notes count, rescanned 0.4 s after typing pauses, and the
      status line's position from `state.cursor`.
- [x] A file dropped on the window opens (after "Discard them?" when there are unsaved changes),
      and dropped text is typed at the cursor with `CodeEditorState.insertText`, through
      `Window(onExternalDrop)`.
- [x] A path given on the command line opens at start: `main(args)` passes it to the composable.
- [x] The C example's minimum window size, as `minimumSize`.
- [x] Runs from Gradle like `samples/hello`, documented in `compose/README.md`.
- [x] A headless test: typing marks the document modified in the status line, choosing a
      language reaches the editor, and a dropped text is inserted (`performExternalDrop`).

## Gaps

- **Phase 3:** headless tests of the dialog flows, with the dialogs' `Dialog`/`FileBrowser` default
  ([Notepad's file dialogs and discard prompt](archive/0074-notepad-file-dialogs-and-discard-prompt.md)).
- **Phase 3:** the C example focuses the editor after a language is chosen, after an Edit command,
  and after a file opens (`Rwidget_focus`); that needs `FocusRequester` (spec section 7, generic
  input and focus).
- **No phase yet, found while porting:** the editor's `Placeholder` ("Type here, open a file
  (Ctrl+O), or drop one from the file manager."), shown after New. `CodeEditor` has no
  `placeholder` parameter, though Raven has `Rtext_editor_set_placeholder` and `TextField` has one;
  it needs a phase and a line in spec section 8.

Everything else of the C example is in phase 2, settled by
[compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md).

## Resolution

Done and run on macOS. `./gradlew build allTests` passes: 272 tests, 6 of them the sample's
(`NotepadTest`). Linux is not run.

- **`application`, not `singleWindowApplication`**: `singleWindowApplication` closes on its close
  button and takes its title and `onExternalDrop` outside the composition, but the C example asks
  "Discard them?" on the close button, shows the file's name and ` *` in its title, and opens a
  dropped file after the same question. So `main(args)` is `application { Notepad(initialPath,
  onExit = ::exitApplication) }`, and `Notepad` holds the app state and composes the application-level
  `RavenTheme` around one `Window(onCloseRequest, rememberWindowState(size = DpSize(980.dp, 640.dp)),
  title, customHeader = true, minimumSize = DpSize(560.dp, 320.dp), onExternalDrop)`. The window
  and its content share the state, as Compose Desktop apps with a close prompt do. No library change.
- **The sample** (`raven.sample.notepad`, `notepad.kexe`): the state is `remember`ed
  `mutableStateOf`s (text, path, modified, language, markers, status, line numbers, word wrap, look)
  and a `rememberCodeEditorState()`.
  - **Header**: a `HeaderBar(Modifier.fillMaxWidth())` (title `null`, so it shows the window's
    "name * - Raven Notepad"; a Column doesn't stretch its children, so without `fillMaxWidth` the
    bar, title and language sat at the left)
    holding the `MenuBar` and, with `Modifier.align(Alignment.End)`, the language `ComboBox`
    (130 by 26 dp, 8 dp each side, tooltip "Language of the text").
  - **Menus**: File (New Ctrl+N, Open... Ctrl+O, Save Ctrl+S, Save as... Ctrl+Shift+S, Quit Ctrl+Q,
    with the C example's separators), Edit (Undo, Redo, Propose words, Select all through
    `CodeEditorState`), View (Line numbers and Word wrap as `CheckboxItem`s, then Dark, Light, Dark
    glass and Light glass, each a `Look.Raven`/`Look.Glass` with `dark` given to the
    application-level `RavenTheme`; until one is chosen it sets nothing, so Raven's look stays).
  - **`MenuBar(native = true)`**: Raven gives the C example's MenuBar to the macOS system menu bar
    (its default), so on macOS the C notepad's header bar holds only the title, the language and the
    window buttons, and the menus are in the system bar; on Windows and Linux they are drawn in the
    header bar. `native = true` reproduces exactly that on every platform, where the default
    (`false`) would put the menus in the header bar on macOS, which the C example never shows.
  - **Files**: New, Open and Quit (the menu, Ctrl+Q and the close button) ask "Discard them?"
    through `messageBox(..., MessageButtons.OkCancel, native = true)` when the text is modified, each
    in a `rememberCoroutineScope()` coroutine; Quit then calls `onExit`. Open and Save as use
    `openFileDialog`/`saveFileDialog(native = true)` with the C example's two filters, title,
    proposed name and default extension. Save writes to the current path, or asks when there is none;
    a failed write leaves the path as it was. Opening or saving under a new name picks the language
    with `codeEditorLanguageForFile`. The status line says "Opened", "Saved", "New text." or "Cannot
    read/write", as the C example. Files are read and written with `platform.posix`, so the sample
    depends only on foundation.
  - **Languages**: `rememberCodeEditorLanguage` registers the C example's INI before
    `codeEditorLanguages()` is read, so the ComboBox lists "Plain text" then every language, INI
    included; choosing one sets `CodeEditor(language)`. The welcome text starts as C.
  - **Completions**: the C example's snippets for the editor's language, then TODO and FIXME as
    notes.
  - **Markers**: `scanNotes(text)` gives a Warning at FIXME, else an Info at TODO, on each line, at
    most 200, with byte columns as Raven counts them. A `LaunchedEffect(text)` rescans after
    `delay(400)`, and opening or a new text scans at once, as the C example does. The notes count
    ("1 note", "N notes", or nothing) and "Line L, column C" from `state.cursor` are in the status
    line, under a `HorizontalDivider`, 16 dp apart with 10 by 4 dp padding.
  - **Drops**: `onExternalDrop` opens a dropped file (after "Discard them?") and types dropped text
    at the cursor with `CodeEditorState.insertText`.
  - **Command line**: a first argument not starting with `-` is opened by a `LaunchedEffect(Unit)`.
- **Look approximations**: the status labels' `Opacity` 0.8 and 0.6 are the theme's text colour
  (`RavenTheme.colors.text`) with that alpha, since there is no `Modifier.alpha` yet; it follows the
  look. The ComboBox's `Metric.ControlHeight="26"` is `Modifier.height(26.dp)`.
- **Port gaps** (added to the Gaps above): the editor's placeholder, and focusing the editor after
  a choice or an open.
- **Deviation from the deliverable's test wording**: the C example shows "modified" as ` *` in the
  window title, which the header bar shows, not in the status line; the port does the same, and the
  test reads the window's title.
- **Checked by eye** on macOS, beside Raven's `raven_notepad` built from `examples/`: both show the
  centred title, the language on the right of the header bar, no menus in the window, the
  underlined TODO and FIXME with their gutter dots, and "Drop a file on the window to open it." /
  "Opened ...", the notes and "Line 1, column 1" under the divider; a `.kts` file opens as Kotlin.
  Neither showed its menus in the system bar in a capture taken while the window was not yet
  clicked, so that part of Raven's native bar was not checked further. The dialogs were not
  driven by hand.
- **README**: how to run `samples/notepad`, with a file to open.
- **Tests** (`NotepadTest`, headless, `Notepad` started in the harness's host with no dialogs):
  - it starts with the welcome text in C, "Untitled - Raven Notepad", the drop hint, "2 notes" with
    two markers in the editor, and "Line 1, column 1";
  - typing makes the title "Untitled * - Raven Notepad";
  - the ComboBox lists INI, and choosing Python, then Plain text, reaches the editor's language;
  - a dropped text is typed at the cursor, marks the text modified, and its FIXME is counted
    ("3 notes") once the 0.4 s rescan has run;
  - a dropped `.ini` file opens with no question (nothing to lose), in INI, with its name in the
    title, "Opened ..." and its note;
  - `scanNotes` finds FIXME before TODO with byte columns, and the notes count reads as the C
    example's.
