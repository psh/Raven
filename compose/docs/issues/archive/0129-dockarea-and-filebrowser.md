---
status: closed
---

# DockArea and FileBrowser

Docked panels and the file browser widget. Spec: [8: Docking and the file browser](../../spec.md#docking-and-the-file-browser-phase-3).

**Depends on:** [Images and resources](0115-images-and-resources.md).

## Deliverables

- [x] `DockArea(state, modifier, movable, tearable) { Panel(name, title, initialDock, closable, icon, open, onClose) { } }`, the arrangement Raven's, open and close controlled, torn-off panels in Raven's windows.
- [x] `DockAreaState`: `saveLayout()`, `loadLayout(text)`, `layout` as snapshot state, `showPanel(name)` ([DockArea and HeaderBar extras](0105-dockarea-and-headerbar-extras.md)).
- [x] `FileBrowser(modifier, mode, initialDirectory, filters, multiple, showHidden, controls, places, fileName, defaultExtension, onAccept, onCancel, onSelectionChange)`, `FileFilter` shared with the file dialogs.
- [x] Tests: panels added by `initialDock`, a refused close, a saved and loaded layout, `layout` following a change, `showPanel`; a file accepted.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 475 tests, 7 of them `DockingTest`'s.

- **`DockArea(state = rememberDockAreaState(), modifier, movable = true, tearable = true) {
  Panel(name, title, initialDock = Dock.Tab(), closable = true, icon = null, open = true, onClose =
  {}) { } }`**, in `Docking.kt`. `Panel` is a member of `DockAreaScope`; its content is in an
  internal Box named `name` (`Rwidget_set_name`, which Raven's layouts name panels by) and titled
  through its slot.
  - **Docking**: Raven docks a child added to the area as its slot says, so `initialDock`
    (`Dock.Left/Right/Top/Bottom(size)`, `Dock.Tab(of)`, `of = null` being the first group of tabs)
    is written to the panel's slot (`Slot.Dock`, `Slot.DockTo`, `Slot.DockSize`) before the applier
    adds it; from then on where it is is Raven's. A new `ChildKind.Docked` has the applier append a
    dock area's children (`Rwidget_add_child`), move nothing in Raven, and leave a panel torn off
    into a window of its own to its release, since it is no longer the area's child.
  - **Closing is controlled**: Raven's close callback (`DockAreaClose`) returns `R_FALSE`, keeping
    the panel, and calls its `onClose`; `open` false hides it (`Rdock_area_hide_panel`), and true
    puts it back with its tab in front (`Rdock_area_open_panel`). `closable` is the slot's
    `Slot.Closable`; `icon` goes through `Rdock_area_set_panel_icon`, once the panel is in the
    area, behind a `dockCalls` seam, as Raven has no getter.
  - **Torn-off panels** stay in the window's composition, as the spec says; tearing isn't tested,
    since it needs a drag out of the window.
- **`DockAreaState`**: `layout` is the arrangement as text, snapshot state, refreshed from Raven's
  changed callback (`DockAreaChanged`, which Raven also calls when a panel is hidden or opened)
  and after the app's changes; `saveLayout()`; `loadLayout(text)` returns whether it is a layout,
  and, given before the area is shown (as when restoring settings), is kept and loaded once the
  panels are in; `showPanel(name)` (`Rdock_area_show_panel`) warns, through `layoutWarningSink`,
  for a panel hidden or unknown.
- **`FileBrowser(modifier, mode = FileBrowserMode.Open, initialDirectory = null, filters =
  emptyList(), multiple = false, showHidden = false, controls = true, places = true, fileName = "",
  defaultExtension = null, onAccept, onCancel, onSelectionChange)`**, in `FileBrowser.kt`: the folder
  is Raven's from `initialDirectory` on (one that can't be read warns), and `FileFilter` is the one
  of the file dialogs (`Dialogs.kt`). `onAccept` gets the paths chosen: Raven's `path`, then the
  rest of the selection when several are chosen; `onSelectionChange` the selection.
- **Tests** (`DockingTest`): panels docked left, below and behind another's tabs by `initialDock`,
  all in `layout`; a close refused (the panel stays) and taken (hidden, `layout` following), then
  reopened in front; a layout saved, changed by Raven, and loaded, and a text that isn't one; a
  layout given before the area is shown; `showPanel` and its two warnings; a panel left out of the
  composition leaving the area and coming back; a file in a temporary folder accepted with Enter.
