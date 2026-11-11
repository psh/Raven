---
status: closed
---

# RavenHost and singleWindowApplication

Write the internal `RavenHost` (`start(content)`, `frame()`, `close()`) and
`singleWindowApplication` on top of it, plus the core of the headless test harness, which shares
the same host. Spec: [4. Application host](../../spec.md#4-application-host),
[9. Testing](../../spec.md#9-testing).

**Depends on:** [RavenNode and RavenApplier](0043-ravennode-and-ravenapplier.md).

Phase 1 has one window. Keep the window's composition, root and Applier separate from the
application composition, so phase 3's `application { Window { } }` adds windows without
reshaping the host.

## Deliverables

- [x] `RavenDispatcher : CoroutineDispatcher` over `Rtask_post` with a `StableRef` per block.
      `isDispatchNeeded` is always `true`, and after shutdown it drops dispatches.
- [x] A `BroadcastFrameClock` whose `onNewAwaiters` calls `Rwake_up()`, ticked from the single
      `Rset_frame_callback` slot with `sendFrame((Rget_time() * 1e9).toLong())`.
- [x] A Recomposer running `runRecomposeAndApplyChanges()` in a scope of the dispatcher plus the
      clock.
- [x] A snapshot apply pump: a global write observer sets a flag and dispatches one
      `Snapshot.sendApplyNotifications()`, so a write from any thread wakes the loop.
- [x] An apply generation counter, advanced each time a frame's changes are applied. Controlled
      inputs need it later.
- [x] `singleWindowApplication(title, width, height) { }`: blocks on the main thread, which becomes
      the UI thread. `Rinit`, a window with an Overlay root set once with `Rwindow_set_content`, a
      close callback that refuses and calls `onCloseRequest` (`exitApplication`), then
      `while (Rwait_frame(-1.0))`.
- [x] The shutdown order: dispose the window's composition, then destroy the window, then dispose
      the application composition, cancel the Recomposer and its scope, unregister the write
      observer, clear the host's caches and advance the host epoch, then `Rshutdown()`.
- [x] The host epoch counter and a per-host cache holder. Phase 1 caches nothing yet, but the
      invariant must already be in place.
- [x] Errors: a `CoroutineExceptionHandler` records the first throwable and calls `Rquit()`, and
      `singleWindowApplication` rethrows it after a normal shutdown.
- [x] Harness core, internal, in foundation's test source set: `runRavenTest { setContent { } }`
      with `Rinit_ex(R_BACKEND_NULL, headless, worker_threads = -1)` and a fixed
      `Rset_animation_time_step`. `waitForIdle()` draws frames until no invalidation, snapshot
      apply or controlled check is pending, and frame awaiters don't count. After every test,
      check for zero blocks held and zero live `StableRef`s. Raven starts fresh for every test.
- [x] Tests: a state write recomposes on the next frame, a write from a background thread wakes the
      loop, an exception in composition shuts down cleanly and rethrows, and a `LaunchedEffect` with
      `withFrameNanos` doesn't stall `waitForIdle()`.

## Resolution

Done on macOS. `compose-raven-ui` has `RavenHost`, `RavenDispatcher`, the phase 1 `Window` and
`singleWindowApplication`. The harness core is `runRavenTest` in foundation's tests. 21 tests pass
in `compose-raven-ui`, and 3 in foundation.

- **The dispatcher keeps a queue of its own** instead of one `Rtask_post` per block. Raven runs
  only the batch of tasks it took when the frame began. With one task per block, a state write's
  apply would resume the Recomposer into the next batch, and the write would show a frame late.
  Now one posted task drains the queue until it is empty. The frame callback also drains before
  and after `sendFrame`. The drain after lets `withFrameNanos` loops take their step within the
  frame, so they leave nothing queued and `waitForIdle()` can finish. Spec section 4 is updated.
- **`isIdle()`**: no queued dispatch, no pending apply, and no `Recomposer.hasPendingWork` with the
  composition frame clock paused for the check. Pausing is how frame awaiters are left out.
- **Opt-in API**: `@InternalComposeRavenApi` (`@RequiresOptIn`, level ERROR) arrives now rather
  than in phase 3, because foundation's harness needs `RavenHost` and `Window` from
  `compose-raven-ui`.
- **Shutdown**: after the Recomposer and scope are cancelled, the dispatcher is drained, so the
  Recomposer's coroutine runs its `finally` and unregisters its snapshot observer. Without that,
  every test would leave an apply observer behind.
- **Epoch**: `HostEpoch` advances when a host closes, and `HostEpoch.checkCurrent(epoch)` throws
  for a handle from an earlier Raven. `HostCaches` is a keyed map cleared at close.
- **Last window**: Raven ends the loop by itself when its last window is destroyed, so removing
  the last `Window` ends `application` with no `Rquit` needed.
- **Tests**, in compose-raven-ui:
  - a window's content goes under its Overlay root;
  - removing a window disposes its composition while the window is still valid, and the test
    fails if `dispose()` destroys the window first;
  - `Rwindow_close` calls `onCloseRequest` and keeps the window;
  - `exitApplication` ends the loop;
  - an exception in the first composition, in a recomposition, in an effect, or in
    `onCloseRequest` shuts down cleanly and rethrows;
  - the apply generation advances;
  - closing clears the caches and ends the epoch.

  In foundation, through the harness:
  - a write recomposes in exactly one frame;
  - a write from a `Worker` wakes `waitFrame()`;
  - an endless `withFrameNanos` loop doesn't stall `waitForIdle()`.

  Every test also checks for no Raven errors, zero blocks held and zero live widget references.
- `singleWindowApplication` itself still needs a display, so it is checked by the sample in
  [samples/hello](../0051-samples-hello.md).
