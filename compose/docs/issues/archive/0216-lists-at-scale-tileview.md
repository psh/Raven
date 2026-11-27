---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Lists at scale: TileView

## Question

How does Raven's TileView, a virtualised grid of tiles from a callback with multi-select, become
a composable, and how does it relate to `LazyVerticalGrid`? Decide its items, selection and
virtualisation. The answer is the spec section and its implementation tickets.

## Resolution

Resolved on 2026-11-26, with the user, in two rounds: designed into
[spec section 8: TileView](../../spec.md#tileview-phase-5) and section 12.

- A first round explored splitting the tile view into a virtualised `LazyVerticalGrid`, a separate
  selection model and a composable `Tile`. The user set that aside: **the lazy grids are left as they
  are**, and a grid scrolling both ways (spreadsheet style) is for another time. TileView wraps
  Raven's widget.
- **The model is the app's** (`items`, `key`, a controlled `selection` of keys); a trailing
  `tile: (T) -> TileContent` gives what each tile shows. It isn't `@Composable`, since Raven calls
  it while painting; painters are made in composition, as for `TreeView`.
- **No `multiSelect`**: Raven always selects several; a refused change reverts.
- **Same count refreshes; a new count gives the items again** and reselects.
- **`TileViewState.revealItem`**, not saveable; `layout` (`Tiles`/`Rows`) and `spacing` through
  `Rwidget_set_attribute`.
- **Waiting**: dragging tiles out, the drop highlight and `get_item_at` (with drags inside the app);
  per-tile tint and colours.
- **Tests** in the headless host; the XML translator maps `<TileView>`'s attributes.

Filed as [Lists at scale: TileView](../0236-lists-at-scale-tileview.md).
