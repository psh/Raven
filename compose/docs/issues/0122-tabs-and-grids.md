---
status: open
---

# Tabs and grids

Controlled tabs, Raven's Grid, and the uniform lazy grid. Spec: [8: Tabs and grids](../spec.md#tabs-and-grids-phase-3).

**Depends on:** [Images and resources](0115-images-and-resources.md), [Lists and trees](0120-lists-and-trees.md).

## Deliverables

- [ ] `TabView(selectedIndex, onSelectedIndexChange, modifier, documents) { Tab(title, closable, onClose, icon) { } }`, selection and closing controlled.
- [ ] `Grid(columns, rows, modifier, spacing)` with `GridTrack.Auto`/`Fill` and `Modifier.cell(column, row, columnSpan, rowSpan)`.
- [ ] `LazyVerticalGrid(columns = GridCells.Fixed(n), minCellSize)` over UniformGrid ([Small port gaps](archive/0107-small-port-gaps.md)).
- [ ] Tests: a refused tab change and close revert; cells and spans; a minimum cell size.
