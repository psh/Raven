---
status: open
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: null
---

# Printing and PDF documents: what Kotlin exposes

## Question

How does a Compose app print, and write the PDFs printing needs, without a widget? Raven prints PDFs
only, blocking on the UI thread, and anything else must first become a PDF through
`Rpdf_document_*`. Decide which of the options in
[Printing: Raven's printing and how Kotlin should expose it](archive/0251-printing-ravens-printing-and-how-kotlin-should-expose-it.md)
compose-raven takes (PDFs only; plus the window printed; plus a PDF document builder in Kotlin; or a
`Printable`-style page callback over a Raven change), the shape of the calls (a `suspend` function
beside the native dialogs, a print result that tells cancelled from failed if Raven can), how a
PdfView's document is printed, and whether Compose keeps drawing behind the macOS print panel (check
it). The answer is the spec section and its implementation tickets: batch ten, printing and PDF
documents.
