---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Packaging and publishing

## Question

How do compose-raven's modules and the native Raven library ship? Decide the published artifacts (klibs with `libraven.a` bundled through cinterop, coordinates, a Maven repository), versioning against Raven's own version, and whether MoltenVK is bundled with macOS apps or left to the user to install.

## Resolution

Facts: Raven's Java binding is on Maven Central as `io.github.oscar-soirey:raven-gui`, signed with gpg and published by hand at Raven's version (0.2.1, from `CMakeLists.txt`), and a published version can't be replaced. compose-raven builds from the `psh/Raven` fork and links `libraven.a` statically. On macOS, Raven `dlopen`s `libvulkan.1.dylib`, `libvulkan.dylib` and then `libMoltenVK.dylib` by bare name (`src/render/vulkan/vk_loader.c`), which doesn't search a `.app` bundle's `Contents/Frameworks`.

- **Coordinates**: group `io.github.psh`, separate from upstream's. Published KMP libraries: `raven-native`, `compose-raven-foundation`, `compose-raven-test`, `compose-raven-vulkan`, `compose-raven-opengl`, `compose-raven-xml`, each with its root module and per-target artifacts (`macosarm64`, `linuxx64`). `raven-xml2compose` ships as per-host binaries on a GitHub release, not on Maven. There is no BOM for now, because all modules share one version.
- **Repository and process**: Maven Central through the Sonatype Central Portal, signed, with `publishToMavenLocal` for development. Each target publishes from its own host: the macOS job publishes the root and metadata modules plus `macosarm64`, and the Linux job publishes `linuxx64`. A git tag such as `compose-v0.1.0` triggers both CI jobs, with the signing key and Central token in CI secrets.
- **Versioning**: compose-raven's own semantic versioning, starting at 0.1.0, with every module at the same version. Each release records the bundled Raven: the fork commit and a fork version such as `0.2.1-psh.3`. Apps never match a Raven version themselves, because `libraven.a` is static.
- **MoltenVK**: still a documented run-time requirement on macOS (`brew install molten-vk` or the Vulkan SDK). Shipping apps (a `.app` with MoltenVK in `Contents/Frameworks`, Linux packages, like Compose Desktop's packaging tasks) goes to the App bundling fog, noting a likely fork change: Raven also trying `@executable_path/../Frameworks/libMoltenVK.dylib`.
