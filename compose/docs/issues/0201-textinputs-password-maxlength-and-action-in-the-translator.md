---
status: open
---

# TextInput's Password, MaxLength and action in the translator

[Password, max length and submit on TextField](archive/0194-password-max-length-and-submit-on-textfield.md)
gave `TextField` a password mode, `maxLength` and `KeyboardActions(onDone)`, but `samples/xml-translator`
still translates a `<TextInput>` as if it had none. It warns that the action "fires on Enter, which
TextField doesn't report", and `Password="true"` and `MaxLength="20"` fall through to "has no
equivalent on TextField". Section 10 of the spec says the same. Translate all three.

**Depends on:** [Password, max length and submit on TextField](archive/0194-password-max-length-and-submit-on-textfield.md).

## Deliverables

- [ ] `Password="true"` becomes `visualTransformation = PasswordVisualTransformation()`, with its import; a bound value (`{...}`) becomes an `if` between it and `VisualTransformation.None`, as other bound flags do.
- [ ] `MaxLength="20"` becomes `maxLength = 20`; 0, Raven's no limit, emits nothing, and a negative value warns.
- [ ] The action (`Action`, `OnCommitted` and the other action names) becomes `keyboardActions = KeyboardActions(onDone = { /* name */ })`, stubbed as a Button's `onClick` is, in place of the warning.
- [ ] Section 10's TextInput sentence updated.
- [ ] Tests in `TranslatorTest` for each, and the translated samples regenerated (fewer warnings than the 252 now).
