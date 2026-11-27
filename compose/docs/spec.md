# compose-raven specification

**Status: phases 1, 2 and 3 complete.** Every decision below is settled. Both wayfinder maps,
[compose-raven spec](issues/0001-compose-raven-spec.md) and
[compose-raven phase 3 spec](issues/0023-compose-raven-phase-3-spec.md), are closed. What remains
is fog, listed on the phase 3 map and in [`roadmap.md`](roadmap.md), for a future phase 5 map. The vocabulary is in
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
| `compose-raven-native` | The cinterop over `Raven.h` (`raven.cinterop`) and the small public helpers in `raven.native` ([section 3](#3-compose-raven-native)). It contains no Compose code. | libraven |
| `compose-raven-ui` | The core, in `raven.compose.ui`: the host and windows, the Applier and node, the binding layer ([section 5](#5-node-model-and-applier)), `Modifier`, `Dp`, input, semantics, theming plumbing | `compose-raven-native` with `implementation`, so apps don't see it; `androidx.compose.runtime:runtime`; from phase 3, `androidx.lifecycle:lifecycle-runtime-compose` and `androidx.savedstate:savedstate-compose` with `api`, for the window owners |
| `compose-raven-foundation` | The composables, in `raven.compose.foundation`: thin declarations over the binding layer | `compose-raven-ui` with `api`; `compose-raven-native` with `implementation`; from phase 5, `kotlinx-datetime` with `api`, for the date and time pickers |
| `compose-raven-bom` (phase 4) | Version constraints for every module and `androidx.compose.runtime` | none |
| `compose-raven-navigation3` (phase 3, opt-in) | Navigation 3's Scene layer and `NavDisplay`, adapted from AndroidX 1.2.1 (Apache-2.0, with NOTICE) ([section 8](#8-component-vocabulary)) | `compose-raven-foundation`, `androidx.navigation3:navigation3-runtime` |
| `compose-raven-xml` (phase 4) | The XML translator as a library: the parse adapter and `translate()` ([section 10](#10-samples)) | `compose-raven-foundation`, `compose-raven-native` (`implementation`) |
| `tools/raven-xml2compose` (phase 4) | Command-line translator over files and directories ([section 10](#10-samples)) | `compose-raven-xml` |
| `compose-raven-ui-test` (Raven MCP) | The matchers (public, package `raven.compose.ui.test`), finding nodes and the actions on a host, headless or live (`@InternalComposeRavenApi`), with no `kotlin.test` ([section 9](#9-testing)) | `compose-raven-foundation` (`api`), `compose-raven-native` (`implementation`) |
| `compose-raven-mcp` (opt-in) | Raven MCP for apps, package `raven.compose.mcp`: `McpConfig` and the `mcp` overloads of `application`, `McpServer`, `McpTool`, `McpStatus`, and the UI tools over Compose semantics ([`mcp.md`](mcp.md#compose-raven-mcptool)) | `raven-mcp` (`api`, its klib), `compose-raven-foundation` (`api`), `kotlinx-serialization-json` (`api`), `compose-raven-ui-test` and `compose-raven-native` (`implementation`) |
| `compose-raven-test` | The public test harness, package `raven.compose.ui.test`: `runRavenTest`, finders, actions, assertions, `mainClock`, leak checks ([section 9](#9-testing)) | `compose-raven-ui-test` (`api`), `compose-raven-foundation` (`api`), `compose-raven-native` (`implementation`) |
| `compose-raven-vulkan` (phase 3, opt-in) | Cinterop over `raven_vulkan.h` (`raven.vulkan.cinterop`, public by design), `VulkanViewport`, Vulkan-backed textures, device access and sharing ([section 8](#8-component-vocabulary)) | `compose-raven-foundation` |
| `compose-raven-opengl` (phase 3, opt-in) | `OpenGLViewport` with a managed context (EGL on Linux, `NSOpenGLContext` on macOS), `OpenGLTexture` | `compose-raven-foundation` |
| `samples/hello`, `samples/xml-translator` | Executable samples ([section 10](#10-samples)) | `compose-raven-foundation`, plus `compose-raven-xml` for `xml-translator` from phase 4 |

The core and the composables are separate modules from the start: almost all of the complexity is
in `compose-raven-ui`, and the composables stay thin declarations over its binding layer. Apps
depend on `compose-raven-foundation`, which brings `compose-raven-ui` with it.

- **Toolchain**: Kotlin 2.4.21 with `kotlin("multiplatform")` and
  `org.jetbrains.kotlin.plugin.compose`. The dependency is `androidx.compose.runtime:runtime:1.12.1`
  from Google Maven; the JetBrains coordinates only redirect to it. Neither Compose UI nor the
  `org.jetbrains.compose` plugin is used. The window owners (phase 3) add
  `lifecycle-runtime-compose:2.10.0` and `savedstate-compose:1.4.0`, the versions
  `navigation3-runtime` 1.2.1 brings; they need only the Compose runtime.
- **Targets**: `macosArm64` and `linuxX64`, plus `mingwX64` from phase 4. Each target builds and is
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
- **Publishing** (phase 4): every library module is published to Maven Central under the group
  `io.github.psh`, at one shared version that follows its own semantic versioning (from 0.1.0).
  - Each target publishes from its own host: the macOS job publishes the root and metadata
    modules plus `macosarm64`, the Linux job publishes `linuxx64`, and the Windows job (phase 4)
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

## 3. compose-raven-native

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
- **`RavenInfo.version`** (phase 3), in `compose-raven-ui`, is Raven's version (`Rget_version`),
  read-only ([The editor's run-time extras](issues/archive/0102-the-editors-run-time-extras.md)).
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

- **`application(followSystemAccessibility = true, liveReload = Platform.isDebugBinary) { }`** blocks, and it must be called from `main()` on the main thread, which
  becomes the **UI thread** (AppKit requires this). It ends when its content has no `Window` left,
  or when `ApplicationScope.exitApplication()` is called. `singleWindowApplication(title, width,
  height, ...)` is shorthand for `application { Window(onCloseRequest = ::exitApplication, ...) }`.
  At start, the host calls `Raccessibility_follow_system()` unless `followSystemAccessibility` is
  false, so text scale, contrast and reduced motion follow the OS. It follows them while the app
  runs too: when the app becomes active again (none of its windows was, and one is), it reads the
  system's settings (`Raccessibility_get_system_settings`) and applies only those whose system value
  changed since it last read them, so a setting the app changed itself stays until the system's
  does. Switching between the app's windows reads nothing, as reading the system is slow on Linux
  (`gsettings`). The screen reader is checked every 2 seconds, and its starting shows the focus
  always (`Raccessibility_set_focus_always_visible`), as `follow_system` does. A headless host
  follows nothing.
  - **`LocalAccessibilitySettings`** (in `raven.compose.ui.platform`), which `Window` provides, is an
    `AccessibilitySettings(reduceMotion, highContrast, textScale, isScreenReaderActive)` read from
    Raven once a frame (`Raccessibility_get_*`, the screen reader from its last check), so it is what
    the app runs with, the system's or the app's own. Its readers recompose when one changes;
    outside a window it is `AccessibilitySettings.Default`
    ([System accessibility settings](issues/archive/0198-system-accessibility-settings.md)).
  - **In phase 1**, `application`, `ApplicationScope.exitApplication()` and `Window(onCloseRequest,
    title, width, height) { }` are already public, so `samples/hello`'s Quit button can end the app.
    Phase 2 completes the single `Window`: `WindowState` and every parameter below but `icon`,
    `island`, `onKeyEvent` and `backKeys`, which arrive with phase 3 as windows that come and go,
    `Dialog` and the lifecycle owners do.
- **`Window(onCloseRequest, state: WindowState = rememberWindowState(), title, customHeader = false, transparent = false, undecorated = false, resizable = true, minimumSize: DpSize? = null, background: Color = Color.Unspecified, opacity: Float = 1f, onExternalDrop: ((DragData) -> Unit)? = null, onKeyEvent: (KeyEvent) -> Boolean = { false }, icon: Painter? = null, island: (@Composable () -> Unit)? = null, backKeys: BackKeys = BackKeys.Default) { }`**:
  - **Closing** is controlled: Raven's close callback always refuses and calls `onCloseRequest`,
    and the window is destroyed when the app removes `Window` from the composition.
  - **Window flags**: `customHeader` uses `R_WINDOW_CUSTOM_HEADER` (for a `HeaderBar`,
    [section 8](#8-component-vocabulary)), `transparent` uses `R_WINDOW_TRANSPARENT`, and
    `undecorated` uses `R_WINDOW_BORDERLESS`; on macOS a borderless window still takes the keyboard
    (upstream, 2026-11-12). `background` uses `Rwindow_set_clear_color`.
  - **`opacity`** (0 to 1, the frame included) uses `Rwindow_set_opacity`. When the window system
    refuses, a warning is logged and the window stays opaque; the app's value stays the source of
    truth (phase 2).
  - **`onExternalDrop`** takes files or text dropped from other applications where no widget takes
    them (`Rwindow_set_drop_callback`): `DragData.FilesList(paths)` or `DragData.Text(text)`, the
    names of Compose Desktop's `onExternalDrag`. A non-null handler accepts drops (Raven shows its
    "can drop here" cursor), a null one refuses them, and the handler runs only on the drop, not on
    enter, move or leave. The harness has `performExternalDrop(paths, text)` over
    `Rwindow_inject_drop` (phase 2). Drop targets on widgets and drags inside the app are phase 5,
    with the gestures.
  - **`LocalWindowInfo.current.isTransparent`** is true when the window system really gave a
    see-through window (`RNativeWindow.transparent`), which `transparent = true` asks for but
    doesn't guarantee (phase 2). The raw native handle stays with phase 3's `NativeViewport`.
  - **`LocalWindowInfo.current.frameStats(): FrameStats`** (phase 3) returns the window's last
    frame as Raven measured it (`Rwindow_get_frame_stats`): `layoutMs`, `paintMs`, `renderMs`,
    `widgetCount`, `primitiveCount`, `drawCallCount`, `backdropCount`, `paintJobCount` and
    `frameIndex`. It is a plain call, not snapshot state, since the numbers change every frame:
    an app samples it from its own loop ([The editor's run-time extras](issues/archive/0102-the-editors-run-time-extras.md)).
  - **`LocalClipboardManager`** provides a `ClipboardManager`, in `raven.compose.ui.platform`, with
    Compose's three members: `setText(text)`, `getText()` (null when the clipboard holds no text)
    and `hasText()`. They take a `String`, since compose-raven has no `AnnotatedString`. The host's
    uses `Rclipboard_set_text` and `Rclipboard_get_text`, the clipboard Raven's own fields and
    editors copy to and paste from; an app or a test provides its own. Raven's HTML copy
    (`Rclipboard_set_html`) has no counterpart in Compose's interface and isn't bound
    ([Clipboard](issues/archive/0193-clipboard.md)).
  - **`minimumSize`** uses `Rwindow_set_min_size`; `null` keeps Raven's default, and a change calls
    it again (phase 2, on `singleWindowApplication` too).
  - **`WindowState`** (phase 2) holds `size: DpSize` (as Compose Desktop's, replacing phase 1's
    `width` and `height`), `position`, `placement` (Floating or Maximized) and
    `isMinimized`. Writes call Raven's setters, and Raven's resize and move callbacks write back.
  - **Window operations** ([Window operations](issues/archive/0197-window-operations.md)):
    - `Window(visible = true)`, on `singleWindowApplication` too: false hides the window
      (`Rwindow_hide`) and keeps its composition, with its lifecycle CREATED; a window composed
      hidden is never shown until it is visible.
    - `WindowPosition.Aligned(alignment)`, made by `WindowPosition(alignment)`, centres the window on
      its monitor's work area with `Rwindow_center` when it opens or is asked again, then becomes
      the real position, as `PlatformDefault` does. Raven has no other placement and no monitor
      geometry, so another alignment centres with a warning.
    - `WindowPlacement.PictureInPicture`, a compose-raven extension, is
      `Rwindow_set_picture_in_picture`: small, above the other windows, in a corner. The state
      follows the user leaving it, and a refusal (Wayland) warns and gives the placement back.
    - `LocalWindowController.current`, beside `LocalWindowInfo`, has `toFront()` (`Rwindow_focus`,
      nothing for a hidden window) and `requestAttention(critical)` (`Rwindow_request_attention`).
      Compose Desktop does both through AWT's window.
    - `WindowDraggableArea(modifier) { }`, in `raven.compose.foundation.window`, is a `Box` whose
      widget drags its window and maximises it on a double click (`Rwidget_set_window_drag`), in
      a `customHeader` window; without one it warns once and does nothing. Compose Desktop's is an
      extension of `WindowScope`, which compose-raven doesn't have.
    - The headless window system centres on a 1920 by 1080 work area, focuses and does picture in
      picture, so the tests check those against Raven. It shows nothing for showing, hiding, asking
      for attention or dragging, so the tests record those calls (`WindowCalls`,
      `WindowDragCalls`).
  - **Composition**: a window emits no node into its parent. It has its own composition, with its
    own Overlay root and Applier, under the one Recomposer, linked with
    `rememberCompositionContext()`.
  - **Owners**: each window provides a `LifecycleOwner` and a `SavedStateRegistryOwner`. They
    persist nothing yet, but they are enough for lifecycle-aware libraries. Navigation 3 needs
    neither: its saveable-state decorator keeps each entry's state in a `SaveableStateHolder`, and
    `rememberSaveable` state comes back after leaving the composition only under a
    `LocalSaveableStateRegistry`, which a window doesn't provide ([verified](../spikes/README.md)). The
    lifecycle follows Compose Desktop: **RESUMED** while the window is shown and active (focused),
    **STARTED** while shown but not active, **CREATED** while minimised or hidden, and
    **DESTROYED** when the window leaves the composition. Raven reports no change of the active
    window, so each window reads `Rwindow_is_active` once a frame, as it reads its position. A
    window is hidden only until it first shows, since `Window` has no `visible` parameter.
  - **`WindowBackdrop(backdrop: Backdrop): Backdrop`** inside a window calls `Rwindow_set_backdrop`
    for that window. `Backdrop` is `None`, `Transparent`, `Blur`, `Acrylic` or `Mica`, and a warning
    is logged when the window isn't transparent or the system refuses. It returns the backdrop in
    effect, read with `Rwindow_get_backdrop` after each apply, as snapshot state: `None` until the
    first apply, then its readers recompose. `val obtained = WindowBackdrop(Backdrop.Mica)`.
- **`Dialog(onDismissRequest, properties: DialogProperties = DialogProperties()) { }`** is a
  Raven sheet over its window, centred over a scrim; its content draws its own background (a
  `Surface`).
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
  - **Phased**: phase 2 shipped all four with their final signatures and built only `native =
    true`, for `samples/notepad`; phase 3 builds the default on `Dialog` and `FileBrowser` without
    changing the API. A default dialog shows over the active window (else the first), in that
    window's composition, until it is answered; cancelling the call takes it away, and a window
    that goes cancels the calls waiting on it. Escape answers a message box Cancel, or No when it
    has no Cancel button, and a file dialog none.

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
  compose-raven owns the single `Rset_frame_callback` slot outright, and the single
  `Rxml_set_reload_callback` slot too (see live reload below). The callback drains the
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
  - **Removing a `Window`**: move its lifecycle to DESTROYED, so the content's observers see it go,
    then dispose its island, dialog and content compositions (innermost first), then
    `Rwindow_destroy`.
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
- **Live reload** (phase 2): Raven reloads what it built from files when they change on disk (theme
  files, and the interface and locale files compose-raven doesn't use), through a native watcher
  thread that `Rshutdown` stops. `liveReload` defaults to `Platform.isDebugBinary`, so a debug
  executable follows its theme files and a release one never looks at them again; it calls
  `Rxml_set_live_reload` right after `Rinit`. When the `RAVEN_LIVE_RELOAD` environment variable is
  set, Raven's reading of it wins and `liveReload` is ignored. compose-raven owns
  `Rxml_set_reload_callback`, which Raven calls for a reloaded theme file too
  ([fork change 8](#11-required-raven-changes)), uses it only to refresh `RavenTheme.themeNames`
  and `RavenTheme.colors` ([section 8](#phase-2-theming)), and runs it inside the C-boundary catch;
  apps get no reload event. `singleWindowApplication` takes `liveReload` too.

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
- **`DpSize(width: Dp, height: Dp)`**, in the same package, is a window's size and minimum size
  (phase 2), rounded to whole logical units for Raven.
- **`LocalDensity`** (phase 3), in `raven.compose.ui.platform`, provides a minimal `Density`: its
  `density` is the window's scale (`Rwindow_get_scale`), with `Dp.toPx()`, `Int.toDp()` and
  `Float.toDp()` in its scope, for turning dp (pointer positions) into pixels (a viewport's size)
  and back ([The editor's scene viewport](issues/archive/0101-the-editors-scene-viewport.md)). Raven
  reports no change of scale, so the window reads it once a frame, and a change recomposes its
  readers ([Modifier.offset and LocalDensity](issues/archive/0118-layout-offset-and-density.md)).
- **`RavenUiScale.value`**, in `raven.compose.ui`, is the scale of the whole interface on top of
  each monitor's (`Rset_ui_scale`, 0.25 to 4; outside that it throws), for an app's zoom setting:
  every window is laid out again in the same logical units, so `LocalDensity` follows and a window
  shows less at a larger scale, and Raven closes the open popups. It is snapshot state, read from
  Raven once a frame (`Rwindow_save_preview` changes it while it draws), and a value set before the
  application starts applies at start, so a saved zoom is restored before the first frame
  ([UI scale](issues/archive/0199-ui-scale.md)).
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
  widget, with its children centred vertically, as a Compose Button's content is. While one of
  its children has a weight (or `fillMaxWidth`), it spans the widget instead, as Compose's Row
  given the button's width does, and that child takes the room left.
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
| `alpha(Float)` | any | `Rwidget_set_opacity` on the outer widget, 0 to 1, its children included; 1 when left out of the chain (phase 2) |
| `clipToBounds()` | any | `Rwidget_set_clipping`: the widget's descendants are clipped to its bounds; off when left out (phase 2) |
| `offset(x: Dp, y: Dp)` | any | the slot padding: left +x, right −x, top +y, bottom −y, added to any `padding`, so the widget moves without changing its size or its neighbours' places; negative `padding` stays an error, as in Compose (phase 3, [Small port gaps](issues/archive/0107-small-port-gaps.md)) |

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
  - `visualTransformation = PasswordVisualTransformation()` uses `Rtext_input_set_password`: Raven
    draws a bullet per character and refuses to copy or cut. `VisualTransformation` is sealed, with
    `None` and `PasswordVisualTransformation(mask)`, since Raven can only show or hide the text; a
    mask other than U+2022 is drawn as a bullet with a warning.
  - `keyboardActions = KeyboardActions(onDone)`: Enter calls `onDone`, from
    `Rtext_input_set_committed_callback`. There is no `KeyboardOptions`, so no IME action picks
    another handler.
  - `maxLength: Int? = null`, a compose-raven extension, uses `Rtext_input_set_max_length`. Raven cuts
    what is typed or pasted to that many code points inside the field, so the caret stays after the
    insertion, which filtering in `onValueChange` can't keep (rewritten text moves the caret to the
    end). A longer `value` from the app is shown whole, and only edits are limited
    ([Password, max length and submit on TextField](issues/archive/0194-password-max-length-and-submit-on-textfield.md)).
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
- **Menus opened from a button**: `DropdownMenu(state = rememberDropdownMenuState(), modifier,
  offset: DpOffset = DpOffset.Zero) { }` holds phase 2's `MenuScope` items (`Item`,
  `CheckboxItem`, `Separator`, sub-`Menu`), and `state.open()` opens them as a Raven Menu
  (`Rmenu_open`) under its parent's widget: the parent's bottom-left (`Rwidget_get_rect`) plus
  `offset`, as Compose's `DropdownMenu` anchors to its parent, so `Box { Button(onClick = {
  state.open() }) { }; DropdownMenu(state) { } }` opens under the button. Raven dismisses it (a
  click outside, Escape, a chosen item), as with `ContextMenuArea`; it reports no close, so there
  is no controlled `expanded` ([Menus opened from a button, and Raven's popups](issues/archive/0106-menus-opened-from-a-button-and-ravens-popups.md)).

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
[Menus opened from a button, and Raven's popups](issues/archive/0106-menus-opened-from-a-button-and-ravens-popups.md),
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
| `TextField(value, onValueChange, modifier, enabled, readOnly, textStyle, placeholder: String, leadingIcon, trailingIcon, visualTransformation, keyboardActions, singleLine = true, maxLength: Int? = null, shape, colors)` | TextInput, in an Overlay with its icons when it has any | single line only: `singleLine = false` fails; `maxLength` is a compose-raven extension ([section 7](#7-events-and-state)). The icons are composed over the field at its start and end, which keeps its text clear of them (`Rtext_input_set_insets`, following their widths once a frame), and a click among them leaves the focus in the field (`Rwidget_set_focus_on_click`); with icons, the modifier's layout and visuals go on the Overlay and its tag, semantics, focus and input on the field (`splitAroundWidget`) |
| `Slider(value, onValueChange, modifier, enabled, valueRange, steps)` | Slider | controlled. `steps` values lie between the ends, as in Compose, so Raven's step is `range / (steps + 1)` |
| `LinearProgressIndicator(progress)` | ProgressBar | |
| `HorizontalDivider()`, `VerticalDivider()` | Separator | |
| `Spacer(modifier)` | Spacer | no children |
| `VerticalScroll(modifier, state: ScrollState = rememberScrollState(), verticalArrangement, horizontalAlignment) { }`, `HorizontalScroll(modifier, state, horizontalArrangement, verticalAlignment) { }` | ScrollBox | a scrolling Column or Row, with `ColumnScope`/`RowScope`. The ScrollBox stacks the children itself, each at its own size. `state` arrives in phase 3 ([scroll position](#scroll-position-phase-3)) |
| `CodeEditor(value, onValueChange = {}, modifier, language: String? = null, readOnly = false, lineNumbers = true)` | TextEditor | controlled. Its change callback carries no text, so the node reads `Rtext_editor_get_text`. `language` is a name for `Rtext_editor_set_language`; an unknown one shows plain text (Raven warns). Text the app rewrites also clears Raven's undo history |
| `HorizontalSplitter(modifier) { }`, `VerticalSplitter(modifier) { }` | Splitter | each child is a pane it fills. In `SplitterScope`, `Modifier.weight` is its relative pane size, 1 by default. Dragging a handle keeps the new sizes until a weight changes |

### Phase 2: menus and the header bar

Added for the `notepad` port:

| Composable | Raven widget | Notes |
|---|---|---|
| `MenuBar(modifier, native = false) { }` | MenuBar | a layout composable; `MenuBarScope` allows only `Menu`. `native = true` lets Raven show it in the system menu bar on macOS (`Rmenu_bar_set_native`), where it takes no room in the window; elsewhere it is drawn in the window either way. The default keeps it in the window everywhere |
| `Menu(text) { }` | Menu | `MenuScope` allows `Item`, `CheckboxItem`, `Separator` and sub-`Menu` |
| `Item(text, onClick, shortcut: KeyShortcut? = null, enabled = true)` | MenuItem | no children |
| `CheckboxItem(text, checked, onCheckedChange, shortcut, enabled)` | MenuItem, checkable | controlled |
| `Separator()` (in `MenuScope`) | MenuItem with `Separator = true` | a plain Separator widget is not a menu separator in Raven, but a MenuItem with the `Separator` attribute (`Rwidget_set_attribute`) is, and can be inserted at an index (verified, [spikes](../spikes/README.md)) |
| `HeaderBar(modifier, title: String? = null, buttons: Set<WindowButton> = WindowButton.all(), background: Boolean = true) { }` | HeaderBar | needs `Window(customHeader = true)`, otherwise a warning is logged; in `HeaderBarScope`, `Modifier.align(Alignment.End)` places a child on the right. `background = false` (phase 3, `Rheader_bar_set_background`) removes the bar's own background, for a header over the window's content or in a glass panel ([DockArea and HeaderBar extras](issues/archive/0105-dockarea-and-headerbar-extras.md)) |

`KeyShortcut(key: Key, ctrl, shift, alt, meta)` uses `Rmenu_item_set_shortcut`, where `meta` is
Super (Cmd on macOS). `Key` mirrors `RKey`: letters, digits, F1 to F12, and the named keys.

### Phase 2: CodeEditor's editing extras

Added for the `notepad` port. `CodeEditor` stays controlled by `value`/`onValueChange`, and gains:

- **`state: CodeEditorState = rememberCodeEditorState()`**, a holder in the manner of `ScrollState`:
  it holds no text, only commands and what it observes.
  - Commands: `undo()`, `redo()`, `selectAll()`, `showCompletions()`, and `insertText(text)`, which
    types at the cursor as the user would (`Rtext_editor_insert_text`: undoable, and it calls
    `onValueChange`). On a state not attached
    to an editor they do nothing.
  - `cursor: TextPosition(line, column)`, zero-based and read-only, is snapshot state refreshed
    each frame from `Rtext_editor_get_cursor`, since Raven has no callback for it.
- **`wordWrap: Boolean = false`**: `Rtext_editor_set_word_wrap`.
- **`placeholder: String = ""`**: `Rtext_editor_set_placeholder`, shown while the text is empty, as
  `TextField`'s `placeholder` is.
- **`completions: ((prefix: String) -> List<Completion>)? = null`**, with `Completion(text, detail:
  String? = null)`: called synchronously inside Raven's completion callback, and each result goes
  to `Rtext_editor_add_completion`. It must be fast.
- **`markers: List<EditorMarker> = emptyList()`**, with `EditorMarker(line, fromColumn, toColumn:
  Int? = null, kind: MarkerKind, message)`; `null` is the end of the line, and `MarkerKind` is
  `Error`, `Warning` or `Info`. Markers are controlled: Raven drops a marker whose line is edited,
  and after each edit the node puts the composed list back, so the editor always shows exactly
  what the app composed. A marker on an edited line keeps its columns until the app sends a new
  list.

Languages:

- **`rememberCodeEditorLanguage(CodeEditorLanguage(...)): String`** registers a language with
  `Rlanguage_register` once per host and returns its name. Raven can't unregister one, so it lasts
  until shutdown. `CodeEditorLanguage` mirrors every field of `RLanguageDesc` with Kotlin names,
  its flags a `Set<LanguageFlag>`. A language registered in the composition is in
  `codeEditorLanguages()` read after it.
- **`codeEditorLanguageForFile(path): String?`** is `Rlanguage_for_file`, valid only inside the
  composition.
- **Phase 5 extras** ([Small widgets: the CodeEditor extras](issues/archive/0212-small-widgets-the-codeeditor-extras.md)):
  - `CodeEditorState` gains `canUndo` and `canRedo` (`Rtext_editor_can_undo`/`_can_redo`) and
    `selection: EditorSelection?` (`start` and `end` `TextPosition`s in text order, null when
    nothing is selected), followed once a frame as `cursor` is; `select(start, end)`,
    `selectedText()` (a function, not copied each frame), `suspend scrollToLine(line)` (a jump);
    and `zoom: Float` (0.5 to 4), written by Raven's zoom callback and calling
    `Rtext_editor_set_zoom` when set.
  - `CodeEditor` gains `zoomable = true`, `tabSize = 4`, `insertSpacesForTab = false`,
    `autoPairs = true` and `autoCompletion = true` (proposals that pop up by themselves; Ctrl+Space
    and `showCompletions()` still work without it), Raven's defaults.
  - **`SyntaxTheme(comment, string, number, keyword, type, function, tag, attribute, punctuation,
    directive, heading, strong, emphasis, link)`**, one `Color` per token kind, provided as
    `LocalSyntaxTheme` (every colour `Unspecified` by default). Each `CodeEditor` applies the one
    around it with `Rtext_editor_set_token_color`, so nested providers scope as a local does; an
    unspecified colour is the editor's Raven theme's, a theme file's `<Syntax>` or Raven's own pick.
  - Left unbound: the token at a position, the line count, a line and the length (the app has the
    text), `set_syntax` and `set_markdown` (`language` covers them, Markdown being Raven's
    `"Markdown"` language) and `is_completing`.

### Phase 2: ComboBox and SpinBox

Added for the `notepad`, `themes` and `overlay` ports (and `glass` in phase 3). All three are controlled.

| Composable | Raven widget | Notes |
|---|---|---|
| `ComboBox(items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit, modifier, enabled = true)` | ComboBox | -1 means nothing selected. When `items` changes (by value), the node calls `clear_items` and `add_item` again |
| `SpinBox(value: Double, onValueChange: (Double) -> Unit, modifier, valueRange = 0.0..100.0, step = 1.0, decimals = 2, enabled = true)` | SpinBox | Raven's `Double` and defaults |
| `SpinBox(value: Int, onValueChange: (Int) -> Unit, modifier, valueRange: IntRange = 0..100, step = 1, enabled = true)` | SpinBox | `decimals = 0`, values rounded with `roundToInt()` |

`codeEditorLanguages(): List<String>` lists Raven's editor languages (`Rlanguage_get_count`/`_name`).
It is valid only inside the composition.

### Date and time pickers (phase 5)

Raven's DatePicker is a date field with a calendar that drops down from it, and its TimePicker a
24-hour field of hours, minutes and seconds changed by the arrows, the wheel or typing. Both are
controlled, as `TextField` is: the user's change calls back, and the app's value is shown again.

- **`DatePicker(date: LocalDate, onDateChange, modifier, enabled, range: ClosedRange<LocalDate>? = null, firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY, format: String = "YYYY-MM-DD")`**
  uses `Rdate_picker_set_date` / `_set_changed_callback`, `_set_range` (an open end where `range`
  is null), `_set_first_day_of_week` and `_set_format`, whose pattern is Raven's (`YYYY`, `MM`,
  `M`, `DD`, `D`, `MMMM`, `MMM`, `dddd`, `ddd`; `"D MMMM YYYY"` gives "3 November 2026"). `date`
  is never null, as Raven's widget always holds one.
- **`TimePicker(time: LocalTime, onTimeChange, modifier, enabled, showSeconds = false, minuteStep = 1)`**
  uses `Rtime_picker_set_time` / `_set_changed_callback`, `_set_seconds_visible` and
  `_set_minute_step`. It is 24-hour only; seconds the picker doesn't show are kept.
- **Types**: kotlinx-datetime's `LocalDate`, `LocalTime` and `DayOfWeek`, a dependency of
  compose-raven-foundation that apps see
  ([Small widgets: date and time pickers](issues/archive/0210-small-widgets-date-and-time-pickers.md)).

### Rating, Pagination, Breadcrumb and VerticalSlider (phase 5)

Raven draws these itself, the stars, the chevrons (mirrored right to left) and the arrows, so
they take no icons. Each is controlled.

- **`Rating(value: Float, onValueChange, modifier, enabled, readOnly = false, max = 5, allowHalf = false, starSize: Dp = 18.dp)`**
  over `Rrating_*`: `value` from 0 to `max`, `max` from 1 to 20, `readOnly` as `TextField`'s.
- **`Pagination(page: Int, pageCount: Int, onPageChange, modifier, enabled, siblings = 1)`** over
  `Rpagination_*`: pages count from 0, as Raven's and Compose's `PagerState.currentPage` do, and
  are shown from 1.
- **`Breadcrumb(items: List<String>, onItemClick: (index: Int) -> Unit, modifier)`** over
  `Rbreadcrumb_*`: the app owns the list and shortens it when an earlier item is clicked; the items
  are given to Raven one by one (`Rbreadcrumb_add_item`), so a text may hold `|`.
- **`VerticalSlider(value, onValueChange, modifier, enabled, valueRange, steps, colors)`**:
  `Slider`'s parameters with `Rslider_set_orientation(R_VERTICAL)`, the minimum at the bottom
  ([Small widgets: Rating, Pagination, Breadcrumb and a vertical Slider](issues/archive/0211-small-widgets-rating-pagination-breadcrumb-and-a-vertical-slider.md)).

### Tray, notifications and taskbar (phase 5)

- **`ApplicationScope.Tray(icon: Painter, state = rememberTrayState(), tooltip: String? = null, onAction = {}, menu: @Composable MenuScope.() -> Unit = {})`**,
  Compose Desktop's shape over `Rtray_*`: the menu is made of the menu composables (`Item`,
  `CheckboxItem`, `Menu`, `Separator`); the icon is pixels, so only a `painterResource` can be one,
  as for `Window(icon)`. While a tray is in the composition the app keeps running after its last
  window closes, Raven's own rule. Where the system shows none (`Rtray_is_supported`), it does
  nothing, with a warning.
- **Notifications**: `TrayState.sendNotification(Notification(title, message, type))`, as Compose
  Desktop's, and `ApplicationScope.sendNotification(notification)` for an app with no tray, over
  `Rnotification_show`; `Notification.Type` (None, Info, Warning, Error) maps to Raven's urgency.
  Each returns whether the system took it, so an app can show its own message where it didn't.
  Raven's clicked callback, Windows only, isn't bound.
- **Taskbar and Dock**: `Window(taskbarProgress: TaskbarProgress? = null)` (`Normal(value)`,
  `Indeterminate`, `Paused(value)`, `Error(value)`; `Rwindow_set_taskbar_progress`);
  `TaskbarBadge(text)`, a composable anywhere in the app whose badge (`Rtaskbar_set_badge`) goes
  when it leaves the composition, the last composed winning; `ApplicationScope.DockMenu { }`, the
  Dock icon's menu on macOS (`Rtaskbar_set_menu`), nothing elsewhere. Windows' jump list goes with
  the Windows target.
- **Tests** record Raven's calls through a seam, as for the window operations, since the headless
  window system shows none of it
  ([Small widgets: notifications and tray](issues/archive/0213-small-widgets-notifications-and-tray.md)).

### ColorPicker (phase 3)

Added for the `music` port, and replacing the stand-in swatches of the phase 2 `themes` port:

| Composable | Raven widget | Notes |
|---|---|---|
| `ColorPicker(color: Color, onColorChange: (Color) -> Unit, modifier, popup = false, alpha = true, compact = false, swatches: List<Color> = emptyList(), enabled = true)` | ColorPicker | controlled; `Rcolor_picker_set_color` doesn't call back, so a refused change reverts on the next frame. `onColorChange` fires on every change, during a drag too, as `Slider`'s does. `popup` is a swatch the size of a button that opens the picker; `alpha = false` makes colours opaque; `compact` hides the code and the numbers. A popup picker shows the colour it opened with beside the new one. Raven's `Numbers` (RGB or HSV) stays its default |

Decided in [Music's accent picker](issues/archive/0100-musics-accent-picker.md).

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

- **`RavenTheme(look: Look? = null, dark: Boolean? = null, theme: String? = null, accent: Color? = null) { }`**, in two
  places:
  - **App-wide, at `application` level**: `application { RavenTheme(look = ..., theme = ...) {
    Window(...) { } } }`. There it emits no node. `look` and `dark` become one `Rlook_set`, `theme`
    uses `Rtheme_use`, and its CompositionLocals reach every window's composition through
    `rememberCompositionContext`. Only this placement may set app-wide settings (`look`, `dark`,
    `accent`, `material`, an app-wide `theme` or `fontFamily`), so two windows can never disagree about
    them. In `singleWindowApplication`, the `RavenTheme` at the top of its content counts as
    application level.
  - **Inside a window**: a subtree setting, which, like `MaterialTheme`, emits no node and so
    leaves the layout alone. `theme` is a CompositionLocal, and every node in the subtree calls
    `Rwidget_set_theme` on its own outer widget when the value it reads changes (null following
    the parent again), through the binding layer, so no composable handles it itself.
    `fontFamily` (phase 3) follows the same pattern with `Rwidget_set_font`, and `typography` is a
    CompositionLocal. Setting an app-wide setting here logs an error. Decided in
    [Subtree themes as a CompositionLocal](issues/archive/0093-subtree-themes-as-a-compositionlocal.md),
    replacing the internal Box of phase 2's first version.
  - `Look` is one of `Raven`, `Glass`, `HighContrast`, `Windows`, `Macos`, `Gnome`, `Native` or
    `MacosGlass`.
- **`accent`** uses `Rtheme_set_accent`, and Raven derives the rest from it: the hovered and
  pressed shades, the focus outline, the selection, the links. Changing the look or the theme
  resets Raven's accent, so compose-raven applies it again after each change.
- **`loadTheme(path): String`** and **`loadThemeFromString(xml): String`** load theme files and
  return the name for `theme =`. They are valid only while Raven runs. While live reload is on
  ([section 4](#4-application-host)), Raven reloads a loaded file when it is saved, updating every
  widget that uses the theme. Apps see no reload event.
- **`saveTheme(path, name: String? = null): Boolean`** writes the theme in use, the accent
  included, with `Rtheme_save`. It is valid only while Raven runs.
- **`RavenTheme.themeNames: List<String>`** is read-only snapshot state listing every named theme
  (`Rtheme_get_count`/`_name_at`), the built-in ones included. It is refreshed after `loadTheme`,
  after a look change, and when Raven reloads a theme file, which can rename it (through
  compose-raven's own reload callback).
- **`RavenTheme.colors`** is a CompositionLocal with one `Color` per Raven colour role, read from
  the app theme with `Rtheme_get_color`. It doesn't see subtree themes.
- **`RavenTheme(metrics: Map<ThemeMetric, Float> = emptyMap())`** (phase 3) sets theme metrics at
  run time with `Rtheme_set_metric`, for example a compact or comfortable density. Only an
  application-level `RavenTheme` may set it, as with `accent`, and compose-raven applies it again
  after each look or theme change, which resets it. A metric left out of the map again goes back to
  the value Raven had before compose-raven set it, since Raven can't reset one. `ThemeMetric` mirrors every Raven metric, the
  glass ones included, in Raven's own values (logical units, fractions for glass). A changed
  `FontSize` moves `Typography.fromTheme()`'s base ([The editor's run-time extras](issues/archive/0102-the-editors-run-time-extras.md)).
- **Colour parameters** follow Material: `XxxDefaults.colors(...)` returns `XxxColors`, passed as
  `colors =`. Every default is `Color.Unspecified`, meaning "keep the Raven theme's value", so
  only the colours given call `Rwidget_set_color`.

  | Composable | Parameter | Raven roles |
  |---|---|---|
  | `Button`, `OutlinedButton`, `TextButton` | `ButtonDefaults.buttonColors(containerColor, contentColor, disabledContentColor, hoveredContainerColor, pressedContainerColor)` | `ACCENT` (`CONTROL` for Outlined/Text), `ACCENT_TEXT` (`TEXT`), `TEXT_DISABLED`, `ACCENT_HOVERED`/`_PRESSED` (`CONTROL_HOVERED`/`_PRESSED`) |
  | `Checkbox` | `CheckboxDefaults.colors(checkedColor, uncheckedColor, checkmarkColor, hoveredCheckedColor, pressedCheckedColor)` | `ACCENT`, `CONTROL_BORDER`, `ACCENT_TEXT`, `ACCENT_HOVERED`, `ACCENT_PRESSED` |
  | `RadioButton` | `RadioButtonDefaults.colors(selectedColor, unselectedColor, hoveredSelectedColor, pressedSelectedColor)` | `ACCENT`, `CONTROL_BORDER`, `ACCENT_HOVERED`, `ACCENT_PRESSED` |
  | `Switch` | `SwitchDefaults.colors(checkedTrackColor, uncheckedTrackColor, thumbColor)` | `SWITCH`, `TRACK`, `HANDLE` |
  | `Slider` | `SliderDefaults.colors(thumbColor, activeTrackColor, inactiveTrackColor, hoveredActiveTrackColor, pressedActiveTrackColor)` | `HANDLE`, `ACCENT`, `TRACK`, `ACCENT_HOVERED`, `ACCENT_PRESSED` |
  | `TextField` | `TextFieldDefaults.colors(containerColor, textColor, placeholderColor, focusedIndicatorColor, unfocusedIndicatorColor, selectionColor)` | `INPUT`, `TEXT`, `TEXT_PLACEHOLDER`, `FOCUS`, `CONTROL_BORDER` and `BORDER`, `SELECTION` |
  | `LinearProgressIndicator` | `color`, `trackColor` | `ACCENT`, `TRACK` |
  | `HorizontalDivider`, `VerticalDivider` | `color` | `SEPARATOR` |
  | `Surface` | `contentColor` | `TEXT`, passed down to the children |

  **Hover and pressed shades:** Raven derives them only for a theme's accent (`Rtheme_set_accent`),
  not for `Rwidget_set_color` ([verified](../spikes/README.md)). So when a `colors` parameter sets
  `ACCENT` or `CONTROL`, compose-raven also sets the hovered and pressed roles, following the
  per-look rules of Raven's `rvn_theme_set_accent`. An explicit hovered or pressed colour (phase
  3, for the buttons, `Checkbox`, `RadioButton` and `Slider`) is used as given instead, each on its
  own, so a transparent button can still have a faint hover
  ([Music's class-styled themes](issues/archive/0099-musics-class-styled-themes.md)).
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
- The theme's glass metrics (frost, refraction, rim, ...) are set in theme files or with
  `RavenTheme(metrics)`.

### Fonts and text styling (phase 3)

- **`FontFamily(vararg fonts: Font)`**, with `Font(path: String or bytes: ByteArray, weight, style)`:
  - The regular font is the family's Raven handle, and the others attach with `Rfont_set_variant`.
  - It is loaded on first use and cached in the `RavenHost` until shutdown ([section 4](#4-application-host)).
  - `FontFamily.Default` is Roboto and `FontFamily.Monospace` is Roboto Mono.
  - Paths resolve as [Image and resources](issues/archive/0035-image-and-resources.md) decides.
- **Weight and style**: `FontWeight` W100 to W500 mean Regular and W600 to W900 mean Bold.
  `FontStyle` is Normal or Italic. Raven synthesises the styles that have no file.
- **`TextStyle(color, fontSize, fontFamily, fontWeight, fontStyle, textAlign)`** is read from
  `LocalTextStyle` and changed for a subtree with `ProvideTextStyle(style) { }`, which merges with
  the outer style. `TextField` and `CodeEditor` take a `textStyle`:
  - `TextField(textStyle)`'s font is the widget's own `Rwidget_set_font`, winning over a subtree's,
    and its size the widget's `R_METRIC_FONT_SIZE`: Raven's field has no font setter. The field keeps
    the theme's control height, and has no alignment. A `textStyle` colour wins over `colors`.
  - `CodeEditor(textStyle)` uses `Rtext_editor_set_font`; its default is the theme's, in the
    fixed-width font.
- **`Text(softWrap = false)`** by default: a Raven label keeps to one line unless told to wrap,
  so `softWrap = true` wraps it on the width its parent gives it. `TextAlign` is `Left`, `Right`,
  `Center`, and `Start`/`End` as left and right.
- **Bold or italic without a family** is that style of the family a `RavenTheme` gives, else of the
  theme's font, read once.
- **`RavenTheme(fontFamily: FontFamily? = null)`**: at application level it sets `Rtheme_set_font`,
  and inside a window it uses `Rwidget_set_font` on its subtree. The starting `LocalTextStyle` comes from the
  theme's font size metric.
- **`MarkdownText(markdown, modifier, style, onLinkClick = null)`** uses `Rlabel_set_markdown` and
  `Rlabel_set_link_callback`. It arrives early, in phase 2, for `samples/themes`; until phase 3's
  `TextStyle`, it takes no `style`. A clicked link goes to `onLinkClick`, or, when it is null, to
  `LocalUriHandler.current.openUri(url)`, as Compose's links do. Raven's own link handling is never
  used, so `action:` links are not Raven actions; an app that wants one handles it in `onLinkClick`
  and passes the rest to the `UriHandler`.
- **`LocalUriHandler`** provides a `UriHandler` (`openUri(uri)`), in `raven.compose.ui.platform`.
  The host's default opens the URI with `Ropen_url` (the browser, the mail program, a file) and
  throws `IllegalArgumentException` when Raven can't, as Compose's does. An app or a test provides
  its own to change how links open (phase 2,
  [LocalUriHandler](issues/archive/0094-localurihandler.md)).
- **`SelectionContainer(modifier) { }`** and **`DisableSelection { }`**, in
  `raven.compose.foundation.text.selection`, provide a composition local that `Text` and
  `MarkdownText` read: inside a container they call `Rlabel_set_selectable`, so the mouse selects
  (drag, double click for a word, triple for a paragraph, Shift + click) and Ctrl+C (Cmd+C) copies;
  Markdown copies without its marks, and its links are still clicked. The container stacks its
  content as a `Box`, as Compose's lays it out; `DisableSelection` lays nothing out. Each text
  selects on its own, and a selectable text takes the mouse, so a `clickable` around it no longer
  gets the click ([Selectable text](issues/archive/0195-selectable-text.md)).

### Custom drawing, viewports and effects (phase 3)

- **`Canvas(modifier) { }`** is a custom widget, sized by its Modifier.
  - `DrawScope` has `drawRect`, `drawRoundRect`, `drawCircle` and `drawLine`, with `style = Fill
    | Stroke(width)`, plus `clipRect { }` and `drawText`.
  - Raven extensions: `drawCubic`, `drawTriangle`, `drawGlass` and `drawShader`. There is no
    `Path`.
  - `onDraw` is read-observed, and a change in state it read calls `Rwidget_invalidate`.
- **Viewports**:
  - `Viewport(image: ImageBitmap, modifier, stretch, flipY, onSizeChanged: ((IntSize) -> Unit)? = null)`
    shows an `ImageBitmap`. `onSizeChanged` reports the viewport's size in **pixels**
    (`Rviewport_set_resize_callback`), the size to render at. There is no `realtime` flag: updating
    the image redraws it, and an app animating it updates it from a `withFrameNanos` loop, so
    nothing redraws while it is still ([The editor's scene viewport](issues/archive/0101-the-editors-scene-viewport.md)).
  - `NativeViewport(modifier, onCreated: (NativeWindowHandle) -> Unit, onResize)` uses a native
    child window. On macOS, that is an `NSView` with a `CAMetalLayer`, which is the route for Metal
    through `platform.Metal`. It sits above the UI and is not clipped. Raven reports only the
    child's `CAMetalLayer`, so compose-raven finds its `NSView` in the window's view tree by that
    layer, and the macOS `NativeWindowHandle` carries both; GL (`compose-raven-opengl`) attaches to
    the view and Metal draws into the layer, beside Raven's MoltenVK ([verified](../spikes/README.md)).
- **`compose-raven-vulkan`**:
  - `VulkanViewport(modifier, realtime = false, onDraw: (VulkanFrame) -> Unit)` uses Raven's draw
    callback. **`onDraw` runs on Raven's render thread**: it records Vulkan commands into
    `VulkanFrame.commandBuffer`, and must not write Compose state or call Raven's UI API. An
    exception it throws is caught on that thread and ends the app through the host's error path.
  - `ImageBitmap.fromVulkanImageView(imageView, sampler, width, height)` is zero copy, and
    `setVulkanImageView(...)` points it at another view after a resize.
  - `RavenVulkan.device` exposes Raven's Vulkan handles (a `VulkanDevice`, null off Vulkan).
  - `application(vulkan = VulkanSetup.External(...))`, the module's overload of `application`,
    shares the app's own device, made with `VulkanSetup.requiredInstanceExtensions` and
    `requiredDeviceExtensions`.
  - The app brings its own Vulkan bindings.
- **`compose-raven-opengl`**:
  - `OpenGLViewport(modifier, realtime = false, onInit, onDraw: (width, height) -> Unit, onDispose)`
    runs on the UI thread in a context the module creates on a native child window
    (`NSOpenGLContext` on the child's `NSView` on macOS). It sits above the UI. `onDraw` gets the
    size in pixels and runs again when state it read changes, on a resize, and every frame with
    `realtime`. GLX on Linux waits for the Linux build (phase 4): Kotlin/Native has no GL platform
    library there, and until then no context is made, with a warning.
  - `OpenGLTexture(width, height)` renders to an FBO in a context of its own, read back into an
    `ImageBitmap` (`image`, top row first) by `render { }`, for GL under the UI.
  - The app brings its own GL bindings.
- **Shader effects**:
  - `ShaderEffect.builtin(Effect.Aurora | ...)`, `fromFile(spirv path)` and `fromBytes(...)`,
    cached in the `RavenHost`.
  - `Surface(effect, effectStyle)` uses `Rborder_set_effect`, and `DrawScope.drawShader(...)` uses
    `Rdraw_shader`.
  - `EffectStyle(color, color2, params, animate: Boolean? = null, pointer: Boolean = true)`:
    `animate` null moves as the shader does, and `true`/`false` force it; `pointer = false` keeps
    the effect from following the pointer. A changed style calls `Rborder_set_effect` again, which
    only replaces the look, so an effect whose colours follow state keeps moving unbroken. The
    `hover`/`press` overrides and an effect texture are left out ([Animated effects and continuous redraw](issues/archive/0104-animated-effects-and-continuous-redraw.md)).
- **No continuous redraw**: an animated Raven effect keeps its window drawing while it is shown,
  compose-raven's animations and `withFrameNanos` loops keep frames coming through the frame clock,
  and a `Canvas` reading changing state invalidates itself, so there is no
  `Rwindow_set_continuous_redraw` counterpart.

### Lists and trees (phase 3)

- **`LazyColumn { }` / `LazyRow { }`** use Compose's DSL (`items(list, key = { ... }) { item -> }`)
  over `VerticalScroll`/`HorizontalScroll`. In phase 3 they compose every item, with no
  virtualisation (from phase 5, [only the items in view](#lazy-virtualisation-phase-5)). `key` keeps rows stable through `Rwidget_move_child`. Each item is in an
  internal Box filling the list across, as each item of Compose's lazy layouts is a node of its
  own, so `LazyListState` knows where items start.
- **`ListView(items, itemText, selectedIndex, onSelectedIndexChange, modifier, state, onActivate)`** uses
  Raven's virtualised ListView in virtual mode, for text rows. Selection is controlled.
- **`TreeView(roots, children, itemText, key, selection, onSelectionChange, multiSelect,
  initiallyExpanded, onActivate, contextMenuItems, itemIcon, modifier)`** uses Raven's virtualised
  TreeView, diffed by key onto Raven item handles. Raven only appends items, so from the first item
  out of place among siblings the rest are destroyed and added again, keeping their expansion.
  - Selection is a controlled set of keys.
  - Expansion stays Raven's, because Raven reports no expansion. `initiallyExpanded` applies to
    new items only. From phase 5 expansion is controlled
    ([TreeView expansion](#treeview-expansion-phase-5)).
  - `contextMenuItems` opens a menu at the pointer.

### Scroll position (phase 3)

The scroll containers, the lazy lists and `ListView` take a state holder, as in Compose. Raven
reports no scroll changes, so what a state reads is polled each frame, as `CodeEditorState.cursor`
is.

- **`ScrollState`** (`rememberScrollState()`), the `state` of `VerticalScroll`/`HorizontalScroll`
  (`VerticalScroll(modifier, state = null, ...)`; without one nothing follows the box):
  - `value: Int`, the offset in dp, read-only snapshot state from `Rscroll_box_get_offset`.
  - `suspend scrollTo(value)` uses `Rscroll_box_set_offset`, which Raven clamps.
    `animateScrollTo(value)` jumps the same way, because Raven doesn't animate scrolling.
  - `suspend scrollToEnd()` (a compose-raven extension) uses `Rscroll_box_scroll_to_end`, in place
    of Compose's `scrollTo(maxValue)`. There is no `maxValue` and no `isScrollInProgress`.
- **`LazyListState`** (`rememberLazyListState()`), the `state` of `LazyColumn`/`LazyRow`:
  - `firstVisibleItemIndex` and `firstVisibleItemScrollOffset`, from the ScrollBox offset and the
    item widgets' positions (every item is composed in phase 3).
  - `suspend scrollToItem(index, scrollOffset = 0)` puts the item at the top, as in Compose.
    `animateScrollToItem` jumps the same way. There is no `layoutInfo`, `isScrollInProgress` or
    `canScrollForward` (phase 5 adds `layoutInfo` and the `canScroll` flags:
    [Lazy virtualisation](#lazy-virtualisation-phase-5)).
- **Saved and restored**: both are `rememberSaveable`, with `ScrollState.Saver` and
  `LazyListState.Saver`, so a position comes back wherever saveable state is kept, such as a
  Navigation 3 entry under its saveable-state decorator.
- **Before attach**: a state keeps the position it was asked for (or restored) and applies it when
  it is attached to its ScrollBox.
- **One scrollable per state**: a state given to a second scrollable logs an error, and only the
  first uses it.
- **`ListViewState`** (`rememberListViewState()`), the `state` of `ListView`, has only
  `suspend revealItem(index)`: `Rlist_view_scroll_to_item`, which scrolls just enough to show the
  row. Raven can't report the list's position, so it isn't saveable, and `revealItem` on a state
  not attached to a list does nothing. A log keeps its last line in view with
  `LaunchedEffect(lines.size) { state.revealItem(lines.lastIndex) }`.
- **`BringIntoViewRequester()`** and **`Modifier.bringIntoViewRequester(it)`**, in
  `raven.compose.foundation.relocation`: `suspend bringIntoView()` calls `Rwidget_scroll_into_view`
  on each widget the requester is attached to, so every ScrollBox around it, innermost first,
  scrolls just far enough along its own axis to show it, and the states read the position on the
  next frame. It jumps, as `animateScrollTo` does, and a requester attached to nothing does nothing.
  There is no `rect` parameter, as there is no `Rect`. A `ListView` row isn't a widget, so
  `revealItem` stays its way. The modifier is a `WidgetAttachedElement`, ui's internal hook for an
  element that needs its widget ([Bring into view](issues/archive/0196-bring-into-view.md)).

### Lazy virtualisation (phase 5)

From phase 5, `LazyColumn` and `LazyRow` compose only the items in view, so a list of thousands
of rows is cheap, with no Raven change ([Lists at scale: lazy virtualisation](issues/archive/0214-lists-at-scale-lazy-virtualisation.md)).
`LazyVerticalGrid` still composes every item.

- **Spacers in the ScrollBox**: the list stays a `VerticalScroll`/`HorizontalScroll` holding a
  Spacer, the visible items (each in its internal Box), and a Spacer, each Spacer sized to the items
  it stands in for. The state's `follow` reads the offset at the start of the frame, before
  recomposing, so the items it needs are composed, laid out and drawn in that same frame.
- **Sizes**: each item's size along the axis is read from its Box after layout and cached by key
  (an item without one by its place), and read again whenever it is composed. An item never
  measured counts as the average of those measured, 48 dp before any are. On its first frame a
  list composes as many items as the estimate says fill the viewport, and corrects on the next.
- **Past the edges**: one item beyond each edge of the viewport is composed, so arrow keys and Tab
  have somewhere to go, and the item holding the focus stays composed (pinned) while it is
  scrolled out of view.
- **Keeping the place**: when an item above the viewport turns out a different size than its
  estimate, or items are inserted or removed above it, the offset shifts so the first visible
  item, found by its key, stays where it was on screen.
- **State in items**: an item scrolled away is disposed, so its `remember` is lost, as in Compose.
  Its `rememberSaveable` is kept by a `SaveableStateHolder` under the item's key and comes back
  with it.
- **`contentType`** on `item` and `items` is accepted, for Compose's code, and ignored: Raven has
  no compositions to reuse.
- **`LazyListState`** gains:
  - `layoutInfo: LazyListLayoutInfo`, snapshot state read once a frame: `visibleItemsInfo` (each
    composed item in view, with `index`, `key`, `offset` and `size`), `totalItemsCount`,
    `viewportStartOffset` and `viewportEndOffset`, in dp;
  - `canScrollForward` and `canScrollBackward`.
  
  `firstVisibleItemIndex` and its offset come from the cached sizes rather than every item's
  widget. `scrollToItem` to an item never measured jumps to its estimated place and corrects over
  the next frames until the item is at the start. `isScrollInProgress` stays out, as Raven reports
  no scrolling.
- **The window**: the arithmetic is one internal, pure module, the lazy list window. From the
  cached sizes, the items' keys, the offset, the viewport and the focused item, it gives the range
  to compose, the two Spacers' sizes, the offset correction and the layout info. It is tested on
  its own with plain numbers, and the lists through `runRavenTest`.
- **Not yet**: sticky headers, `Modifier.animateItem` and prefetching ahead of the scroll.

### Table (phase 5)

`Table` is Raven's sortable data grid, always in virtual mode, for text cells: only the cells drawn
are asked for, so a table of 100,000 rows costs no more than a short one ([Lists at scale: Table](issues/archive/0215-lists-at-scale-table.md)).
Compose has no data table, so the shape follows `ListView`'s.

- **`Table(items: List<T>, selectedIndex, onSelectedIndexChange, modifier, sort: TableSort? = null,
  onSortChange: (TableSort?) -> Unit = {}, state = rememberTableState(), onActivate: (Int) -> Unit = {},
  headerVisible = true, gridLines = false, striped = false, columns: TableScope<T>.() -> Unit)`**:
  ```kotlin
  Table(people, selected, { selected = it }, sort = sort, onSortChange = { sort = it }) {
      column("Name", width = 160.dp) { it.name }
      column("Age", width = 60.dp, align = Alignment.End) { it.age.toString() }
      column("City", fill = 1f) { it.city }
  }
  ```
- **Columns**: `TableScope.column(header, width = 120.dp, fill = 0f, align: Alignment.Horizontal =
  Alignment.Start, sortable = true, text: (T) -> String)`, matched to Raven's columns by place. A
  changed header, alignment, fill or sortable flag is applied; a changed number of columns makes
  them again. The user resizes a column by dragging its header's edge, and Raven keeps that width:
  the app's `width` applies when the column is made and again when the app changes it.
- **Sorting is the app's**: `TableSort(column: Int, descending: Boolean)`. A click on a header
  calls `onSortChange` (null when Raven turns sorting off), and Raven shows the arrow of the `sort`
  passed, so a sort the app doesn't take reverts. The app sorts `items`: the table shows them in the
  order given, and every index is an index of `items`.
- **Selection** is `ListView`'s: one row or none at -1, controlled, a choice the app doesn't take
  reverting. `onActivate` gets a row double-clicked or Enter pressed on it.
- **`TableState`** (`rememberTableState()`) has only `suspend revealRow(index)`
  (`Rtable_scroll_to_row`); Raven can't report the table's position, so it isn't saveable.
- **Not yet**: composable cells (a table over the lazy lists), several rows selected, a context
  menu, and widths the app controls, which would need Raven to report a resize.
- The XML translator maps `<Table>` and its `<Column>`s onto `Table`, its stored `<Row>`s
  becoming a `listOf` of each row's texts.

### TileView (phase 5)

`TileView` is Raven's virtualised grid of tiles, an icon over a name, for collections such as a
folder of files or a library of assets: Raven asks for the tiles in view only, while it paints,
so ten thousand tiles cost what ten do ([Lists at scale: TileView](issues/archive/0216-lists-at-scale-tileview.md)).
The app's items and selection are the model; one function says what each item's tile shows.

- **`TileView(items: List<T>, key: (T) -> Any, selection: Set<Any>, onSelectionChange: (Set<Any>) -> Unit,
  modifier, tileSize = DpSize(112.dp, 112.dp), iconSize = 64.dp, spacing: Dp? = null,
  layout = TileLayout.Tiles, emptyText: String? = null, onActivate: (T) -> Unit = {},
  contextMenuItems: ((T?) -> List<ContextMenuItem>)? = null, state = rememberTileViewState(),
  tile: (T) -> TileContent)`**:
  ```kotlin
  TileView(files, key = { it.path }, selection, { selection = it }, onActivate = { open(it) }) { file ->
      TileContent(file.name, icon = icons[file.kind], detail = file.sizeText)
  }
  ```
- **`TileContent(text, icon: Painter? = null, detail: String? = null, badge: String? = null)`**:
  the name, the icon over it, a dimmer second line and a small text at the top right. `tile` isn't
  `@Composable`: Raven calls it while painting, for the tiles in view, reading the latest `items`.
  Painters are made in composition beforehand, as for `TreeView`'s `itemIcon`. The tint, the
  icon's corner radius and the name's colour stay the theme's.
- **`TileLayout.Tiles`** is the grid; **`TileLayout.Rows`** one item a line, as wide as the view,
  the icon on the left. It and `spacing` (null: Raven's) go through `Rwidget_set_attribute`, as
  Raven has no C setter for them.
- **Selection** is a controlled set of keys, as `TreeView`'s, so it survives the items being
  reordered: the user's change (a click, Ctrl or Shift and a click, a rubber band, Ctrl + A, a right
  click selecting first) calls `onSelectionChange`, and one the app doesn't take reverts. Raven
  always selects several, so there is no `multiSelect`.
- **Changes**: the same number of items redraws the tiles (`Rtile_view_refresh`); a new number gives
  them again (`Rtile_view_set_items`), selecting the app's keys again as Raven clears its selection.
- `onActivate` gets a tile double-clicked or Enter pressed on it; `contextMenuItems`, given the
  tile right-clicked or null for the empty space, lists the menu opened at the pointer.
- **`TileViewState`** (`rememberTileViewState()`) has only `suspend revealItem(index)`
  (`Rtile_view_scroll_to`); Raven can't report the position, so it isn't saveable.
- **Not yet**: dragging tiles out and the drop highlight (with drags inside the app), and
  `Rtile_view_get_item_at`, which only drops need.
- The XML translator maps `<TileView>`'s attributes onto the parameters; the app gives the items.

### TreeView expansion (phase 5)

From phase 5 `TreeView`'s expansion is the app's state, over Raven change 14, an expand callback
([Lists at scale: TreeView expansion and item operations](issues/archive/0217-lists-at-scale-treeview-expansion-and-item-operations.md)).

- **`expanded: Set<Any>` and `onExpandedChange: (Set<Any>) -> Unit`**, the keys of the open items,
  controlled as `selection` is: the user's change (the arrow, a double click, Left and Right, the
  ancestors a selection opens) calls `onExpandedChange`, and one the app doesn't take reverts. They
  replace `initiallyExpanded`. An app that doesn't care holds
  `var expanded by rememberSaveable { mutableStateOf(setOf<Any>()) }`, which keeps it saveable.
  A collapse that moves the selection to the collapsed item calls `onSelectionChange` too.
- **Children asked for lazily**: `children(item)` is asked for only for the items shown (the roots
  and the children of open items) and one level below them, so their arrows show. A file tree lists
  a folder only once its parent is open. Raven items already added stay when a branch closes.
- **`TreeViewState`** (`rememberTreeViewState()`, the `state` parameter) has only
  `suspend revealItem(key)`: an item inside a closed branch calls `onExpandedChange` with its
  ancestors added and is scrolled to once they are open; a shown one is scrolled to at once
  (`Rtree_view_scroll_to_item`). It isn't saveable.
- **Item operations**: a tab is renamed through `Tab(title)`, as Raven reads its slot's title when it
  paints; the selected item is already controlled; the item at a point (`Rtree_view_get_item_at`)
  waits for drags inside the app, its only use.
- The XML translator's `Expanded` on `<Item>` gives the initial `expanded` set.

### Pagers (phase 5)

`HorizontalPager` and `VerticalPager` show one page at a time over Raven's Carousel, named and
shaped after Compose Foundation's pagers, with Raven's dots, arrows, loop and autoplay as parameters
([Overlays and pagers: Carousel](issues/archive/0242-overlays-and-pagers-carousel.md)).

- **`HorizontalPager(state: PagerState, modifier = Modifier, loop = false, indicators = true,
  arrows = true, autoplay: Duration? = null, content: @Composable (page: Int) -> Unit)`**, and
  `VerticalPager` with the same parameters (`Rcarousel_set_orientation`). Raven swipes the pages
  with the pointer, a finger or a trackpad, and goes to one with the keys, a dot, an arrow or the
  wheel. `indicators` are the dots and `arrows` the buttons shown under the pointer, both on by
  default as in Raven; `loop` lets the user and the autoplay go from the last page to the first.
- **Every page is composed**, one Raven child each, `content(page)` for `0 until pageCount`; Raven
  paints only the page shown and the one coming in. A change of `pageCount` adds or removes pages.
- **`autoplay`** is Raven's timer (`Rcarousel_set_autoplay`): it waits while the pointer is on the
  pager, while the pages are dragged and while the keyboard is in it, and starts again after the
  user picks a page. `null` is never.
- **`PagerState`** (`rememberPagerState(initialPage = 0) { pageCount }`, saveable) has only
  `currentPage`, `pageCount`, `suspend scrollToPage(page)` and `suspend animateScrollToPage(page)`
  (`Rcarousel_set_page`, gliding or not). `currentPage` follows Raven's page changed callback (the
  user, the autoplay); it is the pager's position, not a choice the app accepts. The two moves clamp
  to the pages, as Compose's do: `loop` doesn't make them wrap.
- Tests drive a pager through its `PagerState` and the keys and clicks the harness already has
  (the arrows, Home and End, a dot).
- The XML translator turns `<Carousel>` into `HorizontalPager` or `VerticalPager` by its
  `Orientation`, `Page` as `initialPage`, and `Loop`, `Autoplay`, `Indicators` and `Arrows` as their
  parameters.

### Snackbars (phase 5)

Short messages over the content, shown through Material 3's `SnackbarHost` and `SnackbarHostState`
over Raven's Toast, with Raven's kinds, and stacking when asked, over Raven change 15 ([Overlays and pagers: Toast](issues/archive/0243-overlays-and-pagers-toast.md)).

- **`SnackbarHost(hostState: SnackbarHostState, modifier = Modifier, maxVisible = 1)`** is a Toast
  the app places where the messages should appear, as Raven's Toast is placed in an Overlay:
  `Box { content; SnackbarHost(state, Modifier.align(Alignment.BottomCenter).padding(16.dp)) }`.
  With `maxVisible = 1` the messages wait their turn (`R_TOAST_SEQUENCE`), as Material's do; above
  1 they stack (`R_TOAST_STACK`, `Rtoast_set_max_visible`), the newest at the bottom, so a host at
  the bottom grows upwards. Raven's replace mode isn't used: cancelling the earlier call does it.
- **`SnackbarHostState`** (`remember { SnackbarHostState() }`) has only
  `suspend showSnackbar(message: String, actionLabel: String? = null, kind: SnackbarKind =
  SnackbarKind.Info, duration: SnackbarDuration = if (actionLabel == null) Short else Indefinite):
  SnackbarResult`. It returns `ActionPerformed` when the message's button is clicked and `Dismissed`
  when it times out or is cleared. Cancelling the calling coroutine hides the message, or takes it
  out of the queue if it waits (`Rtoast_hide_message`).
- **`SnackbarKind`**: `Info`, `Success`, `Warning`, `Error` (`RToastKind`), coloured as Raven colours
  them. **`SnackbarDuration`**: `Short` (4 s), `Long` (10 s), `Indefinite`.
- No `currentSnackbarData` (ambiguous once messages stack), no `withDismissAction` (Raven's messages
  have only their one button), and no `snackbar` slot (Raven draws the messages).
- Tests are headless: a message timing out, its action, queuing, a stack of three, cancelling, and
  the kinds. There is no sample: no example shows a Toast alone.
- The XML translator turns `<Toast>` into a `SnackbarHost` at its place, with a remembered
  `SnackbarHostState` and a comment where the app's code shows its messages.

### Charts (phase 5)

One composable per kind over Raven's Plot and Chart, their data compared and handed to Raven only
when it changes; Raven keeps drawing, pan and zoom, the legend and the glides ([Charts: one model for Plot and Chart](issues/archive/0245-charts-one-model-for-plot-and-chart.md), from
[Charts: how Compose chart libraries shape their APIs](issues/archive/0244-charts-how-compose-chart-libraries-shape-their-apis.md)).

- **`Plot(modifier, state = rememberPlotState(), title = "", xAxis = PlotAxis(), yAxis = PlotAxis(),
  x2Axis = PlotAxis(), y2Axis = PlotAxis(), legend = PlotLegend.TopRight, crosshair, mouseText,
  tooltips) { ... }`**, the helpers at Raven's defaults. The block declares:
  - **`series(key, y: DoubleArray, x: DoubleArray? = null, name = "", color = Color.Unspecified,
    line = PlotLine.Solid, lineWidth = 1.5f, marker = PlotMarker.None, markerSize = 6f,
    markerFilled = true, step = false, fillOpacity = 0f, axes = PlotAxes.XY)`**: the points of a
    series, `x` null for 0, 1, 2...; NaN leaves a gap. The arrays are compared by reference and
    given to Raven (`Rplot_set_series_data`, which copies them) only when they change, so live data
    is a new array each update and a million points are handed over once. Keys keep a series
    across recompositions as Raven's indices shift. An unspecified colour is Raven's palette;
    `PlotLine.None` is a scatter; `axes` puts a series on X2 or Y2.
  - **`dragLine(key, axis, value: Double, onValueChange: (Double) -> Unit, label = "", color =
    Color.Unspecified)`**: a line the user drags along an axis, controlled as `Slider` is.
- **`PlotAxis(label = "", scale = PlotScale.Linear, linearThreshold = 1.0, format = "%g", range:
  ClosedFloatingPointRange<Double>? = null, grid = true, inverted = false, locked = false)`**:
  `scale` is linear, log or symlog; `format` is Raven's printf; `range = null` fits the data
  (`auto_fit`), and a range is applied when it changes. The second axes show only when a series or
  a drag line uses them.
- **`PlotState`** (`rememberPlotState()`): the view stays Raven's (drag, wheel, pinch, double
  click to fit). The state exposes each axis's visible range as observable state, read on Raven's
  view callback, with `fit()` and `setRange(axis, range)`.
- **The legend** is Raven's, at one of its nine positions or `Hidden`; a click on a series toggles
  it, which stays Raven's and isn't reported.
- **`PieChart(slices: List<PieSlice>, modifier, selectedSlice: Int? = null, onSelectedSliceChange:
  (Int?) -> Unit = {}, hole = 0f, centerText: String? = null, centerCaption: String? = null, title
  = "", format = "%g", legend = true)`**, `PieSlice(label, value, color = Color.Unspecified)`: a
  `hole` from 0.2 to 0.9 makes Raven's donut, and a null `centerText` shows the total. The selected
  slice is controlled, as `ListView`'s selection is.
- **`Gauge(value: Double, modifier, range = 0.0..100.0, bands: List<GaugeBand> = emptyList(),
  title = "", format = "%g", color = Color.Unspecified)`**, `GaugeBand(from, to, color =
  Color.Unspecified)` (unspecified: success, warning and danger in turn).
- **`Heatmap(values: List<DoubleArray>, modifier, rowLabels: List<String> = emptyList(),
  columnLabels: List<String> = emptyList(), colorScale: Pair<Color, Color>? = null, range = null,
  onCellClick: (row: Int, column: Int, value: Double) -> Unit = { _, _, _ -> }, title = "", format =
  "%g")`**: rows of values, NaN an empty cell; the click is an event.
- **`Sparkline(values: DoubleArray, modifier, range = null, fill = false, lastPointMarker = false,
  color = Color.Unspecified)`**.
- The four Chart composables share one node over Raven's Chart, its kind set by the composable.
  Their lists are compared by value, each item given to Raven by index (`Rchart_set_slice_value`,
  `Rchart_set_cell`); value changes glide as Raven makes them.
- Tests check what reached Raven through its getters, and send the user's slice and cell clicks and
  drag line moves as Raven's callbacks; no new harness API.
- The XML translator turns `<Plot>` with `<Series>`, `<Scatter>` and `<DragLine>` into `Plot { }`
  (a remembered value for each drag line), and `<Chart Kind=...>` into its composable, its
  `<Slice>`, `<Band>` and `<Row>` children into lists.

### PdfView (phase 5)

The pages of a PDF document over Raven's PdfView, drawn by PDFium when it is installed, with a
`PdfViewState` as the pagers have, over Raven change 16 ([Documents: PdfView](issues/archive/0247-documents-pdfview.md)). Printing and writing PDF documents
are decided with batch ten.

- **`PdfView(source: PdfSource?, modifier = Modifier, state = rememberPdfViewState(), password:
  String? = null)`**: `PdfSource.File(path)` or `PdfSource.Bytes(ByteArray)` (`Rpdf_view_load_file`,
  `_load_memory`); a `Res` file comes in through its path or its bytes. A changed source reloads,
  a file compared by value and bytes by reference; `null` shows no document. Raven scrolls, zooms
  (Ctrl + wheel, the keys, a pinch) and drags the pages itself.
- **`PdfViewState`** (`rememberPdfViewState(initialPage = 0, fit = PdfFit.Width, zoom = 1f)`,
  saveable): observable `currentPage` (Raven's page callback), `pageCount`, `title` and `status`;
  `suspend scrollToPage(page)`; `fit` (`Width`, `Page`, `None`) and `zoom`, observable and set by
  the app, setting a zoom making the fit `None`, as Raven does; `pageSize(page): DpSize` (points at
  96 logical units per inch); `pageText(page): String`. The page is put back once a document has
  loaded; a different source starts at page 0 and keeps the zoom and the fit.
- **`PdfStatus`**: `None`, `Loaded`, `NeedsPassword`, `Unreadable`, `NoPdfium`. Raven draws its own
  message in the view for the last three; on `NeedsPassword` the app asks the user and passes
  `password`, which reloads.
- No search: Raven has none (page text only).
- Tests are headless: the `NoPdfium` status always; the tests of a loaded document return early,
  saying so, when `Rpdf_is_available()` is false, as Raven's `test_pdf` skips.
- The XML translator turns `<PdfView Source Fit Zoom Page Password>` into
  `PdfView(PdfSource.File(...), password = ..., state = rememberPdfViewState(...))`, its `Page`
  counted from 1 there and from 0 in the state.
- The `pdf` port writes, saves and prints its invoice, so it is batch ten's.

### DiffView (phase 5)

Two texts, two files or a patch compared over Raven's DiffView, with a `DiffViewState` for moving
between the changes ([Documents: DiffView](issues/archive/0248-documents-diffview.md)).

- **`DiffView(content: DiffContent, modifier = Modifier, state = rememberDiffViewState(), mode =
  DiffMode.Unified, context = 3, whitespace = DiffWhitespace.None, ignoreCase = false, wordDiff =
  true, lineNumbers = true, language: String? = null, annotations: List<DiffAnnotation> =
  emptyList(), onLineClick: (side: DiffSide, line: Int, inGutter: Boolean) -> Unit = { _, _, _ ->
  })`**. `DiffContent.Texts(left, right, leftName = "", rightName = "")`, `.Files(leftPath,
  rightPath)` (the paths as names) or `.Patch(text)` (a unified diff: `git diff`, `diff -u`, each
  file under its names); a changed content, compared by value, compares again. `context = -1`
  shows every line; `whitespace` is `None`, `Trailing` or `All`; a null `language` is guessed from
  the names. Raven folds the unchanged lines and opens a fold when it is clicked.
- **`annotations`**, `DiffAnnotation(side, line, text)`: notes under lines, the app's; a change of
  the list clears Raven's and adds them again.
- **`DiffViewState`** (`rememberDiffViewState()`): observable `changeCount` and `currentChange`,
  `goToChange(change)`, `nextChange()`, `previousChange()`, and `selectedText`. `currentChange`
  follows these moves and each new comparison, not the user's scrolling, which Raven doesn't
  report. Raven's rows (folds expanded by the app, scrolling to a row) aren't bound.
- **`unifiedDiff(left, right, leftName = "", rightName = "", context = 3, whitespace, ignoreCase):
  String`** and **`countChanges(left, right, whitespace, ignoreCase): Int`** (`Rdiff_unified`,
  `Rdiff_count_changes`), for a patch without a view.
- Tests are headless and send line clicks as Raven's callback; no new harness API.
- The XML translator turns `<DiffView>` into `DiffView(...)`: `LeftSource`/`RightSource` as
  `DiffContent.Files`, `<Left>`/`<Right>` as `.Texts`, `<Patch>` or `PatchSource` as `.Patch`.

### WebView (phase 5)

Web pages shown by the system's engine (WKWebView, WebView2) over Raven's SystemWebView, with one
`WebViewState` for what is observed and the commands ([The web: SystemWebView](issues/archive/0249-the-web-systemwebview.md), from [The web: how Compose web views shape their APIs](issues/archive/0246-the-web-how-compose-web-views-shape-their-apis.md)).

- **`WebView(state: WebViewState, modifier = Modifier, onMessage: (String) -> Unit = {})`**. Raven
  keeps a native view over the widget, following its place, size and visibility, clipped to a
  scroll box. `onMessage` receives what the page sends with `window.raven.postMessage(value)`: a
  string as it was sent, anything else as JSON. Every page the view shows can send one: check it as
  any input.
- **`WebViewState`** (`rememberWebViewState(url)`, `rememberWebViewStateWithHtml(html, baseUrl)`):
  observable `url`, `title`, `isLoading`, `error`, `canGoBack` and `canGoForward`, read again on each
  of Raven's events (which carry nothing); and the commands `loadUrl`, `loadHtml(html, baseUrl)`,
  `loadFile(path)`, `reload`, `stop`, `goBack`, `goForward`, and `suspend
  evaluateJavaScript(script): String`, the JSON of the script's last value (a promise awaited),
  throwing `WebViewScriptException(message)` with Raven's error. The URL isn't a controlled
  parameter: the state loads it, and follows the page.
- **No progress**: `isLoading` only, as Raven reports.
- **Where the system has no engine** (Linux, headless runs, Windows without WebView2's runtime) the
  same composable shows Raven's message, `url` keeps what it was given and scripts throw;
  `WebView.isSupported` tells which.
- **Raven can't draw over the view**: menus, popups and tooltips that cross it go under it.
- Not bound: navigation interception, replies to the page, the native view's handle, and the back
  key.
- Tests are headless: the unsupported path's state and scripts, and Raven's events and messages sent
  as its callbacks; no new harness API.
- The XML translator turns `<SystemWebView>` into `WebView(rememberWebViewState...)` from its
  source, URL or HTML, with a comment where the app handles the page's messages.

### Maps (phase 5)

`compose-raven-map` holds two maps over two Raven widgets: `MapView` over upstream's raster
MapView, which landed in Raven first and so keeps the name, and `VectorMapView` over the fork's
vector map ([Maps: raster tiles beside the vector map](issues/archive/0250-maps-raster-tiles-beside-the-vector-map.md)). They share a vocabulary (a camera of latitude, longitude and zoom, and a
`TileSource` by z/x/y), not a widget.

- **`TileSource`** (`suspend tile(z, x, y): ByteArray?`, `minZoom`, `maxZoom`): the bytes of a
  tile in its map's format, vector tiles for `VectorMapView` and PNG or JPEG for `MapView`; null
  where there is none, a throw to be asked again later. Called on `Dispatchers.Default` and
  cancelled when the map no longer needs the tile. **`TileSource.files(pathTemplate)`** has Raven
  read the tiles from files (`Rmap_view_set_tile_path`), for `MapView`.
- **`MapView(tiles: TileSource, modifier = Modifier, state = rememberMapViewState(latitude,
  longitude, zoom), markers: List<MapMarker> = emptyList(), onMarkerClick: (key: Any) -> Unit = {},
  attribution: String? = null, zoomRange = 0.0..19.0, tileSize = 256, cacheSize)`** over
  `Rmap_view_*`. **`MapViewState`**: `latitude`, `longitude` and `zoom`, written by the app and by
  the user's drags, wheel and pinches (Raven's view callback); `suspend animateTo(latitude,
  longitude, zoom)` (`Rmap_view_animate_to`, returning when the flight ends); `latLonToScreen`,
  `screenToLatLon` and `cacheUsage()`. **`MapMarker(key, latitude, longitude, label = "", color =
  Color.Unspecified)`**: a changed list moves, relabels, adds or removes markers by key.
- **`VectorMapView(style: MapStyle, tiles: TileSource, modifier, state =
  rememberVectorMapViewState(...), attribution, zoomRange, capacity)`** with `VectorMapViewState`
  (`latitude`, `longitude`, `zoom`, `stats()`): what `compose-raven-map` called `MapView` until
  phase 5, renamed. `MapStyle` keeps its name: only the vector map has a style. What the vector map
  lacks beside `MapView` is ported in
  [Vector map: features from upstream's MapView](issues/0271-vector-map-features-from-upstreams-mapview.md).
- Tests are headless: tiles from a Kotlin `TileSource`, marker clicks and view changes sent as
  Raven's callbacks; no new harness API.
- The XML translator turns `<MapView>` (`<Map>`, `<TileMap>`) and its `<Marker>` children into
  `MapView(...)` and a list of markers keyed by their order.

### Printing and PDF documents (phase 5)

Raven prints PDFs only, so compose-raven prints PDFs and writes them with a builder over
`Rpdf_document_*`; no widget ([Printing and PDF documents: what Kotlin exposes](issues/archive/0252-printing-and-pdf-documents-what-kotlin-exposes.md), from [Printing: Raven's printing and how Kotlin should expose it](issues/archive/0251-printing-ravens-printing-and-how-kotlin-should-expose-it.md)). They live beside `PdfView`.

- **`buildPdf(title = "", author = "", subject = "") { page(width = 595f, height = 842f) { ... } }:
  ByteArray`** runs at once on the UI thread and returns the document's bytes: `PdfSource.Bytes`
  shows them, `printPdf` prints them, a file saves them. There is no document object to close.
  Sizes are points from the page's top-left corner.
- **A page's scope**: `fillColor`, `strokeColor` (`Color`), `lineWidth`; `rect(x, y, width, height,
  radius = 0f, fill = true, stroke = false)`, `ellipse(...)`, `line(x1, y1, x2, y2)`; `font(font:
  PdfFont = PdfFont.Sans, size, bold = false, italic = false)`, `text(x, y, text): Float` and
  `paragraph(x, y, width, text, align = TextAlign.Start): Float` (each giving where it ends),
  `measureText(text)`, `lineHeight`; `image(x, y, width, height, bitmap: ImageBitmap)`; and
  `window(x, y, width, height)`, the current window from Raven's capture. **`PdfFont.Sans`** and
  **`.Mono`** are Raven's; `addFont(bytes): PdfFont` embeds one in the document.
- **`suspend fun printPdf(source: PdfSource, title: String? = null, printer: String? = null, copies
  = 1, dialog = true, output: String? = null, password: String? = null): Boolean`** over
  `Rpdf_print` and `Rpdf_print_file`, through `awaitNativeDialog`: the system's print dialog (macOS's
  panel, Windows' dialog, Raven's own over CUPS on Linux), `false` when nothing was printed,
  cancelled or failed, as Raven can't tell them apart. `dialog = false` prints at once; `output`
  writes the job to a file instead.
- **`PdfViewState.print(title, printer, copies, dialog, output): Boolean`** prints what a `PdfView`
  shows (`Rpdf_view_print`).
- Tests are headless: `buildPdf`'s bytes (`%PDF-`, the pages drawn, their text read back through a
  `PdfView` where PDFium is, returning early without it) and `printPdf` with `dialog = false` to an
  `output` file; no new harness API. Whether Compose keeps drawing behind macOS's print panel is
  checked by hand when it is built.

### NodeGraph (phase 5)

Raven's NodeGraph over app-owned lists of nodes and links, the user's edits proposals the app takes,
as `ListView`'s selection is ([Specialist widgets: NodeGraph](issues/archive/0276-specialist-widgets-nodegraph.md)).

- **`NodeGraph(nodes: List<GraphNode>, links: List<GraphLink>, modifier = Modifier, state =
  rememberNodeGraphState(), selection: Set<Any> = emptySet(), onSelectionChange: (Set<Any>) -> Unit
  = {}, onNodeMove: (key: Any, position: Offset) -> Unit = { _, _ -> }, canLink: (from: PinRef, to:
  PinRef) -> Boolean = { _, _ -> true }, onLink: (from: PinRef, to: PinRef) -> Unit = { _, _ -> },
  onUnlink: (GraphLink) -> Unit = {}, onNodeDelete: ((key: Any) -> Unit)? = null, preset =
  NodeGraphPreset.Auto, pinTypes: Map<String, PinType> = emptyMap(), readOnly = false, grid = true,
  flow = false, content: (@Composable (GraphNode) -> Unit)? = null)`**, with the events below.
  - **`GraphNode(key, title, position: Offset, inputs: List<GraphPin> = emptyList(), outputs:
    List<GraphPin> = emptyList(), color = Color.Unspecified, icon: Painter? = null, badge: String? =
    null, active = false, deletable = true)`**, positions in canvas units; **`GraphPin(key, label =
    "", type = "", multiple = false)`** (`multiple`: an input taking several links);
    **`GraphLink(from: PinRef, to: PinRef)`**, `PinRef(nodeKey, pinKey)`, a link being its two ends.
    The lists are diffed by key against Raven's nodes, pins and links.
  - **Controlled edits**: a node moved (on release), a link made (`canLink` answering Raven's
    connect callback first), a link removed, a node deleted (the Delete key removes nodes only when
    `onNodeDelete` is given, as in Raven), and the selection, a set of node keys. An edit the app
    doesn't take reverts on the next composition.
  - **`content`**: a node's body, composed in Raven's node content (`Rnode_graph_set_node_content`).
- **Events**: `onNodeActivate(key)` (a double click), `onContextMenu(nodeKey: Any?, position)` (the
  right button, which does nothing without it), `onCanvasActivate(position)` (a double click on the
  empty canvas; without it Raven frames every node), `onWireDrop(from: PinRef, position)` (a wire
  let go on the empty canvas, to add a node linked to it) and `onPinClick(pin: PinRef)`.
- **`NodeGraphState`** (`rememberNodeGraphState()`): the view stays Raven's; `frameAll()`,
  `setView(x, y, zoom)`, `view()` (read when asked: Raven reports no change of view), `toCanvas` and
  `fromCanvas`.
- **Styling**: `preset` (`Auto`, `Modern`, `Blueprint`, `Blender`, `Neon`) and `pinTypes`
  (`PinType(color, shape)`); the finer parts (link shapes, pin sizes, grids, headers) stay with
  Raven's theme styles (`<Style Widget="NodeGraph" Part=...>`).
- Pin tags and emphasis, a pin's label width, hidden links and a badge's colour come in
  [Specialist widgets: NodeGraph extras](issues/0282-specialist-widgets-nodegraph-extras.md).
- Tests are headless: the user's edits sent as Raven's callbacks, refused edits reverting; no new
  harness API. The XML translator turns `<NodeGraph>` with `<Type>`, `<Node>`, `<Input>`, `<Output>`
  and `<Link>` into the lists, keyed by name. `studio` is ported with Timeline.

### Timeline (phase 5)

Raven's Timeline over app-owned tracks and items, the user's edits controlled as `NodeGraph`'s are,
and its playhead in a `TimelineState`, as a pager's position is ([Specialist widgets: Timeline](issues/archive/0277-specialist-widgets-timeline.md)).

- **`Timeline(tracks: List<TimelineTrack>, modifier = Modifier, state = rememberTimelineState(),
  markers: List<TimelineMarker> = emptyList(), selection: Set<Any> = emptySet(), onSelectionChange:
  (Set<Any>) -> Unit = {}, onItemChange: (key: Any, trackKey: Any, start: Double, length: Double)
  -> Unit = { _, _, _, _ -> }, onItemDelete: ((key: Any) -> Unit)? = null, onItemActivate: (key:
  Any) -> Unit = {}, duration: Double, frameRate = 24.0, snapping = true, preset =
  TimelinePreset.Auto, headerWidth: Dp? = null, rulerHeight: Dp? = null, trackHeight: Dp? = null)`**.
  - **`TimelineTrack(key, name, color = Color.Unspecified, height = 0f, locked = false, muted =
    false, items: List<TimelineItem>)`**, muted drawn dimmed, what it means the app's;
    **`TimelineItem`**: `Clip(key, start, length, label = "", color = Color.Unspecified)` or
    `Key(key, time)`; **`TimelineMarker(key, time, label = "")`**. Times in seconds; the lists are
    diffed by key against Raven's tracks and items.
  - **Controlled edits**: an item moved or trimmed (once per gesture, on release) calls
    `onItemChange`; the Delete key removes items only when `onItemDelete` is given, as in Raven; the
    selection is a set of item keys. An edit the app doesn't take reverts.
- **`TimelineState`** (`rememberTimelineState(time = 0.0)`): observable `time` (Raven's time
  callback: scrubbed or playing) and `isPlaying`; `play()`, `pause()`, `seek(seconds)`, `loop`; the
  view `setView(start, end)` and `view()`, read when asked (Raven reports no change of view). Raven
  runs the playback.
- The finer parts (clips, keys, the playhead, the ruler) stay with Raven's theme styles, as
  `NodeGraph`'s do.
- Tests are headless: edits and the time sent as Raven's callbacks, refused edits reverting; no new
  harness API. The XML translator turns `<Timeline>` with `<Track>`, `<Clip>`, `<Key>` and `<Marker>`
  into the lists, keyed by their order.

### BlockEditor (phase 5)

Raven's BlockEditor, programs of blocks that snap into each other in the style of Scratch, its
program controlled as text, as `CodeEditor`'s is ([Specialist widgets: BlockEditor](issues/archive/0278-specialist-widgets-blockeditor.md)). Raven reports only that the program changed,
not how, so the program is Raven's saved form rather than lists the app keys.

- **`BlockEditor(program: String, onProgramChange: (String) -> Unit, kinds: List<BlockKind>,
  modifier = Modifier, state = rememberBlockEditorState(), categoryColors: Map<String, Color> =
  emptyMap(), palette = true, onScriptClick: (topBlock: Int) -> Unit = {}, onContextMenu: (block:
  Int?, position: Offset) -> Unit = { _, _ -> })`**. On each change the binding reads
  `Rblock_editor_save` and calls `onProgramChange`; a `program` different from the one shown is
  loaded (`Rblock_editor_load`), so a change the app doesn't take goes back.
- **`BlockKind(name, label, shape: BlockShape, category, slotOptions: Map<Int, List<String>> =
  emptyMap(), visible = true)`**: Raven's label syntax (`%n`, `%s`, `%b`, `%r`, `%m`, `{default}`,
  `|` between a C block's sections: `"repeat %n{10}"`, `"if %b then|else"`); `BlockShape`:
  `Statement`, `Hat`, `Cap`, `C`, `Reporter`, `Boolean`. A changed list adds, relabels or hides kinds
  by name.
- **`BlockEditorState`** (`rememberBlockEditorState()`): `scripts(): List<Block>`, a read-only
  snapshot of the tree from Raven's getters (`Block(id, kind, values, inputs, next, bodies)`), for an
  app that runs the program; `glow(block, on)` for the running blocks; `select`; `setView` and
  `view()`, read when asked.
- A block marked undeletable (`Rblock_editor_set_block_deletable`) isn't bound: it isn't part of the
  saved program.
- Tests are headless: a program loaded and read back, a user's change made through Raven's calls and
  its callback reaching `onProgramChange`, a refused change loaded back, the snapshot, glow, kinds
  added and relabelled; no new harness API. The XML translator turns `<BlockEditor>` with `<Kind>`
  and `<Category>` into `kinds` and `categoryColors`, with a remembered program.

### Tabs and grids (phase 3)

- **`TabView(selectedIndex, onSelectedIndexChange, modifier, documents = false) { Tab(title,
  closable, onClose, icon) { page } }`** puts each `Tab` page in an internal Box, titled through its slot.
  Selection and closing are controlled: Raven's close callback keeps the tab and calls `onClose`.
  Raven closes tabs, and shows icons, only in the documents form.
- **No Switcher**: use `when (page) { }`.
- **`LazyVerticalGrid(columns = GridCells.Fixed(n), minCellSize: DpSize? = null, ...) { items(...) }`**
  uses UniformGrid, and composes every item in phase 3. It has one spacing for both directions.
  `minCellSize` is `Runiform_grid_set_min_cell_size` ([Small port gaps](issues/archive/0107-small-port-gaps.md)).
- **`Grid(columns: List<GridTrack>, rows: List<GridTrack>, modifier, spacing) { }`** uses Raven's
  Grid. `GridTrack` is `Auto` or `Fill(weight)`, and children are placed with `GridScope`'s
  `Modifier.cell(column, row, columnSpan, rowSpan)`.

### Images and resources (phase 3)

- **Resource root**: relative paths resolve against `resources/` next to the executable, which
  `application(resourceRoot = ...)` overrides. `registerResource(path, bytes)` embeds a file
  (`Rfile_register_memory`), and Raven then finds it first everywhere. It may be called before
  `application`: the files are kept for the process and given to each run of Raven as it starts,
  since `Rshutdown` forgets them.
- **`ImageBitmap`** wraps a Raven texture: `ImageBitmap(width, height, rgba)` with
  `update(width, height, rgba)`, which may change its size (`width` and `height` follow) and
  invalidates every `Viewport` showing it, since Raven doesn't redraw for a texture update. `update` is
  called on the UI thread, like the rest of Raven's API; pixels can be computed elsewhere (an app's
  coroutines on `Dispatchers.Default`, in place of Raven's `Rparallel_for`). Its `filterQuality`
  (Compose's `FilterQuality`, settable and a constructor parameter) is the texture's filter, which
  is why it lives on the bitmap and not on `Image`: `None` is nearest, `Low` linear (the default of
  bitmaps made from pixels), `Medium` and `High` mipmapped (`Rtexture_set_filter`); `painterResource`
  images keep Raven's mipmapped default ([Small port gaps](issues/archive/0107-small-port-gaps.md)). Others
  come from `bytes.decodeToImageBitmap()` (Compose Multiplatform's form; mipmapped, as Raven makes
  textures from files, so `Medium`) and `ImageBitmap.fromVulkanImageView(...)`. It lives on the GPU.
  The widgets showing it register with it (`addViewer`, internal API, for `Image`, menu items and
  `Viewport`), and `update` calls `Rwidget_invalidate` on each.
  App-made ones are freed by a `Cleaner` or by `close()`, both of which do nothing once the
  Raven instance that made it has shut down (the host epoch, [section 4](#4-application-host)).
- **`Image(painter, contentDescription, modifier, contentScale = ContentScale.Fit, colorFilter)`**:
  - `painterResource(path)` decodes asynchronously and is cached in the `RavenHost`.
  - `BitmapPainter(image, srcOffset, srcSize)` shows part of an image (a source region).
  - `ContentScale` is `Fit`, `Crop`, `FillBounds` or `None`.
  - `Image(bitmap: ImageBitmap, ...)` is shorthand for a `BitmapPainter` of the whole bitmap.
  - `ColorFilter.tint(color)` tints it: Raven multiplies the pixels by the colour, so a white or
    monochrome icon takes it (Compose's default blend, `SrcIn`, would replace them).
    `ColorFilter.textColor()` (a compose-raven extension,
    `Rimage_set_tint_text`) tints it with the text colour where it sits, so an icon follows the
    theme, a button's content colour or a hover colour without recomposing.
  - `Modifier.clip(RoundedCornerShape(r))` rounds its corners. On other widgets `clip` is
    `clipToBounds`, corners square, since Raven clips children only to a widget's bounds.
  - Formats are PNG, JPEG, BMP, TGA, GIF and PSD, and SVG, which Raven now rasterises into a
    texture (`Rtexture_create_from_svg`) at the size it is shown.
- **Icons** (`Painter?`): `Item(icon)`, `Tab(icon)`, `TreeView(itemIcon)`, `Window(icon)`, and
  `DrawScope.drawImage(...)`. A menu item's icon is drawn as it is, untinted. A window icon is
  pixels (`Rwindow_set_icon`, Windows and X11), and a texture can't be read back, so it is read
  again from the image file: only a `painterResource` can be one, and another painter logs a
  warning.
- **`Icon(painter, contentDescription, modifier, tint: Color = Color.Unspecified)`** (phase 5), in
  `raven.compose.foundation`, draws a vector or raster icon in one colour, 24 dp by default whatever
  the file's own size. A `painterResource` is drawn as a **mask**: its alpha only, loaded with
  `Rtexture_create_mask_from_file_async` and cached beside the colour texture by path, so the tint
  gives exactly its colour and a two-tone icon's lighter layer stays lighter; another painter is
  multiplied, as `Image`'s tint is. `Unspecified` is the text colour where the icon sits
  (`Rimage_set_tint_text`), so it follows a button's content colour, hover and the disabled fade
  without recomposing; an untinted icon is an `Image`. Raven redraws an SVG at each density and
  `RavenUiScale`, so an icon stays sharp at any size. **Any icon downloaded from
  fonts.google.com/icons** renders correctly (Material Symbols' `0 -960 960 960` viewBox, Material
  Icons' two-tone layers, a grey fill): committed Google fixtures prove it. There is no
  `ImageVector` and no bundled icon set: an app ships the SVG files it downloads
  ([Icons and resources: vector icons](issues/archive/0207-icons-and-resources-vector-icons.md)).

### Generated resources (phase 5)

- **Layout**: an app keeps its files in Compose Multiplatform's `composeResources/`, here
  `src/nativeMain/composeResources/` (the source set holding the code), under `drawable/`, `font/`
  and `files/`. There are no strings (`values/strings.xml` waits for
  localisation) and no qualifiers (dark, density, locale): an SVG is redrawn at each density
  already, and the others wait for localisation and theming.
- **`Res`**: a Gradle convention plugin in `build-logic` (`raven.compose-resources`, applied by the
  samples) generates `Res`, with `Res.drawable.<name>` (`DrawableResource`), `Res.font.<name>`
  (`FontResource`) and `suspend Res.readBytes(path)`. It goes in Compose's default package,
  `<group>.<module>.generated.resources`, lowercased with other characters `_` as Compose does
  (`compose_raven.samples.resources.generated.resources` for `samples/resources`), set by
  `ravenResources { packageOfResClass }`. File names
  become identifiers by Compose's rules (`ic-arrow back.svg` is `ic_arrow_back`), and two files
  with one name in one kind fail the build. A resource is its path under `composeResources/`.
- **Shipping**: by default the plugin syncs `composeResources/` into the `resources/` folder beside
  each executable, test executables too, the default resource root, after linking and before
  running or testing. With `ravenResources { embed = true }` it instead generates
  `EmbeddedResources`, every file as string literals (a character a byte), registered with
  `registerResource` the first time `Res` is used, which builds every file into the binary for a
  single-file app; Raven finds an embedded file before the resource root, and `Res.readBytes` finds
  one before an application starts, so the code reading them is the same.
  `ravenResources { composeResourcesDirectory }` names another folder.
- **What embedding costs** (`samples/resources`, release, macOS): its 34 KB of files add nothing
  measurable to the build (34 s compile and link, embedded or copied) and 84 KB to the executable;
  1 MB more adds about 3 s and 2.1 MB, as Kotlin/Native keeps string literals in UTF-16, two bytes
  a character. That suits icons and fonts. Files of megabytes would want a linker section instead
  (the bytes placed by the linker, read through a symbol), which waits until an app needs it.
- **Runtime**, in `raven.compose.ui.res` beside `painterResource(path)`: `DrawableResource`,
  `FontResource`, `painterResource(res: DrawableResource)` and `@Composable Font(res: FontResource,
  weight, style)` take Compose's signatures; `Res.readBytes(path)` calls `readResourceBytes(path)`,
  which reads through Raven's files (`Rfile_read`: the embedded ones, then the resource root) and
  throws for a file it can't read.
- **Not yet**: a published Gradle plugin for apps outside this repository goes with the release
  (phase 4). `samples/resources`, a check module and not a port, holds an SVG drawable, a font and
  a file, and tests them copied and embedded
  ([Icons and resources: generated resources](issues/archive/0208-icons-and-resources-generated-resources.md)).

### Transitions and Island (phase 3)

- **`Modifier.appearTransition(AppearTransition.Fade | Pop | Zoom | FlyUp | FlyDown | FlyLeft |
  FlyRight | Drop | SlideLeft | SlideRight, delay)`** plays Raven's transition each time the
  widget appears. It arrives early, in phase 2, for `samples/components`.
- **`AnimatedVisibility(visible, enter, exit) { }`**:
  - Enter specs map to the nearest Raven transition: `fadeIn` is Fade, `scaleIn` is Pop or
    Zoom, and `slideIn*` are the Fly transitions. Animation specs are ignored.
  - Exit supports `fadeOut()` only, animated with `Rwidget_set_opacity`. Other exits fall back
    to it.
- **`animate*AsState` and `Animatable`** are compose-raven's own small set, on its frame clock, in
  `raven.compose.animation.core`. Compose's `animation-core` can't be used: it has no macOS or
  Windows variant, and on Linux it is a stub that needs Compose UI (verified,
  [spikes](../spikes/README.md)). The set ([Animations beyond appear transitions](issues/archive/0103-animations-beyond-appear-transitions.md)):
  - `animateFloatAsState`, `animateDpAsState`, `animateColorAsState` and `animateOffsetAsState`;
  - `Animatable(Float)`: `value`, `velocity`, `targetValue`, `isRunning`,
    `animateTo(target, animationSpec, initialVelocity)`, `snapTo` and `stop`;
  - specs `spring(dampingRatio, stiffness)`, the default, and `tween(durationMillis, delayMillis,
    easing)` with the four standard easings;
  - **`RavenMotion.Snappy | Smooth | Bouncy | Wobbly`**, spring specs built from Raven's own motions
    (response and damping, `stiffness = (2π / response)²`), so an app moves as Raven's controls do.
    A kick is `animateTo(target, RavenMotion.Bouncy, initialVelocity = v)`.
  - Left out: `updateTransition`, keyframes, `rememberInfiniteTransition`, and `Animatable` of other
    types.
- **`TransitionRequester`**, in `raven.compose.animation`, plays a transition on demand on a widget
  already shown, as `FocusRequester` focuses one: `Modifier.transitionRequester(r)`, then
  `r.play(AppearTransition.Drop, delay)` (`Rwidget_play_transition`), whatever its
  `appearTransition`. A requester not attached does nothing.
- **`RavenAnimation.speed: Float`** and **`RavenAnimation.enabled: Boolean`**, in
  `raven.compose.animation.core`, are snapshot-backed and set Raven's `Rset_animation_speed` and
  `Rset_animations_enabled` for the whole app. They start from the system's reduced motion, which
  the host follows, and compose-raven's own animations obey them too: `speed` scales their time,
  as Compose's `MotionDurationScale` does, and `enabled = false` makes them snap.
- **`Island(expanded, onExpandedChange, modifier, expandOnHover, expandOnClick, attached, notch,
  folded, unfolded)`** is controlled. `Window(island = { Island(...) })` floats it at the top of
  the window (`Rwindow_set_island`), in its own composition.

### Docking and the file browser (phase 3)

- **`DockArea(state = rememberDockAreaState(), modifier, movable, tearable) { Panel(name, title,
  initialDock, closable, icon, open, onClose) { } }`**:
  - The arrangement is Raven's. `initialDock` (`Dock.Left/Right/Top/Bottom(size, of)`, along a
    side of the area or of the panel named `of`, and `Dock.Tab(of)`) applies when a panel is
    first added.
  - `state.saveLayout()` and `loadLayout(text)` persist the arrangement, and panels need a
    `name` for that. `state.layout` is the same text as read-only snapshot state, refreshed from
    Raven's changed callback after the user moves, resizes, shows or closes a panel.
  - `state.showPanel(name)` brings a panel's tab to the front (`Rdock_area_show_panel`); a panel
    hidden or unknown logs a warning. Which tab is shown stays Raven's, as the arrangement does;
    reopening a panel with `open = true` brings it forward too ([DockArea and HeaderBar extras](issues/archive/0105-dockarea-and-headerbar-extras.md)).
  - `open` and `onClose` are controlled, and closing hides the panel.
  - Torn-off panels live in Raven's floating windows but stay in their window's composition, so
    window-level settings still refer to the original window.
- **`FileBrowser(modifier, mode, initialDirectory, filters, multiple, showHidden, controls, places,
  fileName, defaultExtension, onAccept, onCancel, onSelectionChange)`**: the directory is
  Raven's, and `FileFilter` is shared with the file dialogs.

### Toolbar (phase 3)

- **`Toolbar(modifier, contentPadding = PaddingValues(6.dp, 4.dp), horizontalArrangement =
  Arrangement.spacedBy(4.dp)) { }`** is an HBox with `R_ROLE_TOOLBAR`. Its children are centred
  vertically, and `ToolbarScope` (a `RowScope`, for `weight`) adds `ToolbarSeparator()`. Raven's
  HBox has no padding of its own, so the role and the Modifier are on an Overlay that holds the
  HBox, padded by `contentPadding` and filling it ([Toolbar](issues/archive/0123-toolbar.md)).
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
Kotlin/Native has no reflection, so `rememberNavBackStack` takes a `SavedStateConfiguration` that
declares the keys, and refuses the default one ([verified](../spikes/README.md)):

```kotlin
val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclass(Home::class); subclass(ConfirmDelete::class) }
    }
}
val backStack = rememberNavBackStack(navConfig, Home)
```

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
- **Back** comes from each window's `NavigationEventDispatcher` (`LocalNavigationEventDispatcher`,
  from `androidx.navigationevent`, whose Compose artifact has no macOS variant), fed by the mouse
  Back button and Cmd+[ or Alt+Left that nothing else consumed (`Window(backKeys = true)`).
  `NavDisplay` adds a handler, enabled while there is somewhere to go back to, that calls `onBack`.
  Escape only closes dialogs.
- **`NavDisplay(backStack, modifier, onBack, entryDecorators, sceneStrategy,
  sceneDecoratorStrategies, transition, entryProvider)`**: `entryDecorators` defaults to the
  saveable-state decorator, `sceneStrategy` to `SinglePaneSceneStrategy`, and `onBack` removes the
  last key of a mutable back stack. The first scene shown has no transition.

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
  changes it for a subtree, at application level or in a window, and gives the subtree its `body`
  as the text style; `typography = null`, the default, keeps the outer one.
- **Button content** gets `label` merged over the text style around it, as Material's buttons do.
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
[Animated effects and continuous redraw](issues/archive/0104-animated-effects-and-continuous-redraw.md),
[The editor's scene viewport](issues/archive/0101-the-editors-scene-viewport.md),
[Lists and trees](issues/archive/0033-lists-and-trees.md),
[Tabs, Switcher and grids](issues/archive/0034-tabs-switcher-and-grids.md),
[Small port gaps](issues/archive/0107-small-port-gaps.md),
[Scroll position](issues/archive/0096-scroll-position.md),
[Image and resources](issues/archive/0035-image-and-resources.md),
[Music's class-styled themes](issues/archive/0099-musics-class-styled-themes.md),
[Island and appear transitions](issues/archive/0036-island-and-appear-transitions.md),
[Animations beyond appear transitions](issues/archive/0103-animations-beyond-appear-transitions.md),
[DockArea and FileBrowser](issues/archive/0037-docking-and-file-browser.md),
[DockArea and HeaderBar extras](issues/archive/0105-dockarea-and-headerbar-extras.md),
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
  - Every module's tests use it: foundation's, navigation3's, the samples' and the spikes'. What
    it drives in `compose-raven-ui` (`RavenHost`, the node tree, the binding layer) is public
    behind `@InternalComposeRavenApi`, as are its own members that hand out Raven types, for
    compose-raven's tests: `host`, `fetchNode()`, `fetchWidget()`, `findNodes`, `openMenus` and
    `widgetRect`. Its own main sources opt in; its own tests don't, so they check the API an app
    sees.
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
    `compose-raven-native` getters.
- **Leak checks after every test**: after `close()`/`Rshutdown`, `Rmemory_get_stats` must report
  zero blocks held, and the `raven.native` registry must report zero live `StableRef`s.
- **Live reload is off in tests**: the harness's host runs with `liveReload = false`, so no test
  reacts to the disk by itself. A test of reloading writes the file, then calls an internal
  `checkFiles()` over `Rxml_check_files`, which looks at the files even with live reload off. The
  watcher thread a loaded theme file starts is stopped by `Rshutdown`, so leak checks need nothing
  more (phase 2).
- **Isolation**: Raven starts fresh for every test. `Rinit`/`Rshutdown` can be repeated in one
  process with no memory left held ([verified](../spikes/README.md)).
- **Pure logic** gets plain Kotlin unit tests, with no Raven: Modifier folding, move-index
  adjustment and mirror-list bookkeeping. There are no screenshot tests, because the null renderer
  draws nothing.
- **CI** (phase 4; until then, local builds only): a compose job in `.github/workflows/build.yml`
  on a macOS, a Linux and a Windows runner with MSYS2, running `./gradlew -p compose allTests`.
- **What headless tests can't cover** gets a short manual checklist, run before each release on a
  real display: native, OpenGL and Metal viewports, `VulkanViewport`, dock panel tearing, the
  island notch, `native = true` dialogs, window transparency and backdrops, and IME input.

### Public test module (phase 4)

The harness is `compose-raven-test`, package `raven.compose.ui.test` (as Compose's
`androidx.compose.ui.test`). An app adds it as a test dependency: it brings
`compose-raven-foundation` and `kotlin-test` with `api`, and no Raven type.

- **`runRavenTest(checkLeaks = true) { setContent { } }`** hosts a single window.
  `setApplicationContent { }` hosts what an app's `application { }` holds instead: its own
  `Window`, with its title, island, key handling and app-wide theme.
- **Finders**: `onNodeWithText(text, substring)`, `onNodeWithTag`, `onNodeWithContentDescription`,
  their `onAllNodesWith...` forms, and `onNode(matcher)`/`onAllNodes(matcher)`. The matchers
  (`hasText`, `hasTestTag`, `hasContentDescription`, `isEnabled`, `isNotEnabled`, `isFocused`,
  `isToggleable`) combine with `and` and `or`. A collection has `assertCountEquals`, `fetchCount`,
  `[index]`, `onFirst` and `onLast`.
- **Actions** on a node, through Raven's input injection: `performClick`, `performDoubleClick`,
  `performRightClick`, `performMouseEnter`/`Exit`, `performTextInput`, `performTextReplacement`
  (selects the field's text and types over it), `performKeyPress(KeyShortcut)` (focuses the node
  first), `performSelection(index)` on a `ComboBox`, `performValueInput` on a `SpinBox`, and
  `performColorChange(color)` on a `ColorPicker`, which opens its popup if it has one and types the
  colour's hex code into its code field. Scrolling sets the offset instead: `performScrollBy(dx, dy)`
  on a scroll container, `performScrollTo()` (`Rwidget_scroll_into_view`), and
  `performScrollToIndex(i)` on a lazy list (`scrollToItem`) or a `ListView` (`revealItem`).
- **On the window**: `pressKey(key, ctrl, shift, alt, meta)` or `pressKey(KeyShortcut)` (to the
  focused widget, or the window), `clickOpenMenuItem(text)` and `openMenuCount()` for a menu the app
  built from data, `performExternalDrop(paths, text)`, and `checkFiles()` for theme files changed
  on disk, since live reload is off.
- **Assertions**: `assertExists`, `assertDoesNotExist`, `assertIsDisplayed` (shown, sized, and not
  scrolled or clipped out of an ancestor), `assertTextEquals`, `assertIsChecked`/`NotChecked`,
  `assertIsEnabled`/`NotEnabled`, `assertIsFocused`/`NotFocused`, `assertSelectedIndex` on a
  `ComboBox`, `assertValueEquals` on a `Slider`, a `SpinBox` or a progress bar, and
  `assert(matcher)`. `fetchText()` reads what a Text, a text field, a `CodeEditor` or a menu item
  shows, for a check `assertTextEquals` can't make.
- **`mainClock`**: `autoAdvance` (the default) or stepping with `advanceTimeBy(ms)` /
  `advanceTimeByFrame()`, using Raven's fixed animation time step.
- **Leak checks**: `checkLeaks = true` by default, and a failure lists the composables whose
  widgets were not released, by name and test tag (`CallbackRegistry.liveTargets`).

**Shared with Raven MCP.** As Compose splits `ui-test` from `ui-test-junit4`, the parts that don't
belong to a test move to **`compose-raven-ui-test`**: the matchers, finding nodes, the `perform…`
actions and the `fetch…` reads. They work against any `RavenHost`, headless or live, and don't depend
on `kotlin.test`. `compose-raven-test` keeps `runRavenTest`, `mainClock`, the leak checks and the
`assert…` functions on top of it (`api`), and its public API is unchanged.

- The matchers stay public, in the same package. Finding nodes and running actions on a host are
  `@InternalComposeRavenApi`, for `compose-raven-test` and `compose-raven-mcp`'s UI tools:
  `RavenHost.findNodes` and `RavenHost.findNode(matcher, index)`, which gives a `FoundNode` with the
  `fetch…` reads and `perform…` actions, plus `singleWindow`, `openMenus`, `clickOpenMenuItem`,
  `injectKey` and `widgetRect`. `nodeMatcher(description, matches)` makes a matcher of the UI tools'
  own. Nothing there waits or draws a frame: the harness waits for idle before each use and draws
  the frame a scroll needs.
- Lookups and actions throw one `NodeException`, with the messages above (`no node with text
  "Save"`). The harness reports it as a test failure, and the UI tools as a tool failure
  (`Rmcp_call_fail`), so tests and agents read the same words.
- **Idle** (see `GLOSSARY.md`) is `RavenHost.isIdle()` for both. The harness draws frames until it
  holds. A live app's tool can't draw frames, so it suspends in `RavenHost.awaitIdle(maxFrames)`,
  which returns true at the end of the first idle frame and false after `maxFrames` busy ones.
  While anything waits, it wakes the app's loop, so frames keep coming.
  - A frame is judged as the next frame callback starts, before the host drains anything. By then
    the frame has been laid out and painted, and what arrived since (input, posted tasks, timers)
    is still pending. At the end of a frame callback the host is nearly always idle, because
    whatever the frame itself changed has already been composed within it.
  - Only frames that start after the call count, so a tool that acts and then waits always sees
    at least one whole frame.
- In a live window, actions are the same real input: a pointer move, then press and release. So an
  agent can't press a disabled button, and its clicks move hover and focus as a user's would.

Decided in: [Testing strategy](issues/archive/0012-testing-strategy.md),
[Public test module for apps](issues/archive/0029-app-testing-module.md),
[Finders and actions shared by tests and UI tools](issues/archive/0150-shared-finders-and-actions.md).

## 10. Samples

Each sample is its own executable module under `compose/samples/`. A port is idiomatic,
hand-written Compose with the look of the C example it ports, and the behaviour its phase's
vocabulary covers. What it leaves out is a **port gap**, listed on its ticket with the phase that
completes it, and `roadmap.md` lists the gap under that phase; there is no gap without one. A part of
the C example with no Compose counterpart (an XML document built from code, interface files
reloaded from disk) is not a gap, and the ticket says so.

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
- **Parsing**: Raven's own parser, through `compose-raven-native`'s `raven.cinterop`, which already
  binds `raven_xml.h` (in `compose-raven-xml` from phase 4, in the sample before that, as an
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
    range; otherwise a warning. A TextInput's action, whichever of the action names the XML
    gives it, fires on Enter in Raven, so it becomes `keyboardActions = KeyboardActions(onDone = {
    /* name */ })`. `Password="true"` becomes `visualTransformation = PasswordVisualTransformation()`,
    or an `if` with `VisualTransformation.None` when it is bound; `MaxLength` becomes `maxLength`,
    0 (Raven's no limit) nothing, a bound one `.takeIf { it > 0 }`, and a negative one a warning
    ([TextInput's Password, MaxLength and action in the translator](issues/archive/0201-textinputs-password-maxlength-and-action-in-the-translator.md)).
- **Templates**:
  - Each `<Widget Name="Card" Title="Untitled" Wide="false">` becomes a top-level composable,
    `@Composable fun Card(modifier: Modifier = Modifier, title: String = "Untitled", wide: Boolean = false, content: @Composable () -> Unit = {})`,
    emitted before `TranslatedUi()`.
    - Parameters are renamed to lowerCamelCase.
    - Types come from the default value: `Boolean`, `Float` (a number with a dot), `Int`, or
      `String`.
    - Inputs get `remember` state inside the function.
    - A `<Widgets>` root produces only the functions.
    - Declarations are collected in document order, wherever they are. A name Raven refuses (one
      of its tags, `Children`, `Repeat`...) or a `<Widget>` with no element is a warning, and a
      use of a widget inside its own body is a TODO, as in Raven. A function named like a
      composable it would hide (`Column`) gets `Widget` appended.
  - `<Children/>` becomes the trailing `content` slot, and `<Children Name="x"/>` becomes a slot
    `x: @Composable () -> Unit = {}`. At the use site, children with `Into="x"` go to `x = { }`.
    - A slot takes the scope of the panel its `<Children/>` sits in (`content: @Composable
      RowScope.() -> Unit = {}` in an HBox), so the children given to it are placed as there.
    - `content` goes last, for a trailing lambda. The default content written inside
      `<Children>` is left out with a warning.
  - Layout attributes at the use site become the call's `modifier`, which the function passes to
    its root. Any other attribute that would pass through becomes a warning.
    - The call places the root: the root's written-out `Slot.*`, size and tooltip attributes,
      overridden by the use's, are resolved against the use's parent, including the implicit
      fills.
    - Inside the function, the root keeps only what is computed from parameters, then the call's
      modifier: `Modifier.width((if (wide) 200 else 100).dp).then(modifier)`, so the call wins on
      each axis, as the use's attributes do in Raven.
    - The text inside the use goes to a `Text` parameter.
  - The whole `{...}` expression language is translated into Kotlin, with the type taken from the
    target attribute: `Width="{Wide ? 452 : 220}"` becomes
    `Modifier.width((if (wide) 452 else 220).dp)`. Interpolated text becomes string templates.
    `{Instance}` becomes a comment.
    - As in Raven, `{...}` is computed only inside a `<Widget>`, `<Repeat>` or `<ForEach>`;
      elsewhere braces are ordinary characters. `{{` and `}}` are braces.
    - Each expression gets a static type (`Boolean`, `Int`, `Float`, `String`), and Raven's
      conversions are written out: `+` joins texts unless both sides are numbers, `/` is always a
      `Float` division, a comparison is numeric between numbers and textual otherwise, and a
      truth is a number that isn't 0 or a text that isn't empty. `round` is `floor(x + 0.5f)`.
    - Where Raven reads a text as a number (`'12' * 2`), the output does too, with
      `toFloatOrNull() ?: 0f`. Two cases differ: a `Float` prints as `1.0` where Raven prints `1`,
      and a text "0" or "false" counts as true.
    - An attribute that chooses a composable or an alignment (`Style`, `Orientation`,
      `Slot.HAlign`...) can't be an expression: the default is used, with a warning.
  - `If`/`<If>` becomes `if`, `<Repeat>` becomes `repeat(n) { i -> }`, and `<ForEach>` becomes
    `listOf(...).forEachIndexed { n, x -> }`.
    - A condition written out is decided by the translator, as Raven would: an element left out
      becomes a comment.
    - The default variable names are Raven's (`Index`; `Item` and `Index`). `ForEach` splits a
      written-out list as Raven does (an empty last item is no item), and a computed one with
      `split`.
    - Inputs inside a loop declare their state at the top of the loop's body, one per
      iteration.
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
  - It is a Gradle check of the sample, run by `allTests` and `check`: a second executable of the
    sample, `translateFiles`, translates each file headless into a package named after its path
    (`examples.music.ui.nowplaying`), with its warnings as comments, and an `examples` compilation
    compiles them against foundation. The translator binary is an input, so a changed translator
    always translates again.

### `samples/effects` (phase 5)

A port of Raven's `examples/effects`: a four-column grid of cards, one for each of Raven's eight
built-in effects (`Surface(effect = ShaderEffect.builtin(...), effectStyle = EffectStyle(color,
color2, params))`, ripple over a card it runs across), and a row of the app's two effects,
`wave.spv` and `liquid.spv` (`ShaderEffect.fromFile(path, backdrop, animated)`, liquid reading
what is behind it). A `Card` composable stands for the XML's `<Widget Name="Card">`. The `.spv`
files are read from Raven's `examples/effects` at a path the build writes into the sample, as
`samples/themes` does, not copied. The C example's web-only code (GLSL handed to WebGL) has no
counterpart, as there is no web target. No library change; the headless test checks each card's
effect and style and that both of the app's shaders load
([Designed samples: the three ports' plan](issues/archive/0205-designed-samples-the-three-ports-plan.md)).

### `samples/viewport-opengl` (phase 5)

A port of Raven's `examples/viewport_opengl` over `OpenGLViewport`: the spinning scene in a native
child window, a speed slider, a wireframe switch and a label with the GL renderer. The C example
draws with OpenGL 1.1 on a WGL or GLX context and has no macOS branch; `compose-raven-opengl`
makes a 3.2 core context, so the port draws the same scene with a small vertex and fragment shader
and a vertex buffer, a deviation with no library change. It runs on macOS; Linux is a port gap
that waits for GLX on Linux (graphics extras). The headless test checks the controls and the
viewport's warning; with `RAVEN_GPU_TESTS=1`, a frame is drawn and read back.

### `samples/viewport-vulkan` (phase 5)

A port of Raven's `examples/viewport_vulkan` over `compose-raven-vulkan`: the scene shown both ways,
rendered into the app's own image and shown as a texture (`ImageBitmap.fromVulkanImageView`), and
drawn in Raven's frame by `VulkanViewport(onDraw)`, on Raven's device with the queue lock. The Vulkan
calls come from a cinterop local to the sample (`VK_NO_PROTOTYPES`), against the Vulkan headers
Raven's own build found or fetched, which `-PravenVulkanInclude` overrides; Homebrew's MoltenVK
ships no `vulkan/vulkan.h`. As Raven links no Vulkan library, the sample links none either: it opens
the library Raven opened and resolves each function on Raven's device. Shipping Vulkan bindings with
the module stays in graphics extras. The headless test checks
the controls and that neither view draws; with `RAVEN_GPU_TESTS=1`, both views draw a frame.

### `samples/carousel` (phase 5)

A port of Raven's `examples/carousel`: five pages in a `HorizontalPager`, the title and the count of
the page shown in a label, check boxes for `loop`, `autoplay` (three seconds) and the orientation,
which swaps `HorizontalPager` for `VerticalPager` over the same hoisted `PagerState`, and Back and
Next buttons, Next going back to the first page from the last when it doesn't loop, as the C
example does. No port gaps; the headless test checks each control
([Overlays and pagers: Carousel](issues/archive/0242-overlays-and-pagers-carousel.md)).

### `samples/plot` (phase 5)

A port of Raven's `examples/plot`: three live series of 600 points, one on Y2, a new array each
update; a Bode plot on a log X with fixed, locked Y ranges, `"%g dB"` labels and a cutoff drag line;
and a million-point random walk on a symlog Y, its view read back through `PlotState`. No port
gaps; the headless test checks each plot ([Charts: one model for Plot and Chart](issues/archive/0245-charts-one-model-for-plot-and-chart.md)).

### `samples/charts` (phase 5)

A port of Raven's `examples/charts`: sparklines pushed every second, gauges nudged, pie slices
rescaled in place, and the heatmap retitled from its cell click. No port gaps; the headless test
checks each chart ([Charts: one model for Plot and Chart](issues/archive/0245-charts-one-model-for-plot-and-chart.md)).

### `samples/diff-view` (phase 5)

A port of Raven's `examples/diff_view`: two versions of `server.c` side by side, the mode and
context controls, the whitespace and case toggles, Previous and Next with "change N of M", and a
note added where a line is clicked. The two files are read from Raven's `examples/diff_view` at a
path the build writes into the sample. No port gaps; the headless test checks each control ([Documents: DiffView](issues/archive/0248-documents-diffview.md)).

### `samples/web-view` (phase 5)

A port of Raven's `examples/web_view`: Back, Forward (enabled from the state), Reload and Home; an
address field loading on Enter; the title and the loading or error status; a colour picker setting
a CSS variable of the bundled `welcome.html` through `evaluateJavaScript`; "Ask the page for its
size" and its answer; and a log of the page's messages. The page is read from Raven's
`examples/web_view/pages` at a path the build writes into the sample. Without an engine it shows
Raven's message. No port gaps; the headless test checks the controls on the unsupported path
([The web: SystemWebView](issues/archive/0249-the-web-systemwebview.md)).

### `samples/map` (phase 5)

A port of Raven's `examples/map`: tiles it draws itself, offline, from a Kotlin `TileSource` as the
C example draws them from `world.h` (or tiles read from files with `TileSource.files`), the cities'
markers, World, Europe, Americas and Asia flying there with `animateTo`, zoom in and out, a click
on a marker flying to it, and the place and cache usage in a label. `RAVEN_MAP_DEMO`'s tour, the C
example faking a drag and the wheel to show itself, isn't ported: the headless test covers the same
paths through the state. No port gaps ([Maps: raster tiles beside the vector map](issues/archive/0250-maps-raster-tiles-beside-the-vector-map.md)).

### `samples/pdf` (phase 5)

A port of Raven's `examples/pdf`: the three-page invoice written with `buildPdf` and shown at the
start, Open... and Save... through the file dialogs, Print..., Previous and Next with "Page N of
M", zoom out and in with its percentage (over `PdfViewState`'s observable zoom), the fit, and the
document's title. No port gaps; the headless test checks each control, those needing PDFium
returning early without it ([Printing and PDF documents: what Kotlin exposes](issues/archive/0252-printing-and-pdf-documents-what-kotlin-exposes.md)).

### `samples/studio` (phase 5)

A port of Raven's `examples/studio`: the node graph with its typed pins, links refusing a loop
(`canLink`), the selected nodes shown in a label and coloured from the `ColorPicker`; the timeline's
tracks, clips, keys and markers, Play, Pause and Loop over `TimelineState`, the time in a label;
and the presets of both picked from a `ComboBox`. The C example's XML live reload
(`Rxml_set_reload_callback`) has no counterpart: the composition is the source. No port gaps; the
headless test checks each control ([Specialist widgets: Timeline](issues/archive/0277-specialist-widgets-timeline.md), with
[Specialist widgets: NodeGraph](issues/archive/0276-specialist-widgets-nodegraph.md)).

### `samples/blocks` (phase 5)

A sample of compose-raven's own, as no C example uses BlockEditor: a turtle. Kinds for move, turn,
pen up and down, repeat and if; a Run button stepping through the clicked script
(`onScriptClick`), each running block glowing (`state.glow`), its program read through
`state.scripts()`; and a `Canvas` drawing the turtle's path. The program is held in a
`rememberSaveable` string. The headless test runs a script and checks the path ([Specialist widgets: BlockEditor](issues/archive/0278-specialist-widgets-blockeditor.md)).

### `compose-raven-xml` and `raven-xml2compose` (phase 5; published in phase 4)

The translator moves out of the sample into the library module `compose-raven-xml`, and the
sample depends on it. Both are built and tested in this repository in phase 5; publishing them
stays with the release, phase 4. The command-line tool `raven-xml2compose` is built on the module and starts
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

**Build-time translation** (phase 5): the convention plugin `raven.compose-xml` in `build-logic`,
separate from the resources plugin, translates an app's XML as part of its build:

```kotlin
ravenXml {
    source = "src/nativeMain/xml"
    packageName = "app.ui"
    main = "main.xml"
    werror = false
}
```

- One task runs `raven-xml2compose` over the whole folder; each subfolder is a subpackage of
  `packageName` (`xml/notes/notes.xml` under `app.ui` is in `app.ui.notes`). Its inputs are the XML
  files and the tool, taken from the tool project's link task, so a changed translator translates
  again; its output, `build/generated/ravenXml/<target>`, is a source directory of `nativeMain`.
- Warnings show in the build's output; `werror = true` fails the build on them, and errors always do.
- A file the XML names (`<Image Source="drawable/logo.png"/>`) keeps its path, which resolves
  against the resource root: with the resources plugin, that is its path under `composeResources/`.
- Only apps in this repository use it, through the tool's project; apps outside wait for the release.
- The examples check translates Raven's examples through the plugin, each example in its subpackage
  ([Icons and resources: build-time XML translation](issues/archive/0209-icons-and-resources-build-time-xml-translation.md)).

Decided in: [XML to Compose translator sample](issues/archive/0013-xml-to-compose-translator.md),
[Templates in the XML translator](issues/archive/0016-xml-translator-templates.md),
[XML translator as a tool](issues/archive/0030-translator-as-a-tool.md),
[Port the Raven samples to Compose](issues/archive/0014-port-raven-samples.md).

## 11. Required Raven changes

These changes go into the `psh/Raven` fork and are offered upstream later. Changes 1, 3, 4, 5,
6, 7, 8, 9, 10 and 11 are made, one commit each on the `compose-raven` branch, with a C test except
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
7. **`Rborder_get_outline`, `Rborder_get_corner_radius` and `Rborder_get_shadow`** (required for
   tests, phase 2): a Border had setters for its outline, corner radii and shadow but no getters,
   so `Surface`'s tests couldn't read them back. Any pointer may be NULL; on a widget that isn't a
   Border they report an error and read zeros; the corner radii are the ones drawn (the Border's
   own, else a style's `R_METRIC_CORNER_RADIUS`). With a C test in `tests/test_layout.c` and the
   language bindings regenerated. Found by
   [Surface, FlowRow and Expander](issues/archive/0064-surface-flowrow-and-expander.md).
8. **The reload callback for theme files** (required, phase 2): Raven reloaded a changed theme
   file without calling `Rxml_set_reload_callback`, which reported interface files only, so
   compose-raven had to re-read the theme names and colours every frame while live reload was on.
   A theme file's reload now calls it with the file's path and whether it loaded, as an interface
   file's does, after the theme was loaded again; a file with a mistake is reported once, not ok,
   when Raven stops trying it again. No function changes, only the header's description of the
   callback. With a C test in `tests/test_themes.c`. Found by
   [RavenTheme and theme files](issues/archive/0065-raventheme-and-theme-files.md), made in
   [Raven reports theme file reloads](issues/archive/0090-raven-reports-theme-file-reloads.md).
9. **`Rwindow_focus` in the null window system** (required for tests, phase 3): headless, every
   window had the keyboard from its creation and the null window system had no `window_focus`, so
   no test could make a window inactive. It now keeps its windows (not the camera-housing
   overlays) and gives the keyboard to the focused one, sending a focus event to each other window
   that loses it, as a window system does. No function changes. With a C test in
   `tests/test_widgets.c`. Made in
   [Windows that come and go, and their owners](issues/archive/0112-windows-that-come-and-go-and-their-owners.md).
10. **`Rtext_input_set_insets(Rid text_input, float start, float end)`** and `Rtext_input_get_insets`
    (required for `TextField`'s icons): room at the start and the end of a text input's text, in
    logical units, beyond the look's margin. The text, caret, placeholder and selection, scrolling
    and placing the caret with the mouse keep to what is left; the background and focus ring stay
    the whole widget, and the desired width grows by both. The start is on the right in
    right-to-left; a negative inset is an error. With a C test in `tests/test_widgets.c`. Made in
    [Leading and trailing icons on TextField](issues/archive/0200-leading-and-trailing-icons-on-textfield.md).
11. **`Rwidget_set_focus_on_click(Rid widget, int enabled)`** and `Rwidget_get_focus_on_click`
    (required for `TextField`'s icons): with false, a click on the widget or inside it leaves the
    keyboard focus where it is, as a toolbar button does, while the click works and Tab still
    reaches what is focusable inside; the window's press checks the widget and its ancestors up to
    a popup. With a C test in `tests/test_widgets.c`. Made in the same ticket.
12. **`Rtexture_create_mask_from_file_async(const char* path)`** (required for `Icon`, made in `67da73d`):
    decodes any image, SVG or raster, keeping only its alpha in `R_TEXTURE_ALPHA8`, which the shader
    samples as white with that alpha, so a tint gives exactly its colour; an SVG is redrawn at each
    scale as a colour texture is. Planned in [Icons and resources: vector icons](issues/archive/0207-icons-and-resources-vector-icons.md).
13. **`Rtext_editor_set_token_color(Rid editor, RToken token, float r, float g, float b, float a)`**
    and `Rtext_editor_clear_token_colors(Rid editor)` (required for `SyntaxTheme`, to be made): a
    colour for one kind of token in one editor, over its theme's; Raven's are otherwise only the
    global theme's (`Rtheme_set_token_color`). Planned in
    [Small widgets: the CodeEditor extras](issues/archive/0212-small-widgets-the-codeeditor-extras.md).
14. **`Rtree_view_set_expanded_callback(Rid tree, RTreeExpandCallback callback, void* user_data)`**,
    `typedef void (*RTreeExpandCallback)(Rid tree, Rid item, int expanded, void* user_data)`
    (required for `TreeView`'s controlled expansion, to be made): called for each expansion change
    Raven makes itself (the arrow, a double click, Left and Right, the ancestors a selection opens),
    not for `Rtree_item_set_expanded`. A collapse that moves the selection to the collapsed item
    also calls the selection-changed callback, which it skips today. Planned in
    [Lists at scale: TreeView expansion and item operations](issues/archive/0217-lists-at-scale-treeview-expansion-and-item-operations.md).

15. **Toast message ids**: `int Rtoast_show_message(Rid toast, const char* text, RToastKind kind,
    double seconds)` returning the message's id, `Rtoast_hide_message(Rid toast, int id)` hiding it
    or taking it out of the queue, and `Rtoast_set_message_ended_callback(Rid toast,
    RToastEndedCallback callback, void* user_data)`, `typedef void (*RToastEndedCallback)(Rid toast,
    int id, int by_action, void* user_data)` (required for `showSnackbar`'s result, to be made):
    called once for every message that goes, timed out, hidden, cleared, replaced or ended by its
    button. Raven reports only the button today, and a message has no identity, so the end of one
    message among several can't be told. Planned in [Overlays and pagers: Toast](issues/archive/0243-overlays-and-pagers-toast.md).

16. **`Rpdf_view_set_zoom_changed_callback(Rid view, RPdfZoomChangedCallback callback, void*
    user_data)`**, `typedef void (*RPdfZoomChangedCallback)(Rid view, float zoom, RPdfFit fit, void*
    user_data)` (required for `PdfViewState`'s observable zoom, to be made): called when the user
    zooms or picks a fit, and when a fit's zoom changes after a resize; not for
    `Rpdf_view_set_zoom` and `Rpdf_view_set_fit`. Raven reports only the page today, so the
    `pdf` example polls the zoom on a timer. Planned in [Documents: PdfView](issues/archive/0247-documents-pdfview.md).

17. **The vector map's own names** (required so that upstream's map keeps `Map`, to be made): the
    fork's vector map widget, already the class `VectorMap`, has its C API renamed from `Rmap_*` to
    `Rvector_map_*`, `R_WIDGET_MAP` to `R_WIDGET_VECTOR_MAP` and `RAVEN_WITH_MAP` to
    `RAVEN_WITH_VECTOR_MAP`, with `test_vector_map` and `vector_map_benchmark`; upstream's MapView
    owns `<Map>` and `Rmap_view_*`. Planned in [Maps: raster tiles beside the vector map](issues/archive/0250-maps-raster-tiles-beside-the-vector-map.md).

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
| `ScrollState` has no `maxValue` or `isScrollInProgress`, and has `scrollToEnd()`; `LazyListState` has no `isScrollInProgress`; `animateScrollTo`/`animateScrollToItem` jump | the full scroll state, animated | [8](#scroll-position-phase-3), [8](#lazy-virtualisation-phase-5) |
| `ListView` takes a `ListViewState` that can only reveal a row | n/a (no `ListView` in Compose) | [8](#scroll-position-phase-3) |
| `bringIntoView()` takes no `rect`, brings the whole widget and jumps | `bringIntoView(rect)` animates to show a part of it | [8](#scroll-position-phase-3) |
| `DatePicker` is a field with a drop-down calendar, controlled by a `LocalDate`; `TimePicker` a 24-hour field controlled by a `LocalTime` | Material 3's inline calendar and clock dial, driven by `DatePickerState` (UTC milliseconds) and `TimePickerState(is24Hour)` | [8](#date-and-time-pickers-phase-5) |
| `Rating`, `Pagination` and `Breadcrumb` | no counterpart | [8](#rating-pagination-breadcrumb-and-verticalslider-phase-5) |
| `VerticalSlider` is controlled by a `value` | Material 3's `VerticalSlider(state: SliderState)` | [8](#rating-pagination-breadcrumb-and-verticalslider-phase-5) |
| `HorizontalPager`/`VerticalPager` compose every page, and take `loop`, `indicators`, `arrows` and `autoplay`; no `contentPadding`, `pageSpacing`, `pageSize`, `beyondViewportPageCount`, `flingBehavior`, `key` or `userScrollEnabled` | lazy pages and those parameters; dots, loops and autoplay are the app's to build | [8](#pagers-phase-5) |
| `PagerState` has only `currentPage`, `pageCount`, `scrollToPage` and `animateScrollToPage` | `currentPageOffsetFraction`, `settledPage`, `targetPage`, `isScrollInProgress` and more | [8](#pagers-phase-5) |
| `showSnackbar` takes a `kind` (`SnackbarKind`), `SnackbarHost` a `maxVisible` that stacks messages; no `withDismissAction`, `currentSnackbarData` or `snackbar` slot | no kinds, one message at a time, a dismiss button and custom snackbars | [8](#snackbars-phase-5) |
| `Plot`, `PieChart`, `Gauge`, `Heatmap` and `Sparkline`, drawn by Raven; labels formatted by printf strings; the view and the legend's series visibility are Raven's | no charts (libraries format labels with lambdas, and hoist their views) | [8](#charts-phase-5) |
| `PdfView` with a `PdfViewState`; no search | no PDF view | [8](#pdfview-phase-5) |
| `buildPdf { page { } }` returning bytes and `suspend printPdf(...)`: Boolean, no widget | AWT's `PrinterJob`/`Printable` and Android's `PrintManager` | [8](#printing-and-pdf-documents-phase-5) |
| `DiffView` with a `DiffViewState` whose `currentChange` follows navigation only; Raven's rows unbound | no diff view | [8](#diffview-phase-5) |
| `WebView(state)` with one `WebViewState` for observed values and commands; no progress, interception, replies, native handle or back key; popups go under the view | compose-webview-multiplatform's `WebViewState` and `WebViewNavigator`, loading progress, a request interceptor and a JS bridge with replies | [8](#webview-phase-5) |
| `MapView` (raster, markers) and `VectorMapView` (a MapLibre style), sharing `TileSource` | no maps | [8](#maps-phase-5) |
| `NodeGraph` over app-owned nodes and links, edits controlled; its view not observable; pin tags and emphasis, label widths, hidden links and badge colours not yet bound | no node graph | [8](#nodegraph-phase-5) |
| `Timeline` over app-owned tracks and items, edits controlled; the playhead in a `TimelineState` Raven plays; its view not observable | no timeline | [8](#timeline-phase-5) |
| `BlockEditor` controlled by its program as text, the tree read through a snapshot; undeletable blocks not bound | no block editor | [8](#blockeditor-phase-5) |
| `SyntaxTheme` and `LocalSyntaxTheme` colour `CodeEditor`'s tokens | no code editor | [8](#phase-2-codeeditors-editing-extras) |
| `ApplicationScope.sendNotification` beside `TrayState.sendNotification`, each returning whether the system took it | notifications only through a `TrayState` | [8](#tray-notifications-and-taskbar-phase-5) |
| `Window(taskbarProgress)`, `TaskbarBadge` and `DockMenu` | AWT's `Taskbar` | [8](#tray-notifications-and-taskbar-phase-5) |
| `TextField` takes a `String` placeholder, single line only, and has no `label`, `prefix`, `suffix` or `supportingText` | a placeholder slot, multi-line, and those slots | [8](#8-component-vocabulary) |
| `Text(softWrap)` is false by default, as Raven's labels are | true | [8](#fonts-and-text-styling-phase-3) |
| No `TextFieldValue`; if the app rewrites the text, the caret jumps to the end | selection kept in `TextFieldValue` | [7](#7-events-and-state) |
| `TextField(maxLength)` limits typing and pasting inside the field | no limit; the app filters in `onValueChange` | [7](#7-events-and-state) |
| `VisualTransformation` is sealed: `None` or `PasswordVisualTransformation` | any `filter(AnnotatedString)` | [7](#7-events-and-state) |
| `KeyboardActions` has only `onDone`, which Enter calls; no `KeyboardOptions` | `onDone`, `onGo`, `onSearch` and others, picked by `KeyboardOptions.imeAction` | [7](#7-events-and-state) |
| `CodeEditor(completions)` is a synchronous `(prefix) -> List<Completion>`, called inside Raven's completion callback | no code editor; completions come from the app's own popup | [8](#phase-2-codeeditors-editing-extras) |
| A rejected input change can show for a frame before it reverts | never shown | [7](#7-events-and-state) |
| Plain `Button` is Raven's primary style; `OutlinedButton` is the default style | Material's filled and outlined buttons | [8](#8-component-vocabulary) |
| `CodeEditor`, `HorizontalSplitter`, `VerticalSplitter`, `ComboBox`, `SpinBox` | no counterpart in Compose Foundation | [8](#8-component-vocabulary) |
| `ColorPicker` | no counterpart in Compose Foundation | [8](#colorpicker-phase-3) |
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
| Buttons, `Checkbox`, `RadioButton` and `Slider` colours take explicit hovered and pressed colours | hover and press come from `interactionSource` and indication | [8](#phase-2-theming) |
| `ColorFilter.textColor()` tints an image with the text colour around it; there is no `LocalContentColor` | `Icon(tint = LocalContentColor.current)` | [8](#images-and-resources-phase-3) |
| `Icon`'s `tint = Color.Unspecified` is the text colour where it sits; an untinted icon is an `Image` | `Unspecified` draws the icon untinted, and the default is `LocalContentColor.current` | [8](#images-and-resources-phase-3) |
| No `ImageVector` and no bundled icons: an app ships the SVG files it downloads | `Icons.Filled.*` from the material-icons libraries | [8](#images-and-resources-phase-3) |
| No coroutine `pointerInput`, drag gestures or long press | `pointerInput`, `draggable`, `combinedClickable(onLongClick)` | [7](#7-events-and-state) |
| No `onPreviewKeyEvent`, because Raven doesn't tunnel events | preview handlers run root to leaf | [7](#7-events-and-state) |
| No `clearAndSetSemantics` or `mergeDescendants` | merging and clearing semantics | [7](#7-events-and-state) |
| An input in a `Row` is labelled automatically by the `Text` after it | the app merges semantics itself | [7](#7-events-and-state) |
| `accessibleRole` and `accessibleDescription` reach Raven roles that Compose's `Role` lacks | `Role` has a fixed set | [7](#7-events-and-state) |
| `FontWeight` has two steps (W100 to W500 regular, W600 to W900 bold) | any weight the font file has | [8](#8-component-vocabulary) |
| No `AnnotatedString` or spans; `MarkdownText` for formatted text | `AnnotatedString`, `SpanStyle` | [8](#8-component-vocabulary) |
| `MarkdownText`'s links go to `onLinkClick` or `LocalUriHandler`; Raven's `action:` links aren't special | `LinkAnnotation` listeners, then `LocalUriHandler` | [8](#8-component-vocabulary) |
| In a `SelectionContainer`, each `Text` selects on its own, and Ctrl+A selects the focused one; a selectable text takes the click from a `clickable` around it | one selection runs across every text in the container | [8](#fonts-and-text-styling-phase-3) |
| `Typography` has eight compose-raven roles (`display` ... `code`), sized by Raven's Markdown factors; Raven-drawn text and Markdown headings don't follow a custom typography | Material's fifteen roles, applied to every component | [8](#8-component-vocabulary) |
| `Canvas` has no `Path`, but has Raven's cubic curves, triangles, glass and shaders | `Path`, `DrawScope` on Skia | [8](#8-component-vocabulary) |
| `Viewport(onSizeChanged)` reports its pixel size; there is no generic `Modifier.onSizeChanged`, and `Density` has only `density`, `toPx` and `toDp` | `Modifier.onSizeChanged` on any element; a full `Density` with `fontScale` | [8](#custom-drawing-viewports-and-effects-phase-3) |
| Shader effects are SPIR-V (`ShaderEffect`) | `RuntimeShader` / AGSL | [8](#8-component-vocabulary) |
| Native, OpenGL and Metal viewports sit above the UI and aren't clipped | composited like any element | [8](#8-component-vocabulary) |
| `VulkanViewport.onDraw` runs on Raven's render thread | drawing happens on the UI thread | [8](#8-component-vocabulary) |
| `LazyColumn`/`LazyRow` compose every item in phase 3; from phase 5 the items in view and one past each edge, sized by estimate until measured | only visible items are composed, measured as they are | [8](#lazy-virtualisation-phase-5) |
| `Table` has text cells only, one selected row, no context menu, and widths the user sets, not the app; the app sorts its items | no data table in Compose | [8](#table-phase-5) |
| `TileView`'s tiles are described by data (`TileContent`), not composed; no drags out yet | no tile view in Compose | [8](#tileview-phase-5) |
| `contentType` is accepted and ignored, and there are no sticky headers, `animateItem` or prefetching | compositions reused by content type | [8](#lazy-virtualisation-phase-5) |
| `ListView` and `TreeView` take data and text, not composable rows | rows are composables in lazy lists | [8](#8-component-vocabulary) |
| `TreeView` expansion is Raven's, not controlled | n/a (no tree in Compose Foundation) | [8](#8-component-vocabulary) |
| `TabView` holds its pages | Material's `TabRow` is separate from the content | [8](#8-component-vocabulary) |
| `LazyVerticalGrid` has `GridCells.Fixed` only and one spacing, and a `minCellSize` | `Adaptive` cells, separate arrangements | [8](#8-component-vocabulary) |
| `filterQuality` is a property of `ImageBitmap`, not a parameter of `Image` | `Image(filterQuality)` | [8](#images-and-resources-phase-3) |
| `Grid` with tracks and `Modifier.cell` | no explicit grid in Foundation | [8](#8-component-vocabulary) |
| `ImageBitmap` lives on the GPU and can't be read back | a CPU bitmap with `readPixels` | [8](#8-component-vocabulary) |
| No `ImageVector` or vector drawing; an SVG file is rasterised into a texture | `ImageVector`, `painterResource` for vectors | [8](#images-and-resources-phase-3) |
| `ColorFilter.tint` multiplies; `ColorFilter.textColor()` follows the text colour | `tint` blends `SrcIn` | [8](#images-and-resources-phase-3) |
| `Window(icon)` is a `painterResource` only | any `Painter` | [8](#images-and-resources-phase-3) |
| `ContentScale` has `Fit`, `Crop`, `FillBounds` and `None` only | also `Inside`, `FillWidth`, `FillHeight` | [8](#8-component-vocabulary) |
| `Res` has drawables, fonts and files, with no strings or qualifiers, from an in-repo convention plugin; embedding is opt-in | strings, qualifiers, and the published `org.jetbrains.compose` plugin | [8](#generated-resources-phase-5) |
| `AnimatedVisibility` enter specs map to Raven's spring transitions and ignore `animationSpec`; exit is `fadeOut` only | any enter and exit spec, timed by the spec | [8](#8-component-vocabulary) |
| `Modifier.appearTransition` replays on every appearance | no counterpart | [6](#6-layout-and-modifiers) |
| `TransitionRequester` plays a Raven transition on demand | no counterpart | [8](#transitions-and-island-phase-3) |
| `RavenMotion` spring presets, and app-wide `RavenAnimation.speed`/`.enabled` that Raven's own animations obey too | `spring()` parameters; `MotionDurationScale` per coroutine | [8](#transitions-and-island-phase-3) |
| `DockArea`'s arrangement is Raven's (only `initialDock`, plus saved layouts) | no docking in Compose | [8](#8-component-vocabulary) |
| Torn-off dock panels are Raven windows, not `Window`s | n/a | [8](#8-component-vocabulary) |
| `FileBrowser`'s directory isn't controlled | no file browser in Compose | [8](#8-component-vocabulary) |
| Navigation 3's Scene layer and `NavDisplay` come from `compose-raven-navigation3`, in compose-raven packages | `androidx.navigation3.ui` / `.scene` | [8](#8-component-vocabulary) |
| `NavDisplay` transitions are Raven appear transitions (`NavTransition`) | `transitionSpec` / `popTransitionSpec` with `ContentTransform` | [8](#8-component-vocabulary) |
| Back comes from the mouse Back button and Cmd+[ / Alt+Left, not Escape | the system back gesture | [8](#8-component-vocabulary) |
| `rememberNavBackStack` needs a `SavedStateConfiguration` declaring the `NavKey` subclasses (Kotlin/Native) | Android finds them by reflection | [8](#navigation-3-phase-3-compose-raven-navigation3) |
| `runRavenTest` covers one window, has no tree dumps or screenshots | `ComposeUiTest` with multiple roots and screenshots | [9](#9-testing) |
| App-wide theme settings (`look`, `dark`, `material`, app-wide `theme`/`fontFamily`) only at `application` level | any `MaterialTheme` can set anything | [8](#8-component-vocabulary) |
| A modifier a composable can't honour is ignored with a warning | modifiers always apply | [6](#6-layout-and-modifiers) |
| Focus traversal is Raven's own; no `moveFocus` | `FocusManager.moveFocus` | [7](#7-events-and-state) |
| `MenuBar` is a layout composable drawn in the window unless `native = true` | a window-level call, the system menu bar on macOS | [8](#8-component-vocabulary) |
| Shortcuts have no automatic Ctrl-to-Cmd; apps write `meta = true` for macOS | the same in Compose Desktop, but its menus are native | [8](#8-component-vocabulary) |
| `customHeader = true` keeps the OS's resizing and window behaviour; `undecorated = true` removes all decoration, as in Compose Desktop | only `undecorated` | [4](#4-application-host) |
| `Modifier.material(...)` gives any widget a glass or solid surface | no counterpart | [6](#6-layout-and-modifiers) |
| `BackgroundBlur` blurs what is behind it (backdrop blur) | `Modifier.blur` blurs the element itself | [8](#8-component-vocabulary) |
| The backdrop is set with the `WindowBackdrop(...)` composable | no counterpart | [4](#4-application-host) |
| External drops are a `Window(onExternalDrop)` handler for the whole window | `Modifier.dragAndDropTarget` on any element | [4](#4-application-host) |
| `LocalWindowInfo` adds `isTransparent` | `WindowInfo` has focus, modifiers and size | [4](#4-application-host) |
| `LocalWindowInfo.frameStats()` reports Raven's frame timings and counts | no counterpart | [4](#4-application-host) |
| `LocalWindowController` brings a window to the front and asks for attention | `window.toFront()` and AWT's `Taskbar` through `FrameWindowScope.window` | [4](#4-application-host) |
| `LocalAccessibilitySettings` reports reduced motion, contrast, text scale and the screen reader, following the system while the app runs | no counterpart on desktop; `LocalDensity.fontScale` for the text scale | [4](#4-application-host) |
| `RavenUiScale.value` zooms the whole interface | no counterpart; an app scales its own `LocalDensity` | [6](#6-layout-and-modifiers) |
| `WindowPlacement.PictureInPicture`; no `Fullscreen` | `Floating`, `Maximized`, `Fullscreen` | [4](#4-application-host) |
| `WindowPosition.Aligned` only centres | any alignment on the screen | [4](#4-application-host) |
| `WindowDraggableArea` needs no `WindowScope`, and only drags a `customHeader` window | an extension of `WindowScope` | [4](#4-application-host) |
| `ClipboardManager` takes and returns `String` | `AnnotatedString`; `ClipboardManager` is deprecated for the suspending `Clipboard` and `ClipEntry` | [4](#4-application-host) |
| `RavenTheme(metrics)` sets Raven's theme metrics at run time | Material has no metrics; sizes are per component | [8](#phase-2-theming) |
| `Modifier.tooltip(text)` is text only | `TooltipArea(tooltip = { ... })` takes any composable | [6](#6-layout-and-modifiers) |
| `Dialog` is a sheet over its window, and a click outside it does nothing (no `dismissOnClickOutside`) | a separate window on desktop | [4](#4-application-host) |
| No `Popup { }` | `Popup` | [4](#4-application-host) |
| `DropdownMenu` opens from `rememberDropdownMenuState().open()`, and Raven dismisses it | `DropdownMenu(expanded, onDismissRequest)`, controlled (Material) | [7](#generic-input-and-focus-phase-3) |
| Message boxes and file dialogs are suspend functions, built on `Dialog` and `FileBrowser` unless `native = true` | no built-in equivalent | [4](#4-application-host) |

## 13. Roadmap

What gets built and in what order is in [`roadmap.md`](roadmap.md):
- verification spikes
- the fork changes
- the phase 1 and phase 2 milestones
- the phase 3 samples and the decisions each one needs
- phase 4, the release: the public test module, the translator tool, publishing and Windows
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
