---
status: open
wayfinder: map
---

# compose-raven spec

## Destination

An implementation-ready spec for compose-raven, at `compose/docs/spec.md`. compose-raven is a
family of Kotlin/Native modules that drives Raven's retained widgets from the Compose runtime, through
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
- Standing preference: **favour Compose conventions and ergonomics.** Names, parameter shapes,
  defaults and behaviour follow Compose. Wherever Raven differs, compose-raven adapts to Compose
  and the spec documents the difference clearly. Grilling recommendations default to this.
- Closing a ticket from here on also updates `compose/docs/spec.md` (its section, the Roadmap, and
  the Differences from Compose table where relevant).
- Ticket conventions: `ISSUES-FORMAT.md` in this directory.

## Decisions so far

<!-- one line per closed ticket: [title](file): gist -->
- [How Mosaic is built](0003-how-mosaic-is-built.md): copy its thin bottom-up Applier, `ComposeNode`/`Updater` setters, Recomposer wiring and host-ticked frame clock; drop its layout/draw/input machinery (Raven owns those) and add a UI-thread dispatcher plus clock ticks from Raven's frame loop.
- [Compose runtime on Kotlin/Native](0002-compose-runtime-on-kotlin-native.md): depend on `androidx.compose.runtime:runtime:1.12.1` from Google Maven (the JetBrains coordinates are empty redirects to it), built with Kotlin 2.4.21 and `org.jetbrains.kotlin.plugin.compose`; no Compose UI needed. The host supplies the frame clock, a Raven-thread dispatcher and a snapshot apply pump.
- [Raven widget tree API for an Applier](0004-raven-widget-tree-api.md): create/insert-at-index/remove/destroy, per-child slots and `(Rid, ..., void*)` callbacks cover an Applier; calls are UI-thread-only, `Rtask_post` is the dispatcher and `Rset_frame_callback` + `Rwake_up` the frame clock. Gaps: no in-place move, single-child parents destroy on a second insert, one global frame callback, no custom arrange.
- [Kotlin/Native targets and build](0005-kotlin-native-targets-and-build.md): standalone Gradle build in `compose/` (`compose-raven` + `sample`); Gradle drives CMake for a Release `libraven.a` linked statically through one `raven.def` over `Raven.h`; each target on its own host; MoltenVK is run-time only.
- [Layering over cinterop](0006-cinterop-layering.md): two modules, `raven-native` (public cinterop `raven.cinterop` + small `raven.native` helpers for callbacks, strings, thread check; no Compose) under `compose-raven-foundation` (everything Compose, depends on it with `implementation`, so no Raven type is public); `sample` depends on foundation only.
- [Node model and Applier](0007-node-model-and-applier.md): one internal `RavenNode` per Raven widget, with a mirror child list; remove detaches and `onRelease` destroys; a fixed Overlay root; setters call cinterop directly; moves need a new `Rwidget_move_child` in the fork; single-child parents are handled in the Applier.
- [Frame loop and threading](0008-frame-loop-and-threading.md): a blocking `ravenApplication(...) { }` owns the `Rwait_frame` loop; an internal `Rtask_post` dispatcher, a `BroadcastFrameClock` ticked from the frame callback compose-raven owns, and a global write observer wake it from any thread; a fixed shutdown order; errors quit, then rethrow; `exitApplication()` quits.
- [Layout and Modifiers](0009-layout-and-modifiers.md): an own `Modifier` and `Dp`; `Column`/`Row`/`Box` over VBox/HBox/Overlay with Compose's start/top defaults; padding, size, sizeIn, fillMax, weight and align map onto slots and size setters; Compose's `Arrangement` shape with only `spacedBy` in v1; no `Layout {}` or RTL yet.
- [Events and state](0010-events-and-state.md): callbacks registered once per node with lambdas in fields; controlled inputs as in Compose, checked in the frame callback once the Recomposer settles; `TextField` takes a `String` with a documented caret jump on rewrite; exceptions caught at the C boundary; widget-specific events only in v1.
- [v1 component vocabulary](0011-v1-component-vocabulary.md): sized by the XML translator sample; Compose-named `Text`, three Buttons, `Checkbox`/`Switch`/`RadioButton`, single-line `TextField`, `Slider`, progress, dividers, `Spacer`; `VerticalScroll`/`HorizontalScroll` containers; compose-raven's own `CodeEditor` and splitters.
- [Testing strategy](0012-testing-strategy.md): an internal `RavenHost` shared by `ravenApplication` and a headless test harness; Compose-style `runRavenTest` finders and actions over Raven input injection; memory and `StableRef` leak checks after each test; fresh Raven per test (verify first); CI on macOS and Linux.
- [XML to Compose translator sample](0013-xml-to-compose-translator.md): the translator lives in `sample` and parses with Raven's parser through a private cinterop and one adapter into a Kotlin tree; it maps the v1 vocabulary faithfully (explicit `fillMax*`), hoists state, and leaves TODOs plus warnings for anything unsupported; the sample shows XML and generated code side by side, live.
- [Port the Raven samples to Compose](0014-port-raven-samples.md): ports in waves (`hello` in v1; wave 2 is notepad, components, themes, overlay and glass; later waves and fog-bound examples recorded), as a roadmap in the spec; wave-2 tickets don't block assembly; each port is its own module under `compose/samples/`, and `sample` becomes `samples/xml-translator`.
- [Assemble the spec](0015-assemble-the-spec.md): [`spec.md`](../spec.md) written and accepted as v1 complete, in 14 sections stating the final state with links to the deciding tickets; one table of differences from Compose; the map stays open, and each remaining ticket updates the spec when it closes.
- [Templates in the XML translator](0016-xml-translator-templates.md): `<Widget>` declarations become top-level composables (types from defaults, `<Children>` as slot lambdas, layout through `modifier`); the whole `{...}` language is translated except `{Instance}`; `If`/`Repeat`/`ForEach` become `if`/`repeat`/`forEachIndexed`; `Include` stays a TODO; parsing must not register tags globally.
- [Kotlin highlighting in CodeEditor](0017-kotlin-highlighting.md): a built-in Kotlin language in Raven's `syntax.c` (fork change 3), with Prism.js's keyword list, a short list of standard-library types and nested comments; templates, labels and trailing-lambda calls are documented as not highlighted.
- [Menus and the header bar](0018-menus-and-header-bar.md): Compose Desktop's `MenuBar`/`Menu`/`Item`/`CheckboxItem`/`Separator`, but `MenuBar` is placed in the layout; `KeyShortcut` over Raven keys with no automatic Ctrl-to-Cmd; `HeaderBar` plus `ravenApplication(customHeader = true)`; context menus go to the Generic input fog.
- [ComboBox and SpinBox](0019-combobox-and-spinbox.md): `ComboBox` over strings and an index; `SpinBox` with `Double` and `Int` versions, using Raven's defaults; both controlled; `codeEditorLanguages()` lists the editor languages.

## Not yet specified

- **Custom drawing**: an escape hatch into Raven viewport draw callbacks for content Raven's
  widgets can't express. Also custom `Layout {}`, which needs an arrange hook Raven doesn't have yet.
- **Multiple windows, dialogs, popups**: whether a composition owns one window or many, and how
  transient Raven windows map onto composables. v1's `ravenApplication` is one window; this is where it would grow
  into `application { Window { } }`.
- **Generic input and focus**: `Modifier.clickable`, key and pointer modifiers over
  `RWidgetEventCallback`, focus control (`FocusRequester`), and context menus
  (`ContextMenuArea` over `Rmenu_open`), beyond v1's widget-specific callbacks.
- **Wider vocabulary**: lists and trees (virtual, callback-driven), Image, TabView, Grid,
  UniformGrid, TreeView, ListView, Island, Appear transitions, docking, FileBrowser, a `Toolbar { }`
  (a Row with toolbar spacing and padding and `R_ROLE_TOOLBAR`, for `editor`, `workspace` and
  `studio`; until then, a Row of `TextButton`s works), and the specialist
  widgets (Timeline, NodeGraph, WebView, ColorPicker): what wave 3 of [Port the Raven samples to
  Compose](0014-port-raven-samples.md) needs.
- **Accessibility**: semantics for compose-raven, meaning Compose's `Modifier.semantics` /
  `contentDescription` over Raven's `Rwidget_set_accessible_role`, `_name`, `_description`,
  `_hidden` and `_labelled_by`, and which roles composables set by default.
- **App testing**: a public `compose-raven-test` module so apps can test their own UIs, once the
  internal harness has settled.
- **Translator as a tool**: extracting the sample's XML parsing and translator into a shared
  module, a command-line tool or a Gradle task, once the sample proves it useful, including
  following `<Include>` across files.
- **Packaging and publishing**: how compose-raven and the native Raven library ship (klib plus
  bundled static lib?), and whether MoltenVK is bundled with apps or left to the user to install.
- **Windows (mingwX64)**: adding the third target after v1.

## Out of scope

- **JVM / the `raven-gui` Java binding**: this effort is Kotlin/Native only.
- **Source compatibility with Compose Foundation / Material**: names and shapes follow Compose,
  but code written against androidx foundation or material isn't guaranteed to compile.
- **Mixing Raven XML documents with composables at run time**: composables build the whole tree for this pass. (Translating XML into Compose *source* is a separate question and in scope: [XML to Compose translator sample](0013-xml-to-compose-translator.md).)
- **Cross-compilation**: each target builds on its own host, since libraven's C build needs the
  host's toolchain and headers (see [Kotlin/Native targets and build](0005-kotlin-native-targets-and-build.md)).
