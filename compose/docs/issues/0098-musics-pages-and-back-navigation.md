---
status: open
wayfinder: grilling
parent: 0095
blocked_by: [0096, 0097]
assignee: null
---

# Music's pages and back navigation

## Question

`music` shows five pages (Home, an album or playlist, Search, Library, Settings) in a `Switcher`,
and Search shows its genres or its results in a second one. It goes back with Alt+Left and its
header's back button (`nav.back`), so it keeps a history of pages. Phase 3 has no Switcher
([Tabs, Switcher and grids](archive/0034-tabs-switcher-and-grids.md) chose `when (page) { }`,
on the grounds that no phase 3 sample needs a hidden page kept alive, which nobody checked against
`music`). Decide how the port shows its pages: `when`, or `NavDisplay` with a back stack (which has
desktop back keys and the saved-state decorator), and whether a page left and come back to keeps
its state and scroll position, and by what (saved state, [Scroll position](archive/0096-scroll-position.md),
or keeping it composed). If no phase 3 composable can do what the C example does, it is a port gap
with the phase that completes it.
