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
- [ ] A max length (`maxLength` parameter, or the Compose-style value filter, whichever the spec settles on) through `Rtext_input_set_max_length`.
- [ ] `keyboardActions = KeyboardActions(onDone = …)` called from the committed callback.
- [ ] Spec updated; tests for each.
