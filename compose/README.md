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

`:compose-raven-native:checkRavenLinkerOpts`, part of every build, fails when the linker options in
`compose-raven-native/src/nativeInterop/cinterop/raven.def` drift from `RAVEN_LIBRARIES` in Raven's
`CMakeLists.txt`.

## Running the samples

Each sample folder has a launch script that builds the sample for this machine and runs it, from
any directory, passing its arguments on:

```sh
samples/hello/launch-hello
samples/notepad/launch-notepad README.md
```

The scripts are `launch-hello`, `launch-components`, `launch-notepad`, `launch-overlay`,
`launch-themes`, `launch-animations`, `launch-glass` and `launch-xml-translator`; they share
`samples/launch-sample`, which picks the target (`macosArm64` on an Apple silicon Mac, `linuxX64`
on Linux), links the release executable with Gradle and starts it.

Each sample is an executable. Kotlin/Native's run tasks also build and start it:

```sh
./gradlew :samples:hello:runReleaseExecutableMacosArm64   # macOS (Apple silicon)
./gradlew :samples:hello:runReleaseExecutableLinuxX64     # Linux (x64)
```

The linked program is `samples/hello/build/bin/<target>/releaseExecutable/hello.kexe`, which can
be started directly. `runDebugExecutable...` and `debugExecutable/` are the debug build.

`samples/components`, the port of Raven's `examples/components` (cards of your own, added from
code), runs the same way: `./gradlew :samples:components:runReleaseExecutableMacosArm64`, or
`samples/components/build/bin/<target>/releaseExecutable/components.kexe`.

`samples/notepad` runs the same way (`:samples:notepad:runReleaseExecutable...`). Its linked
program opens a file given on its command line:

```sh
samples/notepad/build/bin/macosArm64/releaseExecutable/notepad.kexe README.md
```

`samples/overlay` (`./gradlew :samples:overlay:runReleaseExecutableMacosArm64`, `overlay.kexe`)
is a borderless window that is seen through, with an opacity slider, a backdrop to choose, and the
four `BlurStyle`s over coloured stripes. What shows through is up to the window system, and the
panel says what it obtained: Windows 10 and 11 blur the desktop, and Mica and Acrylic need Windows
11; X11 needs a compositor (and KWin for the blur), and without one the window is opaque and says
so; on macOS the desktop shows as it is, unblurred. Close it with its Close button.

`samples/themes`, the port of Raven's `examples/themes` (looks, theme files, an accent, a card with
a theme of its own, Markdown), runs the same way: `./gradlew :samples:themes:runReleaseExecutableMacosArm64`,
or `samples/themes/build/bin/<target>/releaseExecutable/themes.kexe`. It reads the theme files and
`notes.md` from Raven's `examples/themes`, whose path the build writes into it; give another folder
as its first argument. Edit those files while it runs and the window follows.

`samples/animations`, the port of Raven's `examples/animations` (transitions played on demand, cards
that pop in, springs that follow the pointer, the island with the speed and reduce-motion
controls), runs the same way: `./gradlew :samples:animations:runReleaseExecutableMacosArm64`, or
`samples/animations/build/bin/<target>/releaseExecutable/animations.kexe`.

`samples/glass`, the port of Raven's `examples/glass` (the glass look, panels of frosted, tinted and
clear glass floating over a page that scrolls under them, a player whose time moves), runs the same
way: `./gradlew :samples:glass:runReleaseExecutableMacosArm64`, or
`samples/glass/build/bin/<target>/releaseExecutable/glass.kexe`; `glass.kexe light` starts with the
light glass.

Running an app (not the tests) needs a Vulkan driver:

- **macOS**: MoltenVK, from `brew install molten-vk` or the Vulkan SDK. Raven loads it by name,
  and compose-raven apps carry `/opt/homebrew/lib` in their rpath, so Homebrew's copy is found
  with no `DYLD_*` variable.
- **Linux**: the Vulkan loader and a driver for the GPU (`libvulkan1` and Mesa on Debian and
  Ubuntu).
