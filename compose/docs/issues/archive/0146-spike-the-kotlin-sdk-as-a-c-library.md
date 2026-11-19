---
status: closed
wayfinder: prototype
parent: 0145
blocked_by: []
assignee: Paul Hawke
---

# Spike: the MCP Kotlin SDK as a C library

## Question

Can the MCP Kotlin SDK run inside a Raven app written in another language, behind a C API, and
what does it cost?

Build `spikes/mcp`. It is throwaway and doesn't follow the draft header slavishly.

1. A Kotlin/Native shared library (`binaries.sharedLib`) on `macosArm64` that depends on
   `kotlin-sdk-server` and `ktor-server-cio`. Serve `mcpStreamableHttp { }` on `127.0.0.1`. Check
   first that it links on native at all. The README shows only JVM.
2. Export `Rmcp_start`, `Rmcp_stop`, `Rmcp_tool_register` and `Rmcp_call_reply` with `@CName`, as
   C functions rather than Kotlin/Native's generated struct of function pointers.
3. A tool call hops onto Raven's UI thread with `Rtask_post`, runs a C callback there, and replies.
4. A Python script (Raven's Python binding, `bindings/python`) loads libraven and the spike
   library, registers an `add_note` tool that changes a label, and an MCP client (Claude Code or
   the MCP inspector) calls it.
5. A compose-raven sample registers a tool through the Kotlin API in the same build, linking the
   module rather than the dylib, to show the two paths share one registry design.

## Measure and record

- The size of the shared library, and what it adds to a Python app's memory and start-up time.
- How the library reaches libraven: how it links against a shared libraven
  (`RAVEN_BUILD_SHARED=ON`), and what breaks if it carries its own copy.
- Threads the Kotlin/Native runtime starts, and whether anything misbehaves with Python's GIL when
  a callback comes back on the UI thread.
- Whether a compose-raven app and the dylib in one process fail, so that rule is evidence, not
  assumption.
- Anything in the SDK that doesn't work on native (stdio, `listChanged` notifications, logging).

## Resolution

**It works, and it costs little at run time.** The MCP Kotlin SDK runs as a Kotlin/Native shared
library behind a plain C API. A Raven app in Python registers tools through it, and an MCP client
calls them on Raven's UI thread. Checked 2026-11-18 on macOS (arm64), Raven 0.2.3, Kotlin 2.4.21.
The code is kept on the `spike/mcp` branch (`68e4fcb`), out of `compose-raven`.

What the spike showed:

- **It builds on native.** `kotlin-sdk-server` 0.16.0 and `ktor-server-cio` 3.6.1 resolve and link
  for `macosArm64` with our Kotlin and Ktor versions. All the SDK's server code is `commonMain`,
  Streamable HTTP and stdio included, so nothing in it is JVM-only.
- **Plain C exports.** `@CName` gives `Rmcp_start`, `Rmcp_tool_register` and the rest as ordinary C
  symbols, called from Python's `ctypes` with no Kotlin in sight. Kotlin/Native also writes its own
  `libraven_mcp_api.h`, which we can ignore.
- **It uses the app's libraven.** The dylib links `@rpath/libraven.0.dylib` and carries no Raven of
  its own, so it needs Raven built with `RAVEN_BUILD_SHARED=ON`. The Python binding already needs
  that, so Python gains no new constraint.
- **Size**: 8.6 MB release, 5.9 MB stripped, against 1.9 MB for libraven itself.
- **Start-up and memory**: loading the library takes about 2.5 ms and `Rmcp_start` 1.3 ms. Together
  they add about 6 MB of RSS (15.7 to 21.5 MB) and 8 threads. The threads (Ktor's and the coroutine
  pools) stay after `Rmcp_stop`.
- **Calls**: a `tools/call` round trip over loopback takes 1.4 to 2.5 ms, headless and in a real
  Vulkan window. The hop through `Rtask_post` to the UI thread takes about 0.05 to 0.1 ms. Every
  callback (25 of 25 in the windowed run) ran on the UI thread, which is Python's main thread.
  Kotlin/Native attached to that foreign thread without being asked, and Python's GIL caused no
  trouble.
- **The deferred reply works.** A `CompletableDeferred` behind the `RMcpCall*` (a `StableRef`)
  suspends the SDK's handler until `Rmcp_call_reply` or `Rmcp_call_fail`. A failure reaches the
  client as a tool error (`isError: true`), not a protocol error.
- **`listChanged` needs nothing from us.** The SDK sends `notifications/tools/list_changed` on the
  session's GET stream whenever a tool is added or removed. Registering and unregistering from
  inside a tool's own callback, on the UI thread, is fine.
- **Two Kotlin/Native runtimes coexist** in the simple case: an executable running the server code
  itself and `dlopen`ing the dylib serves both and collects garbage normally. So the rule that
  compose-raven apps link the module still holds, but **for one registry and one copy of the SDK
  and Ktor, not because of a crash**.
- **Shutdown is clean**: `Rmcp_stop`, then `Rshutdown`.

For the header ([The raven_mcp.h contract](0147-the-raven-mcp-h-contract.md)):

- **The schema** should go to the SDK whole. The spike passed only `properties` and dropped
  `required`.
- **Logging**: kotlin-logging and Ktor print to stdout (`kotlin-logging: initializing...`, Ktor's
  INFO lines). The header needs to say where logs go: Raven's error callback, a log callback, or
  silence. A stdio transport would be corrupted by them.
- **A call never replied to** keeps its `StableRef` and leaves the client's request hanging. That
  needs a timeout, or cleanup when the client cancels.
- **The 8 threads stay after `Rmcp_stop`**. The header has to say whether stopping releases
  everything.
- **DNS-rebinding protection is on by default** in `mcpStreamableHttp`
  (`enableDnsRebindingProtection = true`, with allowed hosts and origins), for
  [Who may connect](0154-who-may-connect.md).

Not tested: Linux, a Rust caller, and a real agent as the client. `curl` spoke the protocol (initialize,
session id, tools/list, tools/call, the GET stream). The costs that remain are in the build, not at
run time: a Kotlin toolchain and a shared libraven
([Building and shipping](0148-building-and-shipping-raven-mcp.md)), and the second runtime
weighed against MCP in C ([ADR 0003](0149-adr-the-sdk-or-mcp-in-c.md)).
