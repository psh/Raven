---
status: closed
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: Paul Hawke
---

# Notepad's file dialogs and discard prompt

## Question

`notepad` can't open or save a file without Raven's file dialogs, nor ask "discard the changes?"
without its message box. Phase 3 has them designed as suspend functions
([Multiple windows, dialogs and popups](0024-multiple-windows.md)), built on the nested
frames that [Verify recomposition inside nested frames](0053-verify-recomposition-in-nested-frames.md)
found safe. Do the suspend message and file dialogs move into phase 2 (as a new phase 2 ticket,
whole or a subset), or does `notepad` port in phase 2 as an editor without files, completed in
phase 3? Either way, settle what the phase 2 `notepad` does for New, Open, Save, Save as and Quit
with unsaved changes.

## Resolution

Brought into phase 2, native path only, so `notepad` opens, saves and asks before discarding, as
the C example does with the system's dialogs.

- **Phase 2** ships `messageBox`, `openFileDialog`, `saveFileDialog` and `selectFolderDialog`
  with their final signatures, `native: Boolean = false` included, and builds only
  `native = true`: Raven's blocking calls as a task on `RavenDispatcher`, safe since
  [Verify recomposition inside nested frames](0053-verify-recomposition-in-nested-frames.md). A
  call without `native = true` fails with an error naming phase 3. All four, `selectFolderDialog`
  too, so phase 3 has nothing of the native path left to finish. New ticket:
  [Native message and file dialogs](../0080-native-message-and-file-dialogs.md).
- **Phase 3 completes** the default on `Dialog` and `FileBrowser` with no API change, and with it
  headless tests of dialog flows, `notepad`'s included. Listed on `roadmap.md`'s phase 3.
- **`notepad` in phase 2** passes `native = true`. New, Open and Quit (the menu's Ctrl+Q and the
  window's close button) ask "Discard them?" when the text has unsaved changes; Save writes to the
  current path; Save as asks for one. Each runs in a `rememberCoroutineScope()` coroutine that
  awaits the dialog, and Quit then calls `exitApplication()`.
- **Tests**: `notepad`'s headless test covers the editor and the modified state, not the dialogs.

Spec section 4 has the phasing; `roadmap.md` has the new phase 2 row and the phase 3 item.
