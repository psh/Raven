---
status: open
wayfinder: map
---

# compose-raven wave 3 spec

## Destination

`compose/docs/spec.md` extended past v1 and wave 2. Every area left as fog by
[compose-raven spec](0001-compose-raven-spec.md) is either decided and written into the spec, or
ruled out of scope, so that wave 3 of the sample ports (`editor`, `workspace`, `music`,
`animations`) and the examples waiting on fog can be ported. This map does not do the
implementation.

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
- Standing preference: **keep it slim.** Lean towards the smallest design that works and push
  extras to fog or out of scope.
- Standing preference: **favour Compose conventions and ergonomics.** Names, parameter shapes,
  defaults and behaviour follow Compose. Wherever Raven differs, compose-raven adapts to Compose
  and the spec documents the difference clearly. Grilling recommendations default to this.
- The decisions of the previous map, [compose-raven spec](0001-compose-raven-spec.md), are settled
  and live in `compose/docs/spec.md`. Build on them, don't reopen them.
- Closing a ticket also updates `compose/docs/spec.md` (its section, and the Differences from
  Compose table where relevant) and `compose/docs/roadmap.md` (which milestone it serves, and
  the open tickets and fog).
- Ticket conventions: `ISSUES-FORMAT.md` in this directory.

## Decisions so far

<!-- one line per closed ticket: [title](file): gist -->

## Not yet specified

- **Specialist widgets**: Timeline, NodeGraph, ColorPicker, WebView and BlockEditor, for `studio`
  and `browser`. Each is large and driven by callbacks, and they wait until the list, custom
  drawing and input tickets have settled the common patterns.

## Out of scope

- **JVM / the `raven-gui` Java binding**: this effort is Kotlin/Native only.
- **Source compatibility with Compose Foundation / Material**: names and shapes follow Compose,
  but code written against androidx foundation or material isn't guaranteed to compile.
- **Mixing Raven XML documents with composables at run time**: composables build the whole tree for this pass. (Translating XML into Compose *source* is a separate question and in scope: [XML to Compose translator sample](0013-xml-to-compose-translator.md).)
- **A Material-like module above foundation**: Raven's looks already imitate each platform, and a
  Material layer would compete with them ([Theming](0021-theming.md)).
- **Cross-compilation**: each target builds on its own host, since libraven's C build needs the
  host's toolchain and headers (see [Kotlin/Native targets and build](0005-kotlin-native-targets-and-build.md)).
