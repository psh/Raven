---
status: open
---

# The binding layer

Write the internal binding layer in `compose-raven-ui` before any composable: widget descriptors,
property binding, event binding with the C-boundary catch, the content node, and
`ControlledValue<T>`. Every composable after this is a thin declaration over it. Spec:
[The binding layer](../spec.md#the-binding-layer),
[7. Events and state](../spec.md#7-events-and-state),
[5. Node model and Applier](../spec.md#5-node-model-and-applier) (content of single-child widgets).

**Depends on:** [RavenHost and singleWindowApplication](0044-ravenhost-and-singlewindowapplication.md).

## Deliverables

- [ ] **Widget descriptor**: the Raven widget type, the kind of children, whether it has a content
      node and of which kind (Row or Box), and the default accessible role.
- [ ] **Property binding**: a typed setter per property, called through `Updater.set` only when the
      value changes.
- [ ] **Event binding**: one shared `staticCFunction` per callback kind, registered once per node
      with its `StableRef`. It dispatches to the lambda in the node's field, and a null lambda does
      nothing. Each catches any Kotlin exception and passes it to the host's error path.
- [ ] **Content node**: a single-child widget's composable emits the widget plus an internal Row or
      Box with `R_ROLE_NONE`. It takes no Modifier, and test finders never see it.
- [ ] **`ControlledValue<T>(read, write, equals)`**: remembers the composed value and marks the node
      with the apply generation on a user change. In the Applier's `onEndChanges` of the first frame
      applied after that generation, it writes the composed value back if the two differ. It never
      waits for the Recomposer to be idle.
- [ ] Tests, with a test-only descriptor or two: an unchanged property makes no Raven call, an
      exception in a callback reaches the error path, and a rejected change reverts on the next
      applied frame while an accepted one causes no write.
