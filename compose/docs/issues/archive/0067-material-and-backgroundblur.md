---
status: closed
---

# Modifier.material and BackgroundBlur

Glass panels, for the `overlay` port (and `glass` in phase 3). Spec:
[8: glass and BackgroundBlur](../../spec.md#phase-2-glass-and-backgroundblur).

**Depends on:** [Surface, FlowRow and Expander](0064-surface-flowrow-and-expander.md),
[RavenTheme and theme files](0065-raventheme-and-theme-files.md).

## Deliverables

- [x] `Modifier.material(Material)` with `Default`, `Solid`, `Glass`, `FrostedGlass` and
      `TintedGlass`, through `Rwidget_set_material`. On a `Surface`, `color` then tints the glass.
- [x] `RavenTheme(material = ...)` sets the app-wide material with `Rtheme_set_material`; only an
      application-level `RavenTheme` may, and elsewhere it logs an error.
- [x] `BackgroundBlur(modifier, style, shape, tint, radius, saturation, grain, shadowElevation) { }`
      over BackgroundBlur, its content in an internal Box. `BlurStyle` is `Plain`, `Frosted`,
      `Glass` or `Native`; `radius = Unspecified` keeps the theme's; `null` and `Unspecified`
      leave the rest to Raven, and `tint` returning to `Unspecified` clears it.
- [x] Tests: each parameter reaches Raven and returns to the default when unset; the material
      error is logged.

## Resolution

Done on macOS. 257 tests pass in all, 8 of them new: 5 in foundation (`GlassTest`), 2 in
`RavenThemeTest` and 1 in `LayoutTest`.

- **`Material`** (`Default Solid Glass FrostedGlass TintedGlass`) is in `raven.compose.ui.graphics`,
  beside `Color` and `Shape`, since both `Modifier.material` and `RavenTheme` take it.
- **`Modifier.material(Material)`** is in `raven.compose.ui`, beside `tooltip`, as decoration
  rather than layout. Its `MaterialElement` folds into `FoldedModifier.material`, where the last
  one wins and the slot is untouched. The node calls `Rwidget_set_material` only when the folded
  value changes, on its own widget, which is always the outer one (a content node never applies a
  Modifier). A material that leaves the chain is `Material.Default` again, so the theme's shows.
  On a `Surface`, Raven's Border already tints its glass with its background colour, so `color`
  needs no change.
- **`RavenTheme(material = ...)`** joins the app-wide settings in `HostTheme`. A look or theme
  change resets Raven's material, so it is applied again after one, before the accent, and a
  theme Raven replaces (a reloaded file) gets it back along with the accent.
  `Rtheme_set_material` refuses `R_MATERIAL_DEFAULT`, so `Material.Default` here is the material
  the look and theme give, read with `Rtheme_get_material` before the first one is set over them
  and again after each change of them. `null` leaves Raven's as it is, as for `accent`. Inside a
  window it logs the error, which now names material too. Only Raven's own looks are glass:
  in the others Raven warns and keeps Solid.
- **`BackgroundBlur(modifier, style, shape, tint, radius, saturation, grain, shadowElevation) { }`**,
  in `raven.compose.foundation` (`BackgroundBlur.kt`), is a BackgroundBlur widget with a Box
  content node. `BlurStyle` is `Plain Frosted Glass Native`. `shape` sets the four corner radii,
  `RectangleShape` being explicit square corners as on `Surface` (Raven's own default is the
  theme's corner radius). `tint = Unspecified` uses `Rbackground_blur_clear_tint`;
  `radius = Unspecified`, `saturation = null` and `grain = null` set -1, Raven's "the theme's" or
  "the style's"; `shadowElevation` is `Rbackground_blur_set_shadow`, 0 being none. Inner padding is
  the content's own `Modifier.padding`, so `Rbackground_blur_set_padding` isn't used.
- **No getters**: Raven reads back a BackgroundBlur's style and radius, but not its tint,
  saturation, grain, corners or shadow. Rather than change Raven, the setters go through an
  internal `BlurTarget` (as layout goes through `SlotTarget`), and the test wraps Raven's with a
  recorder that passes every call on. Raven getters for the rest would let a later test read
  them back.
- **Tests**:
  - each material reaches a widget and returns to the theme's with `Default` and when removed;
  - a glass `Surface`'s material is on its Border, its colour is the Border's panel colour, and its
    content node has none of its own;
  - each BackgroundBlur parameter reaches Raven and returns to Raven's default when unset, and
    each `BlurStyle` is Raven's;
  - a BackgroundBlur's content is in a Box, padded by its own Modifier;
  - an application-level material is the app's, survives a change of look, and `Default` is the
    look's own; one in a window logs the error and changes nothing;
  - the last `material` in a chain wins and leaves the slot as it is.
