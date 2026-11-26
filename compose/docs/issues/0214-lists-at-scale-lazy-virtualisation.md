---
status: open
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: null
---

# Lists at scale: lazy virtualisation

## Question

How do `LazyColumn` and `LazyRow` compose only the items in view? Today they compose every item
inside a scroll box. Decide how the visible range comes from the scroll box's offset and measured
item sizes, how items are measured before they are composed (estimates, spacers), what
`LazyListState` gains (`layoutInfo`), how keys and saveable state survive scrolling, and whether
Raven needs a change. Call `codebase-design` too. The answer is the spec section and its
implementation tickets, which Table and TileView build on.
