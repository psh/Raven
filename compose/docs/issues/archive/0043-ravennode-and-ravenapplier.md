---
status: closed
---

# RavenNode and RavenApplier

Write the internal node and Applier in `compose-raven-ui`: one `RavenNode` per Raven widget with a
mirror child list, bottom-up inserts, detaching removes, `onRelease` destruction, moves through
`Rwidget_move_child`, and the single-child parent handling. Spec:
[5. Node model and Applier](../../spec.md#5-node-model-and-applier).

**Depends on:** [raven.native helpers](0042-raven-native-helpers.md).

## Deliverables

- [x] `RavenNode`: the `Rid`, one `StableRef` to itself through the registry, fields for event
      lambdas and folded layout values (empty for now), the mirror child list, and the kind of
      children its widget takes: none, single or many.
- [x] `RavenApplier` (`AbstractApplier<RavenNode>`, bottom-up):
  - [x] `insertBottomUp` through `Rwidget_insert_child`, with a clear Kotlin error for a node that
        takes no children.
  - [x] `remove` only detaches (`Rwidget_remove_child`), so `movableContentOf` works.
  - [x] `move` calls `Rwidget_move_child` once per item, with `to - count` when `from < to`
        (fork change 1, already on this branch).
  - [x] Single-child parents: detach the attached Raven child before inserting another. It stays
        in the mirror list, and removing a node that is no longer attached skips the Raven call.
- [x] `ComposeNodeLifecycleCallback`: `onRelease` calls `Rwidget_destroy`, and the destroy callback
      disposes the `StableRef`. `onReuse` and `onDeactivate` do nothing.
- [x] A root wrapping an Overlay that the Applier never replaces.
- [x] Pure unit tests, with no Raven: move-index adjustment and mirror-list bookkeeping. Headless
      tests driving the Applier directly against Raven, checked through Raven's child getters.

## Resolution

Done on macOS (arm64), 2026-11-10, in `compose-raven-ui/src/nativeMain/kotlin/raven/compose/ui/`
(`RavenNode.kt`, `RavenApplier.kt`). Eleven tests pass. Every headless test fails if Raven reports
an error, such as a stale handle or a double destroy, or if a widget reference outlives Raven.

- **`RavenNode`** has the widget, its `userData` (the registry's `StableRef`), the mirror
  `children` and a `ChildKind`. The fields for event lambdas and layout values aren't added yet:
  [The binding layer](../0045-binding-layer.md) and
  [Modifier, Dp and the layout containers](../0046-modifier-dp-and-layout-containers.md) add them
  with the code that uses them.
- **`onRelease`** destroys the widget only if it is still valid (`Rwidget_is_valid`), because
  destroying an ancestor first has already destroyed it. Tests release parent-first and
  child-first.
- **Moves**: `ravenMoves(from, to, count)` follows `AbstractApplier`'s own list move: a swap for a
  single neighbour, and `to - count` as the block's start when moving forward. The spec's earlier
  "`to - count` per item" was right only for a single item. `RavenMovesTest` checks every move of
  up to six children against the real `AbstractApplier`, and spec section 5 now describes it.
- **Single-child parents**: an insert detaches whatever Raven has attached first. In addition, in
  `onEndChanges`, a single-child parent left with one mirror child that isn't attached gets it
  back. That covers a removal of the attached child that leaves a detached one.
- **`onClear`** detaches the root's children, and released nodes destroy themselves.
- **Composition tests** run a real `Recomposer` on a `BroadcastFrameClock` in `runBlocking`: an
  if/else in a single-child parent, keyed reorders that keep the same widgets, `movableContentOf`
  across a Many and a Single parent, and dispose leaving only the root.
- `TestSupport.kt` starts Raven headless with an error callback. It's a stopgap until
  [RavenHost and singleWindowApplication](0044-ravenhost-and-singlewindowapplication.md) brings
  the harness.
- The cinterop opt-in and `kotlin-test` (in `commonTest`) moved into the `raven.native-target`
  convention plugin, for every module.
