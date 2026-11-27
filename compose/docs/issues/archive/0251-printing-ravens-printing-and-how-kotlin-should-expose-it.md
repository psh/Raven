---
status: closed
wayfinder: research
parent: 0241
blocked_by: []
assignee: claude
---

# Printing: Raven's printing and how Kotlin should expose it

## Question

What printing does Raven offer (`Rpdf_print`, `Rpdf_print_file`, `Rpdf_document_print`,
`Rpdf_view_print`, `RPdfPrintDesc`, the system print dialog, platforms and their fallbacks), and
how should a Compose app reach it without a widget? Survey how Compose and Kotlin expose printing
elsewhere (Android's `PrintManager` and `PrintDocumentAdapter`, Compose Desktop and AWT's
`PrinterJob`, Kotlin Multiplatform libraries) and how much of `Rpdf_document_*` printing needs.
Facts for a decision on printing's API, which this map graduates once they're in.

## Resolution

Resolved on 2026-11-26 by research: the findings are on the local branch `research/printing-for-kotlin` (commit
`bd2ba9a6`), in `compose/docs/research/printing-ravens-printing-and-how-kotlin-should-expose-it.md`; the branch is never merged or pushed.

- **Raven prints PDFs only.** `Rpdf_print_file`, `Rpdf_document_print` and `Rpdf_view_print` all
  end in `Rpdf_print`, which takes bytes starting `%PDF-`; there is no page-drawing callback and no
  image printing.
- **Platforms**: macOS through the Cocoa print panel (vector, no PDFium); Windows through the system
  dialog, pages rasterised by PDFium (fails without it); Linux through CUPS `lp`/`lpr` behind a small
  dialog of Raven's; the browser always fails; iOS and Android, by reading the code, fail but for
  writing to an output file. compose-raven's targets today (macOS arm64, Linux x64) need no PDFium.
- **Shape**: UI thread only, blocking until the job is handed over, returning a bool; no callbacks,
  no job handle, cancelled not told from failed. It is the shape of the dialogs compose-raven
  already wraps as `suspend` functions (`awaitNativeDialog`), so a widgetless `printPdf(...)` fits.
  Unverified: whether Compose keeps drawing behind the macOS print panel.
- **Does printing need PDF writing? Yes**, for anything but an existing PDF (file, bytes, a
  PdfView's document): what an app draws must become a PDF first, through `Rpdf_document_*`
  (vector pages, or the window captured with `Rpdf_document_draw_window`), or a PDF the app writes
  in Kotlin.
- **Elsewhere**: Android's `PrintDocumentAdapter` also writes a PDF (`PrintHelper` prints bitmaps);
  AWT's `PrinterJob` + `Printable` is the main page-callback API; Compose Multiplatform has none.
- **Options** for the decision: print PDFs only; plus the window printed; plus a PDF document
  builder; or a `Printable`-style page callback, which needs new Raven C.

Since printing needs PDF writing, the two are designed together as one batch:
[Printing and PDF documents: what Kotlin exposes](../0252-printing-and-pdf-documents-what-kotlin-exposes.md).
