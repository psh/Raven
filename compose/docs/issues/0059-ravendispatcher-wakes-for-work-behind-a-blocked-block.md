---
status: open
---

# RavenDispatcher wakes for work queued behind a blocked block

While a block that `RavenDispatcher.drain()` is running blocks in nested frames (a `native = true`
dialog), the dispatcher still counts its drain task as posted. A block dispatched then is queued,
but posts no task and doesn't wake Raven. Today the first nested frame comes at once anyway,
because opening a dialog needs a layout (`rvn_window_frame_in` returns 0), and that frame's drain
empties the queue. The dispatcher shouldn't depend on that. Found by
[Verify recomposition inside nested frames](archive/0053-verify-recomposition-in-nested-frames.md);
see the [spikes README](../../spikes/README.md#nested-frames-caveats). Spec:
[4. Application host](../spec.md#4-application-host).

## Deliverables

- [ ] `RavenDispatcher.drain()` posts a new drain task (and so wakes Raven) when it starts a block
      with others still queued, or clears its posted flag before running each block, so a block
      that blocks never hides the work behind it. One posted task per burst stays the rule
      otherwise, so a snapshot apply that resumes the Recomposer still recomposes in the same frame.
- [ ] A test in `compose-raven-ui` that needs no layout to wake: a dispatched block dispatches a
      second one, then waits in its own `Rwait_frame(-1.0)` loop until the second has run. A watchdog
      thread wakes Raven and records the miss after a second, so the test fails instead of hanging.
- [ ] The `spikes/nested-frames` tests still pass, and the README caveat says it no longer applies.
