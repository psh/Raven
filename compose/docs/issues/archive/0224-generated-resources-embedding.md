---
status: closed
---

# Generated resources: embedding

Add `ravenResources { embed = true }`: the plugin generates the `registerResource` calls that
build every file of `composeResources/` into the binary, in place of copying them, so an app can
be a single file. Spec: [8: Generated resources](../../spec.md#generated-resources-phase-5). Planned
in [Icons and resources: generated resources](0208-icons-and-resources-generated-resources.md).

**Depends on:** [Generated resources: Res and the plugin](0223-generated-resources-res-and-the-plugin.md).

## Deliverables

- [x] With `embed = true`, generated Kotlin holding each file's bytes and registering them before the application starts; no `resources/` folder copied.
- [x] A measurement of what it costs: the compile time and binary size for `samples/resources`, and a note in the spec if large files need another way (a linker section).
- [x] `samples/resources`' tests also run embedded: the same `Res` code finds the same files with no folder beside the executable.

## Resolution

Built on 2026-11-26.

- **`embed = true`**: `generateResClass` also writes `EmbeddedResources.kt`, every file of the folder
  as Kotlin string literals of 16 KB, a character a byte (printable ASCII as it is, the rest
  `\uXXXX`), decoded and passed to `registerResource` under its path in the object's `init`. `Res`'s
  `drawable` and `font` objects and `readBytes` call `EmbeddedResources.register()` first, so every
  file is registered before the first `Res` lookup, with nothing for the app to call. The copy tasks
  stay registered but skip themselves (`onlyIf`), so the order of `ravenResources { }` and
  `kotlin { }` in a build file doesn't matter. A new `composeResourcesDirectory` property names the
  folder (Compose's custom directory).
- **Found and fixed**: `registerResource` keeps the bytes and gives them to Raven when an application
  starts, so `Res.readBytes` before one read nothing. `readResourceBytes` now reads the registered
  bytes first.
- **`samples/resources-embedded`**: the sample's code, folder and tests compiled with `embed = true`
  and the sample's `Res` package; its own `EmbeddedTest` checks every file is in Raven's memory files
  (`Rfile_is_memory`). Both modules' tests pass (3 and 3 + 1), and the embedded test and release
  executables have no `resources/` beside them.
- **Measured** (release, macOS arm64, a trivial change to the shared `Main.kt` rebuilding each alone):
  copied, 34 s and 5,796,416 bytes; embedded, 34 s and 5,880,776 bytes (+84 KB for 34 KB of files);
  with a random 1 MB file more, about 37 s and 7,996,712 bytes (+2.1 MB, UTF-16 literals). Written in
  the spec's Generated resources section with the linker-section alternative for files of megabytes,
  left until an app needs it.
