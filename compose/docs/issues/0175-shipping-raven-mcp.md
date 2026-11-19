---
status: open
---

# Shipping Raven MCP

Get the Raven MCP library to every kind of app. Design:
[Building and shipping](../mcp.md#building-and-shipping).

**Depends on:** [The Raven MCP library](0174-the-raven-mcp-library.md).

## Deliverables

- [ ] `raven_mcp.h` through `tools/bindgen`: every binding gets `Rmcp_*`, loading the library on first use (`RAVEN_MCP_LIBRARY`, else beside libraven), and saying Raven MCP isn't available when it's missing.
- [ ] compose-raven's per-host release jobs attach `raven-mcp-<target>.zip` (the library, `raven_mcp.h`, a matching shared libraven).
- [ ] `compose-raven-mcp` published with the other modules and in the BOM.
- [ ] Optional registry packages beside each packaged binding (`raven-gui[mcp]` and the rest), named with each registry's packaging.
