---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0003, 0004, 0006]
assignee: Paul Hawke
---

# Node model and Applier

## Question

What is compose-raven's node type, and how does its Applier map Compose's insert/move/remove operations onto the Raven widget tree? Cover: one node per Raven widget or not, when the widget is created and destroyed relative to the node, the root node (the window?), and how a widget's properties get updated.

Must also decide how the Applier handles the gaps found in "Raven widget tree API for an Applier" (0004): moves have no in-place call (detach + re-insert loses focus/hover and replays appear transitions), and single-child parents destroy their old child on a second insert. Work around them in Kotlin, or have the spec require a Raven change such as `Rwidget_move_child`?

## Resolution

- **Node**: a single internal `RavenNode` class in `raven.compose.ui`. It holds the widget's `Rid`, one `StableRef` to itself (the `user_data` of every callback), and a mirror list of its child nodes. Composables emit it with `ComposeNode<RavenNode, RavenApplier>`, a factory that creates the widget (`RavenNode(Rwidget_create(...))`), and `Updater.set` setters. Each composable states which children its widget takes (none, single or many), because Raven keeps this in private flags. The Applier enforces it in Kotlin with a clear error.
- **Lifetime**: the widget is created in the node factory. `Applier.remove` only detaches (`Rwidget_remove_child`), so `movableContentOf` can re-insert the same node. `RavenNode` implements `ComposeNodeLifecycleCallback`: `onRelease` calls `Rwidget_destroy`, and the widget's destroy callback disposes the `StableRef`. There is no node reuse in v1 (no `ReusableComposeNode`), so `onReuse`/`onDeactivate` do nothing.
- **Root**: the window entry point creates an `R_WIDGET_OVERLAY`, sets it once with `Rwindow_set_content`, and wraps it as the Applier's root node. It stacks its children like `Box`, and the Applier never replaces it.
- **Inserts**: bottom-up, as in [How Mosaic is built](0003-how-mosaic-is-built.md), via `Rwidget_insert_child`.
- **Properties**: each `Updater.set` calls the cinterop setter directly. There is no `onEndChanges` batching, since Raven lays out lazily in the same frame and setters don't fire change callbacks.
- **Moves (required Raven change)**: the spec requires `Rwidget_move_child(parent, from, to)` in the `psh/Raven` fork, to be offered upstream later. It moves one child, with `to` counted after the child is taken out (like `Rwidget_insert_child`), keeps focus, hover and capture, doesn't replay the appear transition, invalidates layout, and treats an out-of-range index as an error. The Applier loops over Compose's `count` and uses `to - count` when `from < to`.
- **Single-child parents** (Border, SizeBox, Button, Expander, BackgroundBlur): before inserting into one, the Applier detaches the current Raven child. That child stays alive in the mirror list. Removing a node that is no longer attached skips the Raven call. No Raven change is needed.
