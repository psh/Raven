---
status: closed
---

# Text, buttons and the first finders

The first end-to-end slice: `Text`, the three buttons, `Spacer`, the dividers and
`LinearProgressIndicator`, plus the harness's finders and `performClick`, so the Counter in the
spec's overview runs as a test. Spec: [8. Component vocabulary](../../spec.md#8-component-vocabulary),
[9. Testing](../../spec.md#9-testing).

**Depends on:** [Modifier, Dp and the layout containers](0046-modifier-dp-and-layout-containers.md).

## Deliverables

- [x] `Text(text, modifier)` over Label. The `color`, `fontSize`, font and `style` parameters arrive
      with phase 2 theming and phase 3 fonts.
- [x] `Button`, `OutlinedButton` and `TextButton(onClick, modifier, enabled) { content }` over Button
      with `R_BUTTON_PRIMARY`, `_DEFAULT` and `_FLAT`. The content goes in a content Row,
      vertically centred, and `Rbutton_set_text` is never called.
- [x] `Spacer(modifier)`, `HorizontalDivider()`, `VerticalDivider()`, and
      `LinearProgressIndicator(progress)`.
- [x] Harness: `onNodeWithText`, `onNodeWithTag` and `onNodeWithContentDescription` over the node
      tree (skipping content nodes), `performClick()` at the centre of `Rwidget_get_rect`, and
      `assertExists()`.
- [x] Tests: the overview's Counter (click "Add", then "Clicked 1 times" exists); a button with
      several children in its content; changing `enabled`; removing a button leaves no leaks.

## Resolution

Done on macOS. 16 tests pass in foundation, including the overview's Counter.

- **Composables**:
  - `Text(text, modifier)` is in `raven.compose.foundation`.
  - `Button`, `OutlinedButton` and `TextButton` use Raven's primary, default and flat styles. Their
    content is a `RowScope`.
  - `HorizontalDivider`, `VerticalDivider` and `LinearProgressIndicator(progress, modifier)` are in
    `raven.compose.foundation`. A divider fills its parent's width or height, as in Compose, and
    progress is clamped to 0..1.
  - `Spacer(modifier)` is in `raven.compose.foundation.layout`, beside Compose's.
- **A button's content is centred**: the Row content panel's slot is set to centre once, when it
  is created, and its children are centred vertically. A Box content panel still fills its widget.
  Spec section 6 is updated.
- **Finders** in the harness:
  - `onNodeWithText`, `onNodeWithTag` and `onNodeWithContentDescription` walk
    `RavenHost.windowRoots` (opt-in public, along with `RavenNode.widget`, `children`, `testTag`
    and `isContentNode`), leaving out content nodes.
  - Each use waits for idle and expects exactly one match. `performClick` injects a press and
    release at the centre of the widget's rect, and `assertExists`/`assertDoesNotExist` check.
  - `widgetWithTag` from the previous ticket is gone, replaced by `onNodeWithTag(...).fetchWidget()`.
- **What the finders match in phase 1**:
  - Text is a label's text read from Raven, so `onNodeWithText("Add")` finds the `Text` inside the
    button. Clicking it clicks the button, since Raven passes the click on.
  - The content description is Raven's accessible name, which Raven derives from the text inside
    a widget unless something sets it. So Texts are left out, and a button is found by its label.
  - Spec section 9 records both.
- **Tests**:
  - the Counter counts;
  - a button's content holds two Texts and a Spacer in a row, centred in the button, and a click
    on its second Text counts;
  - toggling `enabled` blocks and then allows clicks;
  - removing a button destroys it and frees exactly three widget references;
  - the dividers, a Spacer and the progress value lay out or read back as expected;
  - a button is found by its accessible name.
