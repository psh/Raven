---
status: open
wayfinder: map
---

# compose-raven phase 3 spec

## Destination

`compose/docs/spec.md` extended past phases 1 and 2. Every area left as fog by
[compose-raven spec](0001-compose-raven-spec.md) is either decided and written into the spec, or
ruled out of scope, so that phase 3 of the sample ports (`editor`, `workspace`, `music`,
`animations`, `glass`) and the examples waiting on fog can be ported. This map does not do the
implementation.

## Notes

- Domain: the Compose runtime (Applier, nodes, Recomposer, frame clock) over Raven's retained
  widget tree. Vocabulary is in `compose/GLOSSARY.md`; keep it current as terms resolve.
- Shape: like a native-widgets binding. Each composable node owns a Raven widget, Raven does
  layout and painting, and Compose only manages the tree. Mosaic is the template / role model.
- Platform: Kotlin/Native with cinterop. Phase 1 targets are macosArm64 and linuxX64.
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

- [Multiple windows, dialogs and popups](archive/0024-multiple-windows.md): Compose Desktop's `application { Window { } }` with controlled closing, `WindowState` and per-window lifecycle and saved-state owners; `ravenApplication` is renamed `singleWindowApplication`; `Dialog` is a Raven sheet with a new dismiss callback in the fork (change 4); blocking message and file dialogs become suspend functions (verify nested frames first); `Modifier.tooltip`; `Popup` goes back to the fog; Navigation 3 scenes get their own ticket.
- [Generic input and focus](archive/0025-generic-input-and-focus.md): one Raven event callback per node, dispatching to the input modifiers in chain order; `clickable`/`combinedClickable`, Compose Desktop's `onPointerEvent` and `pointerHoverIcon`; `onKeyEvent` and `Window(onKeyEvent)` with no preview handlers; `FocusRequester`/`focusable`/`onFocusChanged`; `ContextMenuArea` over `Rmenu_open`; gestures go to the fog.

## Not yet specified

- **Specialist widgets**: Timeline, NodeGraph, ColorPicker, WebView and BlockEditor, for `studio`
  and `browser`. Each is large and driven by callbacks, and they wait until the list, custom
  drawing and input tickets have settled the common patterns.

- **Gestures**: Compose's coroutine `pointerInput { awaitPointerEventScope { } }`, drag gestures
  (`draggable`, `detectDragGestures`) and long press, beyond `onPointerEvent`.
- **Popups**: Compose's `Popup { }` needs a generic popup in Raven, which only has menu and
  tooltip popups today.

## Out of scope

- **JVM / the `raven-gui` Java binding**: this effort is Kotlin/Native only.
- **Source compatibility with Compose Foundation / Material**: names and shapes follow Compose,
  but code written against androidx foundation or material isn't guaranteed to compile.
- **Mixing Raven XML documents with composables at run time**: composables build the whole tree for this pass. (Translating XML into Compose *source* is a separate question and in scope: [XML to Compose translator sample](archive/0013-xml-to-compose-translator.md).)
- **A Material-like module above foundation**: Raven's looks already imitate each platform, and a
  Material layer would compete with them ([Theming](archive/0021-theming.md)).
- **Cross-compilation**: each target builds on its own host, since libraven's C build needs the
  host's toolchain and headers (see [Kotlin/Native targets and build](archive/0005-kotlin-native-targets-and-build.md)).
