---
status: open
---

# Lists at scale: Table

Add `Table`, Raven's sortable data grid in virtual mode, with a `column` DSL, controlled sorting
and selection, and `TableState.revealRow`. Spec: [8: Table](../spec.md#table-phase-5) and section 12.
Planned in [Lists at scale: Table](archive/0215-lists-at-scale-table.md).

**Depends on:** [Lists at scale: layoutInfo and contentType](archive/0234-lists-at-scale-layoutinfo-and-contenttype.md).

## Deliverables

- [ ] `Table(items, selectedIndex, onSelectedIndexChange, ...) { column(...) { text } }` over `Rtable_set_virtual_rows`, the cell callback reading the latest `items` and columns.
- [ ] `TableScope.column(header, width, fill, align, sortable, text)`, matched by place: changes applied, a changed count making the columns again, the user's resized widths kept until the app's `width` changes.
- [ ] `TableSort` and controlled sorting through `Rtable_set_sort_callback` and `Rtable_sort`, a refused sort reverting.
- [ ] Controlled selection and `onActivate`, as `ListView`'s; `headerVisible`, `gridLines` and `striped`.
- [ ] `TableState` with `revealRow`.
- [ ] Headless tests: cells read through `Rtable_get_cell`, header and row clicks injected for sorting, a refused selection reverting, activation, a changed list shown.
- [ ] The XML translator's `<Table>`, `<Column>` and stored `<Row>`s, with an example.
- [ ] The spec's section and section 12's row checked against what was built; `api-coverage.md` updated.
