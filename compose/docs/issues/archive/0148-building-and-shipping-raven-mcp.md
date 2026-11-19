---
status: closed
wayfinder: grilling
parent: 0145
blocked_by: [0146]
assignee: Paul Hawke
---

# Building and shipping Raven MCP

## Question

Where does Raven MCP live, and how does it reach each kind of app?

- **The library**: a separate `libraven_mcp` beside libraven, built by Gradle (Kotlin/Native) and
  driven from Raven's CMake as an option, or built only by compose's Gradle build and shipped as
  a release artifact? What does a C developer without a JDK do?
- **Linking**: it needs a shared libraven ([the spike](0146-spike-the-kotlin-sdk-as-a-c-library.md)
  records how). Does that make `RAVEN_BUILD_SHARED` a requirement for every app that uses MCP, and
  what do the bindings that ship libraven statically do?
- **compose-raven**: an opt-in `compose-raven-mcp` module (the Kotlin registry, the server, the
  `McpTool` composable), published like the other modules
  ([packaging](0031-packaging-and-publishing.md)). How does it share code with the
  shared library without two copies drifting apart?
- **Off by default**: an app that doesn't ask for MCP carries none of it, in size or in threads.
- **Targets**: `macosArm64` and `linuxX64` now; what `mingwX64` needs later.

From [the spike](0146-spike-the-kotlin-sdk-as-a-c-library.md): the dylib links `@rpath/libraven.0.dylib` (the Python binding already
needs a shared libraven), is 8.6 MB release and 5.9 MB stripped, and costs about 6 MB RSS and 8
threads once started.

## Resolution

Decided with Paul on 2026-11-18. The full write-up is in
[`compose/docs/mcp.md`](../../mcp.md#building-and-shipping).

- **One module, two products**: `compose/raven-mcp` is Kotlin/Native with no Compose, and holds the
  registry, the server and the calls. It builds the shared Raven MCP library (`raven_mcp.h`) for C
  and the bindings, and a klib for the opt-in `compose-raven-mcp` module (`McpTool`, `ui.*`). That
  module is published with the others (`io.github.psh`, in the BOM). The code exists once. Its
  cinterop names no library, so the shared library links `-lraven` and a compose app links the
  libraven it already has.
- **Shared libraven only**, and the documentation says so. Every binding but C++ is shared already.
  A static `libraven_mcp.a` goes to the fog.
- **Built only by compose's Gradle build.** Raven's CMake gets no Kotlin. The per-host release jobs
  that publish compose-raven also attach `raven-mcp-<target>.zip`, holding the library,
  `raven_mcp.h` and a matching shared libraven. If ADR 0003 picks C, the build moves to CMake and
  the header stays.
- **Versioning**: it carries compose-raven's version, plus `RAVEN_MCP_API_VERSION` for the header.
  It records the Raven it was built against, and `Rmcp_start` fails under an older one.
- **Targets**: `macosArm64` and `linuxX64`. There is no Intel Mac (the SDK has no `macosX64`), so
  the universal macOS packages carry an arm64-only Raven MCP library. `mingwX64` follows phase 3's
  Windows target, and linking against MSVC's `raven.dll` is checked then (fog).
- **Bindings**: `tools/bindgen` generates `Rmcp_*` in all of them. They load the library on first
  use (`RAVEN_MCP_LIBRARY`, or beside libraven), and report "not available here" when it is
  missing. Each registry gets a separate, optional package (`raven-gui[mcp]` and so on); exact
  names come with that registry's packaging.
- **Nothing runs until started**: loading starts no thread and opens no port. This is promised in
  `mcp.md`.

This leaves [ADR 0003](0149-adr-the-sdk-or-mcp-in-c.md) with the spike's numbers and this build
cost to weigh. The cost is a Kotlin toolchain in `compose/` only, plus shared libraven and no Intel
Mac.
