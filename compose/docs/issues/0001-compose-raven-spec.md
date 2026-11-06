---
status: open
wayfinder: map
---

# compose-raven spec

## Destination

An implementation-ready spec for compose-raven, at `compose/docs/spec.md`. compose-raven is a
Kotlin/Native library that drives Raven's retained widgets from the Compose runtime, through
cinterop against `Raven.h`. Every design decision is locked and written down, ready to hand to
implementation. This map does not do the implementation.

## Notes

- Domain: the Compose runtime (Applier, nodes, Recomposer, frame clock) over Raven's retained
  widget tree. Vocabulary is in `compose/GLOSSARY.md`; keep it current as terms resolve.
- Shape: like a native-widgets binding. Each composable node owns a Raven widget, Raven does
  layout and painting, and Compose only manages the tree. Mosaic is the template / role model.
- Platform: Kotlin/Native with cinterop. v1 targets are macosArm64 and linuxX64.
- Required reading for Raven questions: `docs/ARCHITECTURE.md` (layers, handles, threads) and
  `include/raven/`.
- Skills: grilling tickets call `grilling` + `domain-modeling`; call `codebase-design` for the
  layering and Applier/node-model tickets.
- Standing preference: **keep it slim.** This is a first pass, so lean towards the smallest spec
  that works and push extras to fog or out of scope.
- Ticket conventions: `ISSUES-FORMAT.md` in this directory.

## Decisions so far

<!-- one line per closed ticket: [title](file): gist -->
- [How Mosaic is built](0003-how-mosaic-is-built.md): copy its thin bottom-up Applier, `ComposeNode`/`Updater` setters, Recomposer wiring and host-ticked frame clock; drop its layout/draw/input machinery (Raven owns those) and add a UI-thread dispatcher plus clock ticks from Raven's frame loop.
- [Compose runtime on Kotlin/Native](0002-compose-runtime-on-kotlin-native.md): depend on `androidx.compose.runtime:runtime:1.12.1` from Google Maven (the JetBrains coordinates are empty redirects to it), built with Kotlin 2.4.21 and `org.jetbrains.kotlin.plugin.compose`; no Compose UI needed. The host supplies the frame clock, a Raven-thread dispatcher and a snapshot apply pump.
- [Raven widget tree API for an Applier](0004-raven-widget-tree-api.md): create/insert-at-index/remove/destroy, per-child slots and `(Rid, ..., void*)` callbacks cover an Applier; calls are UI-thread-only, `Rtask_post` is the dispatcher and `Rset_frame_callback` + `Rwake_up` the frame clock. Gaps: no in-place move, single-child parents destroy on a second insert, one global frame callback, no custom arrange.
- [Kotlin/Native targets and build](0005-kotlin-native-targets-and-build.md): standalone Gradle build in `compose/` (`compose-raven` + `sample`); Gradle drives CMake for a Release `libraven.a` linked statically through one `raven.def` over `Raven.h`; each target on its own host; MoltenVK is run-time only.

## Not yet specified

- **Custom drawing**: an escape hatch into Raven viewport draw callbacks for content Raven's
  widgets can't express.
- **Theming and styles**: how Raven themes and styles show up in composables, if at all, beyond
  the defaults.
- **Multiple windows, dialogs, popups**: whether a composition owns one window or many, and how
  transient Raven windows map onto composables.
- **Packaging and publishing**: how compose-raven and the native Raven library ship (klib plus
  bundled static lib?), and whether MoltenVK is bundled with apps or left to the user to install.
- **Windows (mingwX64)**: adding the third target after v1.
- **Spec assembly**: the structure of `spec.md` itself, once enough decisions exist to fill it.

## Out of scope

- **JVM / the `raven-gui` Java binding**: this effort is Kotlin/Native only.
- **Source compatibility with Material / Foundation**: the component vocabulary is Raven-native.
- **Mixing Raven XML documents with composables**: composables build the whole tree for this pass.
- **Cross-compilation**: each target builds on its own host, since libraven's C build needs the
  host's toolchain and headers (see [Kotlin/Native targets and build](0005-kotlin-native-targets-and-build.md)).
