---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0003, 0004, 0006]
assignee: null
---

# Node model and Applier

## Question

What is compose-raven's node type, and how does its Applier map Compose's insert/move/remove operations onto the Raven widget tree? Cover: one node per Raven widget or not, when the widget is created and destroyed relative to the node, the root node (the window?), and how a widget's properties get updated.

Must also decide how the Applier handles the gaps found in "Raven widget tree API for an Applier" (0004): moves have no in-place call (detach + re-insert loses focus/hover and replays appear transitions), and single-child parents destroy their old child on a second insert. Work around them in Kotlin, or have the spec require a Raven change such as `Rwidget_move_child`?
