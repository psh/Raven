---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# Small port gaps

## Question

Three small gaps, each in one sample. `music`'s genre tiles push a cover past their corner with a
negative slot padding (`Slot.Padding="0,0,-10,-8"`): Compose forbids negative padding and uses
`Modifier.offset`, which compose-raven doesn't have. `glass`'s card grid is three columns with a
minimum cell size (`MinCellSize="200,150"`), which `LazyVerticalGrid(GridCells.Fixed)` doesn't
take. `music` mipmaps its covers and icon atlas (`Rtexture_set_filter(R_FILTER_MIPMAP)`), where
Compose's `Image` has `filterQuality`. Decide each, or leave it a port gap with its phase.

## Resolution

Decided 2026-11-12. Written into spec section 6, section 8 (grids, images) and section 12.

- **`Modifier.offset(x, y)`**: on the slot padding (left +x, right −x, top +y, bottom −y), which
  Raven accepts negative, since a child's area is inset by it; the widget moves without changing
  its size or its neighbours' places, as Compose's `offset` does. `music`'s genre tile is
  `Modifier.align(Alignment.BottomEnd).offset(10.dp, 8.dp)`. A negative `padding` stays an error.
- **`LazyVerticalGrid(minCellSize: DpSize? = null)`** over `Runiform_grid_set_min_cell_size`, for
  `glass`'s three columns of at least 200 by 150. Compose's `GridCells.Adaptive` stays out: Raven
  doesn't pick columns from the width, so compose-raven would have to poll the size.
- **`ImageBitmap.filterQuality`** (settable, and a constructor parameter), Compose's
  `FilterQuality`: `None` nearest, `Low` linear (the default of bitmaps made from pixels), `Medium`
  and `High` mipmapped. On the bitmap, not `Image`, because Raven filters per texture and two
  `Image`s sharing a bitmap couldn't disagree. `painterResource` images keep Raven's mipmapped
  default; `music` makes its covers and atlas with `High`.
