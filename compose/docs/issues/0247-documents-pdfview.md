---
status: open
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: null
---

# Documents: PdfView

## Question

How is Raven's PdfView (the pages of a PDF, scrolled and zoomed, drawn by PDFium loaded at run
time: `Rpdf_view_*`) bound? Decide the composable and its state (the document from a file, bytes
or a resource; page, zoom, search), what a Compose app sees when PDFium isn't installed, and how
tests run without it. Printing a view is decided with printing, in
[Printing and PDF documents: what Kotlin exposes](0252-printing-and-pdf-documents-what-kotlin-exposes.md).
The answer is the spec section and its implementation tickets, with the `pdf` port.
