---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0013, 0014]
assignee: Paul Hawke
---

# Assemble the spec

## Question

What is the structure of `compose/docs/spec.md`, the map's destination, and how are the closed tickets' decisions folded into it? Decide its sections (modules and build, the node model and Applier, frame loop, layout, events, the v1 vocabulary, testing, samples), the section listing the changes the spec requires in the `psh/Raven` fork (so far `Rwidget_move_child`, and possibly repeated `Rinit`/`Rshutdown`), the place for documented differences from Compose, and how the remaining fog patches are recorded as future work.

## Resolution

- **Written**: [`compose/docs/spec.md`](../spec.md), status "v1 complete", reviewed and accepted.
- **Structure**: 14 sections from the outside in: Overview, Modules and build, raven-native, Application host, Node model and Applier, Layout and Modifiers, Events and state, v1 component vocabulary, Testing, Samples, Required Raven changes, Differences from Compose, Roadmap, Out of scope.
- **Folding decisions in**: the spec is self-contained and states only the final state, so where a later ticket amended an earlier one, only the result appears. It says *what*, and each section ends with "Decided in" links to the tickets, which keep the *why*.
- **Differences from Compose**: one table (section 12), each row linking to its section. The sections themselves state behaviour without repeated "unlike Compose" notes.
- **Required Raven changes**: section 11. `Rwidget_move_child` is required. A fix for repeated `Rinit`/`Rshutdown` is conditional on the first verification.
- **After v1**: the map stays open. Each remaining ticket updates `spec.md` (vocabulary, roadmap, differences) when it closes, and the map closes when no tickets remain.
- **Alongside**: the map's Out of scope line on source compatibility was reworded to match the later decisions. Names and shapes follow Compose, with no guarantee that androidx foundation/material code compiles.
