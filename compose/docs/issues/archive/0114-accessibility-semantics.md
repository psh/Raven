---
status: closed
---

# Accessibility semantics

What the screen reader sees beyond Raven's own description of each widget. Spec: [7: Accessibility](../../spec.md#accessibility-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [x] `R_ROLE_NONE` on internal content nodes.
- [x] `Modifier.semantics { }`: `contentDescription`, `role`, `heading()`, `hideFromAccessibility()`, and `accessibleRole`/`accessibleDescription`.
- [x] Automatic labels: an input in a `Row`/`FlowRow` without a description or a `Text` before it is labelled by the `Text` after it; the Applier re-checks a parent after its children change.
- [x] `announceForAccessibility(text, assertive)`; the host calls `Raccessibility_follow_system()` unless `followSystemAccessibility = false`.
- [x] Tests, with `onNodeWithContentDescription`: names, roles, hidden subtrees and automatic labels reach Raven.

## Resolution

Done on macOS. 355 tests pass across the build, 9 of them new: `SemanticsTest` (7) in
compose-raven-foundation and `SemanticsCallsTest` (2) in compose-raven-ui. No Raven change, and no
deviation from the spec.

- **Already there**: content nodes have had `R_ROLE_NONE` since phase 1 (`Binding.kt`), and the
  host has called `Raccessibility_follow_system()` unless `followSystemAccessibility = false`
  since phase 2 (`RavenHost.kt`); `onNodeWithContentDescription`, which reads Raven's accessible
  name and leaves Texts to `onNodeWithText`, was in the test harness too.
- **`Modifier.semantics { }`** (`compose-raven-ui`, `semantics/Semantics.kt`): a
  `SemanticsPropertyReceiver` with `contentDescription`, `role`, `accessibleRole`,
  `accessibleDescription`, `heading()` and `hideFromAccessibility()`. The block runs when the chain
  is built, so equal blocks make equal chains and an unchanged one costs nothing; several in a chain
  add up, the later winning. The node gives Raven only what changed, and what a chain drops goes
  back to Raven's own: `Rwidget_set_accessible_name(NULL)` names the widget from its content again,
  and the role goes back to the one its composable gives it (a descriptor's `role`, kept on the node
  as `defaultRole`), or `R_ROLE_AUTO`.
  - The role is `accessibleRole`, then `role`, then a heading's `R_ROLE_HEADING`.
  - `Role` maps Button, Checkbox, RadioButton, Tab and Image to Raven's roles of the same name,
    `Switch` to `R_ROLE_TOGGLE_BUTTON` and `DropdownList` to `R_ROLE_COMBO_BOX`. `AccessibleRole` has
    every Raven role but `R_ROLE_AUTO`.
  - No `mergeDescendants`: Raven decides which widgets it reads as one.
- **Automatic labels** (`RavenNode.labelInputs`): Raven names an input after the Text before it by
  itself, so compose-raven handles only the Text after. In a Row (HBox) or FlowRow (WrapBox), an
  input (Checkbox, Switch and RadioButton are Raven Checkboxes; Slider, TextField, ComboBox, SpinBox)
  with no `contentDescription` and no Text directly before it gets
  `Rwidget_set_accessible_labelled_by` with the Text directly after it. The Applier labels again
  every parent whose children a batch changed, and a node whose `contentDescription` changes has its
  parent label again. A label no longer wanted is taken back with handle 0, which Raven reads as
  none; `R_INVALID_ID` would make Raven report an invalid handle each time it reads the name.
- **`announceForAccessibility(text, assertive = false)`**, in `raven.compose.ui.semantics`, calls
  `Raccessibility_announce`.
- **What Raven doesn't let a test read**: it has no getter for a widget's description, and the
  headless window system has no announcer, so these two go through `accessibilityCalls`, which the
  compose-raven-ui tests replace, as `windowCalls` is for the window. Hiding has no getter either; a
  hidden Text is seen through Raven passing over it when it looks for an input's label.
- **Tests**: a content description names the widget and is found by `onNodeWithContentDescription`,
  and dropping it gives Raven's own name back; `role`, `Role.Switch`, `heading()` and
  `accessibleRole` (winning over `role`) reach Raven, and a dropped heading is Raven's own role again;
  a hidden Text names nothing; an input in a Row or FlowRow is labelled by the Text after it, unless
  a Text before it or a content description names it; a Text below an input in a Column is no label;
  the label follows the Row's children and the description as they change, and none is left
  pointing at a Text that has gone; an accessible description is given and taken back; an
  announcement reaches Raven.
