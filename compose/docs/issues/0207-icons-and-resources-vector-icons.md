---
status: open
wayfinder: grilling
parent: 0204
blocked_by: [0206]
assignee: null
---

# Icons and resources: vector icons

## Question

How does an app show a vector icon? Decide the API (`painterResource("x.svg")`, an `Icon`
composable with Compose's shape, `ImageVector` or not), how it stays crisp at every density and
`RavenUiScale` (rasterise per scale, cache), how it is tinted (`tint`, `ColorFilter.textColor()`,
`LocalContentColor`), and how it fits `TextField`'s icons and the small widgets' arrows and stars.
**Compatibility is required**: any icon downloaded from <https://fonts.google.com/icons>, Material
Symbols and Material Icons alike, renders correctly, crisp and tinted, proved by a few committed
Google icon fixtures in a test. The answer is the spec section and its implementation tickets.

From [Icons and resources: SVG facts](archive/0206-icons-and-resources-svg-facts.md): Raven already
reads every Google file correctly (nanosvg, the negative `viewBox`) and `painterResource` already
redraws an SVG at each density and UI scale. What is missing is **tint**: Raven's tint multiplies
the texture's colour, so the icons, black by default, stay black. Decide how an icon is tinted (a
Raven change drawing an SVG as an alpha mask, or recolouring the fill before rasterising, or
another way), and whether Material Icons' two-tone `opacity` layers keep their opacity.
