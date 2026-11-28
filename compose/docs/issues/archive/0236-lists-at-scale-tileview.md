---
status: closed
---

# Lists at scale: TileView

Add `TileView` over Raven's virtualised TileView: the app's items and a controlled selection of
keys, and a `tile` function giving each item's `TileContent`. Spec: [8: TileView](../../spec.md#tileview-phase-5)
and section 12. Planned in [Lists at scale: TileView](0216-lists-at-scale-tileview.md).

**Depends on:** [Lists at scale: Table](0235-lists-at-scale-table.md).

## Deliverables

- [x] `TileView(items, key, selection, onSelectionChange, ...) { item -> TileContent(...) }` over `Rtile_view_set_items`, the callback reading the latest `items` and `tile` while Raven paints; icons from painters made in composition, as `TreeView`'s.
- [x] `tileSize`, `iconSize`, `emptyText`, and `layout` (`TileLayout.Tiles`/`Rows`) and `spacing` through `Rwidget_set_attribute`.
- [x] The selection as a controlled set of keys, kept across reorders, a refused change reverting; `refresh` for the same count, `set_items` and reselecting for a new one.
- [x] `onActivate`, `contextMenuItems` at the pointer, and `TileViewState.revealItem`.
- [x] Headless tests: tiles read through the installed callback, click, Ctrl-click and right click injected, a refused selection reverting, the selection across a reorder, refresh against set_items, the menu and activation.
- [x] The XML translator's `<TileView>` attributes, with an example.
- [x] The spec's section and section 12's row checked against what was built; `api-coverage.md` updated (drags and `get_item_at` left for drags inside the app).

## Resolution

Built on 2026-11-27.

- **`TileView`**, **`TileContent`**, **`TileLayout`** and **`TileViewState`** are in foundation's
  `TileView.kt`, with the spec's signature.
  - `tile` is called through the item handler, which reads the latest `items`, and each icon's
    painter gets `addViewer`.
  - A mirror gives the tiles: the same number calls `Rtile_view_refresh`, and a new number calls
    `Rtile_view_set_items`, then selects the app's keys again, since Raven clears its selection.
  - `tileSize` and `iconSize` go through `Rtile_view_set_tile_size`. `layout` and `spacing` go
    through `Rwidget_set_attribute`, and null spacing writes Raven's 8 dp back. `emptyText` goes
    through `Rtile_view_set_empty_text`.
- **ui's bindings** are a new `TileViewBinding.kt`:
  - `TileItemValue` and the `TileViewItem` handler fill `RTileItem`, its texts in an arena that
    is freed at the next tile;
  - `TileViewSelection` is the selection as a set of indices, which `TileView` maps to keys;
  - `TileViewSelectionChanged`, `TileViewActivated` and `TileViewContext` are the events;
  - `setTileViewItems` gives Raven the tiles;
  - `readTile`, for tests, calls the same C callback Raven calls.
- **Tests** are `TileViewTest` (4):
  - tiles read through the callback: texts, the badge, the icon's texture and region, and an
    empty tile past the end;
  - redrawn for the same number and given again for a new one;
  - click and Ctrl-click injected, with a refused selection reverting, the selection following
    a reorder, and kept across a new number of items;
  - a right click selecting first and opening the menu at the pointer, and Enter activating;
  - `revealItem` on 10,000 tiles in `TileLayout.Rows`.
- **The XML translator** maps `<TileView>`'s attributes onto the parameters, hoists the
  selection, and leaves a TODO for the items. Its example is in `TranslatorTest`. The examples
  check now translates and compiles `transfer.xml`'s and `showcase.xml`'s TileViews.
- **Docs**: the spec's section records the bindings, `readTile` and the translation, and the
  translator's mapping table gains `TileView`.
- **`api-coverage.md`**: 581 of 1,704 functions (34%), `raven_controls.h` at 163, 44 of 58 widget
  types, and the row marked built. Sections, drags and `get_item_at` are left for drags inside the
  app.
- **Suites**: the ui, foundation, xml and mcp suites pass.
