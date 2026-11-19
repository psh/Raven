---
status: open
---

# Raven MCP in the other bindings

`Rmcp_*` for the eleven bindings that [Raven MCP in every binding](archive/0183-raven-mcp-in-every-binding.md)
left out, the way it settled: `tools/bindgen` already reads `raven_mcp.h` into `api.mcp`, and each
generator opts in. Design: [Building and shipping](../mcp.md#building-and-shipping).

**Depends on:** [Raven MCP in every binding](archive/0183-raven-mcp-in-every-binding.md).

## Deliverables

- [ ] Loaded on the first `Rmcp_` call (`RAVEN_MCP_LIBRARY`, else beside libraven; missing says Raven MCP isn't available here, and the rest works): LuaJIT, Java, C#, Bun, Node (the addon `dlopen`s it in C, never links it).
- [ ] An opt-in unit that links the Raven MCP library, which an app that doesn't use it never links: Go (a package `raven/mcp`), Zig (`raven_mcp.zig`), Odin (a package of its own), C3 (a module of its own), C++ (`raven_mcp.h` as it is, plus anything `raven.hpp` adds).
- [ ] Each tested like Python's and Rust's where its toolchain is here: publishes a tool and an agent calls it, run from `compose/raven-mcp`'s `check`.
