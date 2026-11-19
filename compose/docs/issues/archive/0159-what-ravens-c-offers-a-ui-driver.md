---
status: closed
wayfinder: research
parent: 0157
blocked_by: []
assignee: claude
---

# What Raven's C already offers a UI driver

## Question

What does Raven's C API already provide for finding widgets, reading what they show, driving them
with real input, and knowing when the app has nothing pending? What is missing for a UI driver
that tests in any language (and Raven MCP) could use?

Survey `include/raven/` and, where a header is unclear, `src/`:

- **Walking and finding**: `Rwidget_find` (by name), child, parent and window getters, widget type,
  popups and menus, dialogs, islands. Can every open window be listed?
- **Reading**: accessible name, description and role (`raven_accessibility.h`), the text of each
  kind of widget (label, text input, button, menu item, list and tree rows), checked, value,
  selection, enabled, visible, focused, and bounds.
- **Acting**: `Rwindow_inject_*` (mouse, keys, text, drop) and anything else that acts as a user
  would, such as `Rmenu_open` and scrolling into view.
- **Idle**: can C tell that nothing is pending (posted tasks, timers, layout, animations), the way
  compose-raven's `RavenHost.isIdle()` does for Compose? How do `Rdraw_frame` and `Rwait_frame` behave
  headless?
- **How compose-raven-test does it**: its finders and actions
  (`compose/compose-raven-test/src/nativeMain/kotlin/raven/compose/ui/test/`) for comparison: which
  rely on Compose, and which only on Raven's C.

Deliver an inventory (call, header, what it gives) and a list of what a C driver would have to add.

## Resolution

Researched on 2026-11-18. The findings are on the throwaway branch
`research/what-ravens-c-offers-a-ui-driver` (`8df6b7f`), in
`compose/docs/research/0159-what-ravens-c-offers-a-ui-driver.md`, with the inventory call by call and
file:line for each claim.

**What Raven's C already provides:**

- **Finding**:
  - Tree walking (`Rwidget_get_parent`, `_get_child_count`, `_get_child`, `_get_type`,
    `_get_window`), and `Rwidget_find` by name.
  - Popups and dialogs (`Rwindow_get_popup_count`/`_get_popup`), and the island.
  - `Rwidget_hit_test`/`_pick`, and the focused and hovered widget.
- **Reading**: per widget, the accessible name and role, bounds, enabled, visibility, focus and
  hover, plus each type's own text, checked, value and selection getters.
- **Acting**: `Rwindow_inject_*` (mouse, wheel, keys, text, external drop) is synchronous. It lays
  out first and dispatches at once, so state can be read straight after with no frame. There are
  also `Rmenu_open`, `Rwindow_close_popups`, `Rwidget_scroll_into_view` and `Rwidget_focus`. Every
  call is UI-thread only.
- **Already built, but internal**: the screen-reader tree (`src/core/accessibility.{h,c}`). It
  walks the window, the island and popups. Per node it gives role, name, description, value,
  range, state flags and bounds, including list, tree and tab rows (though rows seem to carry the
  whole widget's rect). It can press, toggle, expand, select and set values.
- **Idle**:
  - No public call. `Rwait_events` works it out internally from posted tasks, the next timer,
    and per window `needs_layout`/`needs_paint`/animating/springs/`redraw_requested`.
  - `Rset_animation_time_step` fixes animations, but timers keep the real clock.
  - Raven's own tests judge "settled" by an unchanged frame index, which sees painting only.
  - compose-raven's `isIdle()` asks Raven nothing.
- **Today's drivers**: Raven's C tests (`test_click`), the Python and Rust `--test` examples, and
  compose-raven-test all click the same way: `Rwidget_get_rect`, then inject a move, press and
  release at the centre. compose-raven-test needs Compose only for its finders and idle. Its reads
  and actions are almost all plain C.

**What a C driver would add:**

1. A call listing the open windows.
2. A public idle check (what `Rwait_events` computes), with timers reported separately.
3. A finder over every root of a window: the window, the island, popups and dialogs.
   `Rwidget_find` never searches popups or dialogs.
4. The accessibility tree made public, or at least `Rwidget_get_accessible_description`.
5. Rects for rows and entries: list, combo, menu, tab and menu-bar. compose-raven-test copies the
   menu layout numbers from `src/widgets/menu.c` today.
6. `Rbutton_get_text`.
7. Optional: public semantic actions (expand a tree row, set a slider's value).
8. Optional: timers that follow the fixed test time step.
9. Find-all, since `Rwidget_find` returns the first match.
10. A documented way to run calls on the UI thread from another thread and get results back (Raven
    MCP's `Rtask_post` hop, as the spike did).

**Not verified**: whether a native menu bar can be driven by injected input; whether a headless app
exposes nothing to the OS accessibility APIs; the `--test` examples of bindings other than Python and
Rust.
