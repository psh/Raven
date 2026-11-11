---
status: closed
---

# TextField and Slider

The remaining phase 1 inputs: a single-line `TextField(value: String)` and `Slider`, both
controlled, plus `performTextInput`. Spec:
[8. Component vocabulary](../../spec.md#8-component-vocabulary),
[7. Events and state](../../spec.md#7-events-and-state).

**Depends on:** [Text, buttons and the first finders](0047-text-buttons-and-first-finders.md).

## Deliverables

- [x] `TextField(value, onValueChange, modifier, placeholder: String, singleLine = true)` over
      TextInput. Text is written back only when it differs from what the widget shows, so typing
      never moves the caret. If the app rewrites the text, the caret jumps to the end (documented).
- [x] `Slider(value, onValueChange, valueRange, steps)` over Slider, controlled.
- [x] `performTextInput(text)`: click the field to focus it, then `Rwindow_inject_text`.
- [x] Tests: typing reaches `onValueChange`; an app that upper-cases the text gets it written back;
      an app that rejects input reverts; a slider clamps to an app-imposed range.
- [x] Manual, on a real window with an IME: does writing text back during a preedit composition
      cancel it, and what text does Raven report during preedit? Record the result in the roadmap's
      verify-first table and the spikes README. If it fails, defer write-backs while a composition
      is active.

## Resolution

Done on macOS. 35 tests pass in foundation.

- **Composables**, in `raven.compose.foundation` (`Inputs.kt`):
  - `TextField(value, onValueChange, modifier, enabled = true, readOnly = false, placeholder = "",
    singleLine = true)`. `singleLine = false` fails with a pointer to `CodeEditor`. The handler is
    non-null, as in Compose.
  - `Slider(value, onValueChange, modifier, enabled = true, valueRange = 0f..1f, steps = 0)`. The
    range and step are set before the value, since Raven snaps the value to them. `steps` follows
    Compose: that many values between the ends, so Raven's step is `range / (steps + 1)`. A value
    outside the range is shown at the nearest end.
- **Binding layer**: `TextInputText` and `SliderValue` are new `ControlledValue`s, and `TextChanged`
  and `SliderChanged` are events that mark them. `ComposedValue` already writes only when the
  widget shows something else, and Raven's `Rtext_input_set_text` does nothing for equal text, so
  accepted typing never moves the caret. Rewritten text moves it to the end
  (`rvn_textedit_set_text`), as spec section 7 now says.
- **Harness**: `performTextInput(text)` clicks the field unless it already has focus, so a second
  call types where the caret is, then calls `Rwindow_inject_text`. Raven delivers the characters
  one at a time with no frame between them, so the app sees each one before anything is written
  back.
- **The IME check** was settled from Raven's platform code rather than by typing with an IME: no
  backend gives Raven a preedit at all.
  - macOS: the view handles `keyDown:` and reads `[event characters]`. It doesn't implement
    `NSTextInputClient`, so an input method never composes in a Raven window.
  - X11: the input context uses `XIMPreeditNothing`, so the input method composes in its own window
    and Raven sees only committed text (`Xutf8LookupString`).
  - Windows: Raven handles `WM_CHAR` and no `WM_IME_*` message, so the default IME window composes
    and Raven sees committed characters.
  - Wayland: only xkbcommon compose sequences (dead keys), resolved before Raven sees text; no
    text-input protocol.

  So Raven reports only committed text, and a write-back can't cancel a composition it never sees.
  No deferral is needed. Recorded in the roadmap's verify-first table and the spikes README. If
  Raven gains preedit (an `NSTextInputClient` view, or `text-input-v3`), this needs checking again.
- **Tests** (`InputsTest`):
  - typing reaches `onValueChange` character by character;
  - an app that upper-cases gets its text written back;
  - an app that accepts only digits reverts the rest, and one that ignores input keeps the field
    empty;
  - accepted typing leaves the caret where it is (typing after moving it left inserts there), and
    rewritten text moves it to the end;
  - the app can replace the text;
  - a slider follows a click and the app's state; clamps to an app-imposed maximum; reverts when
    the app ignores it; shows an out-of-range value at the end; and snaps to `steps`.

  With `TextChanged` and `SliderChanged` not marking their changes, the reject, ignore and slider
  revert tests fail.
