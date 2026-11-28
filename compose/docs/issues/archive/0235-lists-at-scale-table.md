---
status: closed
---

# Lists at scale: Table

Add `Table`, Raven's sortable data grid in virtual mode, with a `column` DSL, controlled sorting
and selection, and `TableState.revealRow`. Spec: [8: Table](../../spec.md#table-phase-5) and section 12.
Planned in [Lists at scale: Table](0215-lists-at-scale-table.md).

**Depends on:** [Lists at scale: layoutInfo and contentType](0234-lists-at-scale-layoutinfo-and-contenttype.md).

## Deliverables

- [x] `Table(items, selectedIndex, onSelectedIndexChange, ...) { column(...) { text } }` over `Rtable_set_virtual_rows`, the cell callback reading the latest `items` and columns.
- [x] `TableScope.column(header, width, fill, align, sortable, text)`, matched by place: changes applied, a changed count making the columns again, the user's resized widths kept until the app's `width` changes.
- [x] `TableSort` and controlled sorting through `Rtable_set_sort_callback` and `Rtable_sort`, a refused sort reverting.
- [x] Controlled selection and `onActivate`, as `ListView`'s; `headerVisible`, `gridLines` and `striped`.
- [x] `TableState` with `revealRow`.
- [x] Headless tests: cells read through `Rtable_get_cell`, header and row clicks injected for sorting, a refused selection reverting, activation, a changed list shown.
- [x] The XML translator's `<Table>`, `<Column>` and stored `<Row>`s, with an example.
- [x] The spec's section and section 12's row checked against what was built; `api-coverage.md` updated.

## Resolution

Built on 2026-11-27.

- **`Table`**, **`TableScope.column`**, **`TableSort`** and **`TableState`** are in foundation's
  `Table.kt`, with the shape the spec gives.
  - The columns go through a mirror of what Raven was last told. A changed field is set alone,
    and the width only when the app's changes, so a resized column keeps the user's width. A
    different number of columns clears and adds them again, and puts the sort back, as
    `Rtable_clear_columns` drops it.
  - Centre and end alignment become Raven's 0.5 and 1.
- **ui's bindings** are in `Binding.kt`, as `ListView`'s are:
  - `TableRows` calls `Rtable_set_virtual_rows`, and `TableCellText` answers its callback;
  - `TableSelected` and `TableSelectionChanged` handle the selection;
  - `TableSortOrder` (a pair of column and descending, equal whenever both have no column) and
    `TableSortChanged`, over `Rtable_set_sort_callback`, handle the sort;
  - `TableActivated` reports activation.

  The C string a cell's text is returned in is now shared with the list view's
  (`lastTextCopy`).
- **Tests** are `TableTest` (5), headless:
  - cells read through `Rtable_get_cell`, with a changed list shown;
  - columns matched by place: a header renamed, the user's width kept until the app's changes,
    and a third column making them again;
  - header clicks injected: sorting up, then down with the items sorted by the app, then a
    refused sort reverting the arrow, then null and a sort set by the app;
  - row clicks injected: a refused selection reverting, Enter activating, and a selection set by
    the app;
  - `revealRow` on 100,000 rows, before and after it is attached.
- **The XML translator** maps `<Table>` with `<Column>`s or `Columns="A|B"`, and the stored
  `<Row>`s padded to the columns.
  - `Selected` and the sort become hoisted state.
  - The rows are sorted as Raven sorts stored ones: numbers first, by value, then texts
    without regard to case.
  - `Editable`, `Kind`, `FrozenColumns` and `SelectionMode`/`MultiSelect` give warnings.
  - The example is in `TranslatorTest` (2 tests). Its output was compiled against foundation by
    hand: the examples check doesn't reach `workspace.xml`'s Table, which is inside an
    untranslated `<DockArea>`.
  - Hoisted declarations can now span several lines.
- **Docs**: the spec's Table section now covers the column mirror and the translation, and the
  translator's mapping table gains `Table`. Section 12's row stands.
- **`api-coverage.md`**: 569 of 1,704 functions (33%), `raven_controls.h` at 151, 43 of 58 widget
  types, and the Table row marked built.
- **Suites**: the ui, foundation, xml and mcp suites pass.
