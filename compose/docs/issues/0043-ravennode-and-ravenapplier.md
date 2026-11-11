---
status: open
---

# RavenNode and RavenApplier

Write the internal node and Applier in `compose-raven-ui`: one `RavenNode` per Raven widget with a
mirror child list, bottom-up inserts, detaching removes, `onRelease` destruction, moves through
`Rwidget_move_child`, and the single-child parent handling. Spec:
[5. Node model and Applier](../spec.md#5-node-model-and-applier).

**Depends on:** [raven.native helpers](archive/0042-raven-native-helpers.md).

## Deliverables

- [ ] `RavenNode`: the `Rid`, one `StableRef` to itself through the registry, fields for event
      lambdas and folded layout values (empty for now), the mirror child list, and the kind of
      children its widget takes: none, single or many.
- [ ] `RavenApplier` (`AbstractApplier<RavenNode>`, bottom-up):
  - [ ] `insertBottomUp` through `Rwidget_insert_child`, with a clear Kotlin error for a node that
        takes no children.
  - [ ] `remove` only detaches (`Rwidget_remove_child`), so `movableContentOf` works.
  - [ ] `move` calls `Rwidget_move_child` once per item, with `to - count` when `from < to`
        (fork change 1, already on this branch).
  - [ ] Single-child parents: detach the attached Raven child before inserting another. It stays
        in the mirror list, and removing a node that is no longer attached skips the Raven call.
- [ ] `ComposeNodeLifecycleCallback`: `onRelease` calls `Rwidget_destroy`, and the destroy callback
      disposes the `StableRef`. `onReuse` and `onDeactivate` do nothing.
- [ ] A root wrapping an Overlay that the Applier never replaces.
- [ ] Pure unit tests, with no Raven: move-index adjustment and mirror-list bookkeeping. Headless
      tests driving the Applier directly against Raven, checked through Raven's child getters.
