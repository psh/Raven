---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0002, 0003, 0004]
assignee: null
---

# Frame loop and threading

## Question

How does Raven's event loop (`Rwait_frame` on the UI thread) drive Compose? Decide what supplies the `MonotonicFrameClock`, where the `Recomposer` runs, which coroutine dispatcher runs on Raven's UI thread, and how a state change made on another thread wakes the loop (`Rtask_post`?).

Note from "Raven widget tree API for an Applier" (0004): `Rset_frame_callback` is a single global slot, so decide whether compose-raven claims it outright or chains to an existing callback.
