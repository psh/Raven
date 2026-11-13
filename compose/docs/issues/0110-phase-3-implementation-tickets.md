---
status: open
wayfinder: task
parent: 0095
blocked_by: [0096, 0097, 0098, 0099, 0100, 0101, 0102, 0103, 0104, 0105, 0106, 0107, 0108, 0109]
assignee: null
---

# Phase 3 implementation tickets

## Question

Nothing to decide: once the other tickets close, break phase 3 into implementation tickets, as
phase 2 was. `roadmap.md`'s phase 3 section becomes a table of families with their spec sections,
the samples that need them and their tickets, each ticket with a **Depends on** line, in
dependency order, including what phase 3 completes for phase 2 (the default dialogs, `themes`'
`ListView`, `Text(softWrap)`/`fontFamily`, `MarkdownText`'s `style`, the rest of transitions).
Write one sample ticket for each of the five samples, listing only its port gaps, each naming the
phase that completes it. A sample ticket also lists the deliberate differences from its
C example that this map decided (for `music`, in
[Music's pages and back navigation](archive/0098-musics-pages-and-back-navigation.md)). Then close this map.
