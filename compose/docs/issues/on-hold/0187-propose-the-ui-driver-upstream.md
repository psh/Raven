---
status: on-hold
---

# Propose the UI driver upstream

> **On hold (2026-11-19).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose the UI driver to Raven's `main` as one proposal, as what makes every binding testable:
`raven_driver.h`, the window list and idle, and the fixes that came with them. Split out of
[The UI driver in Raven's C](../archive/0169-the-ui-driver.md), which built it, as the design asks
(`agent-skills.md`, "Upstream").

**Depends on:** [The UI driver in Raven's C](../archive/0169-the-ui-driver.md),
[Idle and the window list in Raven's C](../archive/0168-idle-and-the-window-list.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying:
  - `raven_driver.h` and `src/core/driver.c`;
  - `Ris_idle`, `Rwait_idle`, `Rwindow_get_count` and `Rwindow_get`;
  - the fixes: row, tab and menu-entry rects, tree rows nested ones included, menus named by their
    title, `Rbutton_get_text`, `Rwidget_get_accessible_description`;
  - bindgen's `RAccessible`;
  - `tests/test_driver.c` and `tests/test_idle.c`;
  - the bindings regenerated against upstream's headers.
