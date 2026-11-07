---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0002]
assignee: Paul Hawke
---

# Kotlin/Native targets and build

## Question

How is compose-raven built for macosArm64 and linuxX64? Decide the Gradle layout (KMP modules), the cinterop `.def` against `Raven.h`, and how `libRaven` is built (via the existing CMake presets?) and linked (static or dynamic), including MoltenVK on macOS.

## Resolution

- **Gradle layout**: a standalone Gradle build rooted at `compose/` with two modules, the KMP library `compose-raven` and a `sample` app. Kotlin 2.4.21, `org.jetbrains.kotlin.plugin.compose`, `androidx.compose.runtime:runtime:1.12.1` (see [Compose runtime on Kotlin/Native](0002-compose-runtime-on-kotlin-native.md)). Any split of cinterop from composables is left to [cinterop layering](0006-cinterop-layering.md).
- **Building libraven**: a Gradle task runs CMake on the repo root into `compose/build/raven/<target>`, always `Release`, with `RAVEN_BUILD_EXAMPLES`, `RAVEN_BUILD_TESTS`, `RAVEN_BUILD_TOOLS` and `RAVEN_BUILD_WEB` off. The cinterop task depends on it. No prebuilt-library override (`RAVEN_LIB_DIR`) for now.
- **Linking**: static. The cinterop bundles `libraven.a` (`staticLibraries` + `libraryPaths`); `linkerOpts` adds `-framework AppKit -framework QuartzCore -framework Metal -framework CoreFoundation` on macOS and `-lX11 -ldl -lpthread -lm` on Linux.
- **Hosts**: each target builds on its own host only (macosArm64 on macOS, linuxX64 on Linux); no cross-compilation.
- **`.def`**: one `raven.def`, `headers = Raven.h`, `compilerOpts = -I<repo>/include`. Package name and any `headerFilter` belong to [cinterop layering](0006-cinterop-layering.md).
- **MoltenVK**: nothing in the build or link, because Raven loads Vulkan at run time (`src/render/vulkan/vk_loader.c`). The spec states the run-time requirement (`brew install molten-vk` or the Vulkan SDK); bundling it is part of the Packaging fog.
