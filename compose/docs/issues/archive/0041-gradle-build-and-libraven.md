---
status: closed
---

# Gradle build and libraven

Stand up the standalone Gradle build in `compose/` and get Kotlin/Native calling Raven: a CMake
task that builds `libraven.a`, the `raven.def` cinterop in `raven-native`, and empty
`compose-raven-ui`, `compose-raven-foundation` and `samples/*` modules that compile. Spec:
[2. Modules and build](../../spec.md#2-modules-and-build), [3. raven-native](../../spec.md#3-compose-raven-native).

**Depends on:** nothing. This is the first phase 1 ticket.

## Deliverables

- [x] `compose/settings.gradle.kts` and the root build: Kotlin 2.4.21, `kotlin("multiplatform")`,
      `org.jetbrains.kotlin.plugin.compose`, Google Maven, `androidx.compose.runtime:runtime:1.12.1`
      in a version catalog. Targets `macosArm64` and `linuxX64`, each built only on its own host.
- [x] Modules: `raven-native`, `compose-raven-ui` (`implementation(raven-native)`, `api(runtime)`),
      `compose-raven-foundation` (`api(compose-raven-ui)`, `implementation(raven-native)`),
      `samples/hello` and `samples/xml-translator` as executables that depend on foundation only.
- [x] A Gradle task that runs CMake on the repo root into `compose/build/raven/<target>`: always
      `Release`, with `RAVEN_BUILD_EXAMPLES`, `_TESTS`, `_TOOLS` and `_WEB` off. The cinterop task
      depends on it, and it is incremental.
- [x] `raven.def`: `headers = Raven.h`, `package = raven.cinterop`, a `headerFilter` without
      `raven_backend.h` and `raven_vulkan.h`, `staticLibraries`/`libraryPaths` for `libraven.a`,
      and the per-OS `linkerOpts`.
- [x] A build check that fails when the `linkerOpts` drift from `RAVEN_LIBRARIES` in
      `CMakeLists.txt`.
- [x] A smoke test in `raven-native`: `Rinit_ex` with `R_BACKEND_NULL` and `headless = R_TRUE`,
      create and destroy a widget, `Rshutdown`, then `Rmemory_get_stats` reports zero blocks held.
      `./gradlew -p compose allTests` runs it.

## Resolution

Done and checked on macOS (arm64), 2026-11-10. `./gradlew build` and `allTests` pass, and the smoke
test leaves zero blocks held. Making `raven.def` drop `-framework Metal` fails the build with both
lists shown. `buildRaven` is up to date on a second run.

- The drift check doesn't parse `CMakeLists.txt`. `raven-native/cmake/write-link-libraries.cmake`
  is injected with `CMAKE_PROJECT_INCLUDE` and uses `cmake_language(DEFER)` to write the
  `RAVEN_LIBRARIES` CMake actually resolved. Raven's sources stay untouched.
- A `build-logic` convention plugin, `raven.native-target`, declares only the host's target.
- libraven is built for macOS 11.0 and arm64, Kotlin/Native's minimum, so the link doesn't warn.
- Debug links print `could not open ...a.o` warnings for Kotlin/Native's own stdlib cache in
  `~/.konan`. They are toolchain noise, not from libraven, and release links have none.

The `linuxX64` target is untested. That work, with its two risks (finding libX11 and Kotlin/Native's
older glibc), moved to [Linux build and libraven](0058-linux-build-and-libraven.md).
