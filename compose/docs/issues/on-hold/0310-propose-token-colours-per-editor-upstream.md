---
status: on-hold
---

# Propose token colours per editor upstream

> **On hold (2026-11-27).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose `Rtext_editor_set_token_color`, `Rtext_editor_clear_token_colors` and
`Rtext_editor_get_token_color` to Raven's `main`, on their own, as the way for an app to colour the
code of one editor differently from another (a diff beside its source, a read-only preview, a
user's per-document scheme) while Raven's colours of tokens are otherwise only the theme's. It
doesn't mention compose-raven. Split out of
[Raven: token colours per editor](../archive/0229-raven-token-colours-per-editor.md), which built it
(spec section 11, change 13; commit `c3d5a2b`).

**Depends on:** [Raven: token colours per editor](../archive/0229-raven-token-colours-per-editor.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying the three functions and their documentation in `raven_text_editor.h`, the editor's colours in `src/widgets/text_editor.c` (`rvn_ed_token_color`), `rvn_token_internal` made shared in `src/widgets/syntax.c` and `common.h`, `test_token_colors` in `tests/test_text_editor.c`, and the bindings regenerated against upstream's headers.
