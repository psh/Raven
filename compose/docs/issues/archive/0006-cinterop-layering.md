---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0004]
assignee: Paul Hawke
---

# Layering over cinterop

## Question

Does compose-raven call the raw cinterop bindings directly, or is there a thin idiomatic Kotlin layer in between (handles as value classes, callbacks via `StableRef`, strings and structs converted at the edge)? If there is a layer, is it a separate module that can be used without Compose?

## Resolution

- **Modules** (supersedes the module list in [Kotlin/Native targets and build](0005-kotlin-native-targets-and-build.md)): compose-raven is a family, not one module.
  - `raven-native`: the cinterop (package `raven.cinterop`) plus a small hand-written public layer (package `raven.native`). It has no Compose dependency and is usable on its own.
  - `compose-raven-foundation`: the Applier and node, host and frame loop, `Modifier`, window entry point and all composables, in packages `raven.compose.ui` and `raven.compose.foundation`. It depends on `raven-native` with `implementation`, so apps never see Raven.
  - `sample`: depends on `compose-raven-foundation` only.
- **`raven.def`**: `package = raven.cinterop`, `headerFilter` limited to Raven's own headers, without `raven_backend.h` and `raven_vulkan.h`.
- **The `raven.native` layer**: only what recurs in node code. That is the callback registry (one `StableRef` per widget, disposed from `Rwidget_set_destroy_callback`), string conversion at the edge, and the UI-thread check. There are no handle value classes and no per-widget wrappers; nodes call `raven.cinterop` directly for everything else.
- **Public API**: no `Rid` or other Raven type appears in foundation's public API; the goal is a fully Compose interface with no reason to drop down to Raven. Raven enums get Kotlin counterparts (for example `TextAlign`) only as v1 composables need them, mapped internally.
- **Later, without breaking imports**: splitting `compose-raven-ui` out of foundation (the packages already allow it). A Material-like styled layer is part of the Theming fog.
