---
status: open
---

# Modifier.material and BackgroundBlur

Glass panels, for the `overlay` port (and `glass` in phase 3). Spec:
[8: glass and BackgroundBlur](../spec.md#phase-2-glass-and-backgroundblur).

**Depends on:** [Surface, FlowRow and Expander](archive/0064-surface-flowrow-and-expander.md),
[RavenTheme and theme files](archive/0065-raventheme-and-theme-files.md).

## Deliverables

- [ ] `Modifier.material(Material)` with `Default`, `Solid`, `Glass`, `FrostedGlass` and
      `TintedGlass`, through `Rwidget_set_material`. On a `Surface`, `color` then tints the glass.
- [ ] `RavenTheme(material = ...)` sets the app-wide material with `Rtheme_set_material`; only an
      application-level `RavenTheme` may, and elsewhere it logs an error.
- [ ] `BackgroundBlur(modifier, style, shape, tint, radius, saturation, grain, shadowElevation) { }`
      over BackgroundBlur, its content in an internal Box. `BlurStyle` is `Plain`, `Frosted`,
      `Glass` or `Native`; `radius = Unspecified` keeps the theme's; `null` and `Unspecified`
      leave the rest to Raven, and `tint` returning to `Unspecified` clears it.
- [ ] Tests: each parameter reaches Raven and returns to the default when unset; the material
      error is logged.
