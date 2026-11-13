---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
---

# Small port gaps

## Question

Three small gaps, each in one sample. `music`'s genre tiles push a cover past their corner with a
negative slot padding (`Slot.Padding="0,0,-10,-8"`): Compose forbids negative padding and uses
`Modifier.offset`, which compose-raven doesn't have. `glass`'s card grid is three columns with a
minimum cell size (`MinCellSize="200,150"`), which `LazyVerticalGrid(GridCells.Fixed)` doesn't
take. `music` mipmaps its covers and icon atlas (`Rtexture_set_filter(R_FILTER_MIPMAP)`), where
Compose's `Image` has `filterQuality`. Decide each, or leave it a port gap with its phase.
