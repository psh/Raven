---
status: open
---

# DockArea and FileBrowser

Docked panels and the file browser widget. Spec: [8: Docking and the file browser](../spec.md#docking-and-the-file-browser-phase-3).

**Depends on:** [Images and resources](0115-images-and-resources.md).

## Deliverables

- [ ] `DockArea(state, modifier, movable, tearable) { Panel(name, title, initialDock, closable, icon, open, onClose) { } }`, the arrangement Raven's, open and close controlled, torn-off panels in Raven's windows.
- [ ] `DockAreaState`: `saveLayout()`, `loadLayout(text)`, `layout` as snapshot state, `showPanel(name)` ([DockArea and HeaderBar extras](archive/0105-dockarea-and-headerbar-extras.md)).
- [ ] `FileBrowser(modifier, mode, initialDirectory, filters, multiple, showHidden, controls, places, fileName, defaultExtension, onAccept, onCancel, onSelectionChange)`, `FileFilter` shared with the file dialogs.
- [ ] Tests: panels added by `initialDock`, a refused close, a saved and loaded layout, `layout` following a change, `showPanel`; a file accepted.
