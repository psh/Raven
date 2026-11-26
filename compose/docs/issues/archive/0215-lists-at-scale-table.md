---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Lists at scale: Table

## Question

How does Raven's Table, a sortable data grid, become a composable? Decide its columns and rows
(data or composable cells), sorting and selection as controlled state, how it uses lazy
virtualisation, and what of a Compose-style data table fits. The answer is the spec section and its
implementation tickets.

## Resolution

Resolved on 2026-11-26, with the user, in one round: designed into
[spec section 8: Table](../../spec.md#table-phase-5) and section 12.

- **Text cells, always virtual**: Raven virtualises its own rows, so Table doesn't use lazy
  virtualisation; a table of composable cells goes to fog.
- **A `column(...) { text }` DSL**, matched by place.
- **Sorting is controlled** (`sort: TableSort?`, `onSortChange`) and the app sorts its items; no
  sorting helper.
- **Selection as `ListView`'s**, single and controlled, with `onActivate`; no multi-select or context
  menu, which Raven's Table lacks.
- **Widths stay Raven's** once the user drags them; the app's `width` applies at creation and when
  it changes.
- **`TableState.revealRow`**, not saveable; `headerVisible`, `gridLines` and `striped`.
- **Tests** in the headless host; the XML translator maps `<Table>`.

Filed as [Lists at scale: Table](../0235-lists-at-scale-table.md).
