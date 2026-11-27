---
status: open
---

# Raven: PDF view zoom callback

Add `Rpdf_view_set_zoom_changed_callback` to Raven, so `PdfViewState`'s zoom and fit follow the
user and a fit's resize. Spec: [11: change 16](../spec.md#11-required-raven-changes). Planned in [Documents: PdfView](archive/0247-documents-pdfview.md).

**Depends on:** [samples/charts](0262-samples-charts.md), the ticket before it in phase 5's
implementation sequence.

## Deliverables

- [ ] `RPdfZoomChangedCallback(view, zoom, fit, user_data)` and the setter in `raven_controls.h`, documented: called for the user's zoom and fit, and a fit's zoom after a resize; not for `Rpdf_view_set_zoom` or `Rpdf_view_set_fit`.
- [ ] A C test, run where PDFium is (skipped without it, as `test_pdf` is): Ctrl + wheel, Ctrl + 0, a resize under `Width`, and the app's own calls not reported.
- [ ] One commit on `compose-raven`, the bindings regenerated, the reference updated, spec section 11's change 16 marked made.
