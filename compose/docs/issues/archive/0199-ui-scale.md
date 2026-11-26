---
status: closed
---

# UI scale

`LocalDensity` follows a change to Raven's UI scale
([Layout offset and density](0118-layout-offset-and-density.md)), but an app can't read or set
it: `Rget_ui_scale` and `Rset_ui_scale` are unbound. Expose them so an app can offer a zoom
setting. One of the everyday features in the [Raven API coverage](../../api-coverage.md) review (2026-11-14).

## Deliverables

- [x] A UI scale an app reads and sets (an app-level property or composition local; decided: `RavenUiScale.value`, as `RavenAnimation` is), calling `Rset_ui_scale`.
- [x] Tests: setting it changes `LocalDensity` and the layout.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 605 tests, 4 of them in foundation's new
`UiScaleTest`.

- **`RavenUiScale.value`**, in `raven.compose.ui`: an app-wide object as `RavenAnimation` is, since
  Raven's scale is the whole application's, not a window's or a composition's. Snapshot state, so a
  "125%" label recomposes; writes call `Rset_ui_scale`, and the host reads `Rget_ui_scale` once a
  frame, as Raven changes it itself while `Rwindow_save_preview` draws.
- **Range**: Raven clamps to 0.25 to 4 and takes 0 or less as 1; compose-raven throws outside the
  range instead, so a wrong zoom doesn't silently become another.
- **Before start**: unlike `RavenAnimation`, whose early value Raven's replaces, a value set before
  the application starts is applied at start, so an app restores a saved zoom before the first
  frame. The next application starts at Raven's 1 again.
- **What it does to the layout**: Raven lays out in logical units, so a 100 dp box stays 100; each
  window's scale, and with it `LocalDensity`, is multiplied, so a window shows less at a larger
  scale. Raven also closes the open popups and menus; the KDoc says so.
- **Tests**: the scale reaches Raven, each window's scale and `LocalDensity`, and a reader
  recomposes, with the layout in the same logical units; Raven's own change is followed; a value
  set before start applies, and the next app starts at 1; a value out of range throws.
- **Spec**: a bullet in section 6 beside `LocalDensity` and a row in section 12.
- **The everyday-features batch** of the [Raven API coverage](../../api-coverage.md) review is done
  with this: 0193 to 0199.
