---
status: open
---

# Propose text input insets upstream

> **On hold (2026-11-19).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose `Rtext_input_set_insets` and `Rtext_input_get_insets` to Raven's `main`, on their own, as
the way for any app to lay an icon or a button over a text input (a search glass, a clear button,
a password's eye) without the text running under it. It doesn't mention compose-raven. Split out
of [Leading and trailing icons on TextField](archive/0200-leading-and-trailing-icons-on-textfield.md),
which built it (spec section 11, change 10; commit `89e9e14`).

**Depends on:** [Leading and trailing icons on TextField](archive/0200-leading-and-trailing-icons-on-textfield.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying the two functions and their documentation in `raven_controls.h`, the text area and desired width in `src/widgets/text_input.c`, `test_text_input_insets` in `tests/test_widgets.c`, and the bindings regenerated against upstream's headers.
