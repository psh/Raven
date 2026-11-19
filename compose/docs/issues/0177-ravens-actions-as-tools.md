---
status: open
---

# Raven's actions as tools

`Rmcp_expose_actions` and `Rmcp_expose_action`. Design:
[Raven's actions as tools](../mcp.md#ravens-actions-as-tools).

**Depends on:** [The Raven MCP library](archive/0174-the-raven-mcp-library.md),
[Listing Raven's actions](archive/0167-listing-ravens-actions.md).

## Deliverables

- [ ] `Rmcp_expose_actions(prefix)`: bound actions only, never built-ins, invalid names skipped and logged, no hints, kept in step through the list callback.
- [ ] `Rmcp_expose_action(name, description, hints)`, built-ins allowed.
- [ ] Descriptions from `Raction_get_description`, else the generic one with a one-time nudge; the focused window as sender; the app's own tools winning; the reply after idle; "nothing handles the action …" for an unhandled one.
- [ ] Tests: an XML app's `OnClicked` actions called by a client.
