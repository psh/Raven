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
- [Accessibility semantics](archive/0026-accessibility-semantics.md): Raven describes widgets itself, so the only default is `NONE` on internal content nodes; `Modifier.semantics` with Compose's properties plus `accessibleRole`/`accessibleDescription`; an input in a `Row` is automatically labelled by the `Text` after it; `announceForAccessibility`; the host follows the system's settings; tests gain `onNodeWithContentDescription`.
- [Fonts and text styling](archive/0027-fonts-and-text-styling.md): Compose's `FontFamily`/`Font` over Raven font handles and variants, cached per family; `FontWeight` maps to Raven's regular or bold; `TextStyle` with `LocalTextStyle`/`ProvideTextStyle`; `RavenTheme(fontFamily)`; `MarkdownText` over Raven's Markdown labels, with no `AnnotatedString`; style classes stay out; a fuller typography gets its own ticket.
- [Custom drawing and custom layout](archive/0028-custom-drawing-and-layout.md): `Canvas`/`DrawScope` over custom widgets, repainting on state reads; texture `Viewport` and `NativeViewport`; opt-in `compose-raven-vulkan` (`VulkanViewport` on the render thread, Vulkan textures, device sharing) and `compose-raven-opengl` (managed EGL/NSOpenGL context, `OpenGLTexture`) modules; Metal through `NativeViewport` with no module; SPIR-V `ShaderEffect`s; custom `Layout` deferred with a fork plan.
- [Public test module for apps](archive/0029-app-testing-module.md): `compose-raven-test` holds the one harness (foundation's internals behind `@InternalComposeRavenApi`); Compose-style finders, matchers, actions and assertions over Raven's real input; `mainClock`; leak checks on by default; one window, no screenshots.
- [XML translator as a tool](archive/0030-translator-as-a-tool.md): the `compose-raven-xml` library (parse adapter, private cinterop, `translate()`) and the `raven-xml2compose` command-line tool on headless Raven; one Kotlin file per XML file, `<Include>` becomes a call, cycles are errors; generated-file headers guard against overwriting; a Gradle task goes to the fog.
- [Packaging and publishing](archive/0031-packaging-and-publishing.md): the library modules go to Maven Central under `io.github.psh` at one shared version with its own semantic versioning (recording the bundled Raven), with a `compose-raven-bom`; each target publishes from its own host, triggered by a tag in CI; the translator tool ships as release binaries; MoltenVK stays a documented requirement, and app bundling goes to the fog.
- [Windows (mingwX64) target](archive/0032-windows-target.md): libraven built with Raven's `mingw` preset under MSYS2 (linking with Kotlin/Native's toolchain verified first); Windows linker options checked against CMake; WGL for the OpenGL module, `HWND` for native viewports; a third CI and publishing job; three targets from phase 3.
- [Lists and trees](archive/0033-lists-and-trees.md): Compose's `LazyColumn`/`LazyRow` for composable rows (composing every item in phase 3, keyed); `ListView` and `TreeView` as data-driven composables over Raven's virtualised text widgets, with controlled selection; tree expansion stays Raven's; TileView goes to the fog.
- [Tabs, Switcher and grids](archive/0034-tabs-switcher-and-grids.md): `TabView` with `Tab` pages, controlled selection and closing; no Switcher (Compose's `when`); `LazyVerticalGrid` with fixed columns over UniformGrid; a compose-raven `Grid` with `Auto`/`Fill` tracks and `Modifier.cell`.
- [Image and resources](archive/0035-image-and-resources.md): relative paths resolve against a resource root next to the executable, and `registerResource` embeds files Raven finds first; Compose's `ImageBitmap` replaces `Texture`; `Image` with `painterResource`, `BitmapPainter`, `ContentScale` and tint; textures freed by a `Cleaner`; icons on menus, tabs, trees and windows; no vectors.
- [Island and appear transitions](archive/0036-island-and-appear-transitions.md): `Modifier.appearTransition` over Raven's enter transitions; `AnimatedVisibility` maps enter specs onto them and supports only `fadeOut` exits (opacity); `animation-core` used if it resolves without Compose UI; a controlled `Island`, with `Window(island = ...)` for the floating one.
- [DockArea and FileBrowser](archive/0037-docking-and-file-browser.md): `DockArea` with named `Panel`s; Raven owns the arrangement (`initialDock`, saved layouts) and the Applier never indexes its children; open and close are controlled; torn-off panels stay in their window's composition inside Raven's floating windows; `FileBrowser` with Raven's own navigation.
- [Toolbar](archive/0038-toolbar.md): a compose-raven `Toolbar` over an HBox with `R_ROLE_TOOLBAR`, Raven editor's padding and spacing, vertically centred children and `ToolbarSeparator`; combined with menus, header bars and glass surfaces by plain layout.
- [Navigation 3 and scenes](archive/0039-navigation3-and-scenes.md): an opt-in `compose-raven-navigation3` over `navigation3-runtime`, with the Scene layer adapted from AndroidX 1.2.1 (Apache-2.0) under its names; `NavDisplay` with Raven appear transitions; dialog destinations render as `Dialog` and dismiss through `onBack`; back from the mouse Back button and Cmd+[ / Alt+Left; `WindowSceneStrategy` and ViewModels go to the fog.

## Not yet specified

- **Specialist widgets**: Timeline, NodeGraph, ColorPicker, WebView and BlockEditor, for `studio`
  and `browser`. Each is large and driven by callbacks, and they wait until the list, custom
  drawing and input tickets have settled the common patterns.

- **Right-to-left layout**: Raven supports it (`Rset_layout_direction`, `Rwidget_set_direction`).
  Expose Compose's `LayoutDirection` / `LocalLayoutDirection`, make `Alignment.Start`/`End` and
  shapes direction-aware, and amend the spec's "no RTL" lines.
- **Custom `Layout { }`**: needs a fork change (custom widgets that take children, a measure
  callback given the available size, an arrange callback, `Rwidget_measure_child` /
  `Rwidget_arrange_child`). It waits until a sample needs it.
- **Graphics extras**: a `MetalViewport` convenience for macOS, zero-copy GL textures through
  `GL_EXT_memory_object` on Linux (and `_win32` on Windows), and possibly a Vulkan-headers cinterop shipped with
  `compose-raven-vulkan`.
- **Multi-window tests**: `onWindow(title)`, `onDialog()` and tests of `application { }` with
  several windows, beyond `runRavenTest`'s single window.
- **Build-time XML translation**: a Gradle task that runs `raven-xml2compose` for an app's XML
  during the build (Gradle's JVM can't load the native library directly).
- **App bundling**: shipping apps: a macOS `.app` with MoltenVK in `Contents/Frameworks` (likely
  needing a fork change so Raven also tries `@executable_path/../Frameworks/libMoltenVK.dylib`), and
  Linux packages, like Compose Desktop's packaging tasks.
- **Lazy virtualisation**: make `LazyColumn`/`LazyRow` compose only the items in view, from the
  ScrollBox offset and measured row sizes.
- **More list widgets**: TileView (tiles from a callback, multi-select), and controlled TreeView
  expansion, which needs a fork change adding an expand or collapse callback.
- **Generated resources**: a Gradle task that embeds a resources folder in the binary and registers
  it at start-up, and Compose's generated `Res.drawable.foo` accessors.
- **Vector icons**: SVG or `ImageVector`, which Raven can't draw today (rasterise at build time, or
  a Raven vector primitive).
- **Exit transitions**: Raven exit animations (fly, scale, slide out) for `AnimatedVisibility`,
  which need a fork change.
- **Torn-off panels as Windows**: making DockArea's floating windows real compose-raven `Window`s,
  with their own window-level settings.
- **More navigation**: a `WindowSceneStrategy` that opens entries as separate `Window`s (closing
  calls `onBack`), list-detail scenes, and the ViewModel entry decorator
  (`lifecycle-viewmodel-navigation3`).
- **Gestures**: Compose's coroutine `pointerInput { awaitPointerEventScope { } }`, drag gestures
  (`draggable`, `detectDragGestures`) and long press, beyond `onPointerEvent`.
- **Popups**: Compose's `Popup { }` needs a generic popup in Raven, which only has menu and
  tooltip popups today.

## Out of scope

- **A Metal backend for Raven**: Raven renders through Vulkan (MoltenVK on macOS), and Metal
  content goes through `NativeViewport` ([Custom drawing and custom layout](archive/0028-custom-drawing-and-layout.md)).
- **JVM / the `raven-gui` Java binding**: this effort is Kotlin/Native only.
- **Source compatibility with Compose Foundation / Material**: names and shapes follow Compose,
  but code written against androidx foundation or material isn't guaranteed to compile.
- **Mixing Raven XML documents with composables at run time**: composables build the whole tree for this pass. (Translating XML into Compose *source* is a separate question and in scope: [XML to Compose translator sample](archive/0013-xml-to-compose-translator.md).)
- **A Material-like module above foundation**: Raven's looks already imitate each platform, and a
  Material layer would compete with them ([Theming](archive/0021-theming.md)).
- **Cross-compilation**: each target builds on its own host, since libraven's C build needs the
  host's toolchain and headers (see [Kotlin/Native targets and build](archive/0005-kotlin-native-targets-and-build.md)).
