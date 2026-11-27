---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
---

# Documents: DiffView

## Question

How is Raven's DiffView (two texts or a patch compared, unified or side by side, folded:
`Rdiff_view_*`) bound? Decide the composable's parameters (texts or a patch, mode, context,
whitespace and case, word diff, language), what is controlled state, and what clicks report. The
answer is the spec section and its implementation tickets, with the `diff_view` port.

## Resolution

Resolved on 2026-11-27, with the user, in two rounds: designed into
[spec section 8: DiffView](../../spec.md#diffview-phase-5), section 10's
[samples/diff-view](../../spec.md#samplesdiff-view-phase-5) and section 12.

- **`DiffView(content: DiffContent, ...)`**: `Texts`, `Files` or `Patch` (a `git diff` with every
  file under its names), compared by value; the options as parameters shaped like the XML (`mode`,
  `context`, `whitespace`, `ignoreCase`, `wordDiff`, `lineNumbers`, `language`).
- **`annotations`** as a list the app owns; **`onLineClick(side, line, inGutter)`** an event.
- **`DiffViewState`**: change count, current change following navigation only (Raven doesn't
  report scrolling; no Raven change), `goToChange`, next and previous, `selectedText`; Raven's row
  API unbound.
- **`unifiedDiff` and `countChanges`** as top-level functions.
- Headless tests, no new harness API; the `diff_view` port without port gaps; the XML translator
  covers `<DiffView>`.

Filed as [Documents: DiffView](../0266-documents-diffview.md) and
[samples/diff-view](../0267-samples-diff-view.md).
