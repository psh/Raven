---
status: closed
wayfinder: research
parent: 0001
blocked_by: []
assignee: claude
---

# How Mosaic is built

## Question

How does Mosaic (JakeWharton/mosaic) wire the Compose runtime to its own tree? Cover its Applier and node types, how it creates and drives the `Recomposer` and frame clock, how it schedules recomposition and rendering, how it handles input, and which of these choices compose-raven should copy and which come from Mosaic owning its own renderer (and so don't apply to us).

## Resolution

- Mosaic uses a single node type, `MosaicNode`, emitted with `ComposeNode` + `Updater.set` and static setter lambdas. `MosaicNodeApplier : AbstractApplier` inserts bottom-up only, and its `onBeginChanges` sets a `needLayout` flag. `@MosaicComposable` is a `ComposableTargetMarker`.
- It uses two `BroadcastFrameClock`s. The caller's external clock is ticked every 1 ms. Each frame: drain key events, tick the internal clock that the Recomposer and effects use, then lay out or draw if flagged. The Recomposer runs `runRecomposeAndApplyChanges()` launched UNDISPATCHED, and a global write observer calls `Snapshot.sendApplyNotifications()`.
- It works out layout-vs-draw itself by recording which states were read during measure and during draw. Input comes from a TTY reader on `Dispatchers.IO` into a channel, is pulled at the start of each frame, and goes through modifier key-event layers. There is no focus and no hit-testing.
- **Copy for compose-raven:** a bottom-up `AbstractApplier` over Raven handles, `ComposeNode`/`Updater` setters, a `@RavenComposable` marker, the Recomposer/snapshot-manager wiring and shutdown order, a clock ticked by the host and injected through the coroutine context (tests drive frames by hand), the input → recompose → render order per frame, and host state as CompositionLocals.
- **Skip, because Mosaic owns rendering:** measure/place/draw, modifier layers, read-observation dirty flags, the 1 ms busy loop, tree-walk key dispatch, `runBlocking`. Raven needs instead a UI-thread `CoroutineDispatcher` (via `Rtask_post`) and to tick the clock from the frame callback, waking with `Rwake_up` when there is pending work or `withFrameNanos` awaiters.
- **From Redwood:** events as nullable lambda properties, `ChildrenNode`-style child slots, and one `onEndChanges` batch per widget.

Findings: branch `research/how-mosaic-is-built`, file `compose/docs/research/how-mosaic-is-built.md`
