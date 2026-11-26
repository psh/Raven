---
status: closed
wayfinder: research
parent: 0204
blocked_by: []
assignee: claude
---

# Icons and resources: SVG facts

## Question

The facts the vector-icons design waits on, from primary sources:

- What an icon downloaded from <https://fonts.google.com/icons> contains, for Material Symbols
  (Outlined, Rounded, Sharp; fill, weight, grade and optical size as downloaded) and the older
  Material Icons: its `viewBox` (`0 -960 960 960`, `0 0 24 24`), the path commands it uses, any
  `<g>`, transforms, `fill` or `fill-rule`, and its licence for committing a few as test fixtures.
- What Raven's SVG reader handles (`Rtexture_create_from_svg`, `Rtexture_create_from_file`;
  "paths, fills, strokes and gradients"): every path command, a negative `viewBox` origin, a
  missing fill, `fill-rule`, and how `scale` sets the raster size.
- At what scale `painterResource("…svg")` rasterises today (`Rtexture_create_from_file_async`),
  and whether it follows a window's density and `RavenUiScale`.

Findings go on a local `research/svg-icons` branch, which is never merged or pushed.

## Resolution

Resolved on 2026-11-25 by a research run. The findings, with every source quoted, are in
`compose/docs/research/svg-icons.md` on the local branch `research/svg-icons` (commit `5452511`),
which is never merged or pushed. In short:

- **Google's files**: Material Symbols (Outlined, Rounded, Sharp, every axis) are always
  `<svg xmlns height="N" viewBox="0 -960 960 960" width="N">`, N the optical size, with exactly one
  `<path>`: no `fill`, `<g>`, transform, `fill-rule` or `clip-path`, and only `M L H V Q T Z`.
  Material Icons use `viewBox="0 0 24 24"`, `C S` curves, compact numbers (`.5`, `1.5.5`), and at
  times `<g>`, a `fill="none"` box or a `<polygon opacity=".3">` two-tone layer. Apache 2.0: a
  few may be committed as fixtures with the licence beside them. Not verified: what the
  fonts.google.com Download button writes (believed to add `24px` units and a grey fill).
- **Raven's reader** is vendored nanosvg (`third_party/nanosvg`, zlib): every path command, arcs,
  exponents, compact numbers, the negative `viewBox` origin (checked by rendering), `fill-rule`,
  `<g>` and transforms. A shape with no `fill` is opaque black; `currentColor` comes out grey. The
  raster is `ceil(width × scale)` by `ceil(height × scale)`; a file without `width`/`height` would
  be 960 by 960 units.
- **`painterResource("….svg")`** passes no scale: Raven draws the SVG at 2× first, then again on a
  worker at the pixels per unit it is painted with (window scale × `Rget_ui_scale` × stretch), so
  it already follows density and `RavenUiScale` (a headless check: 24, 36, 72 and 24 px as the UI
  scale went 1, 1.5, 3, 1).
- **The blocker**: today's tint (`Rimage_set_tint_color`, `Rimage_set_tint_text`, which
  `ColorFilter.textColor()` uses) multiplies the texture's colour, so Google's black icons stay
  black under any tint. Raven has an alpha-only texture format, for glyphs only; SVG textures
  don't use it.
