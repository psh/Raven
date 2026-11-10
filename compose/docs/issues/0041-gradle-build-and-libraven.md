---
status: open
---

# Gradle build and libraven

Stand up the standalone Gradle build in `compose/` and get Kotlin/Native calling Raven: a CMake
task that builds `libraven.a`, the `raven.def` cinterop in `raven-native`, and empty
`compose-raven-ui`, `compose-raven-foundation` and `samples/*` modules that compile. Spec:
[2. Modules and build](../spec.md#2-modules-and-build), [3. raven-native](../spec.md#3-raven-native).

**Depends on:** nothing. This is the first phase 1 ticket.

## Deliverables

- [ ] `compose/settings.gradle.kts` and the root build: Kotlin 2.4.21, `kotlin("multiplatform")`,
      `org.jetbrains.kotlin.plugin.compose`, Google Maven, `androidx.compose.runtime:runtime:1.12.1`
      in a version catalog. Targets `macosArm64` and `linuxX64`, each built only on its own host.
- [ ] Modules: `raven-native`, `compose-raven-ui` (`implementation(raven-native)`, `api(runtime)`),
      `compose-raven-foundation` (`api(compose-raven-ui)`, `implementation(raven-native)`),
      `samples/hello` and `samples/xml-translator` as executables that depend on foundation only.
- [ ] A Gradle task that runs CMake on the repo root into `compose/build/raven/<target>`: always
      `Release`, with `RAVEN_BUILD_EXAMPLES`, `_TESTS`, `_TOOLS` and `_WEB` off. The cinterop task
      depends on it, and it is incremental.
- [ ] `raven.def`: `headers = Raven.h`, `package = raven.cinterop`, a `headerFilter` without
      `raven_backend.h` and `raven_vulkan.h`, `staticLibraries`/`libraryPaths` for `libraven.a`,
      and the per-OS `linkerOpts`.
- [ ] A build check that fails when the `linkerOpts` drift from `RAVEN_LIBRARIES` in
      `CMakeLists.txt`.
- [ ] A smoke test in `raven-native`: `Rinit_ex` with `R_BACKEND_NULL` and `headless = R_TRUE`,
      create and destroy a widget, `Rshutdown`, then `Rmemory_get_stats` reports zero blocks held.
      `./gradlew -p compose allTests` runs it.
