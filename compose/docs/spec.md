# compose-raven specification

**Status: phases 1, 2 and 3 complete.** Every decision below is settled. Both wayfinder maps,
[compose-raven spec](issues/0001-compose-raven-spec.md) and
[compose-raven phase 3 spec](issues/0023-compose-raven-phase-3-spec.md), are closed. What remains
is fog, listed on the phase 3 map and in [`roadmap.md`](roadmap.md), for a future phase 4 map. The vocabulary is in
[`compose/GLOSSARY.md`](../GLOSSARY.md).

## 1. Overview

compose-raven is a family of Kotlin/Native modules that drives Raven's retained widgets from the
Compose runtime. It works like a native-widgets binding. Each composable node owns exactly one
Raven widget. Raven lays the widgets out and paints them, and Compose only manages the tree. Mosaic
is the model for wiring the Compose runtime to a tree of its own.

Two standing preferences shape every section:

- **Compose conventions and ergonomics.** Names, parameter shapes, defaults and behaviour follow
  Compose. Where Raven differs, compose-raven adapts to Compose, and the
  [Differences from Compose](#12-differences-from-compose) table lists every place where the two
  still differ.
- **Keep it slim.** Phase 1 is the smallest spec that makes a real small tool. Everything else is on
  the [Roadmap](#13-roadmap).

An app looks like this:

```kotlin
fun main() = singleWindowApplication(title = "Hello", width = 800, height = 600) {
    var count by remember { mutableStateOf(0) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Clicked $count times")
        Button(onClick = { count++ }) { Text("Add") }
    }
}
```

The app never calls Raven directly.

## 2. Modules and build

compose-raven is a standalone Gradle build rooted at `compose/`, with these modules:

| Module | Contents | Depends on |
|---|---|---|
| `raven-native` | The cinterop over `Raven.h` (`raven.cinterop`) and the small public helpers in `raven.native` ([section 3](#3-raven-native)). It contains no Compose code. | libraven |
| `compose-raven-ui` | The core, in `raven.compose.ui`: the host and windows, the Applier and node, the binding layer ([section 5](#5-node-model-and-applier)), `Modifier`, `Dp`, input, semantics, theming plumbing | `raven-native` with `implementation`, so apps don't see it; `androidx.compose.runtime:runtime` |
| `compose-raven-foundation` | The composables, in `raven.compose.foundation`: thin declarations over the binding layer | `compose-raven-ui` with `api`; `raven-native` with `implementation` |
| `compose-raven-bom` (phase 3) | Version constraints for every module and `androidx.compose.runtime` | none |
| `compose-raven-navigation3` (phase 3, opt-in) | Navigation 3's Scene layer and `NavDisplay`, adapted from AndroidX 1.2.1 (Apache-2.0, with NOTICE) ([section 8](#8-component-vocabulary)) | `compose-raven-foundation`, `androidx.navigation3:navigation3-runtime` |
| `compose-raven-xml` (phase 3) | The XML translator as a library: the parse adapter and `translate()` ([section 10](#10-samples)) | `compose-raven-foundation`, `raven-native` (`implementation`) |
| `tools/raven-xml2compose` (phase 3) | Command-line translator over files and directories ([section 10](#10-samples)) | `compose-raven-xml` |
| `compose-raven-test` (phase 3) | The public test harness: `runRavenTest`, finders, actions, assertions, `mainClock`, leak checks ([section 9](#9-testing)) | `compose-raven-foundation` (`api`), `raven-native` (`implementation`) |
| `compose-raven-vulkan` (phase 3, opt-in) | Cinterop over `raven_vulkan.h` (`raven.vulkan.cinterop`, public by design), `VulkanViewport`, Vulkan-backed textures, device access and sharing ([section 8](#8-component-vocabulary)) | `compose-raven-foundation` |
| `compose-raven-opengl` (phase 3, opt-in) | `OpenGLViewport` with a managed context (EGL on Linux, `NSOpenGLContext` on macOS), `OpenGLTexture` | `compose-raven-foundation` |
| `samples/hello`, `samples/xml-translator` | Executable samples ([section 10](#10-samples)) | `compose-raven-foundation`, plus `compose-raven-xml` for `xml-translator` from phase 3 |

The core and the composables are separate modules from the start: almost all of the complexity is
in `compose-raven-ui`, and the composables stay thin declarations over its binding layer. Apps
depend on `compose-raven-foundation`, which brings `compose-raven-ui` with it.

- **Toolchain**: Kotlin 2.4.21 with `kotlin("multiplatform")` and
  `org.jetbrains.kotlin.plugin.compose`. The dependency is `androidx.compose.runtime:runtime:1.12.1`
  from Google Maven; the JetBrains coordinates only redirect to it. Neither Compose UI nor the
  `org.jetbrains.compose` plugin is used.
- **Targets**: `macosArm64` and `linuxX64`, plus `mingwX64` from phase 3. Each target builds and is
  tested on its own host, with no cross-compilation. On Windows, libraven is built with Raven's
  `mingw` CMake preset under MSYS2 MINGW64. The implementation first verifies that it links with
  Kotlin/Native's `mingwX64` toolchain, and the fallback is Kotlin/Native's own clang.
- **Building libraven**: a Gradle task runs CMake on the repo root into
  `compose/build/raven/<target>`. The build is always `Release`, with `RAVEN_BUILD_EXAMPLES`,
  `RAVEN_BUILD_TESTS`, `RAVEN_BUILD_TOOLS` and `RAVEN_BUILD_WEB` off. The cinterop task depends on
  it.
- **Linking**: static. `raven.def` bundles `libraven.a` (`staticLibraries` + `libraryPaths`).
  `linkerOpts` adds `-framework AppKit -framework QuartzCore -framework Metal -framework
  CoreFoundation` on macOS, `-lX11 -ldl -lpthread -lm` on Linux, and `-luser32 -lgdi32 -lshell32
  -lcomdlg32 -lole32 -lwinhttp` on Windows. A build check fails if these drift from
  `RAVEN_LIBRARIES` in `CMakeLists.txt`.
- **MoltenVK** is needed at run time only. Raven loads Vulkan dynamically, so nothing is linked.
  On macOS, users install it with `brew install molten-vk` or the Vulkan SDK.
- **Publishing** (phase 3): every library module is published to Maven Central under the group
  `io.github.psh`, at one shared version that follows its own semantic versioning (from 0.1.0).
  - Each target publishes from its own host: the macOS job publishes the root and metadata
    modules plus `macosarm64`, the Linux job publishes `linuxx64`, and the Windows job (phase 3)
    publishes `mingwx64`. A `compose-vX.Y.Z` tag triggers all of them, signed in CI.
  - Each release records the bundled Raven (fork commit and version, for example
    `0.2.1-psh.3`).
  - `raven-xml2compose` ships as per-host binaries on a GitHub release.
  - `compose-raven-bom` (`java-platform`) pins every module at the release version, plus
    `androidx.compose.runtime`. Apps use
    `implementation(project.dependencies.platform("io.github.psh:compose-raven-bom:X.Y.Z"))` and
    leave versions off the modules. It publishes from the macOS job.

Decided in: [Compose runtime on Kotlin/Native](issues/archive/0002-compose-runtime-on-kotlin-native.md),
[Kotlin/Native targets and build](issues/archive/0005-kotlin-native-targets-and-build.md),
[Layering over cinterop](issues/archive/0006-cinterop-layering.md),
[Port the Raven samples to Compose](issues/archive/0014-port-raven-samples.md),
[Packaging and publishing](issues/archive/0031-packaging-and-publishing.md),
[Windows (mingwX64) target](issues/archive/0032-windows-target.md).

## 3. raven-native

- **`raven.def`**: `headers = Raven.h`, `compilerOpts = -I<repo>/include`,
  `package = raven.cinterop`. The `headerFilter` keeps only Raven's own headers, without
  `raven_backend.h` and `raven_vulkan.h`.
- **`raven.native`** is a small public layer that holds only what node code needs again and again:
  - **Callback registry**: one `StableRef` per widget, passed as the `user_data` of every callback
    and disposed from `Rwidget_set_destroy_callback`. The `Rid` the destroy callback receives is
    already dead. The reference is also the widget's Raven user handle, so a widget leads back to
    its node, and a widget can't be registered twice. The registry can report how many references
    are live, for the tests in [section 9](#9-testing).
  - **String conversion** at the C boundary.
  - **UI-thread check**.
- There are no handle value classes and no wrappers per widget. Nodes call `raven.cinterop`
  directly for everything else.
- No Raven type appears in the public API of `compose-raven-ui` or `compose-raven-foundation`. Raven enums get Kotlin
  counterparts (such as `TextAlign`) only where a composable needs them, and they are mapped
  internally.

Decided in: [Layering over cinterop](issues/archive/0006-cinterop-layering.md).

## 4. Application host

Entry points follow Compose Desktop, in `raven.compose.ui`:

```kotlin
fun main() = application {                        // this: ApplicationScope
    Window(onCloseRequest = ::exitApplication, title = "Notes") { ... }
    if (showPrefs) Window(onCloseRequest = { showPrefs = false }, title = "Preferences") { ... }
}
fun main() = singleWindowApplication(title = "Hello", width = 800, height = 600) { ... }
```

- **`application(followSystemAccessibility = true) { }`** blocks, and it must be called from `main()` on the main thread, which
  becomes the **UI thread** (AppKit requires this). It ends when its content has no `Window` left,
  or when `ApplicationScope.exitApplication()` is called. `singleWindowApplication(title, width,
  height, ...)` is shorthand for `application { Window(onCloseRequest = ::exitApplication, ...) }`.
  At start, the host calls `Raccessibility_follow_system()` unless `followSystemAccessibility` is
  false, so text scale, contrast and reduced motion follow the OS.
  - **In phase 1**, `application`, `ApplicationScope.exitApplication()` and `Window(onCloseRequest,
    title, width, height) { }` are already public, so `samples/hello`'s Quit button can end the app.
    The rest of `Window`'s parameters, `WindowState` and windows that come and go arrive with
    phase 3.
- **`Window(onCloseRequest, state: WindowState = rememberWindowState(), title, customHeader = false, transparent = false, undecorated = false, resizable = true, background: Color = Color.Unspecified, onKeyEvent: (KeyEvent) -> Boolean = { false }, icon: Painter? = null, island: (@Composable () -> Unit)? = null, backKeys: BackKeys = BackKeys.Default) { }`**:
  - **Closing** is controlled: Raven's close callback always refuses and calls `onCloseRequest`,
    and the window is destroyed when the app removes `Window` from the composition.
  - **Window flags**: `customHeader` uses `R_WINDOW_CUSTOM_HEADER` (for a `HeaderBar`,
    [section 8](#8-component-vocabulary)), `transparent` uses `R_WINDOW_TRANSPARENT`, and
    `undecorated` uses `R_WINDOW_BORDERLESS`. `background` uses `Rwindow_set_clear_color`.
  - **`WindowState`** holds `size`, `position`, `placement` (Floating or Maximized) and
    `isMinimized`. Writes call Raven's setters, and Raven's resize and move callbacks write back.
  - **Composition**: a window emits no node into its parent. It has its own composition, with its
    own Overlay root and Applier, under the one Recomposer, linked with
    `rememberCompositionContext()`.
  - **Owners**: each window provides a `LifecycleOwner` and a `SavedStateRegistryOwner`. They
    persist nothing yet, but they are enough for Navigation 3 and lifecycle-aware libraries. The
    lifecycle follows Compose Desktop: **RESUMED** while the window is shown and active (focused),
    **STARTED** while shown but not active, **CREATED** while minimised or hidden, and
    **DESTROYED** when the window leaves the composition.
  - **`WindowBackdrop(backdrop: Backdrop)`** inside a window calls `Rwindow_set_backdrop` for that
    window. `Backdrop` is `None`, `Transparent`, `Blur`, `Acrylic` or `Mica`, and a warning is
    logged when the window isn't transparent or the system refuses.
- **`Dialog(onDismissRequest, properties: DialogProperties = DialogProperties()) { }`** is a
  Raven sheet over its window.
  - Its content has its own composition, rooted in a panel given to `Rdialog_open`. When it leaves
    the composition, that composition is disposed, then `Rdialog_close` is called.
  - Escape calls `onDismissRequest`, through `Rdialog_set_dismiss_callback`
    ([fork change 4](#11-required-raven-changes)), and the app decides.
  - `DialogProperties` has `dismissOnBackPress` (Escape). A Raven dialog is a modal sheet: a click
    outside it does nothing, so there is no `dismissOnClickOutside`.
- **Message boxes and file dialogs** are suspend functions: `messageBox(title, text, buttons):
  MessageResult`, `openFileDialog(filters, multiple): List<String>`, `saveFileDialog(...)` and
  `selectFolderDialog(...)`.
  - By default they are built on `Dialog` and `FileBrowser`, so they never block, run no nested
    frames, and work headless in tests.
  - `native = true` uses Raven's blocking calls (`Rmessage_box`, `Rfile_dialog_*`) as a task on
    `RavenDispatcher`. Raven runs frames while those wait, and Compose recomposes in them safely
    (the nested-frames check of the roadmap). The file dialogs are the OS's on Windows, and on
    Linux with zenity or kdialog; everywhere else, and for every message box, they are Raven's
    own, only blocking. Calls that wait at the same time return in reverse order: the first one
    opened resumes only once every later one has closed.

The setup lives in an internal `RavenHost` with the interface `start(content)`, `frame()` and
`close()`. `application` is `RavenHost` plus `while (Rwait_frame(-1.0))`. The test harness is
`RavenHost` plus frames it drives itself ([section 9](#9-testing)).

- **Start**: `Rinit`, then start the application composition. It emits no nodes. Each `Window`
  in it creates a Raven window, its fixed root ([section 5](#5-node-model-and-applier)) and its
  own composition.
- **Dispatcher**: an internal `RavenDispatcher : CoroutineDispatcher`. `isDispatchNeeded` is
  always `true`, so Applier work never runs re-entrantly inside a Raven callback. It isn't public
  and isn't `Dispatchers.Main`.
  - Blocks wait in a queue of its own, behind a lock. The first dispatch into an empty queue
    calls `Rtask_post` with one drain task, which runs the queue until it is empty, including
    blocks queued while it runs. Raven runs only the batch of tasks it took at the start of the
    frame, so with one task per block, a snapshot apply that resumes the Recomposer would only
    recompose a frame later.
  - A block can wait in nested frames (a `native = true` dialog) while the drain task still counts
    as posted, so what is dispatched meanwhile posts no task. Each drain that runs a block arms a
    guard, a one-shot Raven timer due at once: it keeps the nested loop from sleeping, and drains
    when it fires. A drain whose blocks all return destroys it unfired, so it costs no wake-up.
  - After shutdown it drops dispatches instead of calling Raven. The drain task still posted runs
    in `Rshutdown`'s last run of the task queue and finds nothing.
- **Frame clock**: a `BroadcastFrameClock`. Its `onNewAwaiters` calls `Rwake_up()`.
  compose-raven owns the single `Rset_frame_callback` slot outright. The callback drains the
  dispatcher, calls `sendFrame((Rget_time() * 1e9).toLong())`, then drains again.
  - The Recomposer recomposes and applies inside `sendFrame`, and the frame callback runs after
    tasks and before layout, so a recomposition is laid out and painted in the same frame.
  - The drain before takes in what was dispatched after the frame's tasks ran (a timer's state
    write). The drain after lets the frame awaiters that `sendFrame` resumed (animations,
    `withFrameNanos` loops) take their step in this frame, so nothing is left queued after it.
- **Recomposer**: `runRecomposeAndApplyChanges()` in a scope of `RavenDispatcher` plus the clock.
- **Snapshot apply pump**: `Snapshot.registerGlobalWriteObserver` sets a flag. If the flag wasn't
  already set, it dispatches one `Snapshot.sendApplyNotifications()`. `Rtask_post` is thread-safe
  and wakes `Rwait_frame`, so a state write from any thread wakes the loop.
- **When the loop ends**: windows refuse Raven's close, so the user never ends `Rwait_frame`.
  When the content removes its last `Window`, Raven itself returns false, since it ends the loop
  once every window it had is gone. `exitApplication()` calls `Rquit()` directly.
- **Ownership rule**: some Raven containers take ownership of widgets and delete them
  (`Rwindow_set_content`, `Rdialog_open`, `Rwindow_set_island`, a window and everything in it). A
  composition is always **disposed before its Raven owner is destroyed**, so no node ever holds a
  widget that Raven deleted behind it:
  - **Removing a `Window`**: dispose its island, dialog and content compositions (innermost
    first), then `Rwindow_destroy`.
  - **Closing a `Dialog`**: dispose its composition, then `Rdialog_close`.
  - **Removing a window-level `Island`**: dispose its composition, then `Rwindow_set_island(window,
    R_INVALID_ID)`.
- **Shutdown**, when `Rwait_frame` returns false:
  1. Remove every remaining window as above, then dispose the application composition.
  2. Cancel the Recomposer, then its scope, and drain the dispatcher so the cancelled coroutines
     finish (the Recomposer unregisters its snapshot observer as it ends).
  3. Unregister the write observer.
  4. Clear every Raven-handle cache of the host and advance the host epoch (see below).
  5. Close the dispatcher, then `Rshutdown()`.
- **Handles and Raven instances**: Raven's handles carry only an 8-bit generation, and `Rinit`
  resets every pool. So after `Rshutdown` and a new `Rinit` (every test does this), an old `Rid`
  can name a **different, valid** object. Two invariants prevent that:
  - **Caches belong to the host.** Fonts, `painterResource` images, `ShaderEffect`s, theme colours
    and typography sizes are cached in the `RavenHost`, never in process-wide objects, and are
    cleared at shutdown.
  - **Every `Rid` kept outside a node carries the host epoch.** A counter advances with every
    `Rinit`. `ImageBitmap`, cached fonts and effects keep the epoch they were made in. Using one
    from an older epoch throws, and a `Cleaner` or `close()` from an older epoch does nothing.
- **Errors**: a `CoroutineExceptionHandler` on the scope, together with the C-boundary catch in
  [section 7](#7-events-and-state), records the first throwable and calls `Rquit()`. Callbacks
  that run on Raven's render thread (`VulkanViewport.onDraw`) catch too, record the throwable
  under a lock, and call `Rquit()`, which is thread-safe. After the normal shutdown, `application`
  rethrows the first throwable.
- **Exit**: `ApplicationScope.exitApplication()` calls `Rquit()`, which is thread-safe. The loop
  also ends when no `Window` is left.

Decided in: [Frame loop and threading](issues/archive/0008-frame-loop-and-threading.md),
[Testing strategy](issues/archive/0012-testing-strategy.md),
[Multiple windows, dialogs and popups](issues/archive/0024-multiple-windows.md).

## 5. Node model and Applier

- **Node**: a single internal class `RavenNode` in `raven.compose.ui`. It holds:
  - the widget's `Rid`
  - one `StableRef` to itself
  - the Kotlin event lambdas ([section 7](#7-events-and-state))
  - its folded layout values ([section 6](#6-layout-and-modifiers))
  - a mirror list of its child nodes
  - the kind of children its widget takes: **none**, **single** or **many**. Raven keeps this in
    private flags, so each composable states it.

  Composables emit `ComposeNode<RavenNode, RavenApplier>` with a factory that creates the widget
  (`RavenNode(Rwidget_create(...), ...)`) and `Updater.set` setters.
- **Lifetime**: the factory creates the widget. `Applier.remove` only detaches it
  (`Rwidget_remove_child`), so `movableContentOf` can insert the same node again somewhere else.
  `RavenNode` implements `ComposeNodeLifecycleCallback`: `onRelease` calls `Rwidget_destroy`, and
  the destroy callback disposes the `StableRef`. Phase 1 has no node reuse (no `ReusableComposeNode`),
  so `onReuse` and `onDeactivate` do nothing.
- **Root**: the host creates an `R_WIDGET_OVERLAY`, sets it once with `Rwindow_set_content`, and
  wraps it as the Applier's root node. It stacks its children like `Box`, and the Applier never
  replaces it.
- **Inserts** are bottom-up, through `Rwidget_insert_child(parent, child, index)`. The Applier
  rejects an insert into a node that takes no children, in Kotlin, with a clear error.
- **Properties**: each `Updater.set` calls the cinterop setter directly. There is no batching in
  `onEndChanges`, because Raven lays out lazily later in the same frame and setters don't fire
  change callbacks.
- **Moves** use `Rwidget_move_child(parent, from, to)` ([section 11](#11-required-raven-changes)).
  The Applier calls it once for each of Compose's `count` items, following `AbstractApplier`'s
  own list move so Raven stays in step with the mirror list. Moving back, each item goes from
  `from + i` to `to + i`. Moving forward, the block's first item goes from `from` to
  `to - 1` each time. A single item next to its target is swapped, as `AbstractApplier` does.
  Within a single-child parent, a move only reorders the mirror list.
- **DockArea children**: Raven moves dock panels by itself (docking, tearing into floating
  windows), so the Applier makes no index-based Raven calls on a DockArea's children. Insert uses
  `Rdock_area_add_panel`, removal destroys the panel, and moves only reorder the mirror list.
- **Content of single-child widgets**: a composable over a single-child widget (Button, Border,
  Expander, ...) emits two nodes: the widget, plus an internal **content node** holding the
  composable's content. That node is a Row for buttons and a Box for the others. So content may
  emit any number of children. The content node is invisible plumbing: it has `R_ROLE_NONE`, takes
  no Modifier, and is never found by test finders.
- **Single-child parents** (Border, SizeBox, Button, Expander, BackgroundBlur) destroy their
  existing child when a second one is inserted. Before inserting into one, the Applier detaches
  the Raven child currently attached. That child stays alive in the mirror list. Removing a node
  that is no longer attached skips the Raven call.

### The binding layer

Most composables are the same few protocols over different Raven widgets, so those protocols are
one internal module in `compose-raven-ui`, written before any composable, and each composable is a
thin declaration over it:

- **Widget descriptor**: the Raven widget type, the kind of children (none, single, many), whether
  it has a content node and of which kind, and the default accessible role.
- **Property binding**: a typed setter per property, called by `Updater.set` only when the value
  changes.
- **Event binding**: the callback kinds a widget has, registered once per node, dispatching to the
  lambdas in the node's fields, with the C-boundary catch built in. Raven's single generic event
  callback is shared by all input modifiers of the node. An event kind can name the controlled
  value it changes, and the dispatch marks it before calling the lambda.
- **Content node**: `RavenWidget(descriptor, update) { content }` emits the widget and, for a
  descriptor with a content kind, the content node inside it.
- **`ControlledValue<T>(read, write, equals)`**: the controlled-input protocol of
  [section 7](#7-events-and-state) (remember the composed value, mark on a user change, check after
  the next applied frame, write back on difference). `Checkbox`, `Slider`, `TextField`,
  `ComboBox`, `SpinBox`, `CodeEditor`, `Expander`, `TabView`, `Island`, `ListView`, `TreeView`
  and `CheckboxItem` all use it.

Decided in: [How Mosaic is built](issues/archive/0003-how-mosaic-is-built.md),
[Raven widget tree API for an Applier](issues/archive/0004-raven-widget-tree-api.md),
[Node model and Applier](issues/archive/0007-node-model-and-applier.md).

## 6. Layout and Modifiers

- **`Modifier`**: compose-raven has its own immutable chain of elements in `raven.compose.ui`.
  Compose UI's `Modifier` would bring in Compose UI. Every composable takes
  `modifier: Modifier = Modifier`. The node folds the chain into flat layout values, compares them
  with the previous set, and calls only the `Rslot_*` and `Rwidget_set_*size` setters that changed.
  Settings that belong to the widget itself, such as a panel's spacing or a label's text, are
  composable parameters.
- **`Dp`**: a value class in `raven.compose.ui.unit` (`16.dp`). 1.dp is one Raven logical unit
  and is passed through unchanged.
- **Packages follow Compose's**: `Modifier`, `Alignment` and `testTag` are in `raven.compose.ui`.
  The modifier functions (`padding`, `size`, `fillMaxWidth`, ...), `Arrangement`, the scopes and
  `Column`/`Row`/`Box` are in `raven.compose.foundation.layout`. The elements they create, and the
  folding into slot values, are in `compose-raven-ui`.
- **Placement needs the parent**: `fillMaxWidth` means a fill alignment in a Column and a weight
  in a Row, and a child without an alignment takes its container's. So the Applier records each
  node's parent when it inserts it, and the node resolves its folded Modifier against the
  parent's panel layout (axis and default alignments). A container whose layout changes places
  its children again. A content node never applies a Modifier: a Box content node keeps Raven's
  fill defaults and fills its widget, and a Row content node (a button's) is centred in its
  widget, with its children centred vertically, as a Compose Button's content is.
- **Containers**: `Column` uses VBox, `Row` uses HBox and `Box` uses Overlay.
- **Defaults are Compose's**: each node sets its slot to start/top alignment when it is created,
  overriding Raven's `FILL`. Stretching is opt-in.
- **Phase 1 elements**:

| Element | Scope | Raven |
|---|---|---|
| `padding(all)`, `padding(horizontal, vertical)`, `padding(start, top, end, bottom)` | any | `Rslot_set_padding`. Several paddings add up. |
| `size`, `width`, `height` | any | `Rwidget_set_size` |
| `sizeIn`, `widthIn`, `heightIn` | any | `Rwidget_set_min_size` / `Rwidget_set_max_size` |
| `fillMaxWidth()`, `fillMaxHeight()`, `fillMaxSize()` (no fraction) | any | Cross axis, and both axes in `Box`: `FILL` alignment. Main axis of Row or Column: like `weight(1f)` |
| `weight(Float)` | `RowScope`, `ColumnScope`, `SplitterScope` | `Rslot_set_fill`. In a splitter it is the pane's relative size, 1 by default, and panes fill across. In a scroll container each child keeps its own size, so it is ignored with a warning |
| `align(Alignment)` / `align(Alignment.Horizontal)` / `align(Alignment.Vertical)` | `BoxScope` / `ColumnScope` / `RowScope` | `Rslot_set_halign` / `_valign` |
| `testTag(String)` | any | stored on the node only, with no Raven call ([section 9](#9-testing)) |
| `tooltip(String)` | any | `Rwidget_set_tooltip`, text only |
| `semantics { }` | any | Raven's accessible name, role, description and hidden flag ([section 7](#7-events-and-state)) |
| `clickable`, `combinedClickable`, `onPointerEvent`, `pointerHoverIcon`, `onKeyEvent`, `focusable`, `focusRequester`, `onFocusChanged` | any | Raven's event callback, focus and cursor calls ([section 7](#7-events-and-state)) |
| `appearTransition(AppearTransition, delay)` | any | `Rwidget_set_transition`: plays each time the widget appears |
| `material(Material)` | any | `Rwidget_set_material` on the composable's own (outer) widget; `Material.Default` clears it ([section 8](#8-component-vocabulary)) |

  For size and alignment, the last element in the chain wins. Padding is always outside the widget,
  because phase 1 has no drawing modifiers.
- **Which widget a Modifier acts on**: always the composable's **outer** widget, never its content
  node ([section 5](#5-node-model-and-applier)). A modifier that a composable can't honour (for
  example `clip` outside `Image`, `weight` outside a Row or Column, `cell` outside a `Grid`) is
  ignored with a one-time warning naming the composable, never silently.
- **Arrangement and container alignment** keep Compose's parameter shape, for example
  `Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start)`.
  Phase 1's `Arrangement` offers only `Top`/`Start` and `spacedBy(dp)`, which uses `Rpanel_set_spacing`.
  The container's alignment sets each child's default cross-axis alignment, and `Modifier.align`
  overrides it.
- `Alignment.Start` and `Alignment.End` map to `LEFT` and `RIGHT`. Raven supports right-to-left
  layout, but compose-raven doesn't expose it yet (Right-to-left layout fog on the phase 3 map).

Decided in: [Layout and Modifiers](issues/archive/0009-layout-and-modifiers.md).

## 7. Events and state

- **Wiring**: the node factory registers one shared `staticCFunction` per event kind the
  composable supports, once, with the node's `StableRef` as `user_data`. The lambdas live in
  mutable fields on `RavenNode`. `Updater.set` only replaces the field, and a null lambda makes
  the callback do nothing. Lambdas run synchronously on the UI thread. Any state they write is
  recomposed later through `RavenDispatcher`.
- **Controlled inputs**, as in Compose: a composable shows the value the app composed. Raven
  changes a widget's own value before it calls back, so each input node remembers the value it
  last composed, and a user change marks the node with the current **apply generation** (a counter
  the host advances each time a frame's changes are applied). The host checks the marked nodes at
  the end of each frame callback, after the frame's recomposition. A marked node is checked once
  the app has answered: a batch was applied after its generation, or nothing is left to recompose
  (a rejected change composes nothing, so it would never see an applied batch). Each checked node
  sets its widget back to the composed value if the two differ:
  - An accepted change already matches, so nothing happens and nothing flickers.
  - A rejected change reverts on that frame.
  - Running animations or effects never delay the check: frame awaiters aren't pending
    recomposition, and an animation that recomposes every frame applies a batch every frame.

  Raven's setters don't fire change callbacks, so nothing loops.
- **Text**: `TextField(value: String, onValueChange)`. Text is written back only when it differs
  from what the widget shows, so typing never moves the caret. Text the app rewrites moves the
  caret to the end. Raven sees only committed text, never an IME preedit, so a write-back can't
  cancel a composition ([verified](../spikes/README.md)).
- **Errors**: a Kotlin exception must not cross a `staticCFunction`. Each shared callback catches
  it and passes it to the host's error path ([section 4](#4-application-host)).
- **Phase 1 events** are only the callbacks that belong to each widget, listed in
  [section 8](#8-component-vocabulary).

### Generic input and focus (phase 3)

- **Plumbing**: a node registers Raven's single event callback once its Modifier chain has an
  input element. The callback dispatches each event to the handlers in chain order, and the first
  one that consumes it returns true to Raven.
- **Pointer**:
  - `Modifier.clickable(enabled, onClick)` fires on a left press then release, and consumes both.
  - `Modifier.combinedClickable(onClick, onDoubleClick)` uses Raven's click count.
  - `Modifier.onPointerEvent(PointerEventType) { event -> }` handles Press, Release, Move, Enter,
    Exit and Scroll. `PointerEvent` has `position`, `button` (Primary, Secondary, Tertiary, Back,
    Forward), the modifiers and `scrollDelta`. It isn't consumed unless the handler calls
    `consume()`.
  - `Modifier.pointerHoverIcon(PointerIcon)` uses `Rwidget_set_cursor`.
- **Keys**: `Modifier.onKeyEvent { event -> Boolean }` handles keys that go to the focused widget
  and bubble up. `KeyEvent` has `key`, `type`, `isCtrlPressed`, `isShiftPressed`, `isAltPressed`,
  `isMetaPressed` and `isRepeat`. `Window(onKeyEvent)` sees what nothing below consumed.
- **Focus**:
  - `Modifier.focusable()` uses `Rwidget_set_focusable`.
  - `FocusRequester` with `Modifier.focusRequester(r)` and `r.requestFocus()` uses `Rwidget_focus`.
  - `Modifier.onFocusChanged { it.isFocused }` comes from Raven's focus events.
  - `LocalFocusManager.current.clearFocus()` calls `Rwindow_clear_focus`
    ([fork change 5](#11-required-raven-changes)). Focusing the root doesn't clear focus, because
    Raven ignores focus requests on widgets that aren't focusable (verified,
    [spikes](../spikes/README.md)). Until the fork change lands, turning the focused widget's
    focusable flag off and on does the same.
  - Tab traversal is Raven's own.
- **Context menus**: `ContextMenuArea(items = { listOf(ContextMenuItem(label) { ... }) }) { }`
  builds an unparented Raven Menu on a right-click and opens it with `Rmenu_open`. The content goes
  in an internal Box.

### Accessibility (phase 3)

Raven exposes every window to the platform screen reader by itself. Widgets report their role,
name, value and state, and a `Text` directly before an input names it.

- **Internal content nodes** get `R_ROLE_NONE`, so the screen reader sees the app's tree.
- **`Modifier.semantics { }`**:
  - `contentDescription` sets the accessible name.
  - `role = Role.Button | Checkbox | Switch | RadioButton | Tab | Image | DropdownList` sets the
    role.
  - `heading()` sets `R_ROLE_HEADING`.
  - `hideFromAccessibility()` hides the widget and its subtree.
  - compose-raven extensions: `accessibleRole = AccessibleRole.<any Raven role>` and
    `accessibleDescription`.
- **Automatic labels**: in a `Row` or `FlowRow`, an input (`Checkbox`, `Switch`, `RadioButton`,
  `Slider`, `TextField`, `ComboBox`, `SpinBox`) that has no `contentDescription` and no `Text`
  directly before it gets `Rwidget_set_accessible_labelled_by` with the `Text` directly after it.
  - The Applier re-checks a parent after its children change.
  - A `Text` below an input in a `Column` is never used as its label.
- **`announceForAccessibility(text, assertive = false)`** uses `Raccessibility_announce`.

Decided in: [Events and state](issues/archive/0010-events-and-state.md),
[Generic input and focus](issues/archive/0025-generic-input-and-focus.md),
[Accessibility semantics](issues/archive/0026-accessibility-semantics.md).

## 8. Component vocabulary

Phase 1 covers what the translator sample and a basic settings form need. All composables take
`modifier: Modifier = Modifier`.

| Composable | Raven widget | Notes |
|---|---|---|
| `Column`, `Row`, `Box` | VBox, HBox, Overlay | [section 6](#6-layout-and-modifiers) |
| `Text(text, modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontFamily, fontWeight, fontStyle, textAlign, softWrap, style: TextStyle = LocalTextStyle.current)` | Label | explicit parameters override `style`. `color` uses `Rlabel_set_color`, `fontSize` (`14.sp`) uses `Rlabel_set_text_size`, and the font uses `Rlabel_set_font` with the variant for the weight and style |
| `Button(onClick, modifier, enabled) { content }` | Button, `R_BUTTON_PRIMARY` | the content goes in an internal Row (HBox, vertically centred), as Compose's `RowScope` does; never `Rbutton_set_text` |
| `OutlinedButton(...) { }`, `TextButton(...) { }` | Button, `R_BUTTON_DEFAULT` / `R_BUTTON_FLAT` | as `Button` |
| `Checkbox(checked, onCheckedChange)` | Checkbox, `R_CHECKBOX_CHECK` | controlled. Raven's own checkbox text isn't used; the label is a `Text` in a `Row` |
| `Switch(checked, onCheckedChange)` | Checkbox, `R_CHECKBOX_SWITCH` | as `Checkbox` |
| `RadioButton(selected, onClick)` | Checkbox, `R_CHECKBOX_RADIO` | the controlled check makes the app's state override Raven's sibling unchecking: Raven reports each sibling it unchecks, so each is marked, and only the radio that became checked calls `onClick` |
| `TextField(value, onValueChange, modifier, enabled, readOnly, placeholder: String, singleLine = true)` | TextInput | single line only: `singleLine = false` fails |
| `Slider(value, onValueChange, modifier, enabled, valueRange, steps)` | Slider | controlled. `steps` values lie between the ends, as in Compose, so Raven's step is `range / (steps + 1)` |
| `LinearProgressIndicator(progress)` | ProgressBar | |
| `HorizontalDivider()`, `VerticalDivider()` | Separator | |
| `Spacer(modifier)` | Spacer | no children |
| `VerticalScroll(modifier, verticalArrangement, horizontalAlignment) { }`, `HorizontalScroll(modifier, horizontalArrangement, verticalAlignment) { }` | ScrollBox | a scrolling Column or Row, with `ColumnScope`/`RowScope`. The ScrollBox stacks the children itself, each at its own size |
| `CodeEditor(value, onValueChange = {}, modifier, language: String? = null, readOnly = false, lineNumbers = true)` | TextEditor | controlled. Its change callback carries no text, so the node reads `Rtext_editor_get_text`. `language` is a name for `Rtext_editor_set_language`; an unknown one shows plain text (Raven warns). Text the app rewrites also clears Raven's undo history |
| `HorizontalSplitter(modifier) { }`, `VerticalSplitter(modifier) { }` | Splitter | each child is a pane it fills. In `SplitterScope`, `Modifier.weight` is its relative pane size, 1 by default. Dragging a handle keeps the new sizes until a weight changes |

### Phase 2: menus and the header bar

Added for the `notepad` port:

| Composable | Raven widget | Notes |
|---|---|---|
| `MenuBar { }` | MenuBar | a layout composable; `MenuBarScope` allows only `Menu` |
| `Menu(text) { }` | Menu | `MenuScope` allows `Item`, `CheckboxItem`, `Separator` and sub-`Menu` |
| `Item(text, onClick, shortcut: KeyShortcut? = null, enabled = true)` | MenuItem | no children |
| `CheckboxItem(text, checked, onCheckedChange, shortcut, enabled)` | MenuItem, checkable | controlled |
| `Separator()` (in `MenuScope`) | MenuItem with `Separator = true` | a plain Separator widget is not a menu separator in Raven, but a MenuItem with the `Separator` attribute (`Rwidget_set_attribute`) is, and can be inserted at an index (verified, [spikes](../spikes/README.md)) |
| `HeaderBar(modifier, title: String? = null, buttons: Set<WindowButton> = WindowButton.all()) { }` | HeaderBar | needs `Window(customHeader = true)`, otherwise a warning is logged; in `HeaderBarScope`, `Modifier.align(Alignment.End)` places a child on the right |

`KeyShortcut(key: Key, ctrl, shift, alt, meta)` uses `Rmenu_item_set_shortcut`, where `meta` is
Super (Cmd on macOS). `Key` mirrors `RKey`: letters, digits, F1 to F12, and the named keys.

### Phase 2: ComboBox and SpinBox

Added for the `notepad`, `themes` and `overlay` ports (and `glass` in phase 3). All three are controlled.

| Composable | Raven widget | Notes |
|---|---|---|
| `ComboBox(items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit, modifier, enabled = true)` | ComboBox | -1 means nothing selected. When `items` changes (by value), the node calls `clear_items` and `add_item` again |
| `SpinBox(value: Double, onValueChange: (Double) -> Unit, modifier, valueRange = 0.0..100.0, step = 1.0, decimals = 2, enabled = true)` | SpinBox | Raven's `Double` and defaults |
| `SpinBox(value: Int, onValueChange: (Int) -> Unit, modifier, valueRange: IntRange = 0..100, step = 1, enabled = true)` | SpinBox | `decimals = 0`, values rounded with `roundToInt()` |

`codeEditorLanguages(): List<String>` lists Raven's editor languages (`Rlanguage_get_count`/`_name`).
It is valid only inside the composition.

### Phase 2: Surface, FlowRow and Expander

Added for the `components` port:

| Composable | Raven widget | Notes |
|---|---|---|
| `Surface(modifier, shape: Shape = RectangleShape, color: Color = Color.Unspecified, border: BorderStroke? = null, shadowElevation: Dp = 0.dp) { }` | Border | `Unspecified` means the theme's panel colour. Inner padding is the content's own `Modifier.padding`. `shadowElevation` maps to blur = elevation and y offset = elevation / 2. The content goes in an internal Box |
| `FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(x)) { }` | WrapBox | `x` is both the gap between items and the gap between lines |
| `Expander(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier) { }` | Expander | controlled; the content goes in an internal Box |

Graphics types live in `raven.compose.ui.graphics`:

- **`Color`** is a value class: `Color(0xFF3B82F6)`, `Color(red, green, blue, alpha)`, and the
  constants `Black`, `White`, `Gray`, `Red`, `Green`, `Blue`, `Yellow`, `Transparent` and
  `Unspecified`.
- **`Shape`** is sealed: `RectangleShape`, `RoundedCornerShape(all)`,
  `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)`, and `CircleShape`, which is a
  large radius that Raven clamps.
- **`BorderStroke(width: Dp, color: Color)`** uses `Rborder_set_outline`.

### Phase 2: theming

Added for the `themes` port:

- **`RavenTheme(look: Look? = null, dark: Boolean? = null, theme: String? = null) { }`**, in two
  places:
  - **App-wide, at `application` level**: `application { RavenTheme(look = ..., theme = ...) {
    Window(...) { } } }`. There it emits no node. `look` and `dark` become one `Rlook_set`, `theme`
    uses `Rtheme_use`, and its CompositionLocals reach every window's composition through
    `rememberCompositionContext`. Only this placement may set app-wide settings (`look`, `dark`,
    `material`, an app-wide `theme` or `fontFamily`), so two windows can never disagree about
    them. In `singleWindowApplication`, the `RavenTheme` at the top of its content counts as
    application level.
  - **Inside a window**: a subtree setting. `theme` gives the subtree its own theme through an
    internal Box and `Rwidget_set_theme`, `fontFamily` uses `Rwidget_set_font`, and `typography`
    is a CompositionLocal. Setting an app-wide setting here logs an error.
  - `Look` is one of `Raven`, `Glass`, `HighContrast`, `Windows`, `Macos`, `Gnome`, `Native` or
    `MacosGlass`.
- **`loadTheme(path): String`** and **`loadThemeFromString(xml): String`** load theme files and
  return the name for `theme =`. They are valid only while Raven runs.
- **`RavenTheme.colors`** is a CompositionLocal with one `Color` per Raven colour role, read from
  the app theme with `Rtheme_get_color`. It doesn't see subtree themes.
- **Colour parameters** follow Material: `XxxDefaults.colors(...)` returns `XxxColors`, passed as
  `colors =`. Every default is `Color.Unspecified`, meaning "keep the Raven theme's value", so
  only the colours given call `Rwidget_set_color`.

  | Composable | Parameter | Raven roles |
  |---|---|---|
  | `Button`, `OutlinedButton`, `TextButton` | `ButtonDefaults.buttonColors(containerColor, contentColor, disabledContentColor)` | `ACCENT` (`CONTROL` for Outlined/Text), `ACCENT_TEXT` (`TEXT`), `TEXT_DISABLED` |
  | `Checkbox` | `CheckboxDefaults.colors(checkedColor, uncheckedColor, checkmarkColor)` | `ACCENT`, `CONTROL_BORDER`, `ACCENT_TEXT` |
  | `RadioButton` | `RadioButtonDefaults.colors(selectedColor, unselectedColor)` | `ACCENT`, `CONTROL_BORDER` |
  | `Switch` | `SwitchDefaults.colors(checkedTrackColor, uncheckedTrackColor, thumbColor)` | `SWITCH`, `TRACK`, `HANDLE` |
  | `Slider` | `SliderDefaults.colors(thumbColor, activeTrackColor, inactiveTrackColor)` | `HANDLE`, `ACCENT`, `TRACK` |
  | `TextField` | `TextFieldDefaults.colors(containerColor, textColor, placeholderColor, focusedIndicatorColor, selectionColor)` | `INPUT`, `TEXT`, `TEXT_PLACEHOLDER`, `FOCUS`, `SELECTION` |
  | `LinearProgressIndicator` | `color`, `trackColor` | `ACCENT`, `TRACK` |
  | `HorizontalDivider`, `VerticalDivider` | `color` | `SEPARATOR` |
  | `Surface` | `contentColor` | `TEXT`, passed down to the children |

  **Hover and pressed shades:** Raven derives them only for a theme's accent (`Rtheme_set_accent`),
  not for `Rwidget_set_color` ([verified](../spikes/README.md)). So when a `colors` parameter sets
  `ACCENT` or `CONTROL`, compose-raven also sets the hovered and pressed roles, following the
  per-look rules of Raven's `rvn_theme_set_accent`.
- **Shape and padding:** buttons take `shape` and `contentPadding: PaddingValues`, and `TextField`
  takes `shape`.
  - `shape` uses `R_METRIC_CORNER_RADIUS`, which is a single radius. A non-uniform shape uses
    `topStart` and logs a warning.
  - `contentPadding` uses `R_METRIC_PADDING_X`/`_Y` (start, top). Non-symmetric values log a
    warning.

### Phase 2: glass and BackgroundBlur

Added for the `overlay` and `glass` ports:

- **`Modifier.material(Material)`** gives any widget a material of its own: `Default`, `Solid`,
  `Glass`, `FrostedGlass` or `TintedGlass`. On `Surface`, `color` then tints the glass:
  `Surface(Modifier.material(Material.FrostedGlass), color = tint)`.
- **`RavenTheme(..., material: Material? = null)`** sets the material app-wide with
  `Rtheme_set_material`. Only an application-level `RavenTheme` may set it. Controls get glass from the
  Glass and macOS Glass looks.
- **`BackgroundBlur(modifier, style: BlurStyle = BlurStyle.Frosted, shape: Shape = RectangleShape, tint: Color = Color.Unspecified, radius: Dp = Dp.Unspecified, saturation: Float? = null, grain: Float? = null, shadowElevation: Dp = 0.dp) { }`**:
  - It is a container over BackgroundBlur, and its content goes in an internal Box.
  - `BlurStyle` is `Plain`, `Frosted`, `Glass` or `Native`; with `Native`, the OS blurs the
    desktop.
  - `radius = Unspecified` uses the theme's blur radius.
  - Use `Modifier.material(FrostedGlass)` for an ordinary glass panel, and `BackgroundBlur` for
    `Native`, `Plain`, or control over the blur.
- The theme's glass metrics (frost, refraction, rim, ...) are set in theme files only.

### Fonts and text styling (phase 3)

- **`FontFamily(vararg fonts: Font)`**, with `Font(path: String or bytes: ByteArray, weight, style)`:
  - The regular font is the family's Raven handle, and the others attach with `Rfont_set_variant`.
  - It is loaded on first use and cached in the `RavenHost` until shutdown ([section 4](#4-application-host)).
  - `FontFamily.Default` is Roboto and `FontFamily.Monospace` is Roboto Mono.
  - Paths resolve as [Image and resources](issues/archive/0035-image-and-resources.md) decides.
- **Weight and style**: `FontWeight` W100 to W500 mean Regular and W600 to W900 mean Bold.
  `FontStyle` is Normal or Italic. Raven synthesises the styles that have no file.
- **`TextStyle(color, fontSize, fontFamily, fontWeight, fontStyle, textAlign)`** is read from
  `LocalTextStyle` and changed for a subtree with `ProvideTextStyle(style) { }`. `TextField` and
  `CodeEditor` take a `textStyle`.
- **`RavenTheme(fontFamily: FontFamily? = null)`**: at application level it sets `Rtheme_set_font`,
  and inside a window it uses `Rwidget_set_font` on its subtree. The starting `LocalTextStyle` comes from the
  theme's font size metric.
- **`MarkdownText(markdown, modifier, style, onLinkClick = null)`** uses `Rlabel_set_markdown` and
  `Rlabel_set_link_callback`.

### Custom drawing, viewports and effects (phase 3)

- **`Canvas(modifier) { }`** is a custom widget, sized by its Modifier.
  - `DrawScope` has `drawRect`, `drawRoundRect`, `drawCircle` and `drawLine`, with `style = Fill
    | Stroke(width)`, plus `clipRect { }` and `drawText`.
  - Raven extensions: `drawCubic`, `drawTriangle`, `drawGlass` and `drawShader`. There is no
    `Path`.
  - `onDraw` is read-observed, and a change in state it read calls `Rwidget_invalidate`.
- **Viewports**:
  - `Viewport(image: ImageBitmap, modifier, stretch, flipY)` shows an `ImageBitmap`.
  - `NativeViewport(modifier, onCreated: (NativeWindowHandle) -> Unit, onResize)` uses a native
    child window. On macOS, that is an `NSView` with a `CAMetalLayer`, which is the route for Metal
    through `platform.Metal`. It sits above the UI and is not clipped.
- **`compose-raven-vulkan`**:
  - `VulkanViewport(modifier, realtime = false, onDraw: (VulkanFrame) -> Unit)` uses Raven's draw
    callback. **`onDraw` runs on Raven's render thread**: it records Vulkan commands into
    `VulkanFrame.commandBuffer`, and must not write Compose state or call Raven's UI API. An
    exception it throws is caught on that thread and ends the app through the host's error path.
  - `ImageBitmap.fromVulkanImageView(imageView, sampler, width, height)` is zero copy.
  - `RavenVulkan.device` exposes Raven's Vulkan handles.
  - `application(vulkan = VulkanSetup.External(...))` shares the app's own device.
  - The app brings its own Vulkan bindings.
- **`compose-raven-opengl`**:
  - `OpenGLViewport(modifier, realtime = false, onInit, onDraw, onDispose)` runs on the UI thread
    in a context the module creates on a native child window. It sits above the UI.
  - `OpenGLTexture(width, height)` renders to an FBO read back into an `ImageBitmap`, for GL under the
    UI.
  - The app brings its own GL bindings.
- **Shader effects**:
  - `ShaderEffect.builtin(Effect.Aurora | ...)`, `fromFile(spirv path)` and `fromBytes(...)`,
    cached in the `RavenHost`.
  - `Surface(effect, effectStyle)` uses `Rborder_set_effect`, and `DrawScope.drawShader(...)` uses
    `Rdraw_shader`.

### Lists and trees (phase 3)

- **`LazyColumn { }` / `LazyRow { }`** use Compose's DSL (`items(list, key = { ... }) { item -> }`)
  over `VerticalScroll`/`HorizontalScroll`. In phase 3 they compose every item, with no
  virtualisation. `key` keeps rows stable through `Rwidget_move_child`.
- **`ListView(items, itemText, selectedIndex, onSelectedIndexChange, modifier, onActivate)`** uses
  Raven's virtualised ListView in virtual mode, for text rows. Selection is controlled.
- **`TreeView(roots, children, itemText, key, selection, onSelectionChange, multiSelect,
  initiallyExpanded, onActivate, contextMenuItems, modifier)`** uses Raven's virtualised TreeView,
  diffed by key onto Raven item handles.
  - Selection is a controlled set of keys.
  - Expansion stays Raven's, because Raven reports no expansion. `initiallyExpanded` applies to
    new items only.
  - `contextMenuItems` opens a menu at the pointer.

### Tabs and grids (phase 3)

- **`TabView(selectedIndex, onSelectedIndexChange, modifier, documents = false) { Tab(title,
  closable, onClose) { page } }`** puts each `Tab` page in an internal Box, titled through its slot.
  Selection and closing are controlled: Raven's close callback keeps the tab and calls `onClose`.
- **No Switcher**: use `when (page) { }`.
- **`LazyVerticalGrid(columns = GridCells.Fixed(n), ...) { items(...) }`** uses UniformGrid, and
  composes every item in phase 3. It has one spacing for both directions.
- **`Grid(columns: List<GridTrack>, rows: List<GridTrack>, modifier, spacing) { }`** uses Raven's
  Grid. `GridTrack` is `Auto` or `Fill(weight)`, and children are placed with `GridScope`'s
  `Modifier.cell(column, row, columnSpan, rowSpan)`.

### Images and resources (phase 3)

- **Resource root**: relative paths resolve against `resources/` next to the executable, which
  `application(resourceRoot = ...)` overrides. `registerResource(path, bytes)` embeds a file
  (`Rfile_register_memory`), and Raven then finds it first everywhere.
- **`ImageBitmap`** wraps a Raven texture: `ImageBitmap(width, height, rgba)` with `update(...)`,
  `decodeToImageBitmap(bytes)`, and `ImageBitmap.fromVulkanImageView(...)`. It lives on the GPU.
  App-made ones are freed by a `Cleaner` or by `close()`, both of which do nothing once the
  Raven instance that made it has shut down (the host epoch, [section 4](#4-application-host)).
- **`Image(painter, contentDescription, modifier, contentScale = ContentScale.Fit, colorFilter)`**:
  - `painterResource(path)` decodes asynchronously and is cached in the `RavenHost`.
  - `BitmapPainter(image, srcOffset, srcSize)` shows part of an image (a source region).
  - `ContentScale` is `Fit`, `Crop`, `FillBounds` or `None`.
  - `ColorFilter.tint(color)` tints it.
  - `Modifier.clip(RoundedCornerShape(r))` rounds its corners.
  - Formats are PNG, JPEG, BMP, TGA, GIF and PSD.
- **Icons** (`Painter?`): `Item(icon)`, `Tab(icon)`, `TreeView(itemIcon)`, `Window(icon)`, and
  `DrawScope.drawImage(...)`.

### Transitions and Island (phase 3)

- **`Modifier.appearTransition(AppearTransition.Fade | Pop | Zoom | FlyUp | FlyDown | FlyLeft |
  FlyRight | Drop | SlideLeft | SlideRight, delay)`** plays Raven's transition each time the
  widget appears.
- **`AnimatedVisibility(visible, enter, exit) { }`**:
  - Enter specs map to the nearest Raven transition: `fadeIn` is Fade, `scaleIn` is Pop or
    Zoom, and `slideIn*` are the Fly transitions. Animation specs are ignored.
  - Exit supports `fadeOut()` only, animated with `Rwidget_set_opacity`. Other exits fall back
    to it.
- **`animate*AsState` and `Animatable`** are compose-raven's own small set, on its frame clock.
  Compose's `animation-core` can't be used: it has no macOS or Windows variant, and on Linux it is
  a stub that needs Compose UI (verified, [spikes](../spikes/README.md)). `updateTransition` is
  left out for now.
- **`Island(expanded, onExpandedChange, modifier, expandOnHover, expandOnClick, attached, notch,
  folded, unfolded)`** is controlled. `Window(island = { Island(...) })` floats it at the top of
  the window (`Rwindow_set_island`), in its own composition.

### Docking and the file browser (phase 3)

- **`DockArea(state = rememberDockAreaState(), modifier, movable, tearable) { Panel(name, title,
  initialDock, closable, icon, open, onClose) { } }`**:
  - The arrangement is Raven's. `initialDock` (`Dock.Left/Right/Top/Bottom(size)`,
    `Dock.Tab(of)`) applies when a panel is first added.
  - `state.saveLayout()` and `loadLayout(text)` persist the arrangement, and panels need a
    `name` for that.
  - `open` and `onClose` are controlled, and closing hides the panel.
  - Torn-off panels live in Raven's floating windows but stay in their window's composition, so
    window-level settings still refer to the original window.
- **`FileBrowser(modifier, mode, initialDirectory, filters, multiple, showHidden, controls, places,
  fileName, defaultExtension, onAccept, onCancel, onSelectionChange)`**: the directory is
  Raven's, and `FileFilter` is shared with the file dialogs.

### Toolbar (phase 3)

- **`Toolbar(modifier, contentPadding = PaddingValues(6.dp, 4.dp), horizontalArrangement =
  Arrangement.spacedBy(4.dp)) { }`** is an HBox with `R_ROLE_TOOLBAR`. Its children are centred
  vertically, and `ToolbarScope` adds `ToolbarSeparator()`.
- **Arrangements**:

  ```kotlin
  Column {                                   // like Raven's editor
      MenuBar { /* ... */ }
      Toolbar {
          Button(onClick = ::play) { Text("Pause") }
          TextButton(onClick = ::add) { Text("Add object") }
          ToolbarSeparator()
          Spacer(Modifier.weight(1f))
          TextField(query, { query = it }, placeholder = "Search")
      }
      HorizontalDivider()
  }
  HeaderBar { Toolbar { /* in the title bar */ } }
  Surface(Modifier.material(Material.TintedGlass), shape = CircleShape) { Toolbar { /* floating */ } }
  ```

### Navigation 3 (phase 3, `compose-raven-navigation3`)

Apps use `navigation3-runtime` as on Android (`NavKey`, `entryProvider`, `rememberNavBackStack`)
and apply `org.jetbrains.kotlin.plugin.serialization`, because `NavKey`s are `@Serializable`.

```kotlin
NavDisplay(
    backStack = backStack, onBack = { backStack.removeLastOrNull() },
    sceneStrategy = DialogSceneStrategy<NavKey>() then SinglePaneSceneStrategy(),
    transition = NavTransition(push = AppearTransition.SlideLeft, pop = AppearTransition.SlideRight),
    entryProvider = entryProvider {
        entry<Home> { HomeScreen() }
        entry<ConfirmDelete>(metadata = DialogSceneStrategy.dialog()) { ConfirmDeleteDialog() }
    },
)
```

- **The Scene layer** (`Scene`, `SceneStrategy`, `OverlayScene`, `SinglePaneScene`,
  `DialogScene`, `SceneDecoratorStrategy`, `SceneState`) keeps Navigation 3's names and
  behaviour, adapted from AndroidX 1.2.1 without Compose UI.
- **Dialog destinations** render with `Dialog`, and their dismissal calls `onBack`. They stack.
- **Transitions** are Raven appear transitions, chosen by push or pop. There are none by default.
- **Back** comes from each window's `NavigationEventDispatcher`, fed by the mouse Back button and
  Cmd+[ or Alt+Left (`Window(backKeys)`). Escape only closes dialogs.

### Typography (phase 3)

`RavenTheme(typography = Typography.fromTheme())` provides `RavenTheme.typography`, with roles
sized from Raven's base font size *B* using Raven's own Markdown heading factors:

| Role | Size | Default for |
|---|---|---|
| `display` | 1.9 B | |
| `headline` | 1.52 B | |
| `title` | 1.26 B | |
| `subtitle` | 1.1 B | |
| `body` | B (Raven's own size) | `Text` (`LocalTextStyle`), `TextField` |
| `label` | B (Raven's own size) | content of `Button`, `OutlinedButton`, `TextButton` |
| `caption` | 0.92 B | |
| `code` | B, monospace | `CodeEditor` |

- **Raven's own sizes**: `body`, `label` and `code` leave the size to Raven, so theme files still
  set them. The scaled roles are recalculated when the theme or look changes.
- **Custom typography**: `Typography.fromTheme().copy(...)`. A nested `RavenTheme(typography)`
  changes it for a subtree.
- **Raven-drawn text** (menus, tabs, list and tree rows, header and dock titles) keeps Raven's
  theme font.
- **`MarkdownText` headings** use Raven's factors.

Decided in: [v1 component vocabulary](issues/archive/0011-v1-component-vocabulary.md),
[Menus and the header bar](issues/archive/0018-menus-and-header-bar.md),
[ComboBox and SpinBox](issues/archive/0019-combobox-and-spinbox.md),
[Border, WrapBox and Expander](issues/archive/0020-border-wrapbox-expander.md),
[Theming](issues/archive/0021-theming.md),
[Glass and BackgroundBlur](issues/archive/0022-glass-and-background-blur.md),
[Fonts and text styling](issues/archive/0027-fonts-and-text-styling.md),
[Custom drawing and custom layout](issues/archive/0028-custom-drawing-and-layout.md),
[Lists and trees](issues/archive/0033-lists-and-trees.md),
[Tabs, Switcher and grids](issues/archive/0034-tabs-switcher-and-grids.md),
[Image and resources](issues/archive/0035-image-and-resources.md),
[Island and appear transitions](issues/archive/0036-island-and-appear-transitions.md),
[DockArea and FileBrowser](issues/archive/0037-docking-and-file-browser.md),
[Toolbar](issues/archive/0038-toolbar.md),
[Navigation 3 and scenes](issues/archive/0039-navigation3-and-scenes.md),
[Typography in RavenTheme](issues/archive/0040-typography-in-raventheme.md).

## 9. Testing

- **The harness** lives in `compose-raven-test` (see below) and tests compose-raven itself and
  apps. It is built on `RavenHost` ([section 4](#4-application-host)) and runs
  headless: `Rinit_ex` with `R_BACKEND_NULL`, `headless = R_TRUE`, `worker_threads = -1`, and a
  fixed `Rset_animation_time_step`. `waitForIdle()` keeps calling `Rdraw_frame()` until no
  invalidation, snapshot apply or controlled check is pending. Frame awaiters (running animations,
  `withFrameNanos` loops) don't count, so an infinite animation never stalls a test: time for them
  advances through `mainClock`.
  - `RavenHost.isIdle()` checks for no queued dispatch, no pending snapshot apply, and no
    `Recomposer.hasPendingWork` while the composition frame clock is paused for the check, since
    pausing hides its awaiters.
  - Until phase 3, the harness is internal test code: it lives in foundation's
    `src/testHarness/kotlin`, which foundation's tests and the samples' tests both compile. `RavenHost` and the
    single-window `Window` it hosts, and the binding layer foundation's composables are written
    on, are public in `compose-raven-ui` behind `@InternalComposeRavenApi`. Foundation opts in
    module-wide.
- **API**, a small part of `ComposeUiTest`:
  ```kotlin
  runRavenTest {
      setContent { Counter() }
      onNodeWithText("Add").performClick()
      onNodeWithText("Count: 1").assertExists()
  }
  ```
  - `onNodeWithText`, `onNodeWithTag` and `onNodeWithContentDescription` search compose-raven's
    node tree, leaving out content nodes. Each use waits for idle and expects exactly one match.
  - In phase 1, `onNodeWithText` matches a `Text` by its label's text: it finds the `Text` inside
    a button, not the button, and clicking it clicks the button. `onNodeWithContentDescription`
    matches the accessible name Raven gives a widget other than a `Text`, which Raven derives from
    the text inside when nothing sets it, so a button is found by its label.
  - `performClick()` injects a mouse press and release at the centre of `Rwidget_get_rect`.
  - `performTextInput` clicks the field to focus it, then calls `Rwindow_inject_text`.
  - Assertions include `assertExists` and `assertIsChecked`, plus widget values read through
    `raven-native` getters.
- **Leak checks after every test**: after `close()`/`Rshutdown`, `Rmemory_get_stats` must report
  zero blocks held, and the `raven.native` registry must report zero live `StableRef`s.
- **Isolation**: Raven starts fresh for every test. `Rinit`/`Rshutdown` can be repeated in one
  process with no memory left held ([verified](../spikes/README.md)).
- **Pure logic** gets plain Kotlin unit tests, with no Raven: Modifier folding, move-index
  adjustment and mirror-list bookkeeping. There are no screenshot tests, because the null renderer
  draws nothing.
- **CI**: a compose job in `.github/workflows/build.yml` on a macOS, a Linux and (from phase 3) a
  Windows runner with MSYS2, running `./gradlew -p compose allTests`.
- **What headless tests can't cover** gets a short manual checklist, run before each release on a
  real display: native, OpenGL and Metal viewports, `VulkanViewport`, dock panel tearing, the
  island notch, `native = true` dialogs, window transparency and backdrops, and IME input.

### Public test module (phase 3)

The harness moves into `compose-raven-test`, which both apps and foundation's own tests use. What
it needs from foundation is public behind `@InternalComposeRavenApi` (`@RequiresOptIn`).

- **`runRavenTest(checkLeaks = true) { setContent { } }`** hosts a single window.
- **Finders**: `onNodeWithText(text, substring)`, `onNodeWithTag`, `onNodeWithContentDescription`,
  their `onAllNodesWith...` forms, and `onNode(matcher)`. The matchers (`hasText`, `hasTestTag`,
  `hasContentDescription`, `isEnabled`, `isFocused`, `isToggleable`) combine with `and` and `or`.
- **Actions**: `performClick`, `performTextInput`, `performTextReplacement`, `performKeyPress`
  and `performScrollBy`, all through Raven's input injection.
- **Assertions**: `assertExists`, `assertDoesNotExist`, `assertIsDisplayed`, `assertTextEquals`,
  `assertIsChecked`/`NotChecked`, `assertIsEnabled`/`NotEnabled`, `assertIsFocused` and
  `assertValueEquals`. They read typed node accessors, not Raven getters.
- **`mainClock`**: `autoAdvance` (the default) or stepping with `advanceTimeBy(ms)` /
  `advanceTimeByFrame()`, using Raven's fixed animation time step.
- **Leak checks**: `checkLeaks = true` by default, and a failure lists the composables whose
  widgets were not released.

Decided in: [Testing strategy](issues/archive/0012-testing-strategy.md),
[Public test module for apps](issues/archive/0029-app-testing-module.md).

## 10. Samples

Each sample is its own executable module under `compose/samples/`. A port is idiomatic,
hand-written Compose with the same look and behaviour as the C example it ports.

### `samples/hello`

A port of Raven's `examples/hello`: a 24 sp title, a click counter, and Click me (with a tooltip)
and Quit buttons, centred in a 420 by 220 window. The C example has no timer, so the port has no
`LaunchedEffect`. For it, phase 1 also has `Text(fontSize)` with `TextUnit`/`.sp`,
`Modifier.tooltip` and `application { }` ([section 4](#4-application-host)).

### `samples/xml-translator`

This sample translates Raven XML into compose-raven source.

- **UI**: a `HorizontalSplitter`.
  - The left pane is `CodeEditor(xml, language = "XML")`, starting with the example from
    `docs/XML.md`.
  - The right pane is `CodeEditor(code, readOnly = true, language = "Kotlin")`, using Raven's
    built-in Kotlin language ([section 11](#11-required-raven-changes)).
  - A `Text` below shows the warnings.
  - Translation reruns on every change.
- **Parsing**: Raven's own parser, through `raven-native`'s `raven.cinterop`, which already
  binds `raven_xml.h` (in `compose-raven-xml` from phase 3, in the sample before that, as an
  `implementation` dependency, so no Raven type is in its API). A single adapter,
  `parseRavenXml(text)`, calls `Rxml_document_parse` (UI thread, Raven running) and copies the
  result into a Kotlin `XmlElement(tag, attributes, text, line, children)` tree, then destroys the
  document. It throws `XmlParseException(line, column, reason)` for text that isn't well-formed.
  - Comments are kept as nodes with an empty `tag` (`isComment`), as in Raven's tree, except those
    before the root: Raven keeps them out of the tree, with no getter.
  - Raven gives a node its line only. A column exists only for a parse error.
- **Translator**: `translate(xml: String): Translation(code, warnings)`, working on the Kotlin
  tree, which `translate(root: XmlElement)` takes directly, for the unit tests. A text that doesn't
  parse gives no code and its parse error, with its column, as the one warning. Names are matched
  case-insensitively, through an alias table taken from `docs/XML.md` (also `Name`/`Id`,
  `TextSize`/`FontSize`, and the five action spellings). `Box` is Raven's alias for `SizeBox`, not
  `Overlay`, so it is not in the vocabulary.
- **Mapping** (exactly the [phase 1 vocabulary](#8-component-vocabulary)):

  | XML | Compose |
  |---|---|
  | `Window` | `fun main() = singleWindowApplication(...)` |
  | `VBox`, `HBox`, `Overlay` | `Column`, `Row`, `Box` |
  | `ScrollBox` | `VerticalScroll` / `HorizontalScroll` |
  | `Splitter` | `HorizontalSplitter` / `VerticalSplitter` |
  | `Label` | `Text` |
  | `Button` by `Style` | `Button` / `OutlinedButton` / `TextButton`, with `{ Text(...) }` |
  | `Checkbox` by `Style` | `Checkbox` / `Switch` / `RadioButton`, each in a `Row` with a `Text` |
  | `TextInput` | `TextField` |
  | `TextEditor` | `CodeEditor` |
  | `Slider` | `Slider` |
  | `ProgressBar` | `LinearProgressIndicator` |
  | `Separator` | `HorizontalDivider` / `VerticalDivider` |
  | `Spacer` | `Spacer` |
  | `Slot.*`, `Width`/`Height`, `Min*`/`Max*` | Modifiers |
  | `Spacing` | `Arrangement.spacedBy` |
- **Faithful output**: wherever the XML relies on Raven's `Fill` default, the output adds
  explicit `fillMax*` modifiers. What a child gets depends on what its parent reads in Raven:
  - In a `Column` or `VerticalScroll`, `Slot.HAlign` gives `fillMaxWidth()` (Fill) or
    `align(Alignment.Start/CenterHorizontally/End)`. In a `Row` or `HorizontalScroll`, the same
    for `Slot.VAlign`. In a `Box`, both: `align(...)` first, then `fillMax*()` for an axis that
    fills, since the last element wins on each axis.
  - `Slot.Fill` is `weight` in a `Row`, a `Column` and a splitter (the pane's size). Raven's
    ScrollBox and Overlay ignore it, and so does the output.
  - The window's content fills it: `fillMaxSize()`. Content aligned otherwise goes in a
    `Box(Modifier.fillMaxSize())`, since `TranslatedUi()` has no scope to align in.
  - A root other than `<Window>` gets no implicit fill, and its `Slot` attributes are warnings.
- **State and actions**: each input gets a hoisted
  `var <name> by remember { mutableStateOf(<initial>) }` and is wired as a controlled input. The
  name comes from `Name`, or is generated (`checkbox1`). Actions become stub lambdas, for example
  `onClick = { /* file.save */ }`.
  - The radio buttons of one parent share one `Int` state, the index of the checked one or -1,
    since Raven keeps them exclusive: `selected = radioGroup1 == 0`.
  - A `Checkbox` with text becomes a `Row(verticalAlignment = Alignment.CenterVertically)` of the
    toggle and a `Text`, and the slot Modifier goes on the `Row`.
  - A Slider's `Step` becomes `steps`, the count of values between the ends, when it divides the
    range; otherwise a warning. A TextInput's action fires on Enter, which `TextField` doesn't
    report, so it is a warning.
  - Until the templates (`{...}` expressions, `<Widget>`, `<If>`...), they are warnings and TODOs.
- **Templates**:
  - Each `<Widget Name="Card" Title="Untitled" Wide="false">` becomes a top-level composable,
    `@Composable fun Card(modifier: Modifier = Modifier, title: String = "Untitled", wide: Boolean = false, content: @Composable () -> Unit = {})`,
    emitted before `TranslatedUi()`.
    - Parameters are renamed to lowerCamelCase.
    - Types come from the default value: `Boolean`, `Float` (a number with a dot), `Int`, or
      `String`.
    - Inputs get `remember` state inside the function.
    - A `<Widgets>` root produces only the functions.
  - `<Children/>` becomes the trailing `content` slot, and `<Children Name="x"/>` becomes a slot
    `x: @Composable () -> Unit = {}`. At the use site, children with `Into="x"` go to `x = { }`.
  - Layout attributes at the use site become the call's `modifier`, which the function passes to
    its root. Any other attribute that would pass through becomes a warning.
  - The whole `{...}` expression language is translated into Kotlin, with the type taken from the
    target attribute: `Width="{Wide ? 452 : 220}"` becomes
    `Modifier.width((if (wide) 452 else 220).dp)`. Interpolated text becomes string templates.
    `{Instance}` becomes a comment.
  - `If`/`<If>` becomes `if`, `<Repeat>` becomes `repeat(n) { i -> }`, and `<ForEach>` becomes
    `listOf(...).forEachIndexed { n, x -> }`.
  - `<Include>` stays a TODO plus a warning.
  - `Rxml_document_parse` registers no declarations globally and leaves `<Widget>` unexpanded
    ([verified](../spikes/README.md)), so `parseRavenXml` needs no clean-up.
- **Unsupported input** is never refused:
  - An element with no composable, `<Include>`, or an expression that fails to parse becomes
    `// TODO(raven-xml): ...` in its place, plus a warning with its line (Raven gives a node no
    column).
  - An attribute with no equivalent becomes a warning plus a trailing comment.
- **Output**: one compilable file, with 4-space indents. It holds the imports, then
  `@Composable fun TranslatedUi()` with the hoisted state at the top, then `main()` when the root
  is `<Window>`.
- **Tests**: most tests build `XmlElement` trees and check the generated code, as plain unit
  tests. A few headless smoke tests cover `parseRavenXml`. A CI test translates the `.xml` files of
  Raven's `examples/` and **compiles** the output against the current `compose-raven-foundation`,
  so the translator can't drift from the API.

### `compose-raven-xml` and `raven-xml2compose` (phase 3)

The translator moves out of the sample into the library module `compose-raven-xml`, and the
sample depends on it. The command-line tool `raven-xml2compose` is built on the module and starts
Raven headless, with no window.

```
raven-xml2compose ui/ --out src/nativeMain/kotlin/app/ui --package app.ui [--main main.xml] [--werror]
```

- **One Kotlin file per XML file**, named in PascalCase (`now_playing.xml` becomes
  `NowPlaying.kt`). A file whose root is an element also becomes a composable named after the file.
- **`<Include Source>`** resolves relative to the including file and becomes a call to that
  file's composable. Shared files are translated once, and include cycles are errors.
- **`main()`** is generated only for the `<Window>` root given with `--main`.
- **Overwriting**: generated files start with `// Generated by raven-xml2compose from <path>. Do not
  edit.`, and only files with that header are overwritten.
- **Diagnostics** go to stderr as `file:line:column: message`. The exit code is non-zero on errors,
  and `--werror` also fails on warnings.

Decided in: [XML to Compose translator sample](issues/archive/0013-xml-to-compose-translator.md),
[Templates in the XML translator](issues/archive/0016-xml-translator-templates.md),
[XML translator as a tool](issues/archive/0030-translator-as-a-tool.md),
[Port the Raven samples to Compose](issues/archive/0014-port-raven-samples.md).

## 11. Required Raven changes

These changes go into the `psh/Raven` fork and are offered upstream later. Changes 1, 3, 4, 5
and 6 are made, one commit each on the fork's `fork/compose-raven` branch, with a C test except
for change 6, which only a real macOS window reaches.

1. **`Rwidget_move_child(Rid parent, int from, int to)`** (required): it moves one child, and
   `to` is counted after the child has been taken out, as in `Rwidget_insert_child`. It keeps
   focus, hover and capture, doesn't replay the appear transition, invalidates layout, and treats
   an index out of range as an error.
2. ~~**Repeated `Rinit`/`Rshutdown` in one process**~~: **not needed**. Repeating them works
   and leaves no memory held ([verified](../spikes/README.md)).
3. **A built-in Kotlin language** (required), next to Java in `src/widgets/syntax.c`:
   - `name = "Kotlin"`, `extensions = "kt;kts"`.
   - The keywords are Prism.js's Kotlin list: `abstract actual annotation as break by catch class
     companion const constructor continue crossinline data do dynamic else enum expect external
     final finally for fun get if import in infix init inline inner interface internal is lateinit
     noinline object open operator out override package private protected public reified return
     sealed set super suspend tailrec this throw to try typealias val var vararg when where
     while`.
   - `constants = "true false null"`.
   - The types are `Int Long Short Byte Float Double Boolean Char String Unit Any Nothing Array
     List MutableList Map MutableMap Set MutableSet Sequence Pair Triple`.
   - Comments are `//` and `/* */`, and strings use `"` and `'`, `"""` for multiline, and `@`
     for annotations.
   - Flags: `R_LANGUAGE_NESTED_COMMENTS | R_LANGUAGE_CHAR_LITERALS`.
   - Known limits: string templates aren't highlighted, labels look like annotations,
     trailing-lambda calls aren't marked as functions, and a keyword right after a `.` may be
     highlighted.

4. **`Rdialog_set_dismiss_callback`** (required, phase 3): it is called when Escape would
   dismiss a dialog (a click outside never does, in Raven), and it can refuse. `Dialog` then calls `onDismissRequest` and the
   app decides, instead of Raven deleting the content behind Compose's back.
5. **`Rwindow_clear_focus(Rid window)`** (required, phase 3): leaves the window with no focused
   widget, by calling the internal `rvn_window_set_focus(window, NULL)`, as Raven already does when
   the focused widget is disabled or stops being focusable. It's used by
   `LocalFocusManager.clearFocus()`. Until it lands, the flag toggle verified in the
   [spikes](../spikes/README.md) does the same with public API only.
6. **Modifier keys on macOS** (required, a bug fix): Raven's Cocoa `flagsChanged:` asked the event
   for `charactersIgnoringModifiers`, which AppKit refuses for a modifier key with
   `NSInternalInconsistencyException`, so pressing Cmd, Shift, Ctrl or Option in any Raven window
   ended the app. Modifier keys are mapped by key code, so it now passes no characters. Found
   with [samples/hello](issues/archive/0051-samples-hello.md), and checked there by posting Cmd
   presses to the running app.

Decided in: [Node model and Applier](issues/archive/0007-node-model-and-applier.md),
[Testing strategy](issues/archive/0012-testing-strategy.md),
[Kotlin highlighting in CodeEditor](issues/archive/0017-kotlin-highlighting.md),
[Multiple windows, dialogs and popups](issues/archive/0024-multiple-windows.md),
[Generic input and focus](issues/archive/0025-generic-input-and-focus.md), [verify-first spikes](../spikes/README.md).

## 12. Differences from Compose

| compose-raven | Compose | Section |
|---|---|---|
| `fillMaxWidth`/`fillMaxHeight` on a Row's or Column's main axis behave like `weight(1f)` | takes all the remaining space | [6](#6-layout-and-modifiers) |
| `fillMax*` has no fraction argument | `fillMaxWidth(0.5f)` | [6](#6-layout-and-modifiers) |
| `Arrangement` offers only `Top`/`Start` and `spacedBy` | Center, End, SpaceBetween, ... | [6](#6-layout-and-modifiers) |
| `padding` is always outside the widget (no drawing modifiers) | position in the chain decides | [6](#6-layout-and-modifiers) |
| No `Layout {}`, and no right-to-left yet, although Raven supports it (`Start`/`End` are left/right) | custom layouts, `LayoutDirection` | [6](#6-layout-and-modifiers) |
| `VerticalScroll {}` / `HorizontalScroll {}` containers | `Modifier.verticalScroll(rememberScrollState())` | [8](#8-component-vocabulary) |
| `TextField` takes a `String` placeholder, single line only | a placeholder slot, multi-line | [8](#8-component-vocabulary) |
| No `TextFieldValue`; if the app rewrites the text, the caret jumps to the end | selection kept in `TextFieldValue` | [7](#7-events-and-state) |
| A rejected input change can show for a frame before it reverts | never shown | [7](#7-events-and-state) |
| Plain `Button` is Raven's primary style; `OutlinedButton` is the default style | Material's filled and outlined buttons | [8](#8-component-vocabulary) |
| `CodeEditor`, `HorizontalSplitter`, `VerticalSplitter`, `ComboBox`, `SpinBox` | no counterpart in Compose Foundation | [8](#8-component-vocabulary) |
| `SpinBox` shows 2 decimals unless told otherwise (Raven's default) | none | [8](#8-component-vocabulary) |
| `Surface` container for background, border, corners and shadow | `Modifier.background` / `.border` / `.clip` / `.shadow` | [8](#8-component-vocabulary) |
| `shadowElevation` maps to blur and offset as an approximation | Material elevation | [8](#8-component-vocabulary) |
| `Shape` is rounded rectangles only, and Start/End mean left/right | any `Shape`, RTL-aware | [8](#8-component-vocabulary) |
| `FlowRow` has one spacing for both items and lines, and no `verticalArrangement` | separate arrangements | [8](#8-component-vocabulary) |
| `Expander` | no counterpart | [8](#8-component-vocabulary) |
| Colour defaults are `Unspecified` (the Raven theme), and there is no `disabledContainerColor` | Material defaults read its theme | [8](#8-component-vocabulary) |
| `shape` is one corner radius, and `contentPadding` is symmetric | any shape and padding | [8](#8-component-vocabulary) |
| The look can only be set by the outermost `RavenTheme`, and `RavenTheme.colors` shows only the app theme | nested themes are fully independent | [8](#8-component-vocabulary) |
| No style classes; theme rules by class don't apply | no such concept | [8](#8-component-vocabulary) |
| No coroutine `pointerInput`, drag gestures or long press | `pointerInput`, `draggable`, `combinedClickable(onLongClick)` | [7](#7-events-and-state) |
| No `onPreviewKeyEvent`, because Raven doesn't tunnel events | preview handlers run root to leaf | [7](#7-events-and-state) |
| No `clearAndSetSemantics` or `mergeDescendants` | merging and clearing semantics | [7](#7-events-and-state) |
| An input in a `Row` is labelled automatically by the `Text` after it | the app merges semantics itself | [7](#7-events-and-state) |
| `accessibleRole` and `accessibleDescription` reach Raven roles that Compose's `Role` lacks | `Role` has a fixed set | [7](#7-events-and-state) |
| `FontWeight` has two steps (W100 to W500 regular, W600 to W900 bold) | any weight the font file has | [8](#8-component-vocabulary) |
| No `AnnotatedString` or spans; `MarkdownText` for formatted text | `AnnotatedString`, `SpanStyle` | [8](#8-component-vocabulary) |
| `Typography` has eight compose-raven roles (`display` ... `code`), sized by Raven's Markdown factors; Raven-drawn text and Markdown headings don't follow a custom typography | Material's fifteen roles, applied to every component | [8](#8-component-vocabulary) |
| `Canvas` has no `Path`, but has Raven's cubic curves, triangles, glass and shaders | `Path`, `DrawScope` on Skia | [8](#8-component-vocabulary) |
| Shader effects are SPIR-V (`ShaderEffect`) | `RuntimeShader` / AGSL | [8](#8-component-vocabulary) |
| Native, OpenGL and Metal viewports sit above the UI and aren't clipped | composited like any element | [8](#8-component-vocabulary) |
| `VulkanViewport.onDraw` runs on Raven's render thread | drawing happens on the UI thread | [8](#8-component-vocabulary) |
| `LazyColumn`/`LazyRow` compose every item in phase 3 (not lazy yet) | only visible items are composed | [8](#8-component-vocabulary) |
| `ListView` and `TreeView` take data and text, not composable rows | rows are composables in lazy lists | [8](#8-component-vocabulary) |
| `TreeView` expansion is Raven's, not controlled | n/a (no tree in Compose Foundation) | [8](#8-component-vocabulary) |
| `TabView` holds its pages | Material's `TabRow` is separate from the content | [8](#8-component-vocabulary) |
| `LazyVerticalGrid` has `GridCells.Fixed` only and one spacing | `Adaptive` cells, separate arrangements | [8](#8-component-vocabulary) |
| `Grid` with tracks and `Modifier.cell` | no explicit grid in Foundation | [8](#8-component-vocabulary) |
| `ImageBitmap` lives on the GPU and can't be read back | a CPU bitmap with `readPixels` | [8](#8-component-vocabulary) |
| No SVG, `ImageVector` or vector icons | `ImageVector`, `painterResource` for vectors | [8](#8-component-vocabulary) |
| `ContentScale` has `Fit`, `Crop`, `FillBounds` and `None` only | also `Inside`, `FillWidth`, `FillHeight` | [8](#8-component-vocabulary) |
| Resources come from a resource root or `registerResource`; no generated `Res` yet | `Res.drawable.foo` from compose resources | [8](#8-component-vocabulary) |
| `AnimatedVisibility` enter specs map to Raven's spring transitions and ignore `animationSpec`; exit is `fadeOut` only | any enter and exit spec, timed by the spec | [8](#8-component-vocabulary) |
| `Modifier.appearTransition` replays on every appearance | no counterpart | [6](#6-layout-and-modifiers) |
| `DockArea`'s arrangement is Raven's (only `initialDock`, plus saved layouts) | no docking in Compose | [8](#8-component-vocabulary) |
| Torn-off dock panels are Raven windows, not `Window`s | n/a | [8](#8-component-vocabulary) |
| `FileBrowser`'s directory isn't controlled | no file browser in Compose | [8](#8-component-vocabulary) |
| Navigation 3's Scene layer and `NavDisplay` come from `compose-raven-navigation3`, in compose-raven packages | `androidx.navigation3.ui` / `.scene` | [8](#8-component-vocabulary) |
| `NavDisplay` transitions are Raven appear transitions (`NavTransition`) | `transitionSpec` / `popTransitionSpec` with `ContentTransform` | [8](#8-component-vocabulary) |
| Back comes from the mouse Back button and Cmd+[ / Alt+Left, not Escape | the system back gesture | [8](#8-component-vocabulary) |
| `runRavenTest` covers one window, has no tree dumps or screenshots | `ComposeUiTest` with multiple roots and screenshots | [9](#9-testing) |
| App-wide theme settings (`look`, `dark`, `material`, app-wide `theme`/`fontFamily`) only at `application` level | any `MaterialTheme` can set anything | [8](#8-component-vocabulary) |
| A modifier a composable can't honour is ignored with a warning | modifiers always apply | [6](#6-layout-and-modifiers) |
| Focus traversal is Raven's own; no `moveFocus` | `FocusManager.moveFocus` | [7](#7-events-and-state) |
| `MenuBar` is a layout composable drawn in the window | a window-level call, the system menu bar on macOS | [8](#8-component-vocabulary) |
| Shortcuts have no automatic Ctrl-to-Cmd; apps write `meta = true` for macOS | the same in Compose Desktop, but its menus are native | [8](#8-component-vocabulary) |
| `customHeader = true` keeps the OS's resizing and window behaviour; `undecorated = true` removes all decoration, as in Compose Desktop | only `undecorated` | [4](#4-application-host) |
| `Modifier.material(...)` gives any widget a glass or solid surface | no counterpart | [6](#6-layout-and-modifiers) |
| `BackgroundBlur` blurs what is behind it (backdrop blur) | `Modifier.blur` blurs the element itself | [8](#8-component-vocabulary) |
| The backdrop is set with the `WindowBackdrop(...)` composable | no counterpart | [4](#4-application-host) |
| `Modifier.tooltip(text)` is text only | `TooltipArea(tooltip = { ... })` takes any composable | [6](#6-layout-and-modifiers) |
| `Dialog` is a sheet over its window, and a click outside it does nothing (no `dismissOnClickOutside`) | a separate window on desktop | [4](#4-application-host) |
| No `Popup { }` | `Popup` | [4](#4-application-host) |
| Message boxes and file dialogs are suspend functions, built on `Dialog` and `FileBrowser` unless `native = true` | no built-in equivalent | [4](#4-application-host) |

## 13. Roadmap

What gets built and in what order is in [`roadmap.md`](roadmap.md):
- verification spikes
- the fork changes
- the phase 1 and phase 2 milestones
- the phase 3 samples and the decisions each one needs
- what waits on fog

## 14. Out of scope

- **JVM** and the `raven-gui` Java binding: compose-raven is Kotlin/Native only.
- **Source compatibility with Compose Foundation or Material**: names and shapes follow Compose,
  but code written against androidx foundation or material isn't guaranteed to compile against
  compose-raven.
- **Mixing Raven XML documents with composables at run time**: composables build the whole tree.
  Translating XML into Compose source is in scope ([section 10](#10-samples)).
- **A Material-like module**: Raven's looks already imitate each platform.
- **A Metal backend for Raven**: Raven renders through Vulkan (MoltenVK on macOS). Metal content goes
  through `NativeViewport`.
- **Cross-compilation**: each target builds on its own host.
