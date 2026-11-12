---
status: open
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: null
---

# The components sample's source pane

## Question

`components` builds a card as an XML document in C (`Rxml_document_*`, `Rxml_node_*`), adds it,
shows the XML it wrote in a TextEditor, and lists the registered `<Widget>` tags. In Compose a card
is a composable and there is no document. What does the phase 2 port show in that pane, and how
does "Add a card" read, so the sample still teaches what the C example teaches (tags of your own,
and building interface from code)? Settle whether anything here is a port gap at all, and if so,
which phase completes it.
