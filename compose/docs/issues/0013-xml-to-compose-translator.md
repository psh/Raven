---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0009, 0011]
assignee: null
---

# XML to Compose translator sample

## Question

Raven can describe a UI in XML (`docs/XML.md`, `raven_xml.h`). What does a translator from a Raven XML document to equivalent compose-raven Kotlin source look like, and does it ship as the sample app (XML pasted into the left pane, generated Compose code shown on the right)? Decide which XML elements and attributes it covers (tied to the v1 component vocabulary and to how slots map to `Modifier`), what it does with constructs that have no composable (comment, warn, or refuse), how it parses (Raven's own XML parser through `raven-native`, or a Kotlin parser), and whether it is a sample-only tool or a reusable module.
