---
status: closed
wayfinder: grilling
parent: 0157
blocked_by: [0161, 0151]
assignee: Paul Hawke
---

# Driving a running app in any language

## Question

The contract is settled by [The UI tools](0151-the-ui-tools.md): the same six tools,
selectors, outline and replies as compose-raven's. This ticket decides only how they're built over
the C driver, who registers them, and how they're switched on.


How does an agent drive a Raven app that is already running, written in C, Python or Rust (the step
the skills fill with Playwright, or `curl` against a dev server)? This is the
[audit](0158-audit-the-skills-against-raven.md)'s third gap, blocking `diagnosing-bugs`,
`triage` and an interactive `prototype`. It is missing in all four languages, and compose-raven's
`ui.*` tools are planned in the MCP map's [The UI tools](0151-the-ui-tools.md).

- **Over Raven MCP**: a `ui.*` set built on the C driver ([A UI driver in Raven's C](0161-a-ui-driver-in-ravens-c.md))
  and registered through `raven_mcp.h`, so every binding gets it. The same tool names and replies as
  compose-raven's, or a set of its own?
- **Who registers it**: Raven MCP itself, as a switch (`Rmcp_expose_ui`, which the MCP map dropped
  for compose apps because compose registers its own), or a small library each binding calls? A
  compose app must not end up with two `ui.*` sets.
- **Switching it on**: as for compose's `ui.*` (the MCP map's
  [Who may connect, and what they may do](0154-who-may-connect.md)).

## Resolution

Decided with Paul on 2026-11-18, and written into
[`compose/docs/agent-skills.md`](../../agent-skills.md#a-running-app) and `mcp.md`'s header.

- **Built into the Raven MCP library**: the `ui.*` tools on the C driver (`raven_driver.h`), added
  through the library's own registry, so every binding has them with no code of its own. The
  contract is **The UI tools**'.
- **Switched on with `RMcpConfig.ui_tools`**, mirroring compose's `McpConfig.uiTools`. It is set by
  `RAVEN_MCP_UI=1` through the `fromEnvironment` counterparts, or `mcp.start(ui_tools=True)` in
  Python. Off by default.
- **Never two sets**: compose apps leave it off and add their own. A `ui.*` name already taken is
  left alone and logged.
- **Waiting for idle**: the deferred reply, checking `Ris_idle(R_FALSE)` once a frame through
  `Rtask_post`, up to 120 frames. Never `Rwait_idle` inside the app's loop.
