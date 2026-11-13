---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: [0096, 0097]
assignee: Paul Hawke
---

# Music's pages and back navigation

## Question

`music` shows five pages (Home, an album or playlist, Search, Library, Settings) in a `Switcher`,
and Search shows its genres or its results in a second one. It goes back with Alt+Left and its
header's back button (`nav.back`), so it keeps a history of pages. Phase 3 has no Switcher
([Tabs, Switcher and grids](0034-tabs-switcher-and-grids.md) chose `when (page) { }`,
on the grounds that no phase 3 sample needs a hidden page kept alive, which nobody checked against
`music`). Decide how the port shows its pages: `when`, or `NavDisplay` with a back stack (which has
desktop back keys and the saved-state decorator), and whether a page left and come back to keeps
its state and scroll position, and by what (saved state, [Scroll position](0096-scroll-position.md),
or keeping it composed). If no phase 3 composable can do what the C example does, it is a port gap
with the phase that completes it.

## Resolution

Decided 2026-11-12. Nothing new for compose-raven: the port is app code over phase 3's
`compose-raven-navigation3`, and `music` becomes the phase 3 sample that exercises it. It also bears
out [Tabs, Switcher and grids](0034-tabs-switcher-and-grids.md): no page has to stay composed while
hidden.

- **Pages**: a `NavDisplay` with the keys `Home`, `Collection(kind, index)`, `Search`, `Library` and
  `Settings`. The back stack is the history, and the saveable-state decorator keeps each page's
  state, scroll position included (`ScrollState` is saveable, per [Scroll position](0096-scroll-position.md),
  and the decorator keeps it, per [Verify Navigation 3 under the host](0097-verify-navigation-3-under-the-host.md)).
- **Search's two views**: `if (query.isEmpty()) Genres() else Results(query)` inside the Search page;
  the query lives in the header, and nothing needs keeping between the views.
- **A page visited again** moves to the top of the stack (bring to front), so keys stay unique and
  each page has one state; showing the key already on top pushes nothing. The C history's cap of 32
  goes, since the stack is bounded by the distinct pages and albums. To check when implementing: a
  move doesn't count as a pop, so the decorator keeps the page's state.
- **Back with nowhere to go**: `onBack` ignores the request when one entry is left, and the header's
  back button is `enabled = backStack.size > 1`.
- **Transitions**: none on `NavDisplay`; the pages' own appear transitions replay as each page
  appears, as in the C example (whose Switcher has no transition).
- **Back keys**: `Window(backKeys)`'s defaults: Alt+Left, Cmd+[ and the mouse Back button.

Deliberate differences from the C example, for `music`'s sample ticket (not port gaps): Back to an
album shows that album, not the last one opened; a page visited again moves to the top instead of
repeating in the history; Back is disabled at the root; Back takes the extra keys.
