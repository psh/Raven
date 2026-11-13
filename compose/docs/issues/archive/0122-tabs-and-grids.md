---
status: closed
---

# Tabs and grids

Controlled tabs, Raven's Grid, and the uniform lazy grid. Spec: [8: Tabs and grids](../../spec.md#tabs-and-grids-phase-3).

**Depends on:** [Images and resources](0115-images-and-resources.md), [Lists and trees](0120-lists-and-trees.md).

## Deliverables

- [x] `TabView(selectedIndex, onSelectedIndexChange, modifier, documents) { Tab(title, closable, onClose, icon) { } }`, selection and closing controlled.
- [x] `Grid(columns, rows, modifier, spacing)` with `GridTrack.Auto`/`Fill` and `Modifier.cell(column, row, columnSpan, rowSpan)`.
- [x] `LazyVerticalGrid(columns = GridCells.Fixed(n), minCellSize)` over UniformGrid ([Small port gaps](0107-small-port-gaps.md)).
- [x] Tests: a refused tab change and close revert; cells and spans; a minimum cell size.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 468 tests, 7 of them `TabsAndGridsTest`'s and one
`LayoutTest`'s.

- **`TabView(selectedIndex, onSelectedIndexChange, modifier, documents = false) { Tab(title,
  closable = true, onClose = {}, icon = null) { page } }`**, in `Tabs.kt`. `Tab` is a member of
  `TabViewScope`; its page is in an internal Box (an Overlay without a role) titled with
  `Rslot_set_title`, which fills the tab view.
  - The selection is a controlled value (`TabViewActive`, `TabViewChanged` in `Binding.kt`), so a
    change the app refuses reverts. Raven activates no tab it doesn't have, and the tabs come after
    the TabView's own properties, so the selection is written again once they are in: after the
    TabView composes, and after each tab comes or goes.
  - Closing (`TabViewClose`): Raven's close callback returns `R_FALSE`, keeping the tab, and calls
    the closed tab's `onClose`; the tab goes when the app leaves it out. `closable` and `icon` are
    set on the tab view (`Rtab_view_set_tab_closable`, `Rtab_view_set_tab_icon`), so each `Tab`
    sets them once its page is in it, in a `SideEffect`. Raven closes tabs and shows icons only in
    the documents form, which the spec now says.
  - Raven has no getter for a tab's title or icon: the icon goes through a `tabCalls` seam for the
    tests, and the title is not checked.
- **`Grid(columns: List<GridTrack>, rows: List<GridTrack>, modifier, spacing = 0.dp) { }`**, in
  `layout/Grid.kt`, with `GridTrack.Auto` and `GridTrack.Fill(weight)` (`Rgrid_set_column_fill`,
  `Rgrid_set_row_fill`; a track dropped from the list goes back to `Auto`) and `GridScope`'s
  `Modifier.cell(column, row, columnSpan = 1, rowSpan = 1)`. The cell is new in the slot layout
  (`GridCellElement`, `SlotLayout`'s cell, `Rslot_set_grid` and `Rslot_set_grid_span`), and a new
  `MainAxis.Cells` makes a child fill its cell unless it is aligned, which the tab view's pages use
  too. `Modifier.cell` anywhere else warns.
- **`LazyVerticalGrid(columns = GridCells.Fixed(n), modifier, minCellSize: DpSize? = null, spacing =
  0.dp) { items(...) }`**, in `raven.compose.foundation.lazy.grid`, with the lazy DSL
  (`LazyGridScope`, `items`, `itemsIndexed`, keys as `LazyColumn`'s): a UniformGrid
  (`Runiform_grid_set_columns`, `Runiform_grid_set_min_cell_size`) in a `VerticalScroll`, so it
  scrolls as Compose's does, composing every item.
- **Tests** (`TabsAndGridsTest`): the selection set once the tabs are in, taken, refused and
  reverted, and set by the app; a tab added later being the selected one; a close refused (the tab
  stays) and taken (it goes); a tab that isn't closable not asked; icons set and removed; grid
  cells, a Fill column and a span laid out with the spacing; a lazy grid's minimum cell size and
  rows. `LayoutTest`: a grid child fills its cell, the cell setter, and the warning outside a Grid.
