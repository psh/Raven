---
status: open
---

# Modifier, Dp and the layout containers

Write compose-raven's own `Modifier` and `Dp`, fold the phase 1 elements into Raven slot and size
setters, and add `Column`, `Row` and `Box` with Compose's `Arrangement` and `Alignment` shapes.
Spec: [6. Layout and Modifiers](../spec.md#6-layout-and-modifiers).

**Depends on:** [The binding layer](archive/0045-binding-layer.md).

## Deliverables

- [ ] `Modifier` as an immutable chain in `raven.compose.ui`, and `Dp` in `raven.compose.ui.unit`
      (`16.dp`, 1.dp = one Raven logical unit).
- [ ] Phase 1 elements: `padding` (all three forms, which add up), `size`/`width`/`height`,
      `sizeIn`/`widthIn`/`heightIn`, `fillMaxWidth`/`fillMaxHeight`/`fillMaxSize` (no fraction),
      `weight` in `RowScope`/`ColumnScope`, `align` in each scope, and `testTag`. The rest of the
      table in section 6 belongs to later phases.
- [ ] Folding: the node folds the chain into flat layout values, diffs them with the previous set,
      and calls only the `Rslot_*` and `Rwidget_set_*size` setters that changed. The last size or
      alignment wins. `fillMax*` on a Row's or Column's main axis acts like `weight(1f)`.
- [ ] Modifiers act on the composable's outer widget. One the composable can't honour (such as
      `weight` outside a Row or Column) is ignored with a one-time warning naming the composable.
- [ ] `Column`/`Row`/`Box` over VBox/HBox/Overlay. Each child's slot starts at start/top instead of
      Raven's `FILL`. `Arrangement` offers `Top`/`Start` and `spacedBy(dp)` (`Rpanel_set_spacing`).
      The container's alignment is each child's default cross-axis alignment, and `Modifier.align`
      overrides it.
- [ ] Pure unit tests for folding and diffing. Headless tests reading slot values back from Raven.
