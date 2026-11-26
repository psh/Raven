---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Lists at scale: lazy virtualisation

## Question

How do `LazyColumn` and `LazyRow` compose only the items in view? Today they compose every item
inside a scroll box. Decide how the visible range comes from the scroll box's offset and measured
item sizes, how items are measured before they are composed (estimates, spacers), what
`LazyListState` gains (`layoutInfo`), how keys and saveable state survive scrolling, and whether
Raven needs a change. Call `codebase-design` too. The answer is the spec section and its
implementation tickets, which Table and TileView build on.

## Resolution

Resolved on 2026-11-26, with the user, in one round: designed into
[spec section 8: Lazy virtualisation](../../spec.md#lazy-virtualisation-phase-5) and section 12.

- **No Raven change**: the lists keep their ScrollBox, holding a Spacer, the items in view and a
  Spacer. `follow` reads the offset before recomposing, so the items arrive in the same frame.
- **Sizes** are read after layout and cached by key; unmeasured items count as the average, 48 dp
  at first; the first frame is filled by estimate and corrected on the next.
- **One item past each edge**, and the focused item pinned.
- **The place is kept** by the first visible item's key when sizes above it are corrected or items
  come and go above it.
- **`LazyListState`** gains `layoutInfo` and `canScrollForward`/`canScrollBackward`; no
  `isScrollInProgress`. `scrollToItem` to an unmeasured item corrects over the next frames.
- **`rememberSaveable`** in items survives by key; plain `remember` doesn't.
- **`contentType`** is accepted and ignored.
- **The window**: the arithmetic is one internal, pure module, tested with plain numbers; the lists
  through `runRavenTest`.
- **Scope**: lists only. `LazyVerticalGrid` goes with TileView; sticky headers, `animateItem` and
  prefetching to fog.

Filed as [Lists at scale: the lazy list window](../0233-lists-at-scale-the-lazy-list-window.md) and
[Lists at scale: layoutInfo and contentType](../0234-lists-at-scale-layoutinfo-and-contenttype.md).
