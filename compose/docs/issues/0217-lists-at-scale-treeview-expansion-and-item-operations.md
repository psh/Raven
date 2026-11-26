---
status: open
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: null
---

# Lists at scale: TreeView expansion and item operations

## Question

How does `TreeView`'s expansion become the app's state, and which item operations do `TreeView`
and `ListView` gain? Expansion needs a Raven change (an expand or collapse callback); decide its C
shape and the controlled `expanded` state. The item operations: scrolling to an item, the item at
a point, the selected item, renaming a tab. The answer is the spec section, the Raven change and
the implementation tickets.
