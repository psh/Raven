---
status: open
---

# compose-raven-mcp

`McpTool`, `application(mcp = …)` and compose-raven's `ui.*` over Compose semantics. Design:
[compose-raven: McpTool](../mcp.md#compose-raven-mcptool), [The UI tools](../mcp.md#the-ui-tools).

**Depends on:** [The Raven MCP library](0174-the-raven-mcp-library.md),
[compose-raven-ui-test, and idle in a live app](0178-compose-raven-ui-test-and-live-idle.md),
[PNG encoding in Raven's C](0166-png-encoding-in-ravens-c.md).

## Deliverables

- [ ] `McpConfig` (with `uiTools`, `onAgentConnected`, `fromEnvironment()`), and the `mcp` overloads of `application` and `singleWindowApplication`.
- [ ] `McpTool<A>` with the schema derived from `A`'s descriptor and `@McpDescription`; the untyped overload; `McpReply`; `McpHint`; the suspend handler on the UI thread, with exceptions as failures and cancellation; `rememberUpdatedState`; the stack per name.
- [ ] `McpStatus()`.
- [ ] `ui.*` over Compose semantics with `tag=`, the outline, `awaitIdle`, screenshots through `Rimage_encode_png`.
- [ ] The notepad sample publishes `notes.add`, `notes.list`, `notes.delete`; tests drive it through a client.
