---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0002, 0003, 0004]
assignee: Paul Hawke
---

# Frame loop and threading

## Question

How does Raven's event loop (`Rwait_frame` on the UI thread) drive Compose? Decide what supplies the `MonotonicFrameClock`, where the `Recomposer` runs, which coroutine dispatcher runs on Raven's UI thread, and how a state change made on another thread wakes the loop (`Rtask_post`?).

Note from "Raven widget tree API for an Applier" (0004): `Rset_frame_callback` is a single global slot, so decide whether compose-raven claims it outright or chains to an existing callback.

## Resolution

- **Loop**: compose-raven owns it. `ravenApplication(title, width, height) { content }` in `raven.compose.ui` blocks and is called from `main()` on the main thread, which becomes the UI thread (AppKit requires it). It calls `Rinit`, creates the window and the fixed Overlay root ([Node model and Applier](0007-node-model-and-applier.md)), starts the composition, loops `while (Rwait_frame(-1.0))`, shuts down, and returns. It is deliberately one window; growing it into `application { Window { } }` belongs to the multi-window fog.
- **Dispatcher**: an internal `RavenDispatcher : CoroutineDispatcher`. `dispatch` calls `Rtask_post` with a `StableRef` to the block, and `isDispatchNeeded` is always true, so Applier work never runs re-entrantly inside a Raven callback. It is not public and not `Dispatchers.Main`. After shutdown it drops dispatches instead of calling Raven.
- **Frame clock**: a `BroadcastFrameClock` whose `onNewAwaiters` calls `Rwake_up()`. compose-raven claims the single `Rset_frame_callback` slot outright (no app can reach Raven) and calls `sendFrame((Rget_time() * 1e9).toLong())` from it. The callback runs after tasks and before layout, so a recomposition is laid out and painted in the same frame.
- **Recomposer**: `runRecomposeAndApplyChanges()` in a scope of `RavenDispatcher` plus the clock.
- **Cross-thread state**: `Snapshot.registerGlobalWriteObserver` sets a flag and, when not already set, dispatches one `Snapshot.sendApplyNotifications()` through `RavenDispatcher`. `Rtask_post` is thread-safe and wakes `Rwait_frame`, so a write from any thread wakes the loop.
- **Shutdown** (when `Rwait_frame` returns false): `composition.dispose()` (`onRelease` destroys widgets, and destroy callbacks dispose their `StableRef`s), cancel the Recomposer and its scope, unregister the write observer, then `Rshutdown()`. Its last drain of the task queue disposes any `StableRef` still pending in a dispatch.
- **Errors**: a `CoroutineExceptionHandler` on the scope records the first throwable and calls `Rquit()`. After the normal shutdown, `ravenApplication` rethrows it.
- **Exit from code**: the content lambda's receiver is `RavenApplicationScope`, with `exitApplication()` calling `Rquit()` (thread-safe). Closing the window also ends the loop.
