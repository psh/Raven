---
status: open
---

# samples/editor

Port Raven's `examples/editor`: an outliner, a software-rendered scene, a profiler, properties and a log. Spec: [10. Samples](../spec.md#10-samples).

**Depends on:** [Windows that come and go, and their owners](archive/0112-windows-that-come-and-go-and-their-owners.md), [Generic input, focus and menus](archive/0113-generic-input-focus-and-menus.md), [Modifier.offset and LocalDensity](archive/0118-layout-offset-and-density.md), [Theming extras](archive/0119-theming-extras.md), [Lists and trees](0120-lists-and-trees.md), [Scroll position](0121-scroll-position.md), [Tabs and grids](0122-tabs-and-grids.md), [Toolbar](archive/0123-toolbar.md), [Custom drawing, viewports and effects](archive/0124-custom-drawing-viewports-and-effects.md).

## Deliverables

- [ ] `samples/editor` with the C example's look: menu bar, tool bar, the three columns over the log in splitters.
- [ ] The outliner as a `TreeView`, the Scene and Profiler as `TabView` tabs, the properties as a `Grid` in a scroll container, the log as a `ListView` keeping its last line in view (`ListViewState.revealItem`).
- [ ] The scene: a `Viewport` over an `ImageBitmap` rendered at `onSizeChanged`'s pixel size with coroutines on `Dispatchers.Default`, from a `withFrameNanos` loop while playing; dragging objects with `onPointerEvent`, the crosshair cursor.
- [ ] The profiler as a `Canvas`; the stats label from `LocalWindowInfo.frameStats()`; View > Scale with `RavenTheme(metrics)`; `RavenInfo.version` in the log.
- [ ] Runs from Gradle like the other samples, documented in `compose/README.md`.
- [ ] A headless test: adding, selecting and deleting objects; a property change reaching the selected object.

## Gaps

None: its gaps were decided into phase 3 on [compose-raven phase 3 sample gaps](0095-compose-raven-phase-3-sample-gaps.md).
