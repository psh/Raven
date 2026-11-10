---
status: open
---

# Text, buttons and the first finders

The first end-to-end slice: `Text`, the three buttons, `Spacer`, the dividers and
`LinearProgressIndicator`, plus the harness's finders and `performClick`, so the Counter in the
spec's overview runs as a test. Spec: [8. Component vocabulary](../spec.md#8-component-vocabulary),
[9. Testing](../spec.md#9-testing).

**Depends on:** [Modifier, Dp and the layout containers](0046-modifier-dp-and-layout-containers.md).

## Deliverables

- [ ] `Text(text, modifier)` over Label. The `color`, `fontSize`, font and `style` parameters arrive
      with phase 2 theming and phase 3 fonts.
- [ ] `Button`, `OutlinedButton` and `TextButton(onClick, modifier, enabled) { content }` over Button
      with `R_BUTTON_PRIMARY`, `_DEFAULT` and `_FLAT`. The content goes in a content Row,
      vertically centred, and `Rbutton_set_text` is never called.
- [ ] `Spacer(modifier)`, `HorizontalDivider()`, `VerticalDivider()`, and
      `LinearProgressIndicator(progress)`.
- [ ] Harness: `onNodeWithText`, `onNodeWithTag` and `onNodeWithContentDescription` over the node
      tree (skipping content nodes), `performClick()` at the centre of `Rwidget_get_rect`, and
      `assertExists()`.
- [ ] Tests: the overview's Counter (click "Add", then "Clicked 1 times" exists); a button with
      several children in its content; changing `enabled`; removing a button leaves no leaks.
