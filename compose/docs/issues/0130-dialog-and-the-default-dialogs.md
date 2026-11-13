---
status: open
---

# Dialog and the default dialogs

`Dialog`, and the default (not `native`) message and file dialogs that phase 2 left out. Spec: [4. Application host](../spec.md#4-application-host).

**Depends on:** [Windows that come and go, and their owners](archive/0112-windows-that-come-and-go-and-their-owners.md), [DockArea and FileBrowser](0129-dockarea-and-filebrowser.md).

## Deliverables

- [ ] `Dialog(onDismissRequest, properties) { }` as a Raven sheet with its own composition, Escape through `Rdialog_set_dismiss_callback` (fork change 4).
- [ ] The four suspend dialogs' default, built on `Dialog` and `FileBrowser` with no API change ([Notepad's file dialogs and discard prompt](archive/0074-notepad-file-dialogs-and-discard-prompt.md)).
- [ ] Headless tests of dialog flows: answered, dismissed, refused dismissal, a dialog removed from the composition.
