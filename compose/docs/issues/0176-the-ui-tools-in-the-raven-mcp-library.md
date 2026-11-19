---
status: open
---

# The UI tools in the Raven MCP library

`ui.*` for every app that isn't compose-raven, on the C driver, switched on with
`RMcpConfig.ui_tools`. Design: [The UI tools](../mcp.md#the-ui-tools),
[A running app](../agent-skills.md#a-running-app).

**Depends on:** [The Raven MCP library](archive/0174-the-raven-mcp-library.md),
[The UI driver in Raven's C](0169-the-ui-driver.md), [PNG encoding in Raven's C](archive/0166-png-encoding-in-ravens-c.md).

## Deliverables

- [ ] `ui.tree`, `ui.find`, `ui.click`, `ui.type`, `ui.key`, `ui.screenshot` over `raven_driver.h`, with the contract's arguments, outline (500-line cap, `under`, `depth`), replies and hints.
- [ ] Actions hold their reply and check `Ris_idle` once a frame through `Rtask_post`, up to 120 frames.
- [ ] `ui_tools` in `RMcpConfig`, set by `RAVEN_MCP_UI=1`; a `ui.*` name already taken is left and logged.
- [ ] Tests: the notepad driven through MCP from a client, in C and Python.
