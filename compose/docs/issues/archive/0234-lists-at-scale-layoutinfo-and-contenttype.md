---
status: closed
---

# Lists at scale: layoutInfo and contentType

Give `LazyListState` Compose's `layoutInfo` and the `canScroll` flags, and the DSL its
`contentType`. Spec: [8: Lazy virtualisation](../../spec.md#lazy-virtualisation-phase-5) and section 12.
Planned in [Lists at scale: lazy virtualisation](0214-lists-at-scale-lazy-virtualisation.md).

**Depends on:** [Lists at scale: the lazy list window](0233-lists-at-scale-the-lazy-list-window.md).

## Deliverables

- [x] `LazyListLayoutInfo` and `LazyListItemInfo` (`index`, `key`, `offset`, `size`), with `visibleItemsInfo`, `totalItemsCount`, `viewportStartOffset` and `viewportEndOffset` in dp; `LazyListState.layoutInfo` as snapshot state, read once a frame from the window.
- [x] `canScrollForward` and `canScrollBackward`.
- [x] `contentType` on `item`, `items` and the list extensions, accepted and ignored.
- [x] Tests: the layout info as the list scrolls and its items change; the flags at each end; a recomposition reading `layoutInfo` (a "scroll to top" button shown once past the first item).
- [x] Section 12's rows checked against what was built.

## Resolution

Built on 2026-11-27.

- **`LazyListLayoutInfo`** and **`LazyListItemInfo`** are data classes in
  `lazy/LazyListLayoutInfo.kt`, in whole dp. `viewportStartOffset` is 0, as there is no content
  padding, and `viewportEndOffset` is the viewport's length.
- **`layoutInfo`**, **`canScrollForward`** and **`canScrollBackward`** are snapshot state, written
  in the state's settle step, so once the frame is laid out.
  - The items' offsets and sizes come from their real places. They come from the window only when
    the box's children aren't the slots last composed.
  - Equal values write nothing, as the state uses a structural policy.
  - Backward is the offset past 0. Forward is the box's last child (a Spacer or the last item)
    ending past the viewport.
- **`contentType`** is on `item`, `items(count)`, `items(list)` and `itemsIndexed`, accepted and
  ignored. `LazyVerticalGrid`'s DSL doesn't take it yet, which is noted in the spec and in
  [Lists at scale, continued: lazy grids](../0295-lists-at-scale-continued-lazy-grids.md).
- **Tests** are `LazyListLayoutInfoTest` (4):
  - the layout info at the start, after a scroll, and after items are inserted and removed;
  - the flags at the start, the middle and the end, and on a short `LazyRow`;
  - a "Top" button shown through `derivedStateOf` over `layoutInfo`, clicked back to the start;
  - `contentType` through each DSL call.

  The foundation, xml and mcp suites pass.
- **Spec**: the section's `layoutInfo` and `contentType` bullets are updated. Section 12's rows
  stand. No Raven API, so `api-coverage.md` is unchanged.
