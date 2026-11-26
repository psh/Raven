---
status: closed
---

# TextInput's Password, MaxLength and action in the translator

[Password, max length and submit on TextField](archive/0194-password-max-length-and-submit-on-textfield.md)
gave `TextField` a password mode, `maxLength` and `KeyboardActions(onDone)`, but `samples/xml-translator`
still translates a `<TextInput>` as if it had none. It warns that the action "fires on Enter, which
TextField doesn't report", and `Password="true"` and `MaxLength="20"` fall through to "has no
equivalent on TextField". Section 10 of the spec says the same. Translate all three.

**Depends on:** [Password, max length and submit on TextField](archive/0194-password-max-length-and-submit-on-textfield.md).

## Deliverables

- [x] `Password="true"` becomes `visualTransformation = PasswordVisualTransformation()`, with its import; a bound value (`{...}`) becomes an `if` between it and `VisualTransformation.None`, as other bound flags do.
- [x] `MaxLength="20"` becomes `maxLength = 20`; 0, Raven's no limit, emits nothing, and a negative value warns.
- [x] The action (`Action`, `OnCommitted` and the other action names) becomes `keyboardActions = KeyboardActions(onDone = { /* name */ })`, stubbed as a Button's `onClick` is, in place of the warning.
- [x] Section 10's TextInput sentence updated.
- [x] Tests in `TranslatorTest` for each, and the translated samples regenerated (fewer warnings than the 252 now).

## Resolution

Done on macOS. `./gradlew build allTests` passes: 615 tests, 4 of them new in the translator's
`TranslatorTest` and 1 in its `TemplatesTest`. The examples check translates Raven's 43 XML files
with 250 warnings, down from 252, and compiles them.

- **The action**: Raven's XML loader sets one action from any of `Action`, `OnClicked`,
  `OnChanged`, `OnCommitted` and `OnActivated`, and a text input fires it on Enter only. So each
  becomes `keyboardActions = KeyboardActions(onDone = { /* name */ })`, stubbed as a Button's
  `onClick` is. Raven's schema says `OnChanged` fires "at each change" on a TextInput, which its
  widget doesn't do; the translator follows the widget. `examples/notes`' `notes.add` and
  `examples/browser`'s `web.go` are the two warnings gone.
- **`Password`**: `true` becomes `visualTransformation = PasswordVisualTransformation()`, `false`
  nothing, and a bound value `if (hidden) PasswordVisualTransformation() else VisualTransformation.None`.
- **`MaxLength`**: a positive number becomes `maxLength = 20`; 0, Raven's no limit, nothing; a
  negative one a warning ("negative, so there is no limit"); a bound one
  `maxLength = limit.takeIf { it > 0 }`, so 0 stays no limit at run time.
- **Imports**: `KeyboardActions` from `raven.compose.foundation.text`, the transformations from
  `raven.compose.ui.text.input`, added only when used.
- **Spec**: section 10's TextInput sentence.
