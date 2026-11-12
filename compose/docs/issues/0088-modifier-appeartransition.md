---
status: closed
---

# Modifier.appearTransition

`Modifier.appearTransition` arrives early, in phase 2, for `samples/components`' cards.
`AnimatedVisibility`, `animate*AsState` and `Island` stay in phase 3. Spec:
[Transitions and Island](../spec.md#transitions-and-island-phase-3). Decided in:
[Island and appear transitions](archive/0036-island-and-appear-transitions.md), and brought into
phase 2 by [ADR 0001](../adr/0001-phase-split-after-the-phase-2-sample-gaps.md).

**Depends on:** nothing open.

## Deliverables

- [x] `Modifier.appearTransition(AppearTransition, delay)` with `Fade`, `Pop`, `Zoom`, `FlyUp`,
      `FlyDown`, `FlyLeft`, `FlyRight`, `Drop`, `SlideLeft` and `SlideRight`, playing Raven's
      transition each time the widget appears.
- [x] Tests: a node added by recomposition gets its transition and delay; a modifier change doesn't
      replay it on a widget already shown.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 266 tests, 5 of them `AppearTransitionTest`'s.

- **API**, in `raven.compose.ui` (`AppearTransition.kt`, in `compose-raven-ui`), next to
  `testTag` and `tooltip`, the other modifiers any widget can have:
  `Modifier.appearTransition(transition: AppearTransition, delay: Duration = Duration.ZERO)`.
  `AppearTransition` is `Fade`, `Pop`, `Zoom`, `FlyUp`, `FlyDown`, `FlyLeft`, `FlyRight`, `Drop`,
  `SlideLeft` or `SlideRight`, mapped internally to `RTransition`. The delay is a
  `kotlin.time.Duration`, as the decision's `delay = 100.milliseconds` reads; a negative one is
  refused. The last `appearTransition` in the chain wins, and removing it gives Raven
  `R_TRANSITION_NONE`.
- **When it is set**: the node folds it with the rest of the Modifier and calls
  `Rwidget_set_transition` only when the transition or its delay changed. Compose runs a node's
  update before the Applier inserts it, so the first call reaches a widget not yet in the tree, and
  `Rwidget_insert_child` then plays it as the widget appears (or the window's first draw does).
  A move (`Rwidget_move_child`) and other Modifier changes make no call.
- **Raven plays a transition as soon as it is set.** So when a widget already inserted is given a
  new transition or delay (a staggered list whose delays follow the index, say), the node stops the
  replay at once with `Rwidget_play_transition(widget, R_TRANSITION_NONE, 0)`, which keeps the
  transition it was given for its next appearance. The one case this gets wrong is a change in the
  frames between the insert and the window's first draw of the widget, which loses that first play.
- **Tests** (`AppearTransitionTest`, in `compose-raven-ui`): Raven has no getter for a widget's
  transition, so the Raven calls go through `AppearCalls`, which the tests replace with a recorder,
  as `WindowCalls` is for windows.
  - a node in the first composition, and one added by recomposition, get their transition and
    delay before they are inserted;
  - a padding change makes no call; a new delay is set and its replay stopped; removing the
    modifier sets none;
  - reordering keyed children moves them without a call;
  - the last transition in the chain wins, and a negative delay is refused.

  That a transition is seen to play is checked by running `samples/components` once its cards use
  it.
