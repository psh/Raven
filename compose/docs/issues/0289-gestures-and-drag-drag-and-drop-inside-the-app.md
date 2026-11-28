---
status: open
wayfinder: grilling
parent: 0287
blocked_by: [0288]
assignee: null
---

# Gestures and drag: drag and drop inside the app

## Question

How drags inside the app and drop targets on widgets are exposed: `Modifier.dragAndDropSource` / `Modifier.dragAndDropTarget` over `Rdrag_begin` and `Rwidget_set_drop_callback`, beyond phase 2's `Window(onExternalDrop)`; what a drag carries (text, files, an app value), the drag image, and the item at a point that [Lists at scale: TreeView expansion and item operations](archive/0217-lists-at-scale-treeview-expansion-and-item-operations.md) left waiting for drags (reordering a list or tree). From [compose-raven phase 5 spec](maps/0204-compose-raven-phase-5-spec.md#not-yet-specified).
