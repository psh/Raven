---
status: open
wayfinder: research
parent: 0204
blocked_by: []
assignee: null
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
