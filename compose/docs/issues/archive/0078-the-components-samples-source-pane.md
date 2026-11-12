---
status: closed
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: Paul Hawke
---

# The components sample's source pane

## Question

`components` builds a card as an XML document in C (`Rxml_document_*`, `Rxml_node_*`), adds it,
shows the XML it wrote in a TextEditor, and lists the registered `<Widget>` tags. In Compose a card
is a composable and there is no document. What does the phase 2 port show in that pane, and how
does "Add a card" read, so the sample still teaches what the C example teaches (tags of your own,
and building interface from code)? Settle whether anything here is a port gap at all, and if so,
which phase completes it.

## Resolution

Nothing new for the spec: the port teaches the C example's lesson in Compose's terms, where a tag
of your own is a composable and building interface from code is all there is.

- **Cards are composables with slots**, private to the sample: `Card(title, badge, wide, footer,
  content)`, the `footer` slot replacing `Into="footer"`; `Stat`, `Badge`, and `PersonRow` for the
  XML's `Row`. `<Repeat>`, `<ForEach>` and expressions become Kotlin loops and string templates.
- **Adding and removing** work on app state, a `mutableStateListOf<CardData>()` keyed by id; a
  card's "Remove" removes it from the list.
- **The source pane** shows the Kotlin call that composes the last card added, generated from its
  `CardData`, in a read-only Kotlin `CodeEditor`.
- **The "Declared tags" status** has no counterpart, since Compose has no tag registry, and is no
  gap; the status line gives a hint and reports adds and removes.
- **Port gaps left**: the appear transitions (phase 3's `Modifier.appearTransition`) and the
  minimum window size, added to [Window minimum size](0083-window-minimum-size.md).
