---
status: open
---

# Password, max length and submit on TextField

`TextField` can't hide its text, cap its length or report Enter. Bind
`Rtext_input_set_password`, `Rtext_input_set_max_length` and
`Rtext_input_set_committed_callback` through Compose's shapes for them. One of the everyday features
in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [ ] `visualTransformation = PasswordVisualTransformation()` sets `Rtext_input_set_password`.
- [ ] `maxLength: Int? = null`, a compose-raven extension (Compose's `TextField` has none), set through `Rtext_input_set_max_length`. Raven cuts typed and pasted text inside the field, so the caret stays put, which filtering in `onValueChange` can't do here (text the app rewrites moves the caret to the end). Filtering still works alongside it. A `value` longer than `maxLength` is shown whole, as Raven does, and only edits are limited (confirm when building).
- [ ] `keyboardActions = KeyboardActions(onDone = …)` called from the committed callback.
- [ ] Spec updated, `maxLength` listed with the other extensions; tests for each, including a paste cut to `maxLength` with the caret after it.
