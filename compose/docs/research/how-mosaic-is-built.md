# How Mosaic is built

Research for [0003](../issues/0003-how-mosaic-is-built.md): how Mosaic wires the Compose runtime
to its own tree, and which parts compose-raven should copy.

Sources, read in full at these commits:

- Mosaic `JakeWharton/mosaic` @ [`00897990`](https://github.com/JakeWharton/mosaic/tree/00897990ab256beac140eb1713e05afac05b8289)
  (2026-10-27, `0.19.0-SNAPSHOT`, after the `0.18.0` release). Uses `androidx.compose.runtime:runtime:1.12.1`
  and `androidx.lifecycle:lifecycle-runtime-compose:2.11.0` (`gradle/libs.versions.toml`).
- Redwood `cashapp/redwood` @ [`5c49a0bc`](https://github.com/cashapp/redwood/tree/5c49a0bcc224b7fef10316bfdeb227639bcc42ec)
  (2026-01-09).

Short links used below:

- `M/mosaic.kt` = [mosaic-runtime/.../mosaic/mosaic.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/mosaic.kt)
- `M/ui/Node.kt` = [mosaic-runtime/.../mosaic/ui/Node.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/ui/Node.kt)
- `M/layout/Node.kt` = [mosaic-runtime/.../mosaic/layout/Node.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/layout/Node.kt)
- `M/rendering.kt` = [mosaic-runtime/.../mosaic/rendering.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/rendering.kt)
- `M/ui/MosaicComposable.kt` = [mosaic-runtime/.../mosaic/ui/MosaicComposable.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/ui/MosaicComposable.kt)
- `M/KeyModifier.kt` = [mosaic-runtime/.../mosaic/layout/KeyModifier.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/layout/KeyModifier.kt)
- `M/StaticEffect.kt` = [mosaic-runtime/.../mosaic/StaticEffect.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/StaticEffect.kt)
- `M/TtyTerminal.kt` = [mosaic-tty-terminal/.../TtyTerminal.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-tty-terminal/src/commonMain/kotlin/com/jakewharton/mosaic/tty/terminal/TtyTerminal.kt)
- `M/TestMosaic.kt` = [mosaic-testing/.../TestMosaic.kt](https://github.com/JakeWharton/mosaic/blob/00897990ab256beac140eb1713e05afac05b8289/mosaic-testing/src/commonMain/kotlin/com/jakewharton/mosaic/testing/TestMosaic.kt)
- `R/WidgetApplier.kt` = [redwood-compose/.../WidgetApplier.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-compose/src/commonMain/kotlin/app/cash/redwood/compose/WidgetApplier.kt)
- `R/RedwoodComposition.kt` = [redwood-compose/.../RedwoodComposition.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-compose/src/commonMain/kotlin/app/cash/redwood/compose/RedwoodComposition.kt)
- `R/Widget.kt` = [redwood-widget/.../Widget.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-widget/src/commonMain/kotlin/app/cash/redwood/widget/Widget.kt)

## 1. Applier and node types

- **One node type.** Every Mosaic composable emits `MosaicNode` (`M/layout/Node.kt`). A node holds
  a `measurePolicy`, a `debugPolicy`, `children: ArrayList<MosaicNode>`, `parentData`, `testTag`,
  and a chain of *layers* built from its `Modifier`. There are no per-widget subclasses: `Row`,
  `Column`, `Box`, `Text` differ only in their `MeasurePolicy` and draw modifiers.
- **Emission** is plain `ComposeNode<MosaicNode, Applier<Any>>(factory = NodeFactory, update = {
  set(measurePolicy, SetMeasurePolicy); set(modifier, SetModifier); set(debugPolicy, SetDebugPolicy) })`
  (`M/ui/Node.kt`). The setters are top-level `@JvmField` lambdas, so no lambda is allocated per call.
  The factory creates a node with throwing placeholder policies; the `update` block fills them in.
- **`@MosaicComposable`** is a `@ComposableTargetMarker`, so the Compose compiler reports misuse of
  Mosaic composables in another applier's tree (`M/ui/MosaicComposable.kt`).
- **`MosaicNodeApplier : AbstractApplier<MosaicNode>`** (`M/mosaic.kt`) is about 25 lines:
  - `insertTopDown` is ignored; `insertBottomUp` does `current.children.add(index, instance)`.
  - `remove`/`move` delegate to the `MutableList` helpers `AbstractApplier` provides.
  - `onClear` is empty.
  - `onBeginChanges()` calls an `onChanges` callback that sets `needLayout = true`. The comment says
    it is done at *begin* rather than end on purpose, so nothing relies on it as an end-of-changes
    signal. Redwood has the same line and comment (`R/WidgetApplier.kt`).
- **Root**: the applier creates the root itself, a `MosaicNode` with `BoxMeasurePolicy`.
- **Modifiers** are folded into layers in `MosaicNode.setModifier`: each `LayoutModifier`,
  `DrawModifier` and `KeyModifier` element wraps the next layer (`LayoutLayer`, `DrawLayer`,
  `KeyLayer`); `ParentDataModifier` and `TestTagModifier` are stored on the node. Measure, draw and
  key dispatch all start at `topLayer` and walk down to `BottomLayer`, which runs the
  `MeasurePolicy` over the children and draws/dispatches to them.

## 2. Recomposer, frame clock and the main loop

All of this is in `MosaicComposition` (`M/mosaic.kt`).

- **Two clocks.**
  - An *external* `MonotonicFrameClock` must be in the caller's coroutine context (it is
    `checkNotNull`ed). `runMosaicComposition` makes it a `BroadcastFrameClock` and ticks it from a
    coroutine: `while (true) { clock.sendFrame(nanoTime()); delay(1) }`, with the comment "1000 FPS
    should be enough for anybody". The `delay` is there so other coroutines on the same dispatcher
    get to run.
  - An *internal* `BroadcastFrameClock` goes into `composeContext = coroutineContext + job +
    internalClock`. The `Recomposer` and every `LaunchedEffect`/`withFrameNanos` in user code see
    only this one.
- **Recomposer**: `Recomposer(composeContext)`, `Composition(applier, recomposer)`, and
  `recomposer.runRecomposeAndApplyChanges()` launched with `start = UNDISPATCHED`, so it reaches its
  first suspension before the constructor returns. The dispatcher is whatever the caller runs on;
  `runMosaicBlocking` uses `runBlocking`, so everything runs on one thread in its event loop.
- **The frame listener** is the heart of it (`startFrameListener`). In a loop:
  `externalClock.withFrameNanos { nanos -> ... }`, which, in order:
  1. drains every pending terminal event with `tryReceive()` and dispatches key events into the
     tree (section 4);
  2. `internalClock.sendFrame(nanos)`: this releases the Recomposer's frame wait and user frame
     callbacks (animations);
  3. `if (needLayout) performLayout() else if (needDraw) performDraw()`.

  So the external clock is the "vsync", and the internal clock lets Mosaic put input before
  recomposition and layout/draw after it in each frame.
- **`setContent`** wraps the content in `CompositionLocalProvider` (terminal state, static logger,
  `LocalLifecycleOwner`) and calls `performLayout()` at once, so the first frame is synchronous.
- **Snapshot apply notifications**: `GlobalSnapshotManager` registers a global write observer that
  posts into a `Channel(1)`, with an atomic flag so only one is in flight. A consumer coroutine calls
  `Snapshot.sendApplyNotifications()`. Without this, writes to state outside a snapshot (from
  callbacks, for example) would never reach the Recomposer. Redwood does the same with a
  `snapshotJob` field instead of a channel (`R/RedwoodComposition.kt`).
- **Shutdown**: `awaitComplete()` joins the effect jobs, `recomposer.awaitIdle()`, flushes one last
  frame if layout/draw is pending, then `recomposer.close(); recomposer.join()`. `cancel()` disposes
  the apply observer and cancels the recomposer and job. A Mosaic program ends when its composition
  has no more work.
- **Lifecycle**: `MosaicComposition` is a `LifecycleOwner` (`LifecycleRegistry.createUnsafe`), and
  terminal focus maps to `ON_RESUME`/`ON_PAUSE`.

## 3. When it recomposes, lays out and draws

Mosaic decides by itself whether a frame needs layout, only a redraw, or nothing:

- **Recomposition** is the Recomposer's own business: it is invalidated by snapshot apply
  notifications and runs when the internal clock ticks.
- **Layout** is needed when the applier changed the tree (`onBeginChanges` sets `needLayout`), or
  when a state that was *read during the last measure/place* changed. `performLayout` runs
  `rootNode.measureAndPlace()` inside `Snapshot.observe(readObserver = readStatesOnLayout::add)`.
- **Draw only** is needed when a state read during the last draw changed (`draw()` observes into
  `readStatesOnDraw`), or a static log was emitted.
- A `Snapshot.registerApplyObserver` checks the changed states against those two sets and sets
  `needLayout`/`needDraw` (both `@Volatile`).
- **Output**: `onDraw` calls `Rendering.render(mosaic)`, which draws the root into a `TextCanvas`
  and turns it into ANSI text, which is printed (`M/rendering.kt`). Layout always draws after.
- **`StaticEffect`** (`M/StaticEffect.kt`): a one-shot sub-composition
  (`Composition(MosaicNodeApplier(), rememberCompositionContext())`) inside a `SideEffect`. It is
  measured and drawn once, its output is printed above the live area, and it is disposed.

## 4. Input

- **Source**: `Tty.asTerminalIn` (`M/TtyTerminal.kt`) puts the TTY in raw mode and launches a parser
  loop on `Dispatchers.IO` that pushes events into `Channel<Event>(64, DROP_OLDEST)`. Size, focus
  and theme go into `MutableStateFlow`s instead.
- **Terminal state becomes Compose state**: `MosaicComposition` collects those flows (launched
  `Unconfined`, `UNDISPATCHED`) into one `mutableStateOf(TerminalState(...))`, provided as
  `LocalTerminalState`.
- **Key events are pulled, not pushed**: the frame listener drains the channel at the start of each
  frame on the composition thread, so handlers run on the UI thread and their state writes are seen
  by that frame's recomposition.
- **Dispatch** goes through the modifier layers (`M/layout/Node.kt`, `M/KeyModifier.kt`):
  `KeyLayer.sendKeyEvent = onPreKeyEvent || next.sendKeyEvent || onKeyEvent`, the same order as
  Compose UI's preview-down / event-up. `BottomLayer` offers the event to its children in order and
  stops at the first one that handles it. There is **no focus system and no hit-testing**: every
  node with `Modifier.onKeyEvent` can see every key.
- An unhandled Ctrl-C cancels the job, which ends the program.
- Mouse events exist in the terminal layer but the runtime ignores everything that is not a
  `KeyboardEvent`.

## 5. Testing

`Mosaic(coroutineContext, onDraw, terminal)` is public and takes the external clock from the
context, so tests own the clock. `RealTestMosaic` (`M/TestMosaic.kt`) runs on the test's coroutine
context with its own `BroadcastFrameClock`, `sendFrame`s at 60 Hz until `onDraw` fires, sends key
events through `TestTerminal.events`, and snapshots with `mosaic.draw()` or `dumpNodes()`. No real
terminal is needed.

## 6. Contrast: Redwood (native widgets)

Redwood drives real platform views (Android `View`, `UIView`, DOM), so it is closer to us.

- **Two node kinds** (`R/WidgetApplier.kt`): `WidgetNode` wraps a `Widget<W>` (the native view is
  `widget.value`), and `ChildrenNode` represents one *child slot* of a widget
  (`Widget.Children<W>`: `insert/move/remove/onModifierUpdated/detach`, `R/Widget.kt`). A widget can
  have several named slots.
- **`NodeApplier`** uses both directions:
  - `insertTopDown` attaches a `ChildrenNode` to its parent widget's slot;
  - `insertBottomUp` inserts a `WidgetNode` into the current `ChildrenNode` "so that the initial
    properties of the widget have been set before it is attached".
- **Batching for native layout**: tree and modifier changes `recordChanged(widget)`. In
  `onEndChanges`, every changed widget that implements `ChangeListener` gets `onEndChanges()` once,
  so a native container relayouts once per batch instead of once per child.
- **Properties and events** are generated from a schema: the widget interface has setters such as
  `onClick(onClick: (() -> Unit)?)`, set through the `Updater` like any property. Events are just
  lambda properties that the native widget calls. `RedwoodComposeNode` is a copy of `ComposeNode`
  that passes the applier's widget factory into the node factory.
- **Clock**: `RedwoodComposition(scope, ...)` only requires "a `MonotonicFrameClock` key which is
  being ticked" in `scope`; the host platform provides it (Choreographer, CADisplayLink, rAF).
  Redwood does no layout or drawing, and never decides when to render: the platform does.

## 7. What compose-raven should copy, and what does not apply

Raven owns the widget tree, layout (measure/arrange), painting, hit-testing and focus, and runs its
own frame loop (`Rwait_frame`/`Rdraw_frame`, tasks and timers then the frame callback, then layout
and paint only if something is dirty). All of it is on the UI thread (`docs/ARCHITECTURE.md`,
"Threads" and "A frame").

### Copy

- **`AbstractApplier<RavenNode>` with bottom-up insertion**: a few lines that map
  `insert/remove/move` to Raven's child calls. Bottom-up means a widget's properties are set before
  it is attached (Redwood's reason). Raven only lays out at `Rdraw_frame` anyway, so the order is
  about correctness, not speed.
- **`ComposeNode` + `Updater.set(value, Setter)`** with static setter lambdas, one per Raven
  property setter. Add a `@RavenComposable` `ComposableTargetMarker`.
- **The Recomposer wiring as is**: `Recomposer(ctx)`, `Composition(applier, recomposer)`,
  `runRecomposeAndApplyChanges()` launched `UNDISPATCHED`, the `GlobalSnapshotManager` write
  observer → `sendApplyNotifications()`, and Mosaic's `awaitComplete`/`cancel` shutdown order.
- **A `BroadcastFrameClock` for the Recomposer that the host loop ticks**, plus a public factory
  that takes the clock from the coroutine context (like `Mosaic(coroutineContext, ...)`), so tests
  can drive frames by hand without a window (`M/TestMosaic.kt`).
- **Fixed per-frame order: input, recompose, then render.** Mosaic gets it by draining input before
  `sendFrame`. In Raven, input already arrives as callbacks on the UI thread before the frame
  callback, so: Raven callbacks write state; the frame callback ticks the clock; recomposition
  applies changes to widgets; Raven then lays out and paints in the same `Rdraw_frame`.
- **Host state as `CompositionLocal`s and a `LifecycleOwner`** (window size/scale/theme/focus →
  `mutableStateOf`), as `LocalTerminalState` and the focus → `ON_RESUME/ON_PAUSE` mapping do.
- **From Redwood**: events as nullable lambda properties (set via `Updater`, registered as Raven
  callbacks), and `ChildrenNode`-style slots if a Raven widget has more than one child list.

### Do not copy: these exist because Mosaic owns rendering

- `MosaicNode`'s measure/place/draw, the modifier-to-layer folding, `MeasurePolicy`, intrinsics,
  `TextCanvas`. Raven measures, arranges and paints, so a node is just a handle plus its slot
  bookkeeping, and modifiers map to widget properties.
- `readStatesOnLayout`/`readStatesOnDraw` snapshot observation and the `needLayout`/`needDraw`
  flags. Raven's dirty flags already do this: setting a property marks the widget and its ancestors
  dirty.
- The 1 ms `delay` busy loop. Raven sleeps in `Rwait_frame` until something happens. Tick the clock
  from the frame callback, and wake Raven (`Rwake_up` or `Rtask_post`) when the Recomposer has work
  or `withFrameNanos` has awaiters. `BroadcastFrameClock(onNewAwaiters)` is the hook for the second
  case. Mosaic does not need it because it polls.
- Tree-walk key dispatch with no focus and no hit-testing, Ctrl-C handling, and the TTY reader on
  `Dispatchers.IO`. Raven does hit-testing, focus and dispatch, and calls back on the UI thread.
- `StaticEffect`/static logs and ANSI rendering.
- `runBlocking` as the dispatcher. Mosaic can use it because it owns the loop. Since Raven owns the
  loop, compose-raven needs a `CoroutineDispatcher` that runs on the Raven UI thread (for example,
  dispatch via `Rtask_post` and run during the frame), so that the Recomposer and effects never
  touch widgets from another thread.
