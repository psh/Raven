---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
---

# DockArea and HeaderBar extras

## Question

`workspace` shows its saved layout live as panels move (`Rdock_area_set_changed_callback`), and
brings a panel to the front when it adds one or writes to the log (`Rdock_area_show_panel`).
Phase 3's `DockArea` has `saveLayout()` but no change event, and `open` but no way to bring an
open panel forward. `workspace` and `music` also use a `HeaderBar` with no background of its own
(`Background="false"`), which phase 2's `HeaderBar` doesn't offer. Decide the three additions, or
which are port gaps.
