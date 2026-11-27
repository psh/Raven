---
status: open
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: null
---

# Specialist widgets: BlockEditor

## Question

How is Raven's BlockEditor (`Rblock_editor_*`, 36 functions: a document of blocks the user edits,
reorders and converts) bound? Decide whether the document is a controlled value (a list of blocks
the app holds, each edit a proposal) or a state object Raven edits and the app reads, how blocks
are keyed and typed, and what an app reads back (the text, a serialised form). No example uses it:
decide the test app that proves it. The answer is the spec section and its implementation tickets.
