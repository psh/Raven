---
status: closed
---

# Password, max length and submit on TextField

`TextField` can't hide its text, cap its length or report Enter. Bind
`Rtext_input_set_password`, `Rtext_input_set_max_length` and
`Rtext_input_set_committed_callback` through Compose's shapes for them. One of the everyday features
in the [Raven API coverage](../../api-coverage.md) review (2026-11-14).

## Deliverables

- [x] `visualTransformation = PasswordVisualTransformation()` sets `Rtext_input_set_password`.
- [x] `maxLength: Int? = null`, a compose-raven extension (Compose's `TextField` has none), set through `Rtext_input_set_max_length`. Raven cuts typed and pasted text inside the field, so the caret stays put, which filtering in `onValueChange` can't do here (text the app rewrites moves the caret to the end). Filtering still works alongside it. A `value` longer than `maxLength` is shown whole, as Raven does, and only edits are limited (confirmed: `rvn_textedit_set_text` doesn't cut).
- [x] `keyboardActions = KeyboardActions(onDone = …)` called from the committed callback.
- [x] Spec updated, `maxLength` listed with the other extensions; tests for each, including a paste cut to `maxLength` with the caret after it.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 572 tests, 9 of them new in foundation's
`InputsTest`.

- **`VisualTransformation`**, in `raven.compose.ui.text.input` as Compose's is, is a sealed
  interface: `VisualTransformation.None` and `PasswordVisualTransformation(mask = '\u2022')`.
  Compose's filters an `AnnotatedString`, which Raven's field can't draw, so an app can't write
  its own; a port that does fails to compile rather than silently showing the text. Raven draws
  bullets only, so another `mask` warns (`layoutWarningSink`) and is drawn as a bullet. Raven also
  refuses copy and cut in a password field, and the test checks the clipboard is left alone.
- **`KeyboardActions(onDone)`**, in `raven.compose.foundation.text`, has only `onDone`, a plain
  `() -> Unit`. With no `KeyboardOptions` there is no IME action to pick `onGo` or `onSearch`, so
  offering them would be a trap; a port's `KeyboardActions(onDone = { ... })` compiles as it is.
  Enter fires the new `TextCommitted` event in ui's `Binding.kt` (`Rtext_input_set_committed_callback`),
  which carries no text since the app's value already is the text; the latest `onDone` is called.
- **`maxLength: Int? = null`**, after `singleLine` where Compose has `maxLines`, sets
  `Rtext_input_set_max_length` (0 for null, Raven's no limit) and must be positive. Raven counts
  code points, so `maxLength = 2` takes "😀é", which is three UTF-16 units; the KDoc says so. A paste
  is cut at a character boundary and leaves the caret after what was inserted, shown by a test
  that pastes into the middle and then deletes back. A longer `value` is shown whole; typing adds
  nothing and Backspace still deletes.
- **Spec**: three bullets under section 7's Text, the `TextField` row of section 8's table and three
  rows in section 12.

### Left for later

- **The XML translator** (`samples/xml-translator`) still warns that a TextInput's action "fires on
  Enter, which TextField doesn't report", and section 10 says the same. It could now emit
  `keyboardActions = KeyboardActions(onDone = …)`, and translate the TextInput's `Password` and
  `MaxLength` attributes.
- **The test harness** has no `performImeAction()`; the tests press Enter with `Rwindow_inject_key`.
