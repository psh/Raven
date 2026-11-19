---
status: closed
wayfinder: grilling
parent: 0145
blocked_by: [0147, 0150]
assignee: Paul Hawke
---

# The UI tools

## Question

Which `ui.*` tools does compose-raven register, and what do agents send and get back? This is the
first pass of the map: the first client of the registration API, and proof that the API works.

- **The set**: `ui.tree` (or a resource), `ui.find`, `ui.click`, `ui.type`, `ui.read`, `ui.key`,
  maybe `ui.scroll` and `ui.select`. Smallest useful set first.
- **Addressing nodes**: by matcher in every call (text, content description, test tag, role), or by
  an id from `ui.find`/`ui.tree` that stays valid across calls? What happens when recomposition
  replaces the node behind an id?
- **The tree**: what a dump holds (role, text, description, tag, enabled, focused, bounds?), and how
  big it may get before it needs paging or a subtree.
- **Replies**: a tool replies once the app is idle ([Finders and actions shared by tests and UI tools](0150-shared-finders-and-actions.md): `awaitIdle`),
  with what changed or with nothing? Errors: no match, several matches, a disabled node.
- **Screenshots** (graduated from the map's fog): a `ui.screenshot` returning the window as an image,
  over `Rwindow_capture` (`raven_window.h`, RGBA8 of the last frame, Vulkan only: the null backend
  gives just the clear colour). It captures only the app's window, never the user's desktop. Which
  window, at what scale, and as which MCP image content? The PNG bytes come from `Rimage_encode_png`
  ([Writing a capture to a file](0160-writing-a-capture-to-a-file.md)). Used by the interactive part of
  [Prototyping Raven UI with an agent](0156-prototyping-raven-ui-with-an-agent.md).
- **Switching it on**: `ui.*` lets an agent drive the whole app. Is it a separate opt-in from the
  app's own tools (see [Who may connect](0154-who-may-connect.md))?

Use the notepad sample as the worked example.

## Resolution

Decided with Paul on 2026-11-18, and written into [`compose/docs/mcp.md`](../../mcp.md#the-ui-tools).

- **One contract for every Raven app**: tool names, arguments and replies are the same for compose
  apps (over Compose semantics) and the rest (over the C driver), so
  [Driving a running app in any language](0164-driving-a-running-app-in-any-language.md) only
  implements it.
- **Selectors in every call**, in the C driver's language, plus `tag=` for compose test tags. No ids.
- **Six tools**: `ui.tree`, `ui.find`, `ui.click`, `ui.type`, `ui.key`, `ui.screenshot`. Reading is
  part of `ui.find`. Scrolling, selecting and semantic actions go to the fog.
- **The outline**: an indented text outline (role, quoted name, `[states]`, `tag=`), as Playwright's
  accessibility snapshot. Each line reads as a selector. It is capped at 500 lines, saying how many
  were left out; `under` and `depth` narrow it.
- **Actions** use real input, wait up to 120 frames for idle, and reply with the new outline
  (`snapshot: false` skips it). Failures carry the finder's message. Busy at the limit is still a
  success, saying so. Keys are `"ctrl+s"`-style strings of Raven's key names.
- **`ui.screenshot`**: the window's last frame (the focused window, or by title) as PNG image content,
  through `Rimage_encode_png` and `Rmcp_call_reply_json`.
- **Hints**: tree, find and screenshot are `READ_ONLY | CLOSED_WORLD`; the actions are unmarked.
- **Off by default**, a separate opt-in from the app's tools. How it's switched on is for
  [Who may connect, and what they may do](0154-who-may-connect.md).
