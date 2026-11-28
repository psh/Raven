---
status: open
wayfinder: grilling
parent: 0287
blocked_by: []
assignee: null
---

# Lists at scale, continued: lazy list extras

## Question

Which of the lazy list extras to design for `LazyColumn` / `LazyRow`, over [Lists at scale: the lazy list window](archive/0233-lists-at-scale-the-lazy-list-window.md): `stickyHeader`, `Modifier.animateItem`, prefetching ahead of the scroll, and a DSL that keeps intervals and asks for keys and content by index only near the view, so `items(count) { }` over paged data costs only what is on screen (today every key is computed each time the content changes, for the key map). From [compose-raven phase 5 spec](0204-compose-raven-phase-5-spec.md#not-yet-specified).
