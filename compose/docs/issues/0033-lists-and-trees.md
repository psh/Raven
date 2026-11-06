---
status: open
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: null
---

# Lists and trees

## Question

How do Raven's ListView, TreeView and TileView, which are virtual and driven by callbacks (`Rlist_view_set_virtual_items`, text and event callbacks), map onto Compose? Decide between `LazyColumn`-style item DSLs and data-driven composables (`items: List<T>`, item text lambdas), how selection and activation are controlled, how trees express nesting and expansion, and what `music`, `editor` and `workspace` need.
