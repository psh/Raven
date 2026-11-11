---
status: closed
---

# Verify recomposition inside nested frames

A verify-first fact that waits for the host: can Compose recompose safely inside the nested frames
that Raven's blocking dialogs run (`Rmessage_box`, `Rfile_dialog_*`)? Phase 3's `native = true`
dialogs depend on the answer. Spec: [4. Application host](../../spec.md#4-application-host); roadmap:
[0. Verify first](../../roadmap.md#0-verify-first).

**Depends on:** [RavenHost and singleWindowApplication](0044-ravenhost-and-singlewindowapplication.md).

## Deliverables

- [x] A spike under `compose/spikes/` that opens a blocking dialog from a task on `RavenDispatcher`
      while state changes keep arriving, and checks that recomposition, applies and controlled
      checks stay correct and nothing re-enters the Applier.
- [x] The result in the roadmap's verify-first table and the spikes README. If it fails, record that
      `native = true` dialogs are dropped from phase 3.

## Resolution

**Passes**, on macOS headless. `native = true` dialogs stay in phase 3. Results are in the
[spikes README](../../../spikes/README.md#results) and the roadmap's verify-first table, and spec
section 4 now says what `native = true` shows and how calls that wait together return.

- **Spike**: `compose/spikes/nested-frames/`, a Gradle subproject of headless tests on foundation's
  harness (`./gradlew :spikes:nested-frames:allTests`). Each test launches a blocking call from
  `rememberCoroutineScope()`, so it runs as a task on `RavenDispatcher`, and a driver coroutine
  takes one step per nested frame while it waits. 8 tests pass:
  - `Rmessage_box`, `Rdialog_run` and `Rfile_dialog_open`: state writes recompose and apply inside
    the dialog's frames, Text follows, and a controlled Checkbox in a second window reverts a
    refused click and keeps an accepted one.
  - Nothing re-enters the Applier: no blocking call starts inside an apply, and each apply that
    runs while one waits finds exactly one `CompositionImpl#applyChanges` on its stack.
  - Wake-ups, with animations on and off: the click's own write, whose snapshot apply waits in the
    dispatcher's queue behind the blocked block, and a write from another thread are both composed
    within a millisecond. A worker thread wakes Raven and records the miss if either is slept
    through, so a lost wake-up fails the test instead of hanging it.
  - Compose removing the dialog's window, and `Rquit`, end the call with Cancel, with no Raven
    error and nothing leaked.
  - Two dialogs waiting at once return in reverse order.
- **Why it holds**: Raven's wait loop (`rvn_dialog_wait`) clears `in_update` so nested
  `rvn_update`s run tasks, timers and the frame callback. Its task queue runs a copied batch, and
  `rvn_dispatch_begin`/`end` count depth, so dead widgets are collected only once the outermost
  dispatch ends. compose-raven's blocks only ever run from `RavenDispatcher.drain()`, and the
  Recomposer is suspended there, not on the stack, so a nested `drain` and `sendFrame` start from
  a clean state.
- **Caveats** (in the README): the first nested frame comes at once only because opening a dialog
  needs a layout, which resets the dispatcher's posted flag; most "native" dialogs are Raven's own
  (always for message boxes, and for file dialogs on macOS); Windows' own `IFileDialog` modal loop
  waits for a Windows host.
