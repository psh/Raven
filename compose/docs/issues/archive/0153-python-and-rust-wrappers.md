---
status: closed
wayfinder: grilling
parent: 0145
blocked_by: [0147, 0149]
assignee: Paul Hawke
---

# Python and Rust wrappers

## Question

What does Raven MCP look like in the bindings, kept thin as
[ADR 0002](../../adr/0002-rendering-in-c-bindings-thin.md) asks?

- **Python** (`bindings/python`): a decorator such as `@mcp.tool("notes.add")` over a function
  whose type hints give the schema? Callbacks on the UI thread holding the GIL. A deferred reply as a
  returned object or an `asyncio` future? Keeping the callback object alive while registered.
- **Rust** (`bindings/rust`): a closure registered with a lifetime handle that unregisters on drop?
  `serde` for arguments and schema (`schemars`)? `Send` rules for replying from another thread.
- **Both**: `Rmcp_expose_action` for apps that already use named actions.

Settled by [Building and shipping Raven MCP](0148-building-and-shipping-raven-mcp.md):
`tools/bindgen` already generates `Rmcp_*` in every binding, which loads the library on first use
and says when it isn't available. So this ticket covers only the language's idioms on top, and
nothing about finding or loading the library.

Sketch each as the notepad example in that language, against the header from
[0147](0147-the-raven-mcp-h-contract.md).

## Resolution

Decided with Paul on 2026-11-18, and written into [`compose/docs/mcp.md`](../../mcp.md#python-and-rust).

- **Where**: Python's `raven_mcp` module in the optional `raven-gui[mcp]` package (standard library
  only), and Rust's `raven::mcp` behind the cargo feature `mcp`.
- **Python**:
  - `@mcp.tool(name, description, hints=…)` on a function. The schema comes from its type hints:
    defaults make a parameter optional, and `Annotated` gives a description.
  - The return value is the reply (`str` is text, `dict`/`list` is JSON), and raising fails the call.
  - A `call` parameter replies later, from any thread. A second reply raises, and a `Call` collected
    without one fails the call. There is no asyncio.
  - `mcp.remove_tool`.
- **Rust**:
  - `raven::mcp::tool(..).hints(..).add(|args: T| -> Result<..>)`, with arguments through serde and
    the schema from `schemars` (doc comments become descriptions).
  - A `ToolHandle` withdraws on drop, and `.forget()` keeps it.
  - `add_deferred` gives a `Send`, non-`Clone` `Call` whose `reply`/`fail` consume it, so the
    compiler enforces exactly one reply. A dropped `Call` fails the call.
- **Raven's actions**: no idiom needed. They come through `tools/bindgen`.
- **The worked example**: the notepad's `notes.*` tools over the shared `notepad.xml`.
