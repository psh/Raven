---
status: open
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: null
---

# Maps: raster tiles beside the vector map

## Question

Upstream's MapView (`Rmap_view_*`: raster slippy-map tiles the app gives or reads from files,
markers, animated moves) now sits beside the fork's vector map widget, which
`compose-raven-map`'s `MapView` composable already binds. Decide whether raster tiles become a
tile source of the same `MapView`, a second composable, or stay unbound, and how markers fit. The
answer is the spec section and its implementation tickets, with the `map` port, or an
out-of-scope ruling.
