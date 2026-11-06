---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0014]
assignee: null
---

# Menus and the header bar

## Question

Which composables cover Raven's MenuBar, Menu, MenuItem (with Shortcut, Checkable, Checked) and HeaderBar, as the `notepad` port needs? Compose Desktop's `MenuBar { Menu { Item() } }` is the convention to follow. Decide how menus attach to the window from `ravenApplication`, how shortcuts and actions map onto lambdas, and how HeaderBar and the window's `CustomHeader` fit the single-window entry point.
