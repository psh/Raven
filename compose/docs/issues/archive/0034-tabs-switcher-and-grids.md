---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Tabs, Switcher and grids

## Question

How do Raven's TabView (`Slot.Title`, `ActiveTab`, close callback), Switcher (one child shown by index), Grid (cells, spans, `Columns`/`Rows` tracks) and UniformGrid map onto composables? Compose has `TabRow`/`Tab`, `LazyVerticalGrid` and nothing like Switcher, so decide names, how a child's tab title and grid cell are written (parameters or scoped modifiers), and which are controlled.

## Resolution

Facts (`raven_layout.h`): **TabView** pages are its children, titled through their slot (`Rslot_set_title`). It has `set_active`, a changed callback, a close callback that **can keep the tab** (return false), a documents mode (tabs with icon and close button), and per-tab icon and closability. **Switcher** shows one child by index and keeps the others alive. **Grid** places children by slot cell and span, with column and row tracks sized to content or given a fill share, and panel spacing. **UniformGrid** has equal cells filled row by row for a column count, a minimum cell size and one spacing. Compose Foundation has `LazyVerticalGrid` but no explicit grid, and Material's `TabRow` is separate from page content.

- **`TabView(selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit, modifier, documents = false) { Tab(title, closable = true, onClose: (() -> Unit)? = null) { page } }`**, a compose-raven container with a `TabViewScope`. Raven's TabView owns its pages, so it is not split into a `TabRow`.
  - Each `Tab` is a page with its content in an internal Box and its title through the slot.
  - Selection is controlled.
  - Closing is controlled like windows: Raven's close callback always keeps the tab and calls `onClose`, and the tab goes when the app removes it from the composition.
  - Icons wait for [Image and resources](../0035-image-and-resources.md).
- **No Switcher composable**: Compose's `when (page) { ... }` (or `Crossfade`) is the idiom. Raven's keep-hidden-pages-alive is not needed by phase 3 samples, and `TabView` already keeps its pages (documented).
- **`LazyVerticalGrid(columns = GridCells.Fixed(n), modifier, horizontalArrangement, verticalArrangement) { items(...) }`** uses Compose's DSL over UniformGrid (`columns = n`), composing every item in phase 3 like `LazyColumn`. Raven has one spacing for both directions, taken from the horizontal `spacedBy`. `GridCells.Adaptive` is left out for now (documented).
- **`Grid(columns: List<GridTrack>, rows: List<GridTrack>, modifier, spacing: Dp = 0.dp) { }`** is a compose-raven name over Raven's Grid. `GridTrack` is `Auto` (content) or `Fill(weight)` (`Rgrid_set_column_fill`/`row_fill`). Children are placed with `GridScope`'s `Modifier.cell(column, row, columnSpan = 1, rowSpan = 1)` (`Rslot_set_grid`/`_span`), scoped like `weight` in `RowScope`.
