# compose-raven roadmap

The source of truth for **what gets built, in what order**. [`spec.md`](spec.md) says *what*
compose-raven is, and this file says *when*. Implementation planning starts here: each milestone
links to the spec sections that define it. The open design work is on the wayfinder map
[compose-raven phase 3 spec](issues/0023-compose-raven-phase-3-spec.md), and each of its tickets
updates this file when it closes.

## Milestones at a glance

| Milestone | Delivers | Samples |
|---|---|---|
| [0. Verify first](#0-verify-first) | answers to the open facts the spec depends on | none |
| [1. Raven fork changes](#1-raven-fork-changes) | the changes compose-raven needs in `psh/Raven` | none |
| [2. Phase 1](#2-phase-1) | the library, the host, the phase 1 vocabulary, tests and CI | `hello`, `xml-translator` |
| [3. Phase 2](#3-phase-2) | menus, ComboBox/SpinBox, Surface/FlowRow/Expander, theming, glass | `notepad`, `components`, `themes`, `overlay` |
| [4. Phase 3](#4-phase-3) | still being decided on the phase 3 map | `editor`, `workspace`, `music`, `animations`, `glass` |
| [Later](#later) | waits on fog | `effects`, `viewport_opengl`, `viewport_vulkan`, `studio`, `browser` |

Not ported: `memory` (it demonstrates the C-level allocator), `languages` and `bevy_editor` (they
show the other bindings and Bevy, not Raven UIs).

## 0. Verify first

Facts the spec depends on that nobody has checked against Raven yet. Each needs a small spike, and
its result can change the plan:

| Fact to check | If it fails | Spec |
|---|---|---|
| `Rinit`/`Rshutdown` can be repeated in one process | fix it in the fork (fork change 2) | [11](spec.md#11-required-raven-changes), [9](spec.md#9-testing) |
| `Rxml_document_parse` registers no `<Widget>` declarations globally | `parseRavenXml` unregisters them | [10](spec.md#10-samples) |
| A `Separator` inserted into a Menu acts as a menu separator | needed before the menus of phase 2 | [8](spec.md#8-component-vocabulary) |
| Raven derives hover and pressed shades from an overridden accent or control colour | compose-raven sets lighter and darker shades itself | [8](spec.md#8-component-vocabulary) |
| Compose can recompose safely inside the nested frames that Raven's blocking dialogs run (phase 3) | message and file dialogs are rebuilt on `Dialog` and FileBrowser | [4](spec.md#4-application-host) |
| A MinGW-gcc `libraven.a` links with Kotlin/Native's `mingwX64` toolchain (phase 3) | build libraven with Kotlin/Native's own clang and sysroot | [2](spec.md#2-modules-and-build) |
| Focusing the root clears focus in Raven, for `clearFocus()` (phase 3) | add "focus nothing" to the fork | [7](spec.md#7-events-and-state) |
| A GL context (`NSOpenGLContext`) works on Raven's macOS child view, which carries a Metal layer (phase 3) | macOS gets `OpenGLTexture` only | [8](spec.md#8-component-vocabulary) |
| An app can render Metal into the child view's `CAMetalLayer` while Raven uses MoltenVK in the parent (phase 3) | Metal content goes through a texture | [8](spec.md#8-component-vocabulary) |
| `androidx.compose.animation:animation-core` resolves for our targets without Compose UI (phase 3) | compose-raven provides a small `animate*AsState` set | [8](spec.md#8-component-vocabulary) |
| `rememberNavBackStack` and the saved-state decorator of `navigation3-runtime` work under compose-raven's host (phase 3) | settled in [Navigation 3 and scenes](issues/0039-navigation3-and-scenes.md) | none yet |

## 1. Raven fork changes

Made in the `psh/Raven` fork and offered upstream later ([spec section 11](spec.md#11-required-raven-changes)):

1. `Rwidget_move_child(parent, from, to)`: required by the Applier (milestone 2).
2. A fix for repeated `Rinit`/`Rshutdown`: only if milestone 0 finds it necessary.
3. A built-in Kotlin language in `src/widgets/syntax.c`: needed by the `xml-translator` sample.
4. `Rdialog_set_dismiss_callback`, which can refuse a dismissal: needed by `Dialog` (phase 3).

## 2. Phase 1

| Part | Spec |
|---|---|
| Gradle build: `raven-native`, `compose-raven-foundation`, `samples/*`; libraven through CMake, static linking | [2](spec.md#2-modules-and-build) |
| `raven-native`: `raven.def` and the `raven.native` helpers (callback registry, strings, UI-thread check) | [3](spec.md#3-raven-native) |
| `RavenHost` and `singleWindowApplication`: dispatcher, frame clock, snapshot pump, shutdown, errors | [4](spec.md#4-application-host) |
| `RavenNode` and `RavenApplier`, including the internal content node for single-child widgets | [5](spec.md#5-node-model-and-applier) |
| `Modifier`, `Dp`, `Column`/`Row`/`Box`, `Arrangement` | [6](spec.md#6-layout-and-modifiers) |
| Callbacks and controlled inputs | [7](spec.md#7-events-and-state) |
| The phase 1 vocabulary table | [8](spec.md#8-component-vocabulary) |
| Test harness, leak checks, unit tests, CI on macOS and Linux | [9](spec.md#9-testing) |
| `samples/hello` | [10](spec.md#10-samples) |
| `samples/xml-translator`, including templates and its private cinterop (needs fork change 3) | [10](spec.md#10-samples) |

## 3. Phase 2

Each family is fully decided. The samples port when their families are done:

| Family | Spec | Needed by |
|---|---|---|
| Menus and the header bar (`customHeader`, `KeyShortcut`) | [8: menus](spec.md#phase-2-menus-and-the-header-bar), [4](spec.md#4-application-host) | `notepad` |
| ComboBox, SpinBox, `codeEditorLanguages()` | [8: ComboBox](spec.md#phase-2-combobox-and-spinbox) | `notepad`, `themes`, `overlay` (and `glass` in phase 3) |
| Surface, FlowRow, Expander; `Color`, `Shape`, `BorderStroke` | [8: Surface](spec.md#phase-2-surface-flowrow-and-expander) | `components` |
| Theming: `RavenTheme`, theme files, `XxxDefaults.colors()`, `shape`/`contentPadding`, `Text(color, fontSize)` | [8: theming](spec.md#phase-2-theming) | `themes` |
| Glass: `Modifier.material`, `BackgroundBlur`, `transparent`/`undecorated`, `WindowBackdrop` | [8: glass](spec.md#phase-2-glass-and-backgroundblur) | `overlay` (and `glass` in phase 3) |

## 4. Phase 3

Still being designed on [compose-raven phase 3 spec](issues/0023-compose-raven-phase-3-spec.md).
Each port waits on these open tickets:

| Sample | Open tickets it needs |
|---|---|
| `editor` | [Lists and trees](issues/archive/0033-lists-and-trees.md), [Tabs, Switcher and grids](issues/archive/0034-tabs-switcher-and-grids.md), [Custom drawing and custom layout](issues/archive/0028-custom-drawing-and-layout.md) (Viewport), [Toolbar](issues/archive/0038-toolbar.md) |
| `workspace` | [DockArea and FileBrowser](issues/archive/0037-docking-and-file-browser.md), [Lists and trees](issues/archive/0033-lists-and-trees.md), [Island and appear transitions](issues/archive/0036-island-and-appear-transitions.md), [Toolbar](issues/archive/0038-toolbar.md) |
| `music` | [Lists and trees](issues/archive/0033-lists-and-trees.md), [Island and appear transitions](issues/archive/0036-island-and-appear-transitions.md), [Image and resources](issues/archive/0035-image-and-resources.md) |
| `animations` | [Island and appear transitions](issues/archive/0036-island-and-appear-transitions.md), [Custom drawing and custom layout](issues/archive/0028-custom-drawing-and-layout.md) |
| `glass` | [Lists and trees](issues/archive/0033-lists-and-trees.md) (TreeView), [Tabs, Switcher and grids](issues/archive/0034-tabs-switcher-and-grids.md) (UniformGrid). It uses phase 2's glass, ComboBox and SpinBox too |

Decided for phase 3 so far (in the spec):

- [Multiple windows, dialogs and popups](issues/archive/0024-multiple-windows.md): `application { Window { } }`,
  `singleWindowApplication`, `Dialog`, suspend message and file dialogs, `Modifier.tooltip`
- [Generic input and focus](issues/archive/0025-generic-input-and-focus.md): `clickable`, `onPointerEvent`,
  `onKeyEvent`, `FocusRequester`, `pointerHoverIcon`, `ContextMenuArea`
- [Accessibility semantics](issues/archive/0026-accessibility-semantics.md): `Modifier.semantics`, automatic
  labels in rows, `announceForAccessibility`, following the system's settings
- [Fonts and text styling](issues/archive/0027-fonts-and-text-styling.md): `FontFamily`, two-step `FontWeight`,
  `TextStyle`/`LocalTextStyle`, `RavenTheme(fontFamily)`, `MarkdownText`
- [Custom drawing and custom layout](issues/archive/0028-custom-drawing-and-layout.md): `Canvas`, `Viewport`,
  `NativeViewport`, the `compose-raven-vulkan` and `compose-raven-opengl` modules, shader effects
- [Public test module for apps](issues/archive/0029-app-testing-module.md): `compose-raven-test` with the one
  harness, Compose-style finders, actions and assertions, `mainClock`, leak checks
- [XML translator as a tool](issues/archive/0030-translator-as-a-tool.md): the `compose-raven-xml` module and
  the `raven-xml2compose` command-line tool, one Kotlin file per XML file, `<Include>` across files
- [Packaging and publishing](issues/archive/0031-packaging-and-publishing.md): Maven Central under `io.github.psh`,
  one shared version with its own semantic versioning, a `compose-raven-bom`, each target published from its
  own host by CI
- [Windows (mingwX64) target](issues/archive/0032-windows-target.md): libraven through Raven's `mingw` preset,
  Windows linker options kept in sync with CMake, WGL in the OpenGL module, a third CI and publishing job
- [Lists and trees](issues/archive/0033-lists-and-trees.md): `LazyColumn`/`LazyRow` (composing every item for
  now), `ListView` and `TreeView` over Raven's virtualised widgets
- [Tabs, Switcher and grids](issues/archive/0034-tabs-switcher-and-grids.md): controlled `TabView` with `Tab`
  pages, no Switcher, `LazyVerticalGrid` over UniformGrid, `Grid` with tracks and `Modifier.cell`
- [Image and resources](issues/archive/0035-image-and-resources.md): resource root and `registerResource`,
  `ImageBitmap` (renamed from `Texture`), `Image` with `painterResource`/`BitmapPainter`, icons everywhere
- [Island and appear transitions](issues/archive/0036-island-and-appear-transitions.md): `Modifier.appearTransition`,
  `AnimatedVisibility` over Raven's transitions (fade-out exits only), `animation-core` if it resolves, `Island`
- [DockArea and FileBrowser](issues/archive/0037-docking-and-file-browser.md): `DockArea` with `Panel`s, Raven's
  arrangement with saved layouts, controlled open and close, torn-off panels in Raven windows; `FileBrowser`
- [Toolbar](issues/archive/0038-toolbar.md): `Toolbar` with the toolbar role, centred children and
  `ToolbarSeparator`

Open tickets that serve every app rather than one sample:

- [Navigation 3 and scenes](issues/0039-navigation3-and-scenes.md)
- [Typography in RavenTheme](issues/0040-typography-in-raventheme.md)

## Later

- `effects` (shader effects), `viewport_opengl` (`compose-raven-opengl`), `viewport_vulkan`
  (`compose-raven-vulkan`): designed in [Custom drawing and custom layout](issues/archive/0028-custom-drawing-and-layout.md). They port after
  phase 3, once the modules exist.
- `studio`, `browser`: after the specialist widgets (Timeline, NodeGraph, ColorPicker, WebView,
  BlockEditor), which are still fog on the phase 3 map.
