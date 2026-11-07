---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: [0024]
assignee: Paul Hawke
---

# DockArea and FileBrowser

## Question

How do Raven's DockArea (panels with tabs, `Slot.Dock`, `Slot.DockTo`, `Slot.DockSize`, `Closable`, saved layouts, tearing panels into windows) and FileBrowser (directory, mode, filter, file name, places) map onto composables for the `workspace` port? Decide how each panel's docking is written, whether the dock layout is controlled or saved by Raven, and how torn-off panels relate to the multi-window design.

## Resolution

Facts (`raven_shell.h`, `raven_dialogs.h`): **DockArea** adds panels by side (Left, Right, Top, Bottom, Tab) relative to another panel or the whole area. Panels have a title, an icon and a closable flag, and can be moved, closed, hidden and reopened (`set_close_hides` makes closing a hide). It has `set_movable`, `save_layout`/`load_layout` as text naming panels by widget name, a close callback that can keep the panel, and a changed callback. **Tearing** is on by default: a dragged tab moves its panel into a **Raven-owned floating window**, which Raven docks back by itself. **FileBrowser** has a mode (Open, Save, Folder), filters, hidden files, multiple selection, controls and places visibility, file name and default extension, and accepted, cancelled and selection-changed callbacks. It reports **no directory change**.

- **`DockArea(state: DockAreaState = rememberDockAreaState(), modifier, movable = true, tearable = true) { Panel(name, title, initialDock: Dock? = null, closable = true, icon: Painter? = null, open = true, onClose: () -> Unit = {}) { content } }`**:
  - **The arrangement is Raven's**, because the user drags panels. `Dock.Left/Right/Top/Bottom(size)` and `Dock.Tab(of = name)`, optionally relative to another panel's name, apply only when a panel is first added. The first panel fills the area. `state.saveLayout(): String` and `state.loadLayout(text)` let apps persist the arrangement. `name` is required, because saved layouts name panels by it (`Rwidget_set_name`).
  - **Open is controlled**: the area uses `set_close_hides(true)`, so panels and their state survive. The close callback keeps the panel and calls `onClose`. `open = false` hides the panel and `true` reopens it.
  - **Applier special case**: Raven moves panels by itself (docking, tearing), so the Applier makes no index-based Raven calls on a DockArea's children. Insert uses `Rdock_area_add_panel`, removal destroys the panel, and moves only reorder the mirror list.
- **Torn-off panels**: their floating windows are Raven's, not compose-raven `Window`s. A panel's content stays in its original window's composition, and only its widget moves, so state and input keep working. Window-level things (`Window(onKeyEvent)`, `WindowBackdrop`, the lifecycle owners) still refer to the original window (documented). Closing a floating window docks its panels back (Raven's behaviour). Real `Window`s for torn-off panels go to the fog.
- **`FileBrowser(modifier, mode = FileBrowserMode.Open, initialDirectory: String? = null, filters: List<FileFilter> = emptyList(), multiple = false, showHidden = false, controls = true, places = true, fileName = "", defaultExtension: String? = null, onAccept: (List<String>) -> Unit, onCancel: () -> Unit = {}, onSelectionChange: (List<String>) -> Unit = {})`**:
  - The directory is not controlled, because Raven reports no navigation. `initialDirectory` applies once.
  - `FileFilter(name, patterns)` is shared with the suspend file dialogs.
  - The callbacks read `get_selection`.
