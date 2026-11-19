---
status: accepted
---

# Raven MCP on the MCP Kotlin SDK

Raven MCP lets an agent call a running Raven app's tools over the Model Context Protocol, in every
language Raven has a binding for, through one C header, `raven_mcp.h`
([`mcp.md`](../mcp.md)). Something has to implement that header. On 2026-11-18 we chose the
**MCP Kotlin SDK**, in a Kotlin/Native shared library built from `compose/raven-mcp`, over MCP
written in C inside Raven.

The [spike](../issues/archive/0146-spike-the-kotlin-sdk-as-a-c-library.md) ran it end to end: a
Python Raven app published tools through plain C exports, an MCP client called them, and each call ran
on Raven's UI thread in about 2 ms, with deferred replies and `listChanged` working. MCP written in C
would mean writing and maintaining about 2-3k lines (JSON-RPC, a loopback HTTP server with SSE,
sessions, the protocol's state machine) and following every revision of a spec that is still
changing fast. The SDK's maintainers do that for us.

The choice is reversible because of the header. Python, Rust and the other bindings see only
`raven_mcp.h`, and compose-raven apps link the same module directly. None of them can tell which
implementation is behind it, so swapping it later breaks no binding.

This is consistent with [ADR 0002](0002-rendering-in-c-bindings-thin.md), not an exception to it.
ADR 0002 puts drawing and heavy computation in Raven's C so that there is one implementation, every
binding gets it, and a fix lands once. Raven MCP is protocol plumbing, and it keeps all three: one
implementation, reached by every binding through one C header.

## Considered options

- **MCP in C, inside Raven**: no second runtime, every Raven target (Intel Macs, a static libraven),
  and it could go upstream. Against it: the code above, which doesn't exist yet, and tracking the
  spec ourselves.
- **Both**: C behind the header for the bindings, and the SDK inside `compose-raven-mcp`. Rejected:
  two implementations of one contract drift apart, which is what ADR 0002 warns against, and an app
  would have two registries.

## Consequences

- A process running Raven MCP carries a Kotlin/Native runtime: the library is 5.9 MB stripped, and
  once started it adds about 6 MB of memory and 8 threads, which stay after `Rmcp_stop`. Loading it
  starts nothing.
- The build cost stays in `compose/`: Gradle builds the library, and Raven's CMake needs no Kotlin
  ([Building and shipping Raven MCP](../issues/archive/0148-building-and-shipping-raven-mcp.md)).
- Raven MCP needs the shared libraven, and has no Intel Mac build: the SDK has no `macosX64`.
- Raven MCP is not proposed upstream as it stands. Raven's C gains only what Raven MCP needs from it,
  such as listing the registered actions, and those changes are proposed upstream as usual.

## When to revisit

Write Raven MCP in C, behind the same header, when any of these happens:

- Upstream Raven wants Raven MCP in its own repository or CMake build.
- A real app needs Raven MCP on an Intel Mac, or with a static libraven.
- Someone won't use Raven MCP because of its runtime cost (size, threads, the second runtime).
- The MCP Kotlin SDK falls behind the MCP spec, or stops being maintained.
