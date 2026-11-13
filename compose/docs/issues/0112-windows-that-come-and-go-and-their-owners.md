---
status: open
---

# Windows that come and go, and their owners

Finishes spec section 4's windows for phase 3: several `Window`s in one `application`, each with its own lifecycle and saved-state owners, and the window-level extras the samples read. Spec: [4. Application host](../spec.md#4-application-host).

**Depends on:** nothing in phase 3.

## Deliverables

- [ ] Several `Window`s in one `application { }`, each added and removed by composition with its own composition, root and Applier under the one Recomposer (check what already arrived early with `samples/hello`).
- [ ] Per-window `LifecycleOwner` (RESUMED shown and active, STARTED shown, CREATED minimised or hidden, DESTROYED on leaving) and `SavedStateRegistryOwner`, provided through their CompositionLocals.
- [ ] `LocalWindowInfo.current.frameStats(): FrameStats`, a plain call over `Rwindow_get_frame_stats` ([The editor's run-time extras](archive/0102-the-editors-run-time-extras.md)).
- [ ] `RavenInfo.version` (`Rget_version`) in `compose-raven-ui`.
- [ ] Tests: two windows open and close independently; lifecycle states follow show, focus, minimise and removal; `frameStats()` reports a frame after one is drawn.
