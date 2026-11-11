---
status: open
---

# CI job for compose

Add a compose job to `.github/workflows/build.yml` on a macOS and a Linux runner, running
`./gradlew -p compose allTests`. Spec: [9. Testing](../spec.md#9-testing).

**Depends on:** [RavenHost and singleWindowApplication](archive/0044-ravenhost-and-singlewindowapplication.md),
so there are headless tests worth running.

## Deliverables

- [ ] A job matrix of `macos-latest` (arm64) and `ubuntu-latest`, each building only its own target.
- [ ] Gradle and Kotlin/Native caches, and the CMake build directory cached by Raven's sources.
- [ ] The job fails on a leak check, a linker-option drift or a failing test.
- [ ] The Linux job can close [Linux build and libraven](0058-linux-build-and-libraven.md), if that
      ticket is still open when this one runs.
