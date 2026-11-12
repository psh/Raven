---
status: open
---

# Native message and file dialogs

The suspend message and file dialogs with their final signatures, built for `native = true` only:
Raven's blocking calls run as a task on `RavenDispatcher`, for `samples/notepad`. Phase 3 builds the
default on `Dialog` and `FileBrowser`. Spec: [4. Application host](../spec.md#4-application-host).
Decided in: [Notepad's file dialogs and discard prompt](archive/0074-notepad-file-dialogs-and-discard-prompt.md).

**Depends on:** nothing open (phase 1's host, [Verify recomposition inside nested frames](archive/0053-verify-recomposition-in-nested-frames.md)
and [RavenDispatcher wakes for work queued behind a blocked block](archive/0059-ravendispatcher-wakes-for-work-behind-a-blocked-block.md)).

## Deliverables

- [ ] `messageBox(title, text, buttons, native = false): MessageResult`, with `MessageButtons` and
      `MessageResult` mirroring Raven's, over `Rmessage_box`.
- [ ] `openFileDialog(filters, multiple, native = false): List<String>`, `saveFileDialog(...)` and
      `selectFolderDialog(...)` over `Rfile_dialog_*`, an empty list or `null` meaning cancelled
      as the spec fixes it.
- [ ] With `native = true`, each suspends while its blocking call runs as a task on
      `RavenDispatcher`, then resumes with the result. Calls that wait together return in reverse
      order, as spec section 4 says.
- [ ] Without `native = true`, each fails with an error saying the default (on `Dialog` and
      `FileBrowser`) arrives in phase 3. The signatures don't change when it does.
- [ ] Tests: a call without `native = true` fails with that error. The native path can't be driven
      headless; it is checked by running `samples/notepad`.
