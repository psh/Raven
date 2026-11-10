---
status: open
---

# RavenHost and singleWindowApplication

Write the internal `RavenHost` (`start(content)`, `frame()`, `close()`) and
`singleWindowApplication` on top of it, plus the core of the headless test harness, which shares
the same host. Spec: [4. Application host](../spec.md#4-application-host),
[9. Testing](../spec.md#9-testing).

**Depends on:** [RavenNode and RavenApplier](0043-ravennode-and-ravenapplier.md).

Phase 1 has one window. Keep the window's composition, root and Applier separate from the
application composition, so phase 3's `application { Window { } }` adds windows without
reshaping the host.

## Deliverables

- [ ] `RavenDispatcher : CoroutineDispatcher` over `Rtask_post` with a `StableRef` per block.
      `isDispatchNeeded` is always `true`, and after shutdown it drops dispatches.
- [ ] A `BroadcastFrameClock` whose `onNewAwaiters` calls `Rwake_up()`, ticked from the single
      `Rset_frame_callback` slot with `sendFrame((Rget_time() * 1e9).toLong())`.
- [ ] A Recomposer running `runRecomposeAndApplyChanges()` in a scope of the dispatcher plus the
      clock.
- [ ] A snapshot apply pump: a global write observer sets a flag and dispatches one
      `Snapshot.sendApplyNotifications()`, so a write from any thread wakes the loop.
- [ ] An apply generation counter, advanced each time a frame's changes are applied. Controlled
      inputs need it later.
- [ ] `singleWindowApplication(title, width, height) { }`: blocks on the main thread, which becomes
      the UI thread. `Rinit`, a window with an Overlay root set once with `Rwindow_set_content`, a
      close callback that refuses and calls `onCloseRequest` (`exitApplication`), then
      `while (Rwait_frame(-1.0))`.
- [ ] The shutdown order: dispose the window's composition, then destroy the window, then dispose
      the application composition, cancel the Recomposer and its scope, unregister the write
      observer, clear the host's caches and advance the host epoch, then `Rshutdown()`.
- [ ] The host epoch counter and a per-host cache holder. Phase 1 caches nothing yet, but the
      invariant must already be in place.
- [ ] Errors: a `CoroutineExceptionHandler` records the first throwable and calls `Rquit()`, and
      `singleWindowApplication` rethrows it after a normal shutdown.
- [ ] Harness core, internal, in foundation's test source set: `runRavenTest { setContent { } }`
      with `Rinit_ex(R_BACKEND_NULL, headless, worker_threads = -1)` and a fixed
      `Rset_animation_time_step`. `waitForIdle()` draws frames until no invalidation, snapshot
      apply or controlled check is pending, and frame awaiters don't count. After every test,
      check for zero blocks held and zero live `StableRef`s. Raven starts fresh for every test.
- [ ] Tests: a state write recomposes on the next frame, a write from a background thread wakes the
      loop, an exception in composition shuts down cleanly and rethrows, and a `LaunchedEffect` with
      `withFrameNanos` doesn't stall `waitForIdle()`.
