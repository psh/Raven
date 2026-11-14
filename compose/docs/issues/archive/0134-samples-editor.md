---
status: closed
---

# samples/editor

Port Raven's `examples/editor`: an outliner, a software-rendered scene, a profiler, properties and a log. Spec: [10. Samples](../../spec.md#10-samples).

**Depends on:** [Windows that come and go, and their owners](0112-windows-that-come-and-go-and-their-owners.md), [Generic input, focus and menus](0113-generic-input-focus-and-menus.md), [Modifier.offset and LocalDensity](0118-layout-offset-and-density.md), [Theming extras](0119-theming-extras.md), [Lists and trees](0120-lists-and-trees.md), [Scroll position](0121-scroll-position.md), [Tabs and grids](0122-tabs-and-grids.md), [Toolbar](0123-toolbar.md), [Custom drawing, viewports and effects](0124-custom-drawing-viewports-and-effects.md).

## Deliverables

- [x] `samples/editor` with the C example's look: menu bar, tool bar, the three columns over the log in splitters.
- [x] The outliner as a `TreeView`, the Scene and Profiler as `TabView` tabs, the properties as a `Grid` in a scroll container, the log as a `ListView` keeping its last line in view (`ListViewState.revealItem`).
- [x] The scene: a `Viewport` over an `ImageBitmap` rendered at `onSizeChanged`'s pixel size with coroutines on `Dispatchers.Default`, from a `withFrameNanos` loop while playing; dragging objects with `onPointerEvent`, the crosshair cursor.
- [x] The profiler as a `Canvas`; the stats label from `LocalWindowInfo.frameStats()`; View > Scale with `RavenTheme(metrics)`; `RavenInfo.version` in the log.
- [x] Runs from Gradle like the other samples, documented in `compose/README.md`.
- [x] A headless test: adding, selecting and deleting objects; a property change reaching the selected object.

## Gaps

None: its gaps were decided into phase 3 on [compose-raven phase 3 sample gaps](../0095-compose-raven-phase-3-sample-gaps.md).

## Resolution

Done on macOS. `./gradlew build allTests` passes: 525 tests, 5 of them `EditorTest`'s and 2 new in
`ListsTest`. The sample was launched and looks as the C example does, its scene moving.

- **`samples/editor`** (`settings.gradle.kts`, `launch-editor`, documented in `compose/README.md`):
  `EditorApp` holds the app-wide theme and scale around the window; an `Editor` holds the scene,
  the selection, whether it plays, the log, the rendering settings and the profiler's samples.
- **The window**: a `MenuBar` (File: New scene, Add object, Quit; View: the four themes as checkable
  entries, Scale), a `Toolbar` (Pause or Play, Add object, Delete, the stats), and splitters: the
  outliner, the `TabView` of Scene and Profiler, and the properties, over the log. F5 and Delete
  are the window's `onKeyEvent`, Ctrl+N, Ctrl+D and Ctrl+Q the menu's shortcuts.
- **The outliner** is a `TreeView` of the objects, by id; **the properties** a
  `Grid(Auto, Fill)` in a `VerticalScroll` (name, radius, speed, colour) with Visible and the
  Rendering expander, editing the selected object's snapshot state, disabled without one; **the
  log** a `ListView` that `ListViewState.revealItem` keeps at its last line, opening with
  `RavenInfo.version`.
- **The scene**: a `Viewport` over an `ImageBitmap` rendered in software at its `onSizeChanged`
  pixel size (divided by the Resolution), in bands of 8 rows with `async` on `Dispatchers.Default`
  where the C example uses `Rparallel_for`, or on the UI thread when "Render on all cores" is off.
  It renders from a `withFrameNanos` loop while it plays, and when edited while it doesn't.
  Pressing picks the object under the pointer and dragging moves it (`onPointerEvent`, positions in
  dp mapped to the pixel size with `LocalDensity`), under `PointerIcon.Crosshair`.
- **The profiler** is a `Canvas` of the last 240 times between frames, the 16.7 ms line and the
  peak; the stats label is `LocalWindowInfo.current.frameStats()` four times a second; View > Scale
  is `RavenTheme(metrics)` with the C example's font size, control height and row height.
- **Found while porting, fixed with tests**:
  - [Lists and trees](0120-lists-and-trees.md)'s `ListView` bound its items by equality, so a
    `SnapshotStateList` changed in place, equal to itself, never reached Raven: it binds a copy.
  - Its `TreeView` looked the selection's handles up as it composed, before the items new to the
    tree were added, so a selection given with its items was lost: the tree shows its selection
    again once it has matched its items.
  - [Tabs and grids](0122-tabs-and-grids.md)'s `GridScope` had no `align`: it has
    `Modifier.align(Alignment)` and `align(Alignment.Vertical)`, as `BoxScope` does.
- **Tests** (`EditorTest`, headless, the clock held as the scene moves every frame, and rendered on
  the UI thread, since rendering on other threads takes real time the test's frames don't): the
  three first objects and the version in the log; adding, selecting in the outliner with the
  keyboard, deleting, and Ctrl+D; the speed, colour, name and visibility reaching the selected
  object; F5 pausing the scene's time.
