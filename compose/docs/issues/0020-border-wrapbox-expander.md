---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0014]
assignee: null
---

# Border, WrapBox and Expander

## Question

How do Raven's Border (padding, background, border colour and width, corner radius, shadow), WrapBox and Expander appear in compose-raven, as the `components` port needs? Border overlaps Compose's `Modifier.background`/`border`/`clip`, but v1 has no drawing modifiers and one node per widget. Decide between a container composable (`Surface`/`Card`-like) and drawing modifiers, and map WrapBox to `FlowRow` and Expander to a composable.
