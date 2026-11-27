---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
---

# Printing and PDF documents: what Kotlin exposes

## Question

How does a Compose app print, and write the PDFs printing needs, without a widget? Raven prints PDFs
only, blocking on the UI thread, and anything else must first become a PDF through
`Rpdf_document_*`. Decide which of the options in
[Printing: Raven's printing and how Kotlin should expose it](0251-printing-ravens-printing-and-how-kotlin-should-expose-it.md)
compose-raven takes (PDFs only; plus the window printed; plus a PDF document builder in Kotlin; or a
`Printable`-style page callback over a Raven change), the shape of the calls (a `suspend` function
beside the native dialogs, a print result that tells cancelled from failed if Raven can), how a
PdfView's document is printed, and whether Compose keeps drawing behind the macOS print panel (check
it). The answer is the spec section and its implementation tickets: batch ten, printing and PDF
documents, with the `pdf` port, which writes, saves and prints its invoice (moved here by
[Documents: PdfView](0247-documents-pdfview.md)).

## Resolution

Resolved on 2026-11-27, with the user, in three rounds: designed into
[spec section 8: Printing and PDF documents](../../spec.md#printing-and-pdf-documents-phase-5),
section 10's [samples/pdf](../../spec.md#samplespdf-phase-5) and section 12.

- **Print PDFs and write them**: the third option of the research, which the `pdf` port needs; no
  page-drawing callback (it would need new C on every platform).
- **`buildPdf(...) { page { } }: ByteArray`**, run at once, returning bytes, so no document handle to
  close; a page scope mirroring Raven's calls in Compose types, `window(...)` among them.
- **`suspend printPdf(source, ...): Boolean`** through `awaitNativeDialog`; `dialog = false` and
  `output` for tests; cancelled and failed both `false`, as Raven reports them (telling them apart
  goes to fog).
- **`PdfViewState.print(...)`**; everything PDF in one place, beside `PdfView`.
- **The macOS print panel** checked by hand when built.
- Batch ten's tickets go straight after batch eight's; batch nine's follow when filed.

Filed as [Printing and PDF documents: buildPdf and printPdf](../0279-printing-and-pdf-documents-buildpdf-and-printpdf.md)
and [samples/pdf](../0280-samples-pdf.md).
