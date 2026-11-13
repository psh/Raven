---
status: open
---

# Windows that come and go, and their owners

Finishes spec section 4's windows for phase 3: several `Window`s in one `application`, each with its own lifecycle and saved-state owners, and the window-level extras the samples read. Spec: [4. Application host](../spec.md#4-application-host).

**Depends on:** nothing in phase 3.

## Deliverables

- [x] Several `Window`s in one `application { }`, each added and removed by composition with its own composition, root and Applier under the one Recomposer (check what already arrived early with `samples/hello`).
- [x] Per-window `LifecycleOwner` (RESUMED shown and active, STARTED shown, CREATED minimised or hidden, DESTROYED on leaving) and `SavedStateRegistryOwner`, provided through their CompositionLocals.
- [x] `LocalWindowInfo.current.frameStats(): FrameStats`, a plain call over `Rwindow_get_frame_stats` ([The editor's run-time extras](archive/0102-the-editors-run-time-extras.md)).
- [x] `RavenInfo.version` (`Rget_version`) in `compose-raven-ui`.
- [x] Tests: two windows open and close independently; lifecycle states follow show, focus, minimise and removal; `frameStats()` reports a frame after one is drawn.

## Resolution

Done on macOS. 328 tests pass across the build, 6 of them new in compose-raven-ui
(`MultipleWindowsTest`, `RavenInfoTest`), and Raven's `test_widgets` gains one.

- **A Raven change** (its own commit, [fork change 9](../spec.md#11-required-raven-changes)):
  headless, every window had the keyboard from its creation and the null window system had no
  `window_focus`, so `Rwindow_focus` only showed a window and no test could make one inactive. The
  null window system now keeps its windows (not the camera-housing overlays) and implements
  `window_focus` as a window system does: the focused window takes the keyboard, and each other one
  that had it gets a focus event saying it lost it. Windows still start with the keyboard, so
  nothing changes until `Rwindow_focus` is called. No function changes, so the bindings stay as
  they are. `test_window_focus_moves_the_keyboard` in `tests/test_widgets.c` covers it.
- **Several windows**: already there since phase 1 (`samples/hello` has one, the tests had two).
  Each `Window` has its own composition, Overlay root and Applier under the one Recomposer, and
  comes and goes with the composition; this ticket adds the test that two windows open and close
  independently, the other keeping its composition and its remembered state.
- **Owners** (`WindowOwners.kt`): one object per window is its `LifecycleOwner` and
  `SavedStateRegistryOwner`, provided as `LocalLifecycleOwner` (`androidx.lifecycle.compose`) and
  `LocalSavedStateRegistryOwner` (`androidx.savedstate.compose`) to the window's content. Its
  `LifecycleRegistry` is `createUnsafe`, since the host already keeps every call on the UI thread
  and the registry's own check needs `Dispatchers.Main`. The saved-state registry is attached and
  restored with nothing, so it is ready for lifecycle-aware libraries and persists nothing.
  - The lifecycle is RESUMED while the window is shown and active, STARTED while shown but not
    active, CREATED while minimised (or hidden, which a window is only before it first shows), and
    DESTROYED once it leaves the composition. Raven reports no change of the active window, so the
    window reads `Rwindow_is_active` and `Rwindow_is_minimized` once a frame, where it already read
    its position and placement, and again after each update the app composes.
  - **On removal** the lifecycle goes to DESTROYED first, then the composition is disposed, so an
    observer the content registered sees ON_PAUSE, ON_STOP and ON_DESTROY before its
    `DisposableEffect` ends. Spec section 4's ownership rule now says so.
  - **Dependencies**: `androidx.lifecycle:lifecycle-runtime-compose:2.10.0` and
    `androidx.savedstate:savedstate-compose:1.4.0`, with `api` in compose-raven-ui, the versions
    `navigation3-runtime` 1.2.1 already brings; they need only the Compose runtime. They are in the
    version catalog, and spec section 2 lists them.
- **`LocalWindowInfo.current.frameStats(): FrameStats`** (`platform/WindowInfo.kt`), a plain call
  over `Rwindow_get_frame_stats`: `layoutMs`, `paintMs`, `renderMs`, `widgetCount`,
  `primitiveCount` (Raven's `instance_count`), `drawCallCount`, `backdropCount`, `paintJobCount`
  and `frameIndex`. All zeros before the first frame; Raven's `frame_index` counts from 0, so the
  first frame drawn has index 0, and the test checks the widget count instead.
- **`RavenInfo.version`** (`RavenInfo.kt`, in `raven.compose.ui`) is `Rget_version`, readable
  before Raven starts.
- **Deviations**: `Window` has no `visible` parameter, as the spec's signature has none, so
  "hidden" only covers the time before the window first shows. Spec section 4 says so.
- **Tests**:
  - two windows open and close independently: removing one destroys only its Raven window, the
    other keeps its content and its composition, and one reopened is a new window composed anew;
  - the lifecycle follows show (RESUMED for both, each window its own owner), focus
    (`Rwindow_focus` makes the other STARTED and back), minimise (by the state and by the user,
    CREATED, then RESUMED once restored) and removal (DESTROYED, the other unaffected);
  - an observer in the content sees ON_CREATE, ON_START and ON_RESUME, then ON_PAUSE, ON_STOP and
    ON_DESTROY before its effect is disposed;
  - each window has its own saved-state registry, restored with nothing, that takes providers;
  - `frameStats()` reports nothing before the first frame and the widgets laid out after it;
  - `RavenInfo.version` is a version.
