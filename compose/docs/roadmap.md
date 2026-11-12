# compose-raven roadmap

The source of truth for **what gets built, in what order**. [`spec.md`](spec.md) says *what*
compose-raven is, and this file says *when*. Implementation planning starts here: each milestone
links to the spec sections that define it. Phases 1 to 3 are fully designed. All three wayfinder
maps are closed ([compose-raven phase 2 sample gaps](issues/0073-compose-raven-phase-2-sample-gaps.md)
settled the phase 2 samples), and the remaining fog, listed on
[compose-raven phase 3 spec](issues/0023-compose-raven-phase-3-spec.md#not-yet-specified), waits
for a phase 5 map. The split between phases 2, 3 and 4 follows [ADR 0001](adr/0001-phase-split-after-the-phase-2-sample-gaps.md).

## Milestones at a glance

| Milestone | Delivers | Samples |
|---|---|---|
| [0. Verify first](#0-verify-first) | answers to the open facts the spec depends on | none |
| [1. Raven fork changes](#1-raven-fork-changes) | the changes compose-raven needs in `psh/Raven` | none |
| [2. Phase 1](#2-phase-1) | the library (`compose-raven-ui` with the binding layer, then `compose-raven-foundation`), the host, the phase 1 vocabulary, tests and CI | `hello`, `xml-translator` |
| [3. Phase 2](#3-phase-2) | the finished single `Window` (`WindowState`), menus and the header bar, native dialogs, the CodeEditor extras, ComboBox/SpinBox, Surface/FlowRow/Expander, theming, glass, `MarkdownText`, `appearTransition` | `notepad`, `components`, `themes`, `overlay` |
| [4. Phase 3](#4-phase-3) | the app vocabulary: windows that come and go, dialogs, input, accessibility, fonts, drawing and graphics modules, lists, tabs and grids, images, transitions, docking, toolbar, navigation | `editor`, `workspace`, `music`, `animations`, `glass` |
| [5. Phase 4](#5-phase-4-release) | the release: CI and the Linux build finished, the public test module, the translator tool, packaging and publishing, the Windows target | none new |
| [Later](#later) | phase 5, waiting on fog | `effects`, `viewport_opengl`, `viewport_vulkan`, `studio`, `browser` |

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
| Compose can recompose safely inside the nested frames that Raven's blocking dialogs run (phase 3) | `native = true` dialogs are dropped (the default ones are already built on `Dialog` and FileBrowser) | [4](spec.md#4-application-host) | **Passed** (2026-11-11): recomposition, applies and controlled values stay correct and nothing re-enters the Applier. Calls waiting together return in reverse order, and Windows' own modal loop waits for a Windows host |
| A MinGW-gcc `libraven.a` links with Kotlin/Native's `mingwX64` toolchain (phase 4) | build libraven with Kotlin/Native's own clang and sysroot | [2](spec.md#2-modules-and-build) | Waits for a Windows host |
| Raven applies the accessibility text scale to explicit text sizes (`Rlabel_set_text_size`) too (phase 3) | compose-raven multiplies explicit sizes by the scale itself | [8](spec.md#8-component-vocabulary) | **Passed** |
| Writing text back to a TextInput during IME composition (preedit) doesn't cancel the composition, and what Raven reports as the text during preedit (phase 1) | `TextField` defers write-backs while a composition is active, if Raven exposes that, or a fork change adds it | [7](spec.md#7-events-and-state) | **Passed**: Raven has no preedit on any backend and reports only committed text (checked in the platform code, 2026-11-11) |
| Focusing the root clears focus in Raven, for `clearFocus()` (phase 3) | add "focus nothing" to the fork | [7](spec.md#7-events-and-state) | **Failed**: fork change 5, `Rwindow_clear_focus` (the flag toggle works until then) |
| A GL context (`NSOpenGLContext`) works on Raven's macOS child view, which carries a Metal layer (phase 3) | macOS gets `OpenGLTexture` only | [8](spec.md#8-component-vocabulary) | Waits for a real window |
| An app can render Metal into the child view's `CAMetalLayer` while Raven uses MoltenVK in the parent (phase 3) | Metal content goes through a texture | [8](spec.md#8-component-vocabulary) | Waits for a real window |
| `androidx.compose.animation:animation-core` resolves for our targets without Compose UI (phase 3) | compose-raven provides a small `animate*AsState` set | [8](spec.md#8-component-vocabulary) | **Failed**: compose-raven provides its own |
| `rememberNavBackStack` and the saved-state decorator of `navigation3-runtime` work under compose-raven's host and window owners, with the kotlinx-serialization plugin, and `navigationevent` resolves without Compose UI (phase 3) | windows call registered back handlers directly; any other gap is fixed in `compose-raven-navigation3` | [8](spec.md#8-component-vocabulary) | `navigationevent` **passed**; the rest waits for the host |

## 1. Raven fork changes

Made in the `psh/Raven` fork and offered upstream later ([spec section 11](spec.md#11-required-raven-changes)).
**Done** for 1, 3, 4, 5, 6, 7 and 8, one commit each on `fork/compose-raven`:

1. `Rwidget_move_child(parent, from, to)`: required by the Applier (milestone 2).
2. ~~A fix for repeated `Rinit`/`Rshutdown`~~: not needed, milestone 0 found it works.
3. A built-in Kotlin language in `src/widgets/syntax.c`: needed by the `xml-translator` sample.
4. `Rdialog_set_dismiss_callback`, which can refuse a dismissal by Escape: needed by `Dialog` (phase 3).
5. `Rwindow_clear_focus(window)`, which leaves no widget focused: needed by `clearFocus()` (phase 3).
6. A macOS bug fix: a modifier key no longer ends the app (Cocoa `flagsChanged:`). Found by
   `samples/hello`; it has no C test, because only a real window reaches it.
7. `Rborder_get_outline`, `_corner_radius` and `_shadow`, reading a Border back: needed by
   `Surface`'s tests (phase 2).
8. Raven calls the reload callback for theme files too, so compose-raven refreshes
   `RavenTheme.themeNames` and the colours when a theme file reloads instead of every frame
   (phase 2, [Raven reports theme file reloads](issues/archive/0090-raven-reports-theme-file-reloads.md)).

Noted for upstream, not a fork change: `raven_xml.h` says of `Rxml_check_files` that files are
looked at "five per second", but the watcher in `src/xml/xml_watch.c` is event-driven, polling every
0.2 s only as a fallback ([Raven's live reload under compose-raven](issues/archive/0084-ravens-live-reload-under-compose-raven.md)).

Each change is **one isolated commit** in the fork, with a C test in Raven's own `tests/`, and is
offered upstream as its own pull request, so the fork rebases on upstream cheaply and drops each
change once upstream has it.

## 2. Phase 1

Broken into tickets, in dependency order. Each ticket's **Depends on** line names its blockers.
**Done on macOS** but for two rows: CI ([CI job for compose](issues/archive/0052-ci-job-for-compose.md)) and
the Linux build ([Linux build and libraven](issues/archive/0058-linux-build-and-libraven.md)). Both are
closed unfinished for now and finish in [phase 4](#5-phase-4-release), the release; until then,
every phase is checked on macOS only.

| Part | Spec | Ticket |
|---|---|---|
| Gradle build: `raven-native`, `compose-raven-ui`, `compose-raven-foundation`, `samples/*`; libraven through CMake, static linking | [2](spec.md#2-modules-and-build) | [Gradle build and libraven](issues/archive/0041-gradle-build-and-libraven.md) (macOS), [Linux build and libraven](issues/archive/0058-linux-build-and-libraven.md) |
| `raven-native`: `raven.def` and the `raven.native` helpers (callback registry, strings, UI-thread check) | [3](spec.md#3-raven-native) | [raven.native helpers](issues/archive/0042-raven-native-helpers.md) |
| `RavenNode` and `RavenApplier` | [5](spec.md#5-node-model-and-applier) | [RavenNode and RavenApplier](issues/archive/0043-ravennode-and-ravenapplier.md) |
| `RavenHost` and `singleWindowApplication`: dispatcher, frame clock, snapshot pump, host epoch and per-host caches, ownership and shutdown order, errors; the harness core | [4](spec.md#4-application-host), [9](spec.md#9-testing) | [RavenHost and singleWindowApplication](issues/archive/0044-ravenhost-and-singlewindowapplication.md) |
| The binding layer in `compose-raven-ui`, **before any composable**: widget descriptors, property and event binding, the content node, `ControlledValue<T>` | [5](spec.md#the-binding-layer), [7](spec.md#7-events-and-state) | [The binding layer](issues/archive/0045-binding-layer.md) |
| `Modifier`, `Dp`, `Column`/`Row`/`Box`, `Arrangement` | [6](spec.md#6-layout-and-modifiers) | [Modifier, Dp and the layout containers](issues/archive/0046-modifier-dp-and-layout-containers.md) |
| The phase 1 vocabulary table, with the harness's finders, actions and assertions | [8](spec.md#8-component-vocabulary), [9](spec.md#9-testing) | [Text, buttons and the first finders](issues/archive/0047-text-buttons-and-first-finders.md), [Checkbox, Switch and RadioButton](issues/archive/0048-checkbox-switch-and-radiobutton.md), [TextField and Slider](issues/archive/0049-textfield-and-slider.md) (with the IME check), [Scroll containers, splitters and CodeEditor](issues/archive/0050-scroll-containers-splitters-and-codeeditor.md) |
| CI on macOS and Linux | [9](spec.md#9-testing) | [CI job for compose](issues/archive/0052-ci-job-for-compose.md) |
| `samples/hello` | [10](spec.md#10-samples) | [samples/hello](issues/archive/0051-samples-hello.md) |
| `samples/xml-translator`, including templates (needs fork change 3) | [10](spec.md#10-samples) | [parsing](issues/archive/0054-xml-translator-parsing.md), [mapping the phase 1 vocabulary](issues/archive/0055-xml-translator-mapping.md), [templates and expressions](issues/archive/0056-xml-translator-templates-and-expressions.md), [the sample UI and the examples check](issues/archive/0057-xml-translator-sample-ui-and-examples-check.md) |
| Verify first: recomposition inside nested frames, now that the host exists | [4](spec.md#4-application-host) | [Verify recomposition inside nested frames](issues/archive/0053-verify-recomposition-in-nested-frames.md) |

## 3. Phase 2

Each family is fully decided. Broken into tickets, in dependency order; each ticket's **Depends
on** line names its blockers. The samples port when their families are done, and each sample
ticket lists its port gaps, each with the later phase that completes it (listed under that phase
below). The map [compose-raven phase 2 sample gaps](issues/0073-compose-raven-phase-2-sample-gaps.md)
settled every one; nothing is left open before implementation.

| Family | Spec | Needed by | Ticket |
|---|---|---|---|
| `Color`, `Shape`, `BorderStroke`, `Text(color)` | [8: Surface](spec.md#phase-2-surface-flowrow-and-expander) | every family below | [Color, Shape and BorderStroke](issues/archive/0060-color-shape-and-borderstroke.md) |
| The single `Window` finished: `WindowState`, `DpSize`, `minimumSize`, the flags, `background`, `opacity`, `WindowBackdrop`, `LocalWindowInfo.isTransparent` | [4](spec.md#4-application-host), [6](spec.md#6-layout-and-modifiers) | every phase 2 sample | [Window: WindowState and its parameters](issues/archive/0086-window-windowstate-and-its-parameters.md) |
| Menus and the header bar (`KeyShortcut`, `HeaderBar`) | [8: menus](spec.md#phase-2-menus-and-the-header-bar) | `notepad` | [Menus and KeyShortcut](issues/archive/0061-menus-and-keyshortcut.md), [HeaderBar](issues/archive/0062-headerbar.md) |
| Native message and file dialogs (`native = true` only) | [4](spec.md#4-application-host) | `notepad` | [Native message and file dialogs](issues/archive/0080-native-message-and-file-dialogs.md) |
| CodeEditor's editing extras: `CodeEditorState`, word wrap, completions, markers, registered languages | [8: CodeEditor](spec.md#phase-2-codeeditors-editing-extras) | `notepad` | [CodeEditorState, completions, markers and languages](issues/archive/0081-codeeditorstate-completions-markers-and-languages.md) |
| Files and text dropped on a window (`Window(onExternalDrop)`) | [4](spec.md#4-application-host) | `notepad` | [Files and text dropped on a window](issues/archive/0082-files-and-text-dropped-on-a-window.md) |
| `MenuBar(native)`, a choice of the macOS system menu bar | [8: menus](spec.md#phase-2-menus-and-the-header-bar) | `notepad` | [MenuBar(native)](issues/archive/0089-menubar-native-parameter.md) |
| Theme file reloads reported by Raven (fork change 8) | [11](spec.md#11-required-raven-changes), [4](spec.md#4-application-host) | `themes` | [Raven reports theme file reloads](issues/archive/0090-raven-reports-theme-file-reloads.md) |
| `Modifier.alpha` and `Modifier.clipToBounds` | [6](spec.md#6-layout-and-modifiers) | `components`, `notepad`, `overlay` | [Modifier.alpha and clipToBounds](issues/archive/0091-modifier-alpha-and-cliptobounds.md) |
| `CodeEditor(placeholder)` | [8: CodeEditor](spec.md#phase-2-codeeditors-editing-extras) | `notepad` | [CodeEditor placeholder](issues/archive/0092-codeeditor-placeholder.md) |
| Subtree themes as a CompositionLocal, with no node of their own | [8: theming](spec.md#phase-2-theming) | `themes` | [Subtree themes as a CompositionLocal](issues/archive/0093-subtree-themes-as-a-compositionlocal.md) |
| `LocalUriHandler`, and `MarkdownText`'s links going through it | [8: fonts](spec.md#fonts-and-text-styling-phase-3) | `themes` | [LocalUriHandler](issues/archive/0094-localurihandler.md) |
| ComboBox, SpinBox, `codeEditorLanguages()` | [8: ComboBox](spec.md#phase-2-combobox-and-spinbox) | `notepad`, `themes`, `overlay` (and `glass` in phase 3) | [ComboBox, SpinBox and codeEditorLanguages](issues/archive/0063-combobox-spinbox-and-codeeditorlanguages.md) |
| Surface, FlowRow, Expander | [8: Surface](spec.md#phase-2-surface-flowrow-and-expander) | `components` | [Surface, FlowRow and Expander](issues/archive/0064-surface-flowrow-and-expander.md) |
| Theming: `RavenTheme` (with `accent`), theme files, `saveTheme`, `themeNames`, `XxxDefaults.colors()`, `shape`/`contentPadding` | [8: theming](spec.md#phase-2-theming) | `themes`, `notepad` | [RavenTheme and theme files](issues/archive/0065-raventheme-and-theme-files.md), [Component colours, shapes and padding](issues/archive/0066-component-colors-shapes-and-padding.md) |
| Glass: `Modifier.material`, `BackgroundBlur` | [8: glass](spec.md#phase-2-glass-and-backgroundblur) | `overlay` (and `glass` in phase 3) | [Modifier.material and BackgroundBlur](issues/archive/0067-material-and-backgroundblur.md) |
| `MarkdownText` (early from phase 3, without `style`) | [8: fonts](spec.md#fonts-and-text-styling-phase-3) | `themes` | [MarkdownText](issues/archive/0087-markdowntext.md) |
| `Modifier.appearTransition` (early from phase 3) | [8: transitions](spec.md#transitions-and-island-phase-3) | `components` | [Modifier.appearTransition](issues/archive/0088-modifier-appeartransition.md) |
| Samples | [10](spec.md#10-samples) | | [notepad](issues/archive/0069-samples-notepad.md), [components](issues/archive/0070-samples-components.md), [themes](issues/archive/0071-samples-themes.md), [overlay](issues/archive/0072-samples-overlay.md) |

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

Phase 3 also completes what phase 2 builds as a foundation:

- The default message and file dialogs, on `Dialog` and `FileBrowser`, and headless tests of
  dialog flows (`samples/notepad` included); phase 2 builds only `native = true`
  ([Notepad's file dialogs and discard prompt](issues/archive/0074-notepad-file-dialogs-and-discard-prompt.md)).
- `samples/themes`' `ListView`, with the lists
  ([Theme extras the themes sample uses](issues/archive/0077-theme-extras-the-themes-sample-uses.md)).
- `Text(softWrap)` and `Text(fontFamily)`, with the text styling, so `samples/overlay`'s
  explanation and `samples/themes`' status line wrap, and `samples/themes`' monospace label is
  monospace ([samples/overlay](issues/archive/0072-samples-overlay.md),
  [samples/themes](issues/archive/0071-samples-themes.md)).
- `MarkdownText`'s `style` and the rest of transitions (`AnimatedVisibility`, `animate*AsState`,
  `Island`); phase 2 brings `MarkdownText` and `Modifier.appearTransition` early ([ADR 0001](adr/0001-phase-split-after-the-phase-2-sample-gaps.md)).

Decided for phase 3 so far (in the spec):

- [Multiple windows, dialogs and popups](issues/archive/0024-multiple-windows.md): `application { Window { } }`,
  `singleWindowApplication`, `Dialog`, suspend message and file dialogs, `Modifier.tooltip`
  (`application`, a phase 1 `Window` and `Modifier.tooltip` arrived early, with
  [samples/hello](issues/archive/0051-samples-hello.md))
- [Generic input and focus](issues/archive/0025-generic-input-and-focus.md): `clickable`, `onPointerEvent`,
  `onKeyEvent`, `FocusRequester`, `pointerHoverIcon`, `ContextMenuArea`
- [Accessibility semantics](issues/archive/0026-accessibility-semantics.md): `Modifier.semantics`, automatic
  labels in rows, `announceForAccessibility`, following the system's settings
- [Fonts and text styling](issues/archive/0027-fonts-and-text-styling.md): `FontFamily`, two-step `FontWeight`,
  `TextStyle`/`LocalTextStyle`, `RavenTheme(fontFamily)`, `MarkdownText`
- [Custom drawing and custom layout](issues/archive/0028-custom-drawing-and-layout.md): `Canvas`, `Viewport`,
  `NativeViewport`, the `compose-raven-vulkan` and `compose-raven-opengl` modules, shader effects
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

## 5. Phase 4: release

What makes compose-raven usable outside this repository, split from phase 3 by [ADR 0001](adr/0001-phase-split-after-the-phase-2-sample-gaps.md).
It needs none of phase 3's vocabulary, so it can start as soon as phase 2 is done, beside phase 3.

- Phase 1's unfinished CI and Linux build: [CI job for compose](issues/archive/0052-ci-job-for-compose.md)
  and [Linux build and libraven](issues/archive/0058-linux-build-and-libraven.md), both disabled or
  deferred until a fuller codebase runs.

Decided for phase 4 (in the spec):

- [Public test module for apps](issues/archive/0029-app-testing-module.md): `compose-raven-test` with the one
  harness, Compose-style finders, actions and assertions, `mainClock`, leak checks
- [XML translator as a tool](issues/archive/0030-translator-as-a-tool.md): the `compose-raven-xml` module and
  the `raven-xml2compose` command-line tool, one Kotlin file per XML file, `<Include>` across files
- [Packaging and publishing](issues/archive/0031-packaging-and-publishing.md): Maven Central under `io.github.psh`,
  one shared version with its own semantic versioning, a `compose-raven-bom`, each target published from its
  own host by CI
- [Windows (mingwX64) target](issues/archive/0032-windows-target.md): libraven through Raven's `mingw` preset,
  Windows linker options kept in sync with CMake, WGL in the OpenGL module, a third CI and publishing job

## Later

**Phase 5 fog** (not yet designed): specialist widgets (for `studio` and `browser`), custom
`Layout {}`, graphics extras, generated resources, vector icons, right-to-left layout, lazy
virtualisation, more list widgets, exit transitions, torn-off panels as windows, more navigation,
multi-window tests, build-time XML translation, app bundling, gestures (with drop targets on widgets, drags inside the app, and dragging a window by its content,
`samples/overlay`'s `WindowDrag`) and popups.

- `effects` (shader effects), `viewport_opengl` (`compose-raven-opengl`), `viewport_vulkan`
  (`compose-raven-vulkan`): designed in [Custom drawing and custom layout](issues/archive/0028-custom-drawing-and-layout.md). They port after
  phase 3, once the modules exist.
- `ColorPicker`, one of the specialist widgets, also completes `samples/themes`: it replaces the
  row of accent swatches the phase 2 port uses
  ([Theme extras the themes sample uses](issues/archive/0077-theme-extras-the-themes-sample-uses.md)).
- `studio`, `browser`: after the specialist widgets (Timeline, NodeGraph, ColorPicker, WebView,
  BlockEditor), which are still fog on the phase 3 map.
