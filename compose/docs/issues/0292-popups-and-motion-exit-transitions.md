---
status: open
wayfinder: grilling
parent: 0287
blocked_by: []
assignee: null
---

# Popups and motion: exit transitions

## Question

How `AnimatedVisibility` gets exit transitions (`fadeOut`, `slideOut*`, `scaleOut`, `shrinkOut`): a Raven change for exit animations (fly, scale, slide out) that keeps a removed widget drawn until its animation ends, or compose-raven keeping the node alive and animating it itself. What happens to a widget removed mid-animation, and what proves it. From [compose-raven phase 5 spec](0204-compose-raven-phase-5-spec.md#not-yet-specified).
