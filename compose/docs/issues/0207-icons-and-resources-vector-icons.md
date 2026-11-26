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
