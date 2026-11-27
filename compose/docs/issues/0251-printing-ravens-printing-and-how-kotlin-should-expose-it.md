---
status: open
wayfinder: research
parent: 0241
blocked_by: []
assignee: null
---

# Printing: Raven's printing and how Kotlin should expose it

## Question

What printing does Raven offer (`Rpdf_print`, `Rpdf_print_file`, `Rpdf_document_print`,
`Rpdf_view_print`, `RPdfPrintDesc`, the system print dialog, platforms and their fallbacks), and
how should a Compose app reach it without a widget? Survey how Compose and Kotlin expose printing
elsewhere (Android's `PrintManager` and `PrintDocumentAdapter`, Compose Desktop and AWT's
`PrinterJob`, Kotlin Multiplatform libraries) and how much of `Rpdf_document_*` printing needs.
Facts for a decision on printing's API, which this map graduates once they're in.
