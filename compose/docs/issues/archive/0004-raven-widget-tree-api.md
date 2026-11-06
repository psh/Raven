---
status: closed
wayfinder: research
parent: 0001
blocked_by: []
assignee: claude
---

# Raven widget tree API for an Applier

## Question

What does Raven's public C API (`include/raven/`, `src/widget/`) offer that an Applier and its nodes need? Cover: creating and destroying widgets; inserting a child at an index, moving and reparenting it; the layout model and which properties set size, padding, alignment and weight; how event callbacks and their user data are shaped; and which calls must run on the UI thread. Note any gap where the Applier would need something Raven doesn't expose.

## Resolution

- **Tree:** `Rwidget_create`, `Rwidget_destroy` (subtree, deferred if inside a callback), `Rwidget_insert_child(parent, child, index)` (out-of-range appends; reparents implicitly, index taken after detach), `Rwidget_remove_child` (detach, caller owns), `get_child`/`get_child_count`/`get_parent`. The root is set via `Rwindow_set_content`, which replaces and deletes the previous root.
- **Layout (Slate-style):** per-child slot settings (`Rslot_set_padding`, `_halign`/`_valign`, `_fill` = weight, `_auto`, `_max_size`, grid/canvas placement) live on the child and survive moves; `Rwidget_set_size`/`_min_size`/`_max_size`; `Rpanel_set_spacing`.
- **Callbacks:** C function pointers `(Rid, ..., void* user_data)`, one slot per event, replaced on set. Fits `staticCFunction` + one `StableRef` per node, disposed in `Rwidget_set_destroy_callback` (fires for every widget, including at `Rshutdown`; its `Rid` is already dead). Setting values from code does not fire change callbacks.
- **Threads:** every call is UI-thread-only (checked: `R_ERROR_WRONG_THREAD`); `Rtask_post`/`Rwake_up` are thread-safe and wake `Rwait_frame`. **Frame clock:** `Rset_frame_callback` runs after tasks and before layout; wake with `Rwake_up`, time from `Rget_time`. It has a single global slot.
- **Headless:** `R_BACKEND_NULL` + `headless = R_TRUE`; layout and input injection work.
- **Gaps:** no in-place move (detach + insert drops focus/hover and replays appear transitions; proposed `Rwidget_move_child`); single-child parents destroy the old child on a second insert; one global frame callback; no arrange hook for custom layouts; no public child-index lookup.

Findings: branch `research/raven-widget-tree-api`, file `compose/docs/research/raven-widget-tree-api.md`
