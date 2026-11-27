---
status: open
---

# Documents: DiffView

Bind Raven's DiffView as `DiffView(content, state, ...)` with a `DiffViewState`, and
`unifiedDiff` and `countChanges`. Spec: [8: DiffView](../spec.md#diffview-phase-5) and section 12.
Planned in [Documents: DiffView](archive/0248-documents-diffview.md).

**Depends on:** [Documents: PdfView](0265-documents-pdfview.md), the ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] `DiffView(content, modifier, state, mode, context, whitespace, ignoreCase, wordDiff, lineNumbers, language, annotations, onLineClick)` over `Rdiff_view_*`: `DiffContent.Texts`, `.Files` and `.Patch`, compared by value.
- [ ] `annotations` cleared and added again when the list changes; `onLineClick` from Raven's callback.
- [ ] `DiffViewState`: `changeCount`, `currentChange` following navigation, `goToChange`, `nextChange`, `previousChange`, `selectedText`.
- [ ] `unifiedDiff` and `countChanges` over `Rdiff_unified` and `Rdiff_count_changes`.
- [ ] Headless tests: each content form, the options reaching Raven, navigation and its count, the annotations, a line click, a multi-file patch.
- [ ] The XML translator's `<DiffView>`.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the DiffView row and the numbers).
