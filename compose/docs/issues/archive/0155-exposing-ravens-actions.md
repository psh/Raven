---
status: closed
wayfinder: grilling
parent: 0145
blocked_by: []
assignee: Paul Hawke
---

# Exposing Raven's actions

## Question

How does a Raven app make its named actions (`Raction_bind`, `OnClicked="file.save"`) MCP tools,
with one call and no code per action? The answer covers the fork change to Raven's actions API and
the Raven MCP call over it.

**Preferred direction (Paul, 2026-11-18): a fork change that lets code list the registered
actions**, rather than walking the widget tree for the names widgets carry. Grill the shape of that,
not whether to do it. The tree walk is the fallback to argue against, and it may still fill gaps.

What Raven has today (`include/raven/raven_core.h`, ACTIONS; `src/core/context.c`):

- Bound actions live in a private array (`g_rvn.actions`: name, callback, user data), with no count,
  iterator or change notification in the C API.
- `Raction_fire(name, sender)` runs the bound function, a built-in, or the default handler. It is
  UI-thread only, so Raven MCP posts it with `Rtask_post`, as
  [the spike](0146-spike-the-kotlin-sdk-as-a-c-library.md) did for every call.
- `Rwidget_get_action(widget)` gives the action a widget fires. Built-ins (`app.quit`,
  `window.close`, `show:NAME`, `open:ADDRESS`, `copy:TEXT`, and more) answer to names no one binds.

To settle:

- **The fork change**: `Raction_get_count()`/`Raction_get_name(i)`, a
  `Raction_foreach(callback, user_data)`, or both? Is a change notification needed (a
  `Raction_set_listener` called on bind and unbind), so Raven MCP can send `listChanged` without
  polling? Is it thread-safe, or UI-thread only like the rest?
- **Descriptions**: `Raction_bind` takes none. Add `Raction_set_description(name, text)` (or a
  `Raction_bind_ex` with a description, an input schema, or annotations) to the fork change, give
  MCP its own description with `Rmcp_expose_actions`, or fall back to the text of a widget
  carrying the name?
- **The sender**: listed actions have no widget, and `Raction_fire` with `R_INVALID_ID` leaves
  built-ins that act relative to their widget (`window.close`, `show:NAME`) with nothing to act on.
  Do bound actions ever need a sender? Fire with the focused window's root, or a widget found
  carrying the name?
- **Which names**: only bound actions, or built-ins too? Never a generic "fire any name" tool:
  `open:ADDRESS` and `copy:TEXT` carry their argument in the name. Is `app.quit` excluded by
  default? Is there a prefix filter (`"file."`)? This is shared with
  [Who may connect, and what they may do](0154-who-may-connect.md).
- **Tool names**: are action names valid MCP tool names as they are (`file.save` is, `theme:NAME`
  isn't), and how are the others mapped?
- **The Raven MCP call**: `Rmcp_expose_actions(prefix)` once, kept in step through the listener, and
  how it sits beside `Rmcp_expose_action(name, description)` for a single action.
- **Upstream**: the change is useful beyond MCP (command palettes, menus built from the actions,
  scripting), so propose it to Raven's `main` as the drawing additions were
  ([0143](../on-hold/0143-propose-the-drawing-additions-upstream.md)). Check how the other bindings in
  `bindings/` would surface it.

## Resolution

Decided with Paul on 2026-11-18, and written into
[`compose/docs/mcp.md`](../../mcp.md#ravens-actions-as-tools) and its header.

- **The fork change**, proposed upstream on its own (command palettes, menus, scripting):
  `Raction_get_count`/`_get_name`, `Raction_set_description`/`_get_description` (kept with the bound
  action), and `Raction_set_list_callback` (bound or unbound, one slot).
- **`Rmcp_expose_actions(prefix)`**: bound actions only, never built-ins. Invalid tool names are
  skipped and logged, never renamed. It stays in step through the list callback and claims no hints.
- **`Rmcp_expose_action(name, description, hints)`**: one action on purpose, built-ins allowed, with
  hints.
- **Description**: the action's own, else "Runs the app's action `…`", with a one-time nudge in the
  log.
- **Sender**: the focused window, else the first window.
- **The app's own tools win** over an exposed action of the same name.
- **Reply**: fire, wait for idle (120 frames), "ran …". An action nothing handles fails the call.
