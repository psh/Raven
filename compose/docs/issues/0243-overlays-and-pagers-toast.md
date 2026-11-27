---
status: open
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: null
---

# Overlays and pagers: Toast

## Question

How is Raven's Toast (transient messages: `Rtoast_*`) bound? Compose has no Toast; Material 3 has
`Snackbar` shown through a `SnackbarHostState` and `showSnackbar(message, actionLabel)`, which
suspends until dismissed. Decide whether compose-raven follows that shape over Raven's toast, or
offers Raven's under its own name, and where it shows (window, stacked). Proved by a test app. The
answer is the spec section and its implementation tickets.
