# Compose Bindings

Compose runtime sits below UI, Foundation and Material.  Projects like "mosaic" build on Compose runtime
to provide a custom rendering (mosaic offers text in a terminal).

## Compose UI

Today, compose-ui is tightly bound to the Skia rendering engine, and that means the standard "foundation"
components are also tightly bound to Skia

## Raven

See: https://github.com/oscar-soirey/Raven

Today, Raven allows code (in C) or XML to define the UI.  It would be natural to add a "compose-raven"
layer that maps between the core compose runtime and the Raven widgets and rendering, mimicing the 
current compose-ui layer, using mosaic as a template / role-model to follow.

## Building

compose-raven is a standalone Gradle build in this directory. It needs a JDK 17 or newer and CMake,
and it builds only the target of the host it runs on: `macosArm64` on an Apple silicon Mac,
`linuxX64` on Linux (with the X11 development package). Gradle builds libraven itself, with Raven's
CMake build, into `build/raven/<target>`.

```sh
./gradlew build        # libraven, the modules and the samples
./gradlew allTests     # the headless tests
```

From the repository root, use `./compose/gradlew -p compose allTests`.

`:raven-native:checkRavenLinkerOpts`, part of every build, fails when the linker options in
`raven-native/src/nativeInterop/cinterop/raven.def` drift from `RAVEN_LIBRARIES` in Raven's
`CMakeLists.txt`.

## Running the samples

Each sample is an executable. Kotlin/Native's run tasks build and start it:

```sh
./gradlew :samples:hello:runReleaseExecutableMacosArm64   # macOS (Apple silicon)
./gradlew :samples:hello:runReleaseExecutableLinuxX64     # Linux (x64)
```

The linked program is `samples/hello/build/bin/<target>/releaseExecutable/hello.kexe`, which can
be started directly. `runDebugExecutable...` and `debugExecutable/` are the debug build.

Running an app (not the tests) needs a Vulkan driver:

- **macOS**: MoltenVK, from `brew install molten-vk` or the Vulkan SDK. Raven loads it by name,
  and compose-raven apps carry `/opt/homebrew/lib` in their rpath, so Homebrew's copy is found
  with no `DYLD_*` variable.
- **Linux**: the Vulkan loader and a driver for the GPU (`libvulkan1` and Mesa on Debian and
  Ubuntu).
