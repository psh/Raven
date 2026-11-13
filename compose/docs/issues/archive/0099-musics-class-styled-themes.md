---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# Music's class-styled themes

## Question

`music` styles nearly everything through theme classes: its three themes (`Nocturne`, `Aurora`,
`Daylight`) and `styles.xml` (laid over any look) give `muted` and `eyebrow` labels their colour,
opacity and size, and `nav`, `active`, `icon`, `round`, `white`, `chip`, `on`, `card`, `quick`,
`row`, `link` and `top` buttons their colours, **hovered and pressed colours** and corner radius,
`seek` sliders their track, `bare` inputs no frame, and `surface`, `bar`, `search` and `card-flat`
borders their background. Classes stay unexposed (settled three times; see the map's notes), so
the port has to say all of this in Kotlin, per theme. Decide what that needs that phase 3 lacks:
`XxxColors` with explicit hovered and pressed colours (today compose-raven derives them), the
border and focus colours a frameless `TextField` needs, and an icon tint that follows the colour
of the text around it, hover included (`Rimage_set_tint_text`; Compose's `LocalContentColor`).
Also settle how the port switches its per-theme values with the theme it applies.

## Resolution

Decided 2026-11-12. Classes stay unexposed; the port says its themes in Kotlin, and compose-raven
gains three small things for what that can't express. Written into spec section 8 (theming, and
images) and section 12.

- **The port's own design system**: a `MusicStyle` palette for each of Nocturne, Aurora, Daylight
  and "over any look" (`Unspecified` colours with alpha, from `styles.xml`'s values), provided
  through the app's own `CompositionLocal` and picked from the app's Look selection, not from
  Raven's theme name. The class-styled widgets become the app's composables (`IconButton`,
  `PlayButton`, `Chip`, `NavItem`, `AlbumCard`, `TrackRow`, ...) reading it.
- **Theme files**: `nocturne.xml`, `aurora.xml` and `daylight.xml` load from the C example's
  directory with `loadTheme`, for their colours, metrics, accent and base look; their class rules
  stay in place and reach nothing. `styles.xml`, all class rules, isn't loaded.
- **Explicit hovered and pressed colours** (new), each `Unspecified` by default (the derived shade)
  and used as given when set, independently, on every colour parameter whose shades compose-raven
  derives that the pointer can reach: `ButtonDefaults.buttonColors(hoveredContainerColor,
  pressedContainerColor)` for the three buttons, `CheckboxDefaults.colors(hoveredCheckedColor,
  pressedCheckedColor)`, `RadioButtonDefaults.colors(hoveredSelectedColor, pressedSelectedColor)` and
  `SliderDefaults.colors(hoveredActiveTrackColor, pressedActiveTrackColor)`. They set Raven's
  `_HOVERED`/`_PRESSED` roles, so Raven keeps animating between states. `LinearProgressIndicator`
  isn't interactive and gets none. Preferred over Compose's `interactionSource` route, which would
  lose Raven's state animation and lag a frame.
- **`ColorFilter.textColor()`** (new) for `Image`: `Rimage_set_tint_text`, so Raven tints the image
  with the text colour where it sits (the theme's, a button's content colour, a hover colour)
  without recomposing. Preferred over a `LocalContentColor`, which would duplicate Raven's own
  cascade of the text colour.
- **`TextFieldDefaults.colors(unfocusedIndicatorColor)`** (new), Compose's name: `CONTROL_BORDER`
  and `BORDER`. All four transparent gives `music`'s frameless search input.
- **App code, no API**: an icon button's glyph brightening under the pointer (Raven has no
  hovered-text role) is the port's `IconButton` tracking `onPointerEvent(Enter/Exit)` and swapping
  `contentColor`; the playing track's row is highlighted with its button's `contentColor`, which
  Raven's painter carries to the Labels (and text-tinted Images) inside, while the muted Labels keep
  their own colour.
