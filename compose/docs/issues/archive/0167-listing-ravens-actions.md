---
status: closed
---

# Listing Raven's actions

Let code list the registered actions, describe them and hear when they change: for Raven MCP's
`Rmcp_expose_actions`, and for command palettes, menus built from actions and scripting. Design:
[What Raven's C gains](../../mcp.md#what-ravens-c-gains).

**Depends on:** nothing open.

## Deliverables

- [x] `Raction_get_count`, `Raction_get_name(index)` in `raven_core.h`'s ACTIONS section.
- [x] `Raction_set_description(name, text)`/`Raction_get_description(name)`, kept with the bound action and dropped on unbind.
- [x] `Raction_set_list_callback(callback, user_data)`: one slot, called on the UI thread as actions are bound and unbound.
- [x] Tests in `tests/`: listing, descriptions surviving rebinding of the callback, the list callback on bind and unbind.
- [x] The bindings regenerated with `tools/bindgen`.
- [ ] The upstream proposal opened, on its own, as a command-palette and scripting feature: moved to [Propose listing actions upstream](../0186-propose-listing-actions-upstream.md), on hold.

## Resolution

Done, on macOS (arm64), except the upstream proposal: that publishes outside this machine, so it
moved to [Propose listing actions upstream](../0186-propose-listing-actions-upstream.md), on hold.
`tests/test_actions.c` (34 checks) passes, and so do `bindings_up_to_date` and
`./gradlew :raven-mcp:check`. The rest of `ctest` passes apart from `test_shell` and `test_themes`,
which already failed.

- **Listing** (`raven_core.h`, ACTIONS; `core/context.c`): `Raction_get_count()` and
  `Raction_get_name(index)` cover the bound actions in the order they were bound, and return NULL
  out of range. Built-in actions and names used only in XML aren't bound, so they aren't listed.
  `Raction_unbind` used to move the last action into the gap. It now closes the gap and keeps the
  order, so a palette doesn't shuffle.
- **Descriptions** live on the bound action (`rvn_action_t.description`). Rebinding a bound name
  replaces only its function, and the description stays. Unbinding drops it, and NULL or `""`
  removes it. Describing a name that isn't bound is reported (`R_ERROR_INVALID_OPERATION`, a
  warning) and does nothing.
- **`Raction_set_list_callback`**: one slot. It is called inside a dispatch on the UI thread when a
  name is bound or unbound, including `Raction_bind(name, NULL)`, but not when a bound name only
  gets a new function. The name passed is freed only after the callback returns, so
  `Raction_unbind(Raction_get_name(i))` is safe. The callback may itself bind and unbind.
- **Tests**:
  - listing and its order, through unbinding;
  - XML and built-in names not counted;
  - a description surviving a new function, cleared, dropped on unbind and refused on an unbound
    name;
  - the list callback on bind, unbind, unbinding with NULL, and not on rebinding;
  - unbinding a name read from the list;
  - a callback that binds.
- **Bindings**: regenerated (1164 functions, 80 callbacks). The Python callback was checked by
  hand.
