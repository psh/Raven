---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Windows (mingwX64) target

## Question

What does adding `mingwX64` as a third target take? Decide how libraven is built on a Windows host (CMake presets, toolchain), the `raven.def` linker options for Windows, the CI runner, and anything in the spec that assumes macOS or Linux.

## Resolution

Facts: Raven already builds with MinGW. `CMakePresets.json` has a `mingw` preset (MinGW-w64 gcc, static), and Raven's CI runs a `windows-mingw` job under MSYS2 MINGW64. On Windows it links `user32 gdi32 shell32 comdlg32 ole32 winhttp` and uses Win32 threads, with no pthread or `dl`. Vulkan comes from `vulkan-1.dll`, which GPU drivers install, so nothing like MoltenVK is needed. Native viewports work (`native_regions = true`). The Compose runtime, Mosaic and `navigation3-runtime` publish `mingw_x64`.

- **Building libraven**: on a Windows host, the Gradle task runs CMake with Raven's `mingw` preset under MSYS2 MINGW64, giving a Release `libraven.a` linked statically. **Verify first** that a gcc-built `libraven.a` links with Kotlin/Native's `mingwX64` toolchain (LLVM plus MinGW sysroot), watching for libgcc and stack-protector symbols. The fallback is to build libraven with Kotlin/Native's bundled clang and sysroot through a CMake toolchain file.
- **`raven.def`**: `linkerOpts.mingw_x64 = -luser32 -lgdi32 -lshell32 -lcomdlg32 -lole32 -lwinhttp`, mirroring `RAVEN_LIBRARIES` in `CMakeLists.txt`. A check in the compose build fails if any OS's linker options drift from the CMake list. The Windows host needs the Vulkan headers only to compile Raven, as Raven's CI does.
- **Optional modules**: `compose-raven-vulkan` works as is. `compose-raven-opengl` creates a **WGL** context on the native child window, as `viewport_opengl` does. Zero-copy `OpenGLTexture` through `GL_EXT_memory_object_win32` joins the graphics extras in the fog. `NativeViewport` hands over an `HWND`. Metal does not apply.
- **CI and publishing**: a third compose job on `windows-latest` with MSYS2 runs `allTests` headless. The same `compose-vX.Y.Z` tag starts all three jobs, and the Windows job publishes the `mingwx64` artifacts. `raven-xml2compose` gains a `.exe` release binary.
- **Spec**: three targets from phase 3, and each still builds on its own host. The main-thread rule is AppKit's: on Windows, the thread that calls `application` becomes the UI thread, though the main thread is still recommended. File dialogs use Explorer's through Raven.
