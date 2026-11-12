---
status: wont-fix
---

# Window minimum size and DpSize

`Window(minimumSize: DpSize?)` and the `DpSize` type it takes, for the `notepad` and `components`
ports. Spec: [4. Application host](../../spec.md#4-application-host),
[6. Layout and Modifiers](../../spec.md#6-layout-and-modifiers). Decided in:
[Window minimum size](0083-window-minimum-size.md).

**Depends on:** nothing open.

## Deliverables

- [ ] `DpSize(width: Dp, height: Dp)` in `raven.compose.ui.unit`, with equality and
      `DpSize.Unspecified` as Compose has it.
- [ ] `Window(minimumSize: DpSize? = null)` and the same parameter on `singleWindowApplication`,
      through `Rwindow_set_min_size` with the size rounded to whole logical units. `null` keeps
      Raven's default; a change calls Raven again.
- [ ] Tests: the minimum reaches the window at creation and after a change.

## Resolution

Not done as its own ticket: merged into [Window: WindowState and its parameters](../0086-window-windowstate-and-its-parameters.md)
by [ADR 0001](../../adr/0001-phase-split-after-the-phase-2-sample-gaps.md), so the single `Window` is finished in one place.
