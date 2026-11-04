# Compose runtime on Kotlin/Native (macosArm64, linuxX64)

Research for ticket [0002](../issues/0002-compose-runtime-on-kotlin-native.md), parent spec 0001 (compose-raven).
Checked 2026-11-03 against Maven metadata, the published klibs and sources, and upstream repos.

## TL;DR

- **Depend on `androidx.compose.runtime:runtime`** (Google Maven), not `org.jetbrains.compose.runtime:runtime`.
  Since 1.9.0 the multiplatform runtime (desktop, iOS, native) has been upstreamed into AndroidX and is published to Google Maven. The JetBrains coordinates still exist, but on native they are empty shells that redirect to the AndroidX artifact.
- **Current versions:** AndroidX `1.12.1` is the latest stable release (`1.13.0-beta02` is in pre-release). JetBrains `1.12.2` is the latest stable release (`1.13.0-beta01` is in pre-release), and it depends on AndroidX `1.12.1`.
- **Both v1 targets are published:** `runtime-macosarm64` and `runtime-linuxx64` (also linuxArm64, mingwX64, the iOS/tvOS/watchOS targets, jvm/desktop, js and wasmJs). macosX64 has been dropped.
- **Kotlin and compiler plugin:** apply `org.jetbrains.kotlin.plugin.compose` at the same version as Kotlin. The 1.12.1 klibs are built with Kotlin 2.3.20 and need kotlin-stdlib ≥ 2.1.20. From 1.13 the libraries require kotlinc 2.2. Recommendation: use the current stable Kotlin, 2.4.21. Mosaic uses 2.3.21.
- **Native caveats.** None of these block us, but the host has to provide each one:
  - No usable default `MonotonicFrameClock`. Supply a `BroadcastFrameClock` driven by the Raven loop.
  - No `Dispatchers.Main` on Linux/Native. Supply our own dispatcher bound to the Raven UI thread.
  - Snapshot apply notifications must be wired up by us (a `GlobalSnapshotManager` pattern).
  - `SnapshotContextElement` throws on non-JVM targets.
  - The memory model is not an issue.
- **Mosaic** has the same shape as our project (a KMP library, no Compose UI, native targets including macosArm64 and linuxX64). It declares `api "androidx.compose.runtime:runtime:1.12.1"` and applies `org.jetbrains.kotlin.plugin.compose`.

## 1. Which artifact

### AndroidX is now the home of the multiplatform runtime

The AndroidX Compose Runtime release notes for 1.9.0 (2025-08-13) say:

> "`androidx.compose.runtime:runtime` will now support additional platforms. Support for desktop, iOS and native targets has been upstreamed from JetBrains' Compose Multiplatform project, and will be released through Google Maven moving forward. No other Compose artifacts are affected."

Source: <https://developer.android.com/jetpack/androidx/releases/compose-runtime>

The Gradle module metadata for `androidx.compose.runtime:runtime:1.12.1` has `available-at` variants for `runtime-linuxx64` and `runtime-macosarm64`, among others.
Source: <https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime/1.12.1/runtime-1.12.1.module>

### The JetBrains coordinates are empty shells on native

- `org.jetbrains.compose.runtime:runtime:1.12.2` has variants for macos_arm64 and linux_x64. These point to `runtime-macosarm64` and `runtime-linuxx64`.
  Source: <https://repo1.maven.org/maven2/org/jetbrains/compose/runtime/runtime/1.12.2/runtime-1.12.2.module>
- `runtime-macosarm64-1.12.2.module` lists only two dependencies: `androidx.compose.runtime:runtime` (requires `1.12.1`) and `kotlin-stdlib` (requires `2.2.20`). Its klib is 7,703 bytes, so it holds no runtime code.
  Source: <https://repo1.maven.org/maven2/org/jetbrains/compose/runtime/runtime-macosarm64/1.12.2/runtime-macosarm64-1.12.2.module>
- Mosaic's changelog (Unreleased) says: "Use the AndroidX versions of the Compose runtime and lifecycle dependencies which are now fully multiplatform. Dependency constraints to the corresponding JetBrains artifacts (which are now empty) are present to prevent symbol duplication."
  Source: <https://github.com/JakeWharton/mosaic/blob/trunk/CHANGELOG.md>

Conclusion: depend on `androidx.compose.runtime:runtime` directly. This removes the JetBrains indirection and its stdlib 2.2.20 floor. It also needs only the `google()` repository, plus `mavenCentral()` for coroutines, atomicfu and the stdlib.

### Versions (from maven-metadata.xml)

| Artifact | Latest stable | Latest pre-release |
|---|---|---|
| `androidx.compose.runtime:runtime` | 1.12.1 | 1.13.0-beta02 |
| `org.jetbrains.compose.runtime:runtime` | 1.12.2 | 1.13.0-beta01 |

Sources:
- <https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime/maven-metadata.xml>
- <https://repo1.maven.org/maven2/org/jetbrains/compose/runtime/runtime/maven-metadata.xml>

### Transitive dependencies on native (1.12.1, macosArm64 and linuxX64 identical)

From `runtime-macosarm64-1.12.1.module` and `runtime-linuxx64-1.12.1.module`:

- `androidx.collection:collection` ≥ 1.5.0
- `androidx.compose.runtime:runtime-annotation` 1.12.1
- `org.jetbrains.kotlin:kotlin-stdlib` ≥ 2.1.20
- `org.jetbrains.kotlinx:atomicfu` ≥ 0.28.0
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` ≥ 1.9.0

This is slim, with no UI, Skiko or lifecycle dependencies. `runtime-saveable` and `lifecycle-runtime-compose` are optional and are not needed for v1.

Sources:
- <https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-macosarm64/1.12.1/runtime-macosarm64-1.12.1.module>
- <https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-linuxx64/1.12.1/runtime-linuxx64-1.12.1.module>

## 2. Kotlin version and compiler plugin

- Since Kotlin 2.0.0 the Compose compiler lives in the Kotlin repository. It "ships simultaneously with Kotlin" and "will always be compatible with Kotlin of the same version". Apply the plugin `org.jetbrains.kotlin.plugin.compose` with `version.ref = "kotlin"` in each module that uses Compose. The docs' catalog example uses `kotlin = "2.4.21"`.
  Source: <https://kotlinlang.org/docs/compose-compiler-migration-guide.html>
- Latest Kotlin is 2.4.21 stable and 2.5.0-Beta2 pre-release.
  Source: <https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml>
- The klib manifest of `runtime-macosarm64-1.12.1.klib` contains `compiler_version=2.3.20`, `abi_version=2.3.0` and `metadata_version=1.4.1`. Consuming it with Kotlin 2.3.20 or newer is safe. Older compilers are not guaranteed to read a newer klib ABI.
- The release notes require KGP ≥ 2.0.0 for libraries built with Kotlin 2.0. The 1.13.0-beta01 notes (2026-10-07) say: "Libraries now target Kotlin 2.2 and require kotlinc 2.2".
  Source: <https://developer.android.com/jetpack/androidx/releases/compose-runtime>
- **We do not need the JetBrains Compose Gradle plugin** (`org.jetbrains.compose`). Mosaic shows that `kotlin-multiplatform` plus `plugin.compose` is enough.

Minimal setup:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}
repositories { google(); mavenCentral() }
kotlin {
    macosArm64(); linuxX64()
    sourceSets.commonMain.dependencies {
        api("androidx.compose.runtime:runtime:1.12.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    }
}
```

## 3. Mosaic as reference

- `gradle/libs.versions.toml` has `kotlin = "2.3.21"` and `kotlinx-coroutines = "1.11.0"`. It defines `kotlin-plugin-compose = org.jetbrains.kotlin:compose-compiler-gradle-plugin` at the Kotlin version, and `androidx-compose-runtime = "androidx.compose.runtime:runtime:1.12.1"`.
  Source: <https://github.com/JakeWharton/mosaic/blob/trunk/gradle/libs.versions.toml>
- `mosaic-runtime/build.gradle` applies `org.jetbrains.kotlin.multiplatform` and `org.jetbrains.kotlin.plugin.compose`. It declares `api libs.androidx.compose.runtime` and `api libs.kotlinx.coroutines.core` in `commonMain`. The `kotlinx.cinterop.ExperimentalForeignApi` opt-in is enabled.
  Source: <https://github.com/JakeWharton/mosaic/blob/trunk/mosaic-runtime/build.gradle>
- Targets in `addAllTargets.gradle`: `jvm`, `linuxArm64`, `linuxX64`, `macosArm64`, `mingwX64`. The changelog notes that macosX64 was removed "since support has been removed from the Compose runtime".
  Source: <https://github.com/JakeWharton/mosaic/blob/trunk/addAllTargets.gradle>
- The latest Mosaic release on Maven Central is 0.18.0 (2025-08-21). The AndroidX switch is in trunk and unreleased.
  Source: <https://repo1.maven.org/maven2/com/jakewharton/mosaic/mosaic-runtime/maven-metadata.xml>

## 4. Known limitations on Native

All findings below come from `runtime-macosarm64-1.12.1-sources.jar` (`nativeMain` / `nonJvmMain`) unless another source is given.
Source: <https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-macosarm64/1.12.1/runtime-macosarm64-1.12.1-sources.jar>

### MonotonicFrameClock

The native `DefaultMonotonicFrameClock` is `@Deprecated("MonotonicFrameClocks are not globally applicable across platforms. Use an appropriate local clock.")`. Its implementation is `yield()` followed by `onFrame(elapsed)`, which is not tied to any display.

The host must put a `MonotonicFrameClock` in the `Recomposer`'s coroutine context. Mosaic does this: it creates a `BroadcastFrameClock()`, adds it to `coroutineContext`, and runs `clock.sendFrame(nanoTime())` in a loop with `delay(1)`.
Source: <https://github.com/JakeWharton/mosaic/blob/trunk/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/mosaic.kt>

For compose-raven, `sendFrame` should be called from Raven's frame/idle callback.

### Coroutines and dispatchers

- On Linux/Native (`nativeOther`), `Dispatchers.Main` is `MissingMainDispatcher`, which throws `TODO("Dispatchers.Main is missing on the current platform")`. `Dispatchers.Default` is a fixed thread pool.
  Source: <https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/nativeOther/src/Dispatchers.kt>
- On macOS (`nativeDarwin`), `Dispatchers.Main` dispatches to `dispatch_get_main_queue()`. That queue is only serviced if the main thread runs a CFRunLoop or dispatch main.
  Source: <https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/nativeDarwin/src/Dispatchers.kt>
- Implication: compose-raven needs its own `CoroutineDispatcher` that queues work onto the thread running Raven's event loop. This is required because Raven's C widgets are presumably single-threaded. Both targets then behave the same way. Do not rely on `Dispatchers.Main`.

### Snapshot system

- Nothing on native sends global snapshot apply notifications automatically. Compose UI does this internally; a runtime-only host must do it itself. Mosaic's `GlobalSnapshotManager` uses `Snapshot.registerGlobalWriteObserver` to `trySend` on a conflated channel, and a consumer coroutine calls `Snapshot.sendApplyNotifications()`. We should copy this pattern.
  Source: <https://github.com/JakeWharton/mosaic/blob/trunk/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/mosaic.kt>
- `SnapshotContextElementImpl` (nonJvm) throws `error("provide SnapshotContextElementImpl when coroutines lib has necessary APIs")`. `Snapshot.asContextElement()` is therefore unusable on Native. This is minor.
- Locks are `kotlinx.atomicfu.locks.SynchronizedObject`. Thread identity is a `@ThreadLocal` counter. The runtime is thread-safe in the same way it is on the JVM.

### Memory model

- The runtime relies on the new Kotlin/Native memory manager, which has been the default since Kotlin 1.7.20. The legacy manager was removed in 1.9.20. With the new manager there are no freezing or shared-mutability restrictions, so nothing extra is needed for Kotlin 2.x. On native, `WeakReference` is backed by `kotlin.native.ref.WeakReference`.
  Source: <https://kotlinlang.org/docs/native-migration-guide.html> ("enabled by default since Kotlin 1.7.20"; "completely removed in Kotlin 1.9.20")
- Cross-boundary concern, not a runtime limitation: Raven widget handles held by Compose nodes must be destroyed explicitly. Do this in the `Applier`'s `remove`/`clear` or in `onRelease`, not with GC finalizers.

### Other

- macosX64 is not published. This does not affect our v1 targets.
- `@Composable` lambdas on native go through `ComposableLambda.native.kt`. No special handling is needed.

## 5. Recommendation for the spec

- Dependency: `androidx.compose.runtime:runtime:1.12.1`, from the `google()` repository. Move to 1.13 when it is stable; 1.13 requires Kotlin ≥ 2.2.
- Kotlin 2.4.21 with `org.jetbrains.kotlin.plugin.compose` 2.4.21. No `org.jetbrains.compose` Gradle plugin and no Compose UI.
- The host must provide:
  - a Raven-thread `CoroutineDispatcher`
  - a `BroadcastFrameClock` fed by Raven's loop
  - a `Recomposer` running `runRecomposeAndApplyChanges()`
  - a GlobalSnapshotManager-style apply-notification pump
  - an `AbstractApplier<RavenNode>` over cinterop handles
