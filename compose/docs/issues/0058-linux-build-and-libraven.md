---
status: open
---

# Linux build and libraven

Make the build from [Gradle build and libraven](archive/0041-gradle-build-and-libraven.md) work on
the `linuxX64` target: build `libraven.a`, link it through `raven.def`, and pass the headless
tests on a Linux (x64) host. That ticket was done and checked on macOS only. Spec:
[2. Modules and build](../spec.md#2-modules-and-build).

**Depends on:** [Gradle build and libraven](archive/0041-gradle-build-and-libraven.md) (closed).

## Deliverables

- [ ] `./gradlew build` and `allTests` pass on Linux (x64) with the X11 development package
      installed, and the smoke test leaves zero blocks held.
- [ ] libX11 is found at link time. Kotlin/Native links against its own sysroot, so
      `linkerOpts.linux` adds `-L/usr/lib/x86_64-linux-gnu -L/usr/lib64`. Confirm that, or replace
      it with something less tied to one distribution's layout.
- [ ] `libraven.a` links against Kotlin/Native's old glibc sysroot. A library built with the
      system's gcc may need newer glibc symbols (`__isoc23_*`, fortified `*_chk`), and then
      libraven is built with Kotlin/Native's own clang and sysroot instead (a CMake toolchain file
      set by `configureRaven`).
- [ ] `:raven-native:checkRavenLinkerOpts` passes on Linux: CMake's `X11_X11_LIB`, `m`, `dl` and
      `Threads::Threads` map onto `-lX11 -ldl -lpthread -lm`.
- [ ] Whatever the Linux build needs (packages, the toolchain choice) goes in `compose/README.md`'s
      Building section.

Verify on a Linux machine, a container, or through [CI job for compose](0052-ci-job-for-compose.md),
whichever comes first.
