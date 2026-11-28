---
status: open
---

# Lists at scale: TileView

Add `TileView` over Raven's virtualised TileView: the app's items and a controlled selection of
keys, and a `tile` function giving each item's `TileContent`. Spec: [8: TileView](../spec.md#tileview-phase-5)
and section 12. Planned in [Lists at scale: TileView](archive/0216-lists-at-scale-tileview.md).

**Depends on:** [Lists at scale: Table](archive/0235-lists-at-scale-table.md).

## Deliverables

- [ ] `TileView(items, key, selection, onSelectionChange, ...) { item -> TileContent(...) }` over `Rtile_view_set_items`, the callback reading the latest `items` and `tile` while Raven paints; icons from painters made in composition, as `TreeView`'s.
- [ ] `tileSize`, `iconSize`, `emptyText`, and `layout` (`TileLayout.Tiles`/`Rows`) and `spacing` through `Rwidget_set_attribute`.
- [ ] The selection as a controlled set of keys, kept across reorders, a refused change reverting; `refresh` for the same count, `set_items` and reselecting for a new one.
- [ ] `onActivate`, `contextMenuItems` at the pointer, and `TileViewState.revealItem`.
- [ ] Headless tests: tiles read through the installed callback, click, Ctrl-click and right click injected, a refused selection reverting, the selection across a reorder, refresh against set_items, the menu and activation.
- [ ] The XML translator's `<TileView>` attributes, with an example.
- [ ] The spec's section and section 12's row checked against what was built; `api-coverage.md` updated (drags and `get_item_at` left for drags inside the app).
