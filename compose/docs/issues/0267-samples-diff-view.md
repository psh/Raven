---
status: open
---

# samples/diff-view

Port Raven's `examples/diff_view`. Spec: [10: samples/diff-view](../spec.md#samplesdiff-view-phase-5). Planned in [Documents: DiffView](archive/0248-documents-diffview.md).

**Depends on:** [Documents: DiffView](0266-documents-diffview.md).

## Deliverables

- [ ] `samples/diff-view`: the two versions of `server.c` (read from Raven's example at a path the build writes in), mode, context, whitespace and case, Previous and Next with "change N of M", a note where a line is clicked.
- [ ] A headless test checking each control.
- [ ] No port gaps; the roadmap's sample table and `api-coverage.md` updated.
