---
status: closed
wayfinder: research
parent: 0001
blocked_by: []
assignee: claude
---

# Compose runtime on Kotlin/Native

## Question

Which Compose runtime artifact can a non-UI Kotlin/Native project depend on for macosArm64 and linuxX64 (JetBrains `org.jetbrains.compose.runtime:runtime` or another)? Which version, which Kotlin version and compiler plugin setup does it require, and are there known limitations of the runtime on Native (coroutines, memory model, `MonotonicFrameClock`, snapshot system)?

## Resolution

- Depend on `androidx.compose.runtime:runtime:1.12.1` (Google Maven, the latest stable release). Since 1.9.0 AndroidX publishes the multiplatform runtime, including the `macosarm64` and `linuxx64` artifacts. On native, `org.jetbrains.compose.runtime:runtime` (1.12.2) is now an empty shell that redirects to AndroidX 1.12.1.
- Setup is `kotlin("multiplatform")` plus `org.jetbrains.kotlin.plugin.compose`, both at the same Kotlin version. Recommend Kotlin 2.4.21; the klibs are built with 2.3.20, and 1.13 requires kotlinc ≥ 2.2. The `org.jetbrains.compose` Gradle plugin and Compose UI are not needed. Transitive dependencies are only collection, runtime-annotation, atomicfu and coroutines.
- Mosaic declares the same dependency: `api "androidx.compose.runtime:runtime:1.12.1"` with `plugin.compose` on Kotlin 2.3.21, targeting jvm, linuxX64, linuxArm64, macosArm64 and mingwX64. macosX64 has been dropped by the runtime.
- On Native the host must provide:
  - its own `MonotonicFrameClock`, for example a `BroadcastFrameClock` fed from Raven's loop (the default clock is deprecated and is just a `yield()`)
  - its own dispatcher on the Raven thread, because `Dispatchers.Main` is missing on Linux/Native
  - a snapshot apply-notification pump (`registerGlobalWriteObserver` → `sendApplyNotifications`), like Mosaic's `GlobalSnapshotManager`
  - nothing for the memory model: the new memory manager imposes no constraints
- `SnapshotContextElement` throws on non-JVM targets.

Findings: branch `research/compose-runtime-on-kotlin-native`, file `compose/docs/research/compose-runtime-on-kotlin-native.md`
