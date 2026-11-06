---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0004, 0007]
assignee: Paul Hawke
---

# Layout and Modifiers

## Question

How do layout composables (`Column`, `Row`, ...) and Modifiers (padding, size, weight, alignment) map onto Raven's layout model? Is `Modifier` the right API at all, or do Raven layout properties become composable parameters?

## Resolution

- **Modifier**: compose-raven defines its own immutable `Modifier` chain in `raven.compose.ui` (compose-ui's would bring in compose-ui). Every composable takes `modifier: Modifier = Modifier`. The node folds the chain into flat layout values, compares them with the previous set, and calls only the changed `Rslot_*` / `Rwidget_set_*size` setters. Settings that belong to the widget itself (a panel's spacing, a label's text) are composable parameters.
- **Units**: an own `Dp` value class in `raven.compose.ui.unit` (`16.dp`). 1.dp is one Raven logical unit, passed through unchanged.
- **Containers**: `Column` uses VBox, `Row` HBox, `Box` Overlay. Other panels are for [v1 component vocabulary](0011-v1-component-vocabulary.md).
- **Defaults are Compose's**: each node sets its slot to start/top alignment when it is created, overriding Raven's `FILL`. Stretching is opt-in through `fillMax*`.
- **v1 elements**:
  - `padding` (all, horizontal/vertical, start/top/end/bottom) uses slot padding. With no drawing modifiers in v1 it is always outside the widget, and several paddings add up.
  - `size`/`width`/`height` use `Rwidget_set_size`. `sizeIn`/`widthIn`/`heightIn` use the min and max sizes. For size and alignment, the last element in the chain wins.
  - `fillMaxWidth`/`fillMaxHeight`/`fillMaxSize`, with no fraction. On the cross axis, and on both axes in `Box`, they mean `FILL` alignment. On the main axis of Row or Column they act like `weight(1f)` (`Rslot_set_fill(1)`), which is a documented difference from Compose.
  - `weight(Float)`, only in `RowScope`/`ColumnScope`, uses `Rslot_set_fill`.
  - `align(...)` is scoped as in Compose: `Alignment` in `BoxScope`, `Alignment.Horizontal` in `ColumnScope`, `Alignment.Vertical` in `RowScope`.
- **Arrangement**: Compose's parameter shape, e.g. `Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start)`. v1's `Arrangement` offers only `Top`/`Start` and `spacedBy(dp)`, which uses `Rpanel_set_spacing`. Center/End/SpaceBetween can come later, emulated with spacers. The container's alignment sets each child's default cross-axis alignment, and `Modifier.align` overrides it.
- **Not in v1**: `background`, `border`, `clickable` and other non-layout elements (clicks belong to [Events and state](0010-events-and-state.md), styling to the Theming fog). Custom `Layout {}` is not possible because Raven has no arrange hook (Custom drawing fog). No right-to-left and no `LayoutDirection`: `Alignment.Start`/`End` map to `LEFT`/`RIGHT`.
