---
status: closed
wayfinder: grilling
parent: 0145
blocked_by: []
assignee: Paul Hawke
---

# Who may connect, and what they may do

## Question

Raven MCP opens a door into a running app. How is it kept shut by default and narrow when open?

- **Binding**: loopback only, and the port: fixed, from config, or chosen by the OS and reported?
- **Authentication**: a token per launch. How does the user's agent get it (printed, written to a
  file, an env var), and how is it checked on Streamable HTTP? Origin checks against DNS rebinding,
  as the MCP spec advises for local servers.
- **Switching it on**: in code only, or also by an env var or flag so a developer can turn it on in
  any build? Should release builds refuse unless the app opts in explicitly?
- **Scope**: `ui.*` separately from the app's tools; per-tool annotations (read-only, destructive)
  for agents to respect.
- **Seeing it**: does the app show that an agent is connected (a window badge, an announcement)?

From [the spike](0146-spike-the-kotlin-sdk-as-a-c-library.md): the SDK's `mcpStreamableHttp` turns on DNS-rebinding protection by
default (`enableDnsRebindingProtection`, `allowedHosts`, `allowedOrigins`), so Host and Origin
checking comes for free on the SDK route.

## Resolution

Decided with Paul on 2026-11-18, and written into [`compose/docs/mcp.md`](../../mcp.md#who-may-connect).

- **Host and Origin are always checked**: the SDK's DNS-rebinding protection with its localhost
  defaults. It can't be turned off.
- **A bearer token by default**, made once per app and user and stored with the URL in
  `~/.raven/mcp/<app name>.json` (mode 0600). It's reused across launches, so the agent is set up
  once, and deleting the file makes a new one. `RMcpConfig.token`: NULL means the stored token, a
  string means that token, and `""` means none, with a warning.
- **On only when the app's code says so.** `McpConfig.fromEnvironment()` and its C and Python
  counterparts read `RAVEN_MCP=1`, `RAVEN_MCP_PORT` and `RAVEN_MCP_UI=1`, for "on for developers
  only". `RAVEN_MCP=0` is a kill switch over everything. There's no detection of build type.
- **Knowing an agent is there**: `RMcpConfig.on_session` (connected or disconnected, with the
  client's name), compose's `McpConfig(onAgentConnected)`, and a ready-made `McpStatus()` marker
  the app may place. Every connection is logged.
- **Confirming destructive tools** stays in the fog.
