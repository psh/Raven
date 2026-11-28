---
status: on-hold
---

# Propose PDF view zoom callback upstream

> **On hold (2026-11-27).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose `Rpdf_view_set_zoom_changed_callback` to Raven's `main`, on its own, as the way for any
app to follow a PdfView's zoom without polling it (the `pdf` example does, on a timer). It doesn't
mention compose-raven. Split out of [Documents: PdfView](../archive/0247-documents-pdfview.md).

**Depends on:** [Raven: PDF view zoom callback](../0263-raven-pdf-view-zoom-callback.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying the callback and its documentation, its C test, the `pdf` example's timer replaced by it, and the bindings regenerated against upstream's headers.
