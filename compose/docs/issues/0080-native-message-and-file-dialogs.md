---
status: closed
---

# Native message and file dialogs

The suspend message and file dialogs with their final signatures, built for `native = true` only:
Raven's blocking calls run as a task on `RavenDispatcher`, for `samples/notepad`. Phase 3 builds the
default on `Dialog` and `FileBrowser`. Spec: [4. Application host](../spec.md#4-application-host).
Decided in: [Notepad's file dialogs and discard prompt](archive/0074-notepad-file-dialogs-and-discard-prompt.md).

**Depends on:** nothing open (phase 1's host, [Verify recomposition inside nested frames](archive/0053-verify-recomposition-in-nested-frames.md)
and [RavenDispatcher wakes for work queued behind a blocked block](archive/0059-ravendispatcher-wakes-for-work-behind-a-blocked-block.md)).

## Deliverables

- [x] `messageBox(title, text, buttons, native = false): MessageResult`, with `MessageButtons` and
      `MessageResult` mirroring Raven's, over `Rmessage_box`.
- [x] `openFileDialog(filters, multiple, native = false): List<String>`, `saveFileDialog(...)` and
      `selectFolderDialog(...)` over `Rfile_dialog_*`, an empty list or `null` meaning cancelled
      as the spec fixes it.
- [x] With `native = true`, each suspends while its blocking call runs as a task on
      `RavenDispatcher`, then resumes with the result. Calls that wait together return in reverse
      order, as spec section 4 says.
- [x] Without `native = true`, each fails with an error saying the default (on `Dialog` and
      `FileBrowser`) arrives in phase 3. The signatures don't change when it does.
- [x] Tests: a call without `native = true` fails with that error. The native path can't be driven
      headless; it is checked by running `samples/notepad`.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 244 tests, 8 of them new in foundation.

- **API**, in `raven.compose.foundation` (`Dialogs.kt`), the module of the `FileBrowser` that
  phase 3's default needs:
  - `messageBox(title, text, buttons = MessageButtons.Ok, native = false): MessageResult`.
    `MessageButtons` is `Ok`, `OkCancel`, `YesNo` or `YesNoCancel`, and `MessageResult` is
    `Cancel`, `Ok`, `Yes` or `No`. An empty `title` shows none. Escape answers `Cancel`, or `No`
    when there is no Cancel button (Raven's rule).
  - `openFileDialog(filters = emptyList(), multiple = false, title = null, initialDirectory =
    null, native = false): List<String>`, empty when cancelled.
  - `saveFileDialog(filters, title, initialDirectory, fileName = "", defaultExtension = null,
    native): String?` and `selectFolderDialog(title, initialDirectory, native): String?`, `null`
    when cancelled.
  - `FileFilter(name, patterns: List<String>)`, with a `vararg` constructor: the class
    `FileBrowser` will share. Each pattern stands alone ("*.png"), so a `;` in one is refused, and
    the patterns are joined with `;` for Raven.
  - **Choices**: the spec's `filters, multiple` come first. `title` and `initialDirectory` (the
    name `FileBrowser` uses for Raven's `directory`) follow, and `null` keeps Raven's title and
    starting folder (the last dialog's, else the home). `fileName` and `defaultExtension` are
    `FileBrowser`'s names, and `native` stays last. Compose Desktop has no suspend dialogs to
    follow. Raven's show-hidden flag is left out, since no caller needs it yet; it can join later
    with a default.
- **Without `native = true`** each call throws `UnsupportedOperationException`: "openFileDialog
  needs native = true for now: the default, on Dialog and FileBrowser, arrives in phase 3"
  (`messageBox` names `Dialog` only).
- **Dispatch**: `awaitNativeDialog(call)`, internal API in `compose-raven-ui`, runs the blocking
  call with `withContext` on the running host's `RavenDispatcher`, after a `yield()`. So it is
  always a block of its own, never inside a callback or an apply that resumed the caller
  undispatched. It fails when no host runs. The dialog goes over the active window, else
  `R_INVALID_ID`, for which Raven takes its first visible window. Calls that wait together return
  in reverse order, each running in the nested frames of the one before, as the `nested-frames`
  spike's `stackedDialogsReturnInReverseOrder` shows. A cancelled caller sees its cancellation
  only once the dialog has closed.
- **Paths** are copied from `Rfile_dialog_get_result` as soon as the call returns, before another
  file dialog can replace them.
- **Tests** (`DialogsTest`):
  - each call without `native = true` fails with that error, and a native call with no host fails;
  - a filter's patterns join as Raven takes them, and a `;` or no pattern is refused;
  - Raven's results map to `MessageResult`;
  - headless, Raven shows its own message box and file browser, so a key answers them, pressed by
    a `LaunchedEffect` that runs in the nested frames: Escape gives `Cancel`, or `No` for
    `YesNo`; Enter gives `Yes` for `YesNoCancel`; Escape gives an empty list or `null` for the
    three file dialogs.

  The system's dialogs (Windows, and Linux with zenity or kdialog) are checked by running
  `samples/notepad`.
- **Raven's limit for stacked file dialogs**: Raven keeps one list of results for every file
  dialog. If the user accepts an outer file dialog while an inner one waits, the inner one's
  answer replaces it before the outer call returns, so the outer call returns the inner one's
paths, or nothing when the inner one was cancelled. It is
  in Raven's C code and not fixed here; a message box stacked over a file dialog is unaffected.
