---
status: open
---

# Verify recomposition inside nested frames

A verify-first fact that waits for the host: can Compose recompose safely inside the nested frames
that Raven's blocking dialogs run (`Rmessage_box`, `Rfile_dialog_*`)? Phase 3's `native = true`
dialogs depend on the answer. Spec: [4. Application host](../spec.md#4-application-host); roadmap:
[0. Verify first](../roadmap.md#0-verify-first).

**Depends on:** [RavenHost and singleWindowApplication](0044-ravenhost-and-singlewindowapplication.md).

## Deliverables

- [ ] A spike under `compose/spikes/` that opens a blocking dialog from a task on `RavenDispatcher`
      while state changes keep arriving, and checks that recomposition, applies and controlled
      checks stay correct and nothing re-enters the Applier.
- [ ] The result in the roadmap's verify-first table and the spikes README. If it fails, record that
      `native = true` dialogs are dropped from phase 3.
