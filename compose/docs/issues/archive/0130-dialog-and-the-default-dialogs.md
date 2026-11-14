---
status: closed
---

# Dialog and the default dialogs

`Dialog`, and the default (not `native`) message and file dialogs that phase 2 left out. Spec: [4. Application host](../../spec.md#4-application-host).

**Depends on:** [Windows that come and go, and their owners](0112-windows-that-come-and-go-and-their-owners.md), [DockArea and FileBrowser](0129-dockarea-and-filebrowser.md).

## Deliverables

- [x] `Dialog(onDismissRequest, properties) { }` as a Raven sheet with its own composition, Escape through `Rdialog_set_dismiss_callback` (fork change 4).
- [x] The four suspend dialogs' default, built on `Dialog` and `FileBrowser` with no API change ([Notepad's file dialogs and discard prompt](0074-notepad-file-dialogs-and-discard-prompt.md)).
- [x] Headless tests of dialog flows: answered, dismissed, refused dismissal, a dialog removed from the composition.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 495 tests, 8 of them `DefaultDialogsTest`'s, and
`DialogsTest`'s phase 2 check that a default call fails now checks that it fails with no
application running.

- **`Dialog(onDismissRequest, properties = DialogProperties()) { }`**, in `compose-raven-ui`
  (`Dialog.kt`): its content has a composition of its own, under the window's (so it sees the
  window's CompositionLocals), rooted in an Overlay with `R_ROLE_DIALOG` given to `Rdialog_open`,
  which Raven shows as a modal sheet over the window. The content draws its own background, as the
  spec now says. Escape goes through `Rdialog_set_dismiss_callback` ([fork change 4](../../spec.md#11-required-raven-changes)):
  the callback returns `R_FALSE`, keeping the dialog, and calls `onDismissRequest` when
  `DialogProperties.dismissOnBackPress`, so the app decides. Leaving the composition disposes the
  dialog's composition, then calls `Rdialog_close`, as the ownership rule says; a dialog Raven
  closed itself (its window gone) isn't closed again. A window's content composition is disposed
  before `Rwindow_destroy`, which disposes its dialogs first.
- **The default dialogs**: `messageBox`, `openFileDialog`, `saveFileDialog` and
  `selectFolderDialog` keep their signatures. Without `native = true` each is a `Dialog` shown by
  `awaitWindowDialog` (internal API): it runs on the UI thread, composes the dialog over the active
  window (else the first) after the window's content, and resumes with the answer as the dialog
  leaves. They never block or run nested frames. Cancelling the call takes the dialog away, and a
  window that goes cancels the calls waiting on it.
  - A message box is a sheet with its title, its text and its buttons on the right, the main one
    last; Escape answers Cancel, or No when there is no Cancel button.
  - A file dialog is its title (by default "Open", "Save as" or "Select a folder") over a
    `FileBrowser` in the matching mode, whose own buttons answer; Escape or Cancel answers none.
    Given no folder, it starts where the last default file dialog ended.
- **Harness**: the finders and `pathTo` look in dialogs too (`RavenHost.dialogRoots`).
- **Tests** (`DefaultDialogsTest`, headless): a dialog answered by its button and closed as it
  leaves the composition; Escape asking, refused (the dialog stays) and taken; no
  `dismissOnBackPress`; a dialog seeing its window's locals and recomposing; a message box answered
  by its button, and Escape answering Cancel or No; a call cancelled taking its dialog away; a file
  chosen in the open dialog with Enter, and the save dialog dismissed.
