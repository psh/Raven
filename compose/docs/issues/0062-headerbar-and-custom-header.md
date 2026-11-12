---
status: open
---

# HeaderBar and Window(customHeader)

A title bar that is part of the window's content, for the `notepad` port. Spec:
[8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar),
[4. Application host](../spec.md#4-application-host).

**Depends on:** nothing open.

## Deliverables

- [ ] `Window(customHeader = true)` creates the window with `R_WINDOW_CUSTOM_HEADER`, and so does
      `singleWindowApplication(customHeader = true)`. `Window` keeps its phase 1 `width` and
      `height` until phase 3's `WindowState`.
- [ ] `HeaderBar(modifier, title, buttons: Set<WindowButton> = WindowButton.all()) { }` over
      HeaderBar, with `WindowButton` and `Rheader_bar_set_buttons`.
- [ ] In `HeaderBarScope`, `Modifier.align(Alignment.End)` places a child on the right.
- [ ] A `HeaderBar` in a window without `customHeader` logs a warning.
- [ ] Tests: the title and buttons follow their parameters; a child aligned to the end sits on the
      right of one that isn't; the warning is logged.
