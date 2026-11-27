---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
---

# Documents: PdfView

## Question

How is Raven's PdfView (the pages of a PDF, scrolled and zoomed, drawn by PDFium loaded at run
time: `Rpdf_view_*`) bound? Decide the composable and its state (the document from a file, bytes
or a resource; page, zoom, search), what a Compose app sees when PDFium isn't installed, and how
tests run without it. Printing a view is decided with printing, in
[Printing and PDF documents: what Kotlin exposes](../0252-printing-and-pdf-documents-what-kotlin-exposes.md).
The answer is the spec section and its implementation tickets, with the `pdf` port.

## Resolution

Resolved on 2026-11-27, with the user, in two rounds: designed into
[spec section 8: PdfView](../../spec.md#pdfview-phase-5), section 11's change 16 and section 12.

- **`PdfView(source: PdfSource?, state, password)`**: `PdfSource.File` or `.Bytes`, reloading on a
  changed source; Raven keeps scrolling, zooming and its own status messages.
- **`PdfViewState`**, as the pagers have, saveable: observable `currentPage`, `pageCount`, `title`,
  `status`; `scrollToPage`; `fit` and `zoom` observable and set, over **a Raven change** (16), a zoom
  changed callback, instead of polling as the C example does; `pageSize` and `pageText`. A new
  source starts at page 0.
- **`PdfStatus`** with the password flow left to the app.
- **No search** (fog); **headless tests**, those of a loaded document returning early without
  PDFium.
- **The `pdf` port moves to batch ten**: it writes, saves and prints its invoice. Printing a view
  is decided there too.

Filed as [Raven: PDF view zoom callback](../0263-raven-pdf-view-zoom-callback.md),
[Propose PDF view zoom callback upstream](../0264-propose-pdf-view-zoom-callback-upstream.md) (on
hold) and [Documents: PdfView](../0265-documents-pdfview.md).
