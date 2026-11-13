---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# DockArea and HeaderBar extras

## Question

`workspace` shows its saved layout live as panels move (`Rdock_area_set_changed_callback`), and
brings a panel to the front when it adds one or writes to the log (`Rdock_area_show_panel`).
Phase 3's `DockArea` has `saveLayout()` but no change event, and `open` but no way to bring an
open panel forward. `workspace` and `music` also use a `HeaderBar` with no background of its own
(`Background="false"`), which phase 2's `HeaderBar` doesn't offer. Decide the three additions, or
which are port gaps.

## Resolution

Decided 2026-11-12. Written into spec section 8 (docking, and the phase 2 `HeaderBar` row).

- **Layout changes**: `DockAreaState.layout: String`, read-only snapshot state refreshed from
  `Rdock_area_set_changed_callback` (after the user moves, resizes, shows or closes a panel), as
  `LazyListState` observes its position. `saveLayout()` stays, as
  [DockArea and FileBrowser](0037-docking-and-file-browser.md) decided; `workspace` shows
  `state.layout` live.
- **Bringing a panel forward**: `DockAreaState.showPanel(name)` (`Rdock_area_show_panel`), an
  imperative call like `FocusRequester.requestFocus`; a hidden or unknown panel logs a warning.
  Which tab is shown stays Raven's, as the arrangement does (a controlled choice would need an
  `onShown` on every `Panel`). A new panel is shown with `LaunchedEffect(name) { state.showPanel(name) }`;
  reopening with `open = true` already brings a panel forward (`Rdock_area_open_panel`).
- **`HeaderBar(background: Boolean = true)`** (`Rheader_bar_set_background`) for `workspace`'s and
  `music`'s headers over their content; `music`'s height is `Modifier.height(56.dp)`.
- **With Navigation 3**: phase 3 puts a DockArea inside an entry (or beside a `NavDisplay`), so
  `showPanel` is unaffected. Panels as back-stack entries, a docking scene where showing a panel is
  pushing its key and the user's tab picks and closes report back, went to the phase 5 fog under
  "More navigation" on [compose-raven phase 3 spec](../0023-compose-raven-phase-3-spec.md#not-yet-specified).
