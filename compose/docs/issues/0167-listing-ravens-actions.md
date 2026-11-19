---
status: open
---

# Listing Raven's actions

Let code list the registered actions, describe them and hear when they change: for Raven MCP's
`Rmcp_expose_actions`, and for command palettes, menus built from actions and scripting. Design:
[What Raven's C gains](../mcp.md#what-ravens-c-gains).

**Depends on:** nothing open.

## Deliverables

- [ ] `Raction_get_count`, `Raction_get_name(index)` in `raven_core.h`'s ACTIONS section.
- [ ] `Raction_set_description(name, text)`/`Raction_get_description(name)`, kept with the bound action and dropped on unbind.
- [ ] `Raction_set_list_callback(callback, user_data)`: one slot, called on the UI thread as actions are bound and unbound.
- [ ] Tests in `tests/`: listing, descriptions surviving rebinding of the callback, the list callback on bind and unbind.
- [ ] The bindings regenerated with `tools/bindgen`.
- [ ] The upstream proposal opened, on its own, as a command-palette and scripting feature.
