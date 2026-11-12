---
status: closed
---

# CI job for compose

Add a compose job to `.github/workflows/build.yml` on a macOS and a Linux runner, running
`./gradlew -p compose allTests`. Spec: [9. Testing](../../spec.md#9-testing).

**Depends on:** [RavenHost and singleWindowApplication](0044-ravenhost-and-singlewindowapplication.md),
so there are headless tests worth running.

## Deliverables

- [ ] A job matrix of `macos-latest` (arm64) and `ubuntu-latest`, each building only its own target.
- [ ] Gradle and Kotlin/Native caches, and the CMake build directory cached by Raven's sources.
- [ ] The job fails on a leak check, a linker-option drift or a failing test.
- [ ] The Linux job can close [Linux build and libraven](0058-linux-build-and-libraven.md), if that
      ticket is still open when this one runs.

## Resolution

Closed unfinished, to be revisited once more of compose-raven runs. `.github/workflows/compose.yml`
(its own workflow, not a job in upstream's `build.yml`) has the matrix, the Gradle, `~/.konan` and
ccache caches, and fails on a failing test, a leak, a linker-option drift or the xml-translator's
examples check. Both of its runs failed on linuxX64 at link time
([Linux build and libraven](0058-linux-build-and-libraven.md)). The workflow is disabled on GitHub,
as is upstream's Build: `gh workflow enable compose` turns it back on.
