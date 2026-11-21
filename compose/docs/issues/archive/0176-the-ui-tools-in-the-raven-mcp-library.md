---
status: closed
---

# The UI tools in the Raven MCP library

`ui.*` for every app that isn't compose-raven, on the C driver, switched on with
`RMcpConfig.ui_tools`. Design: [The UI tools](../../mcp.md#the-ui-tools),
[A running app](../../agent-skills.md#a-running-app).

**Depends on:** [The Raven MCP library](0174-the-raven-mcp-library.md),
[The UI driver in Raven's C](0169-the-ui-driver.md), [PNG encoding in Raven's C](0166-png-encoding-in-ravens-c.md).

## Deliverables

- [x] `ui.tree`, `ui.find`, `ui.click`, `ui.type`, `ui.key`, `ui.screenshot` over `raven_driver.h`, with the contract's arguments, outline (500-line cap, `under`, `depth`), replies and hints.
- [x] Actions hold their reply and check `Ris_idle` once a frame through `Rtask_post`, up to 120 frames.
- [x] `ui_tools` in `RMcpConfig`, set by `RAVEN_MCP_UI=1`; a `ui.*` name already taken is left and logged.
- [x] Tests: the notepad driven through MCP from a client, in C and Python.

## Resolution

Done on macOS (arm64), on the `compose-raven` branch. `./gradlew :raven-mcp:check` passes three runs
in a row: the 29 tests of `src/test/python/test_raven_mcp.py` (10 of them new, `UiToolsTest`), and
`cTest`, which builds `src/test/c/notes.c` and drives it in its own process from
`src/test/python/test_ui_tools_c.py`.

- **The tools** live in `UiTools.kt` over `raven_driver.h`, with the contract's arguments, replies and
  hints. Failures carry the driver's own words (`2 accessibles with role=button, expected one`,
  `role=button name="Delete" is disabled`), and bad arguments get messages of their own.
- **The outline** shows what a field holds as `text="…"`, the states a selector names, and a closed
  menu without its items. That's now written into [The outline](../../mcp.md#the-outline).
- **`ui.key` with no selector** goes to the deepest focused accessible of the focused window, else
  to the window. Key names are parsed in Kotlin: Raven's `rvn_parse_shortcut` isn't exported.
- **Screenshots**: when a pixel's alpha isn't 255, the pixel is made straight before
  `Rimage_encode_png`. The reply is an MCP image through `replyJson`. The null backend captures
  too, so the test checks the PNG's size against `Rwindow_get_scale`.
- **The registry** now records who published each tool: the app, an exposed action, or the UI tools.
  An app's own `ui.*` tool wins and is logged. Removing it brings the UI tool back. `Rmcp_stop`
  withdraws the UI tools, so they never go stale across runs of Raven.
- **Changed from the deliverable: the wait is timed, not counted.** It checks `Ris_idle` once a
  frame for up to 2 s on Raven's clock instead of 120 frames. Headless frames take microseconds, so
  the focus and press animations needed about 800 frames, and every reply after a click or typing
  waited out the limit. `mcp.md`, `agent-skills.md` and `raven_mcp.h` say so. Raven's actions as
  tools share the same wait (`Idle.kt`). The test client now pumps frames for a time, not a count.
- **Found along the way, not fixed here**: a `ListView`'s `OnChanged` fires on activation only,
  because every widget has one action slot, although the XML schema says it fires when the
  selection changes. The notepads use `Rlist_view_set_selection_changed_callback` instead.
- **Not here**: Linux isn't tried. The tests use the null backend, not headless Vulkan.
