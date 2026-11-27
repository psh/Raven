---
status: open
---

# Printing and PDF documents: buildPdf and printPdf

Write PDFs with `buildPdf { page { } }` over `Rpdf_document_*`, print them with `suspend printPdf`,
and print a `PdfView` with `PdfViewState.print`. Spec:
[8: Printing and PDF documents](../spec.md#printing-and-pdf-documents-phase-5) and section 12.
Planned in [Printing and PDF documents: what Kotlin exposes](archive/0252-printing-and-pdf-documents-what-kotlin-exposes.md).

**Depends on:** [samples/map](0275-samples-map.md), the ticket before it in phase 5's implementation
sequence (batch nine's tickets, once filed, follow batch ten's).

## Deliverables

- [ ] `buildPdf(title, author, subject) { page(width, height) { } }: ByteArray` and the page scope: colours, line width, rect, ellipse, line, fonts (`PdfFont.Sans`, `.Mono`, `addFont`), text, paragraph, measureText, lineHeight, image, window.
- [ ] `suspend printPdf(source, title, printer, copies, dialog, output, password): Boolean` over `Rpdf_print` and `Rpdf_print_file` through `awaitNativeDialog`.
- [ ] `PdfViewState.print(...)` over `Rpdf_view_print`.
- [ ] Headless tests: the bytes of a built document, its text read back where PDFium is, `printPdf` to an `output` file without a dialog.
- [ ] By hand on macOS, recorded here: whether Compose keeps drawing behind the print panel, and what to do if it doesn't.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the PdfView row, `raven_controls.h`'s numbers).
