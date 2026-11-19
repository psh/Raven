---
status: open
---

# Raven MCP in every binding

Run `raven_mcp.h` through `tools/bindgen` so every binding can call `Rmcp_*`, all on the local
machine with nothing released. Split out of [Shipping Raven MCP](0175-shipping-raven-mcp.md),
which is on hold. Design: [Building and shipping](../mcp.md#building-and-shipping).

**Depends on:** [The Raven MCP library](archive/0174-the-raven-mcp-library.md).

## Deliverables

- [ ] `raven_mcp.h` through `tools/bindgen`: every binding gets `Rmcp_*`, loading the library on first use (`RAVEN_MCP_LIBRARY`, else beside libraven), and saying Raven MCP isn't available when it's missing.
