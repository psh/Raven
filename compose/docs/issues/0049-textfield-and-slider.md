---
status: open
---

# TextField and Slider

The remaining phase 1 inputs: a single-line `TextField(value: String)` and `Slider`, both
controlled, plus `performTextInput`. Spec:
[8. Component vocabulary](../spec.md#8-component-vocabulary),
[7. Events and state](../spec.md#7-events-and-state).

**Depends on:** [Text, buttons and the first finders](archive/0047-text-buttons-and-first-finders.md).

## Deliverables

- [ ] `TextField(value, onValueChange, modifier, placeholder: String, singleLine = true)` over
      TextInput. Text is written back only when it differs from what the widget shows, so typing
      never moves the caret. If the app rewrites the text, the caret jumps to the end (documented).
- [ ] `Slider(value, onValueChange, valueRange, steps)` over Slider, controlled.
- [ ] `performTextInput(text)`: click the field to focus it, then `Rwindow_inject_text`.
- [ ] Tests: typing reaches `onValueChange`; an app that upper-cases the text gets it written back;
      an app that rejects input reverts; a slider clamps to an app-imposed range.
- [ ] Manual, on a real window with an IME: does writing text back during a preedit composition
      cancel it, and what text does Raven report during preedit? Record the result in the roadmap's
      verify-first table and the spikes README. If it fails, defer write-backs while a composition
      is active.
