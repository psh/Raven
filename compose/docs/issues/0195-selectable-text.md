---
status: open
---

# Selectable text

A `Text` can't be selected and copied. Bind `Rlabel_set_selectable` behind Compose's
`SelectionContainer`, so text inside one is selectable. One of the everyday features in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [ ] `SelectionContainer { }` and `DisableSelection { }`, a composition local the `Text` composables read.
- [ ] Tests: a `Text` inside is selectable, one outside or under `DisableSelection` isn't.
