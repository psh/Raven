---
status: open
---

# Checkbox, Switch and RadioButton

The first controlled inputs: `Checkbox`, `Switch` and `RadioButton` over Raven's Checkbox styles,
using `ControlledValue`, plus `assertIsChecked`. Spec:
[8. Component vocabulary](../spec.md#8-component-vocabulary),
[7. Events and state](../spec.md#7-events-and-state).

**Depends on:** [Text, buttons and the first finders](archive/0047-text-buttons-and-first-finders.md).

## Deliverables

- [ ] `Checkbox(checked, onCheckedChange)` (`R_CHECKBOX_CHECK`) and `Switch(...)`
      (`R_CHECKBOX_SWITCH`), controlled. Raven's own checkbox text is never set; the label is a
      `Text` in a `Row`.
- [ ] `RadioButton(selected, onClick)` (`R_CHECKBOX_RADIO`). The controlled check makes the app's
      state override Raven's unchecking of siblings.
- [ ] `assertIsChecked()` and `assertIsNotChecked()`, reading the widget's value.
- [ ] Tests: a checkbox whose app ignores `onCheckedChange` reverts; one that accepts doesn't
      flicker; a radio group follows the app's state even when the app picks a different button.
