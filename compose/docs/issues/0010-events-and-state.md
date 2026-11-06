---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0006, 0007]
assignee: null
---

# Events and state

## Question

How do Raven event callbacks reach Kotlin lambdas passed to composables (`onClick` and so on), and how does input state flow? For inputs like text fields and checkboxes, are composables controlled (state hoisted, Raven's value overwritten on recomposition) or uncontrolled, and how are update loops avoided?
