---
status: open
---

# samples/pdf

Port Raven's `examples/pdf`. Spec: [10: samples/pdf](../spec.md#samplespdf-phase-5). Planned in [Printing and PDF documents: what Kotlin exposes](archive/0252-printing-and-pdf-documents-what-kotlin-exposes.md).

**Depends on:** [Printing and PDF documents: buildPdf and printPdf](0279-printing-and-pdf-documents-buildpdf-and-printpdf.md).

## Deliverables

- [ ] `samples/pdf`: the invoice written with `buildPdf` and shown, Open... and Save..., Print..., Previous and Next with "Page N of M", zoom with its percentage, the fit, the title.
- [ ] A headless test checking each control (those needing PDFium returning early without it).
- [ ] No port gaps; the roadmap's sample table and `api-coverage.md` updated.
