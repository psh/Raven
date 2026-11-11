---
status: closed
---

# Checkbox, Switch and RadioButton

The first controlled inputs: `Checkbox`, `Switch` and `RadioButton` over Raven's Checkbox styles,
using `ControlledValue`, plus `assertIsChecked`. Spec:
[8. Component vocabulary](../../spec.md#8-component-vocabulary),
[7. Events and state](../../spec.md#7-events-and-state).

**Depends on:** [Text, buttons and the first finders](0047-text-buttons-and-first-finders.md).

## Deliverables

- [x] `Checkbox(checked, onCheckedChange)` (`R_CHECKBOX_CHECK`) and `Switch(...)`
      (`R_CHECKBOX_SWITCH`), controlled. Raven's own checkbox text is never set; the label is a
      `Text` in a `Row`.
- [x] `RadioButton(selected, onClick)` (`R_CHECKBOX_RADIO`). The controlled check makes the app's
      state override Raven's unchecking of siblings.
- [x] `assertIsChecked()` and `assertIsNotChecked()`, reading the widget's value.
- [x] Tests: a checkbox whose app ignores `onCheckedChange` reverts; one that accepts doesn't
      flicker; a radio group follows the app's state even when the app picks a different button.

## Resolution

Done on macOS. 23 tests pass in foundation.

- **Composables**: `Checkbox` and `Switch(checked, onCheckedChange, modifier, enabled)`, and
  `RadioButton(selected, onClick, modifier, enabled)`, are in `raven.compose.foundation`. They are
  declarations over the binding layer: the style is a property, `enabled` uses the shared `Enabled`
  property, and the value is `CheckboxChecked`. Raven's checkbox text is never set.
- **Radio groups**: Raven checks the clicked radio, then unchecks its radio siblings with
  notification, so each sibling's change callback runs. A new event kind, `RadioClicked`, uses
  the same callback: it marks every change, but calls `onClick` only for the radio that became
  checked. The end-of-frame check then restores what the app composed, and a click the app ignores
  leaves the old choice selected. Restoring a sibling to checked makes Raven uncheck the clicked
  one silently, which is also the composed state. Spec section 8's row records it.
- **A null handler** still lets Raven toggle the widget, and the check puts the app's value back
  before the frame is painted, since the click arrives before the frame callback.
- **Harness**: `assertIsChecked()` and `assertIsNotChecked()` read `Rcheckbox_is_checked`, and
  fail on a node that isn't a checkbox.
- **Tests**:
  - an ignored checkbox click reverts;
  - an accepted one shows at once and stays, with no write-back;
  - a Switch follows its state both ways;
  - a disabled checkbox ignores clicks;
  - a radio group follows a click, calls back only for the clicked radio, and can't be clicked off;
  - a radio group keeps the app's choice when the click is ignored, and shows a different choice
    when the app picks one.

  With `RadioClicked` not marking its changes, the ignored-click radio test fails.
