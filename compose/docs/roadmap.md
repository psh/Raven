# compose-raven roadmap

The source of truth for **what gets built, in what order**. [`spec.md`](spec.md) says *what*
compose-raven is, and this file says *when*. Implementation planning starts here: each milestone
links to the spec sections that define it. Phases 1 to 3 are fully designed. Both wayfinder maps
are closed, and the remaining fog, listed on
[compose-raven phase 3 spec](issues/0023-compose-raven-phase-3-spec.md#not-yet-specified), waits
for a phase 4 map.

## Milestones at a glance

| Milestone | Delivers | Samples |
|---|---|---|
| [0. Verify first](#0-verify-first) | answers to the open facts the spec depends on | none |
| [1. Raven fork changes](#1-raven-fork-changes) | the changes compose-raven needs in `psh/Raven` | none |
| [2. Phase 1](#2-phase-1) | the library (`compose-raven-ui` with the binding layer, then `compose-raven-foundation`), the host, the phase 1 vocabulary, tests and CI | `hello`, `xml-translator` |
| [3. Phase 2](#3-phase-2) | menus, ComboBox/SpinBox, Surface/FlowRow/Expander, theming, glass | `notepad`, `components`, `themes`, `overlay` |
| [4. Phase 3](#4-phase-3) | windows, input, accessibility, fonts, drawing and graphics modules, lists, tabs and grids, images, transitions, docking, toolbar, test module, translator tool, publishing, Windows | `editor`, `workspace`, `music`, `animations`, `glass` |
| [Later](#later) | waits on fog | `effects`, `viewport_opengl`, `viewport_vulkan`, `studio`, `browser` |

Not ported: `memory` (it demonstrates the C-level allocator), `languages` and `bevy_editor` (they
show the other bindings and Bevy, not Raven UIs).

## 0. Verify first

Facts the spec depends on. The ones that need only Raven or Gradle are checked in
[`../spikes/`](../spikes/README.md), and the spec follows their results. The rest wait for the host,
a Windows host or a real window:

| Fact to check | If it fails | Spec | Result (2026-11-06) |
|---|---|---|---|
| `Rinit`/`Rshutdown` can be repeated in one process | fix it in the fork (fork change 2) | [11](spec.md#11-required-raven-changes), [9](spec.md#9-testing) | **Passed** |
| `Rxml_document_parse` registers no `<Widget>` declarations globally | `parseRavenXml` unregisters them | [10](spec.md#10-samples) | **Passed** |
| A `Separator` inserted into a Menu acts as a menu separator | needed before the menus of phase 2 | [8](spec.md#8-component-vocabulary) | **Failed**, worked around: a MenuItem with `Separator = true` |
| Raven derives hover and pressed shades from an overridden accent or control colour | compose-raven sets lighter and darker shades itself | [8](spec.md#8-component-vocabulary) | **Failed**: compose-raven sets them |
| Compose can recompose safely inside the nested frames that Raven's blocking dialogs run (phase 3) | `native = true` dialogs are dropped (the default ones are already built on `Dialog` and FileBrowser) | [4](spec.md#4-application-host) | Waits for the host |
| A MinGW-gcc `libraven.a` links with Kotlin/Native's `mingwX64` toolchain (phase 3) | build libraven with Kotlin/Native's own clang and sysroot | [2](spec.md#2-modules-and-build) | Waits for a Windows host |
| Raven applies the accessibility text scale to explicit text sizes (`Rlabel_set_text_size`) too (phase 3) | compose-raven multiplies explicit sizes by the scale itself | [8](spec.md#8-component-vocabulary) | **Passed** |
| Writing text back to a TextInput during IME composition (preedit) doesn't cancel the composition, and what Raven reports as the text during preedit (phase 1) | `TextField` defers write-backs while a composition is active, if Raven exposes that, or a fork change adds it | [7](spec.md#7-events-and-state) | Waits for a real window and an IME |
| Focusing the root clears focus in Raven, for `clearFocus()` (phase 3) | add "focus nothing" to the fork | [7](spec.md#7-events-and-state) | **Failed**: fork change 5, `Rwindow_clear_focus` (the flag toggle works until then) |
| A GL context (`NSOpenGLContext`) works on Raven's macOS child view, which carries a Metal layer (phase 3) | macOS gets `OpenGLTexture` only | [8](spec.md#8-component-vocabulary) | Waits for a real window |
| An app can render Metal into the child view's `CAMetalLayer` while Raven uses MoltenVK in the parent (phase 3) | Metal content goes through a texture | [8](spec.md#8-component-vocabulary) | Waits for a real window |
| `androidx.compose.animation:animation-core` resolves for our targets without Compose UI (phase 3) | compose-raven provides a small `animate*AsState` set | [8](spec.md#8-component-vocabulary) | **Failed**: compose-raven provides its own |
| `rememberNavBackStack` and the saved-state decorator of `navigation3-runtime` work under compose-raven's host and window owners, with the kotlinx-serialization plugin, and `navigationevent` resolves without Compose UI (phase 3) | windows call registered back handlers directly; any other gap is fixed in `compose-raven-navigation3` | [8](spec.md#8-component-vocabulary) | `navigationevent` **passed**; the rest waits for the host |

## 1. Raven fork changes

Made in the `psh/Raven` fork and offered upstream later ([spec section 11](spec.md#11-required-raven-changes)).
**Done** for 1, 3, 4 and 5, one commit each on `fork/compose-raven`:

1. `Rwidget_move_child(parent, from, to)`: required by the Applier (milestone 2).
2. ~~A fix for repeated `Rinit`/`Rshutdown`~~: not needed, milestone 0 found it works.
3. A built-in Kotlin language in `src/widgets/syntax.c`: needed by the `xml-translator` sample.
4. `Rdialog_set_dismiss_callback`, which can refuse a dismissal by Escape: needed by `Dialog` (phase 3).
5. `Rwindow_clear_focus(window)`, which leaves no widget focused: needed by `clearFocus()` (phase 3).

Each change is **one isolated commit** in the fork, with a C test in Raven's own `tests/`, and is
offered upstream as its own pull request, so the fork rebases on upstream cheaply and drops each
change once upstream has it.

## 2. Phase 1

Broken into tickets, in dependency order. Each ticket's **Depends on** line names its blockers.

| Part | Spec | Ticket |
|---|---|---|
| Gradle build: `raven-native`, `compose-raven-ui`, `compose-raven-foundation`, `samples/*`; libraven through CMake, static linking | [2](spec.md#2-modules-and-build) | [Gradle build and libraven](issues/archive/0041-gradle-build-and-libraven.md) (macOS), [Linux build and libraven](issues/0058-linux-build-and-libraven.md) |
| `raven-native`: `raven.def` and the `raven.native` helpers (callback registry, strings, UI-thread check) | [3](spec.md#3-raven-native) | [raven.native helpers](issues/archive/0042-raven-native-helpers.md) |
| `RavenNode` and `RavenApplier` | [5](spec.md#5-node-model-and-applier) | [RavenNode and RavenApplier](issues/archive/0043-ravennode-and-ravenapplier.md) |
| `RavenHost` and `singleWindowApplication`: dispatcher, frame clock, snapshot pump, host epoch and per-host caches, ownership and shutdown order, errors; the harness core | [4](spec.md#4-application-host), [9](spec.md#9-testing) | [RavenHost and singleWindowApplication](issues/archive/0044-ravenhost-and-singlewindowapplication.md) |
| The binding layer in `compose-raven-ui`, **before any composable**: widget descriptors, property and event binding, the content node, `ControlledValue<T>` | [5](spec.md#the-binding-layer), [7](spec.md#7-events-and-state) | [The binding layer](issues/0045-binding-layer.md) |
| `Modifier`, `Dp`, `Column`/`Row`/`Box`, `Arrangement` | [6](spec.md#6-layout-and-modifiers) | [Modifier, Dp and the layout containers](issues/0046-modifier-dp-and-layout-containers.md) |
| The phase 1 vocabulary table, with the harness's finders, actions and assertions | [8](spec.md#8-component-vocabulary), [9](spec.md#9-testing) | [Text, buttons and the first finders](issues/0047-text-buttons-and-first-finders.md), [Checkbox, Switch and RadioButton](issues/0048-checkbox-switch-and-radiobutton.md), [TextField and Slider](issues/0049-textfield-and-slider.md) (with the IME check), [Scroll containers, splitters and CodeEditor](issues/0050-scroll-containers-splitters-and-codeeditor.md) |
| CI on macOS and Linux | [9](spec.md#9-testing) | [CI job for compose](issues/0052-ci-job-for-compose.md) |
| `samples/hello` | [10](spec.md#10-samples) | [samples/hello](issues/0051-samples-hello.md) |
| `samples/xml-translator`, including templates and its private cinterop (needs fork change 3) | [10](spec.md#10-samples) | [parsing](issues/0054-xml-translator-parsing.md), [mapping the phase 1 vocabulary](issues/0055-xml-translator-mapping.md), [templates and expressions](issues/0056-xml-translator-templates-and-expressions.md), [the sample UI and the examples check](issues/0057-xml-translator-sample-ui-and-examples-check.md) |
| Verify first: recomposition inside nested frames, now that the host exists | [4](spec.md#4-application-host) | [Verify recomposition inside nested frames](issues/0053-verify-recomposition-in-nested-frames.md) |

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

Designed on [compose-raven phase 3 spec](issues/0023-compose-raven-phase-3-spec.md). Every decision these
ports need is now made:

| Sample | Decisions it needs |
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
- [Navigation 3 and scenes](issues/archive/0039-navigation3-and-scenes.md): `compose-raven-navigation3` with the
  Scene layer adapted from AndroidX, `NavDisplay`, dialog destinations, desktop back keys
- [Typography in RavenTheme](issues/archive/0040-typography-in-raventheme.md): eight roles sized by Raven's
  Markdown factors, `RavenTheme.typography`, component defaults, custom typographies

## Later

**Phase 4 fog** (not yet designed): specialist widgets (for `studio` and `browser`), custom
`Layout {}`, graphics extras, generated resources, vector icons, right-to-left layout, lazy
virtualisation, more list widgets, exit transitions, torn-off panels as windows, more navigation,
multi-window tests, build-time XML translation, app bundling, gestures and popups.

- `effects` (shader effects), `viewport_opengl` (`compose-raven-opengl`), `viewport_vulkan`
  (`compose-raven-vulkan`): designed in [Custom drawing and custom layout](issues/archive/0028-custom-drawing-and-layout.md). They port after
  phase 3, once the modules exist.
- `studio`, `browser`: after the specialist widgets (Timeline, NodeGraph, ColorPicker, WebView,
  BlockEditor), which are still fog on the phase 3 map.
