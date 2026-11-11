---
status: closed
---

# The binding layer

Write the internal binding layer in `compose-raven-ui` before any composable: widget descriptors,
property binding, event binding with the C-boundary catch, the content node, and
`ControlledValue<T>`. Every composable after this is a thin declaration over it. Spec:
[The binding layer](../../spec.md#the-binding-layer),
[7. Events and state](../../spec.md#7-events-and-state),
[5. Node model and Applier](../../spec.md#5-node-model-and-applier) (content of single-child widgets).

**Depends on:** [RavenHost and singleWindowApplication](0044-ravenhost-and-singlewindowapplication.md).

## Deliverables

- [x] **Widget descriptor**: the Raven widget type, the kind of children, whether it has a content
      node and of which kind (Row or Box), and the default accessible role.
- [x] **Property binding**: a typed setter per property, called through `Updater.set` only when the
      value changes.
- [x] **Event binding**: one shared `staticCFunction` per callback kind, registered once per node
      with its `StableRef`. It dispatches to the lambda in the node's field, and a null lambda does
      nothing. Each catches any Kotlin exception and passes it to the host's error path.
- [x] **Content node**: a single-child widget's composable emits the widget plus an internal Row or
      Box with `R_ROLE_NONE`. It takes no Modifier, and test finders never see it.
- [x] **`ControlledValue<T>(read, write, equals)`**: remembers the composed value and marks the node
      with the apply generation on a user change. In the Applier's `onEndChanges` of the first frame
      applied after that generation, it writes the composed value back if the two differ. It never
      waits for the Recomposer to be idle.
- [x] Tests, with a test-only descriptor or two: an unchanged property makes no Raven call, an
      exception in a callback reaches the error path, and a rejected change reverts on the next
      applied frame while an accepted one causes no write.

## Resolution

Done on macOS. The binding layer is `Binding.kt` in `compose-raven-ui`, and `RavenNode` now holds
its event lambdas and composed values. 28 tests pass in `compose-raven-ui`.

- **What a composable writes**: a `WidgetDescriptor` (type, children, content kind, role, events),
  then `RavenWidget(descriptor, { bind(value, Property); bind(lambda, Event); bind(value,
  ControlledValue) }) { content }`. The `bind` overloads go through `Updater.set`, so nothing runs
  for an unchanged value. Lambdas are replaced in the node's field, and the C callback stays
  registered.
- **Events**: an `Event<L>` points its Raven callback at one shared `staticCFunction`, which calls
  `dispatchEvent`. That looks up the node from `user_data`, marks the controlled value the event
  changes, calls the lambda if there is one, and passes anything thrown to
  `RavenHost.reportError`. `ButtonClicked` and `CheckboxChanged` (with `CheckboxChecked`) are
  defined. The later widget tickets add their own kinds.
- **The controlled-value check moved** from the Applier's `onEndChanges` to the end of the host's
  frame callback. A rejected change makes the app compose nothing, so no batch is applied, and a
  check that waits for one would never revert it. A marked value is now checked when a batch was
  applied after its mark, or when nothing is left to recompose. Frame awaiters don't count, so
  animations never hold it back. Spec section 7 is updated. `RavenHost.isIdle()` is false while a
  marked value waits.
- **Nodes know their host**: `RavenNode(widget, kind, host, isContentNode)`. The binding layer
  always passes the host. The Applier tests' bare nodes have none, and need none.
- **Tests** (`BindingTest`, with test-only descriptors and counting setters):
  - an unchanged property makes no Raven call;
  - a single-child widget's content goes into its HBox panel;
  - an exception in a click reaches the error path and ends the loop;
  - a rejected change reverts on the next frame, with one write;
  - a change with no handler reverts;
  - a rejected change reverts while an animation recomposes every frame;
  - an accepted change makes no write.

  Clicks are injected at the widget's centre. With the check turned off, the three controlled-value
  tests fail.
