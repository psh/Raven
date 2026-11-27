---
status: open
---

# Documents: PdfView

Bind Raven's PdfView as `PdfView(source, state, password)` with a `PdfViewState`. Spec:
[8: PdfView](../spec.md#pdfview-phase-5) and section 12. Planned in [Documents: PdfView](archive/0247-documents-pdfview.md).

**Depends on:** [Raven: PDF view zoom callback](0263-raven-pdf-view-zoom-callback.md).

## Deliverables

- [ ] `PdfView(source, modifier, state, password)` over `Rpdf_view_*`: `PdfSource.File` and `.Bytes`, reloading on a changed source.
- [ ] `PdfViewState`, saveable: `currentPage`, `pageCount`, `title`, `status`, `scrollToPage`, `fit` and `zoom` over the zoom callback, `pageSize`, `pageText`; the page put back after a load, a new source at page 0.
- [ ] `PdfStatus` and the password flow.
- [ ] Headless tests: `NoPdfium` always; with PDFium (returning early without it): a file and bytes, a password, the page and the zoom followed and set, a reload.
- [ ] The XML translator's `<PdfView>`.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the PdfView row and the numbers).
