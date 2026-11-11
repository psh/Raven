---
status: closed
---

# Modifier, Dp and the layout containers

Write compose-raven's own `Modifier` and `Dp`, fold the phase 1 elements into Raven slot and size
setters, and add `Column`, `Row` and `Box` with Compose's `Arrangement` and `Alignment` shapes.
Spec: [6. Layout and Modifiers](../../spec.md#6-layout-and-modifiers).

**Depends on:** [The binding layer](0045-binding-layer.md).

## Deliverables

- [x] `Modifier` as an immutable chain in `raven.compose.ui`, and `Dp` in `raven.compose.ui.unit`
      (`16.dp`, 1.dp = one Raven logical unit).
- [x] Phase 1 elements: `padding` (all three forms, which add up), `size`/`width`/`height`,
      `sizeIn`/`widthIn`/`heightIn`, `fillMaxWidth`/`fillMaxHeight`/`fillMaxSize` (no fraction),
      `weight` in `RowScope`/`ColumnScope`, `align` in each scope, and `testTag`. The rest of the
      table in section 6 belongs to later phases.
- [x] Folding: the node folds the chain into flat layout values, diffs them with the previous set,
      and calls only the `Rslot_*` and `Rwidget_set_*size` setters that changed. The last size or
      alignment wins. `fillMax*` on a Row's or Column's main axis acts like `weight(1f)`.
- [x] Modifiers act on the composable's outer widget. One the composable can't honour (such as
      `weight` outside a Row or Column) is ignored with a one-time warning naming the composable.
- [x] `Column`/`Row`/`Box` over VBox/HBox/Overlay. Each child's slot starts at start/top instead of
      Raven's `FILL`. `Arrangement` offers `Top`/`Start` and `spacedBy(dp)` (`Rpanel_set_spacing`).
      The container's alignment is each child's default cross-axis alignment, and `Modifier.align`
      overrides it.
- [x] Pure unit tests for folding and diffing. Headless tests reading slot values back from Raven.

## Resolution

Done on macOS. 40 tests pass in `compose-raven-ui`, and 10 in foundation.

- **Where things are**: `Modifier`, `Alignment`, `testTag` and the layout elements (`PaddingElement`,
  `SizeElement`, `SizeInElement`, `FillElement`, `WeightElement`, `AlignElement`) are in
  `compose-raven-ui`. `Dp` is in `raven.compose.ui.unit`. The modifier functions, `Arrangement`,
  `ColumnScope`/`RowScope`/`BoxScope` and `Column`/`Row`/`Box` are in
  `raven.compose.foundation.layout`, the package Compose uses, so imports port one-to-one.
- **Layout is resolved against the parent.** `fillMaxWidth` is a fill alignment in a Column but a
  weight in a Row, and a child with no alignment takes its container's. So the Applier records each
  node's parent on insert, and the node resolves its folded Modifier against the parent's
  `PanelLayout` (axis plus default alignments), set by the container through `bind(PanelLayout)`.
  When the container's alignment changes, its children are placed again. The node diffs the result
  against what it last told Raven, which persists on the widget even across reparenting.
- **Defaults**: a Column child gets an auto slot, aligned by the Column's `horizontalAlignment`. A
  Row child gets an auto slot, aligned by the Row's `verticalAlignment`. A Box child gets the Box's
  `contentAlignment`. `weight` wins over `fillMax*` on the main axis. A content node keeps Raven's
  fill defaults. A Button's Row content panel centres its children vertically.
- **Warnings**: `weight` in a Box, `align(Alignment.Vertical)` in a Column and
  `align(Alignment.Horizontal)` in a Row are ignored. Each prints a warning once per process,
  naming the composable from its `WidgetDescriptor`.
- **The binding layer is now public behind `@InternalComposeRavenApi`**, because foundation's
  composables are written on it. Foundation and both modules' tests opt in module-wide in Gradle.
- **Test lookup**: `RavenHost.widgetWithTag(tag)` walks each open window's node tree. The
  finders in [Text, buttons and the first finders](0047-text-buttons-and-first-finders.md) can
  build on it.
- **Tests**:
  - Pure, in `LayoutTest`: chain equality, padding sums, last size and alignment wins, resolution
    in a Column, Row and Box, weight over fill, warnings, and which setters a diff calls.
  - Headless, in `LayoutsTest`, reading `Rwidget_get_rect`, since Raven has no slot getters:
    stacking with spacing, column alignment and its overrides, weights sharing a Row's width,
    Box alignment, padding, size bounds, `fillMaxSize`, and a changed Modifier laid out again.
  - Plus a one-time warning test.
