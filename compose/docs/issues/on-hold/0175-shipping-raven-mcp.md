---
status: on-hold
---

# Shipping Raven MCP

> **On hold (2026-11-19).** Nothing is to be released beyond the local machine for now: no GitHub
> release assets, no Maven Central, no registry packages. `publishToMavenLocal` is fine.

Get the Raven MCP library to every kind of app. Design:
[Building and shipping](../../mcp.md#building-and-shipping).

**Depends on:** [Raven MCP in every binding](../archive/0183-raven-mcp-in-every-binding.md), which took the
`tools/bindgen` deliverable.

## Deliverables

- [ ] compose-raven's per-host release jobs attach `raven-mcp-<target>.zip` (the library, `raven_mcp.h`, a matching shared libraven).
- [ ] `compose-raven-mcp` published with the other modules and in the BOM.
- [ ] Optional registry packages beside each packaged binding (`raven-gui[mcp]` and the rest), named with each registry's packaging.
