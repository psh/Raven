---
status: open
wayfinder: grilling
parent: 0287
blocked_by: [0288]
assignee: null
---

# Gestures and drag: nested scroll

## Question

Whether and how compose-raven offers `Modifier.nestedScroll` and scroll-linked effects such as collapsing toolbars. They need two Raven changes: a scroll offered to a callback before the scroll box applies it, reporting what is left; and the scroll box's drawn offset, so an effect follows the smooth scroll rather than its target (today `ScrollState.value` reaches the target at once, a frame late). Which of the two are worth it, the shape of `NestedScrollConnection` that results, and what proves it. From [compose-raven phase 5 spec](0204-compose-raven-phase-5-spec.md#not-yet-specified).
