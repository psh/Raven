---
status: on-hold
---

# Propose listing actions upstream

> **On hold (2026-11-19).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose listing, describing and hearing about Raven's actions to Raven's `main`, on its own, as a
feature for command palettes, menus built from the actions and scripting. It doesn't mention MCP.
Split out of [Listing Raven's actions](../archive/0167-listing-ravens-actions.md), which built it.

**Depends on:** [Listing Raven's actions](../archive/0167-listing-ravens-actions.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying `Raction_get_count`, `Raction_get_name_at`, `Raction_set_description`/`Raction_get_description`, `Raction_set_list_callback`, the change that keeps unbinding in bind order, `tests/test_actions.c`, and the bindings regenerated against upstream's headers.
