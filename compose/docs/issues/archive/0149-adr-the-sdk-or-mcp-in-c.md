---
status: closed
wayfinder: task
parent: 0145
blocked_by: [0146, 0148]
assignee: Paul Hawke
---

# ADR 0003: the Kotlin SDK or MCP in C

## Question

Is Raven MCP implemented with the MCP Kotlin SDK in a Kotlin/Native shared library, or written in
C inside Raven?

Write `compose/docs/adr/0003-...md` from the spike's measurements and the shipping decision.

- **The SDK** keeps up with the MCP spec for us. Against it: a Kotlin/Native runtime in every Python
  or Rust process, a Kotlin toolchain in Raven's build for one piece, and the libraven linking rule.
- **C in Raven** follows [ADR 0002](../../adr/0002-rendering-in-c-bindings-thin.md) and needs no second
  runtime. Against it: JSON-RPC, a loopback HTTP server with SSE and the protocol state machine,
  about 2-3k lines, and we follow the spec ourselves. Raven already has a JSON parser
  (`src/map/json.c`).
- **Both**: C for the C API, and the SDK only inside `compose-raven-mcp`. Two implementations of one
  contract, the drift ADR 0002 warns about.

The header ([0147](0147-the-raven-mcp-h-contract.md)) is the same either way, so this decision can
be revisited later without breaking a binding. Record when it should be.

## Resolution

Decided with Paul on 2026-11-18, and recorded as
[ADR 0003: Raven MCP on the MCP Kotlin SDK](../../adr/0003-raven-mcp-on-the-kotlin-sdk.md).

- **The SDK**, in a Kotlin/Native library built from `compose/raven-mcp`, behind `raven_mcp.h`. The
  spike proved it works, and the SDK's maintainers keep up with a fast-moving spec. The header makes
  the choice reversible.
- **Consistent with ADR 0002**, not an exception: one implementation, which every binding gets
  through one C header. ADR 0002 itself is unchanged.
- **Rejected**: MCP in C now (about 2-3k lines, and following the spec ourselves), and both together
  (two implementations drifting apart, two registries).
- **Revisit, in C behind the same header,** if upstream wants Raven MCP in its repository, a real app
  needs an Intel Mac or a static libraven, the runtime cost keeps someone from using it, or the SDK
  falls behind the spec.
