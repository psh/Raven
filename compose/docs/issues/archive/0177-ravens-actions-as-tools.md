---
status: closed
---

# Raven's actions as tools

`Rmcp_expose_actions` and `Rmcp_expose_action`. Design:
[Raven's actions as tools](../../mcp.md#ravens-actions-as-tools).

**Depends on:** [The Raven MCP library](0174-the-raven-mcp-library.md),
[Listing Raven's actions](0167-listing-ravens-actions.md).

## Deliverables

- [x] `Rmcp_expose_actions(prefix)`: bound actions only, never built-ins, invalid names skipped and logged, no hints, kept in step through the list callback.
- [x] `Rmcp_expose_action(name, description, hints)`, built-ins allowed.
- [x] Descriptions from `Raction_get_description`, else the generic one with a one-time nudge; the focused window as sender; the app's own tools winning; the reply after idle; "nothing handles the action …" for an unhandled one.
- [x] Tests: an XML app's `OnClicked` actions called by a client.

## Resolution

Done, on macOS (arm64), in the Raven MCP library (`compose/raven-mcp`, `Actions.kt`).
`./gradlew :raven-mcp:check` passes three runs in a row: 19 Python tests, 6 of them new, and the
Rust example.

- **`Rmcp_expose_actions(prefix)`** publishes the bound actions under the prefix ("" or NULL: all).
  Built-ins are never included, even when the app binds one (`app.quit`). A name that isn't a
  valid tool name is skipped with one log line, and no hints are claimed. Calling it again adds a
  prefix.
- **Kept in step** through `Raction_set_list_callback`, from
  [Listing Raven's actions](0167-listing-ravens-actions.md). A change is applied a frame later
  through `Rtask_post`, so a description set right after `Raction_bind` is the one agents see.
  The callback is set again on each expose, since `Rinit` starts with none.
- **`Rmcp_expose_action(name, description, hints)`** publishes one action on purpose, built-ins
  included, with hints. An invalid name returns `R_FALSE`.
- **Descriptions**: the one given, else `Raction_get_description`, else "Runs the app's action
  `name`", which is logged once as a nudge.
- **A call**: if the action is neither bound nor built-in, it fails with "nothing handles the
  action name". Otherwise `Raction_fire` runs with the focused window as the sender (else the first
  open one, from [Idle and the window list](0168-idle-and-the-window-list.md)). The call then
  checks `Ris_idle` once a frame through `Rtask_post`, and replies "ran name" when the app is idle,
  or after 120 frames with a warning.
- **The app's own tools win**: an exposed action never replaces a tool added with `Rmcp_tool_add`.
  When that tool is removed, the action comes back. `Rmcp_tool_remove_all` forgets every exposed
  action and gives the list callback back.
- **Tests** (`test_raven_mcp.py`, `ExposedActionsTest`, a headless notepad in XML):
  - the prefix, descriptions and skipped names;
  - a call firing `notes.add` from the window and the label changing;
  - binding and unbinding while serving;
  - the app's tool winning, then giving way;
  - `theme.dark` exposed on purpose with hints;
  - an unhandled action failing;
  - `Rmcp_tool_remove_all` forgetting.
- **Limits**, recorded in `mcp.md`:
  - The list callback has one slot.
  - An action only the default handler answers is reported as unhandled, because Raven can't be
    asked about its default handler.
