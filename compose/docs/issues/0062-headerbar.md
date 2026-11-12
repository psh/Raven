---
status: open
---

# HeaderBar

A title bar that is part of the window's content, for the `notepad` port. The
`customHeader` window flag it needs moved to
[Window: WindowState and its parameters](0086-window-windowstate-and-its-parameters.md). Spec:
[8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar),
[4. Application host](../spec.md#4-application-host).

**Depends on:** [Window: WindowState and its parameters](0086-window-windowstate-and-its-parameters.md) (`customHeader`).

## Deliverables

- [ ] `HeaderBar(modifier, title, buttons: Set<WindowButton> = WindowButton.all()) { }` over
      HeaderBar, with `WindowButton` and `Rheader_bar_set_buttons`.
- [ ] In `HeaderBarScope`, `Modifier.align(Alignment.End)` places a child on the right.
- [ ] A `HeaderBar` in a window without `customHeader` logs a warning.
- [ ] Tests: the title and buttons follow their parameters; a child aligned to the end sits on the
      right of one that isn't; the warning is logged.
