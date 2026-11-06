---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0004]
assignee: null
---

# Layering over cinterop

## Question

Does compose-raven call the raw cinterop bindings directly, or is there a thin idiomatic Kotlin layer in between (handles as value classes, callbacks via `StableRef`, strings and structs converted at the edge)? If there is a layer, is it a separate module that can be used without Compose?
