---
status: closed
---

# RavenDispatcher wakes for work queued behind a blocked block

While a block that `RavenDispatcher.drain()` is running blocks in nested frames (a `native = true`
dialog), the dispatcher still counts its drain task as posted. A block dispatched then is queued,
but posts no task and doesn't wake Raven. Today the first nested frame comes at once anyway,
because opening a dialog needs a layout (`rvn_window_frame_in` returns 0), and that frame's drain
empties the queue. The dispatcher shouldn't depend on that. Found by
[Verify recomposition inside nested frames](0053-verify-recomposition-in-nested-frames.md);
see the [spikes README](../../../spikes/README.md#nested-frames-caveats). Spec:
[4. Application host](../../spec.md#4-application-host).

## Deliverables

- [x] `RavenDispatcher.drain()` posts a new drain task (and so wakes Raven) when it starts a block
      with others still queued, or clears its posted flag before running each block, so a block
      that blocks never hides the work behind it. One posted task per burst stays the rule
      otherwise, so a snapshot apply that resumes the Recomposer still recomposes in the same frame.
- [x] A test in `compose-raven-ui` that needs no layout to wake: a dispatched block dispatches a
      second one, then waits in its own `Rwait_frame(-1.0)` loop until the second has run. A watchdog
      thread wakes Raven and records the miss after a second, so the test fails instead of hanging.
- [x] The `spikes/nested-frames` tests still pass, and the README caveat says it no longer applies.

## Resolution

Done on macOS. `compose-raven-ui` and the `spikes/nested-frames` tests pass, and so does
`./gradlew build`.

- **The gap was wider than this ticket said.** `drain()` cleared its posted flag only when the
  queue was already empty, so the flag stayed set while the last block ran too. A dialog launched
  by a click, the only block, hid every later dispatch, from any thread, until a nested drain ran.
- **Fix: a guard timer, not another task.** Both remedies listed above wake Raven after ordinary
  bursts: a posted task, or a cleared flag that lets the next dispatch post one, makes the loop run
  an empty iteration after nearly every frame with work. Instead each `drain()` that runs a block
  arms a one-shot Raven timer of 0 s. `Rtimer_create` wakes nothing, but `Rwait_events` won't sleep
  past a timer that is due, so a block that waits in nested frames gets a nested frame at once, and
  the timer's callback drains. A drain whose blocks all return destroys the guard unfired. A guard
  that fired is armed again for the next block of the same drain. The guards of the drains running
  now are kept innermost last, so each drain destroys only its own unfired one, and a fired id is
  forgotten before Raven can reuse its slot. Spec section 4 describes it under the dispatcher.
- **Tests** (`compose-raven-ui`, `RavenDispatcherTest`), headless with no window, so no layout can
  wake Raven:
  - a block dispatches another, then waits in `Rwait_events(-1.0)`: it returns at once. Without the
    fix it slept until the watchdog woke Raven, 1.2 s later;
  - after a drain whose blocks returned, `Rwait_events(0.2)` sleeps its full time.
  - Not covered: the destroy, for the host's drains in the frame callback. A guard left by a drain
    in a task fires in the same frame anyway, since Raven runs timers after tasks, and a hosted
    window always has a frame due right after idle, so neither shows a leftover guard.

