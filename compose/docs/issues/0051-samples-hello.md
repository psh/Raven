---
status: open
---

# samples/hello

Port Raven's `examples/hello` to idiomatic compose-raven: a label, buttons and a click counter,
with its timer as a `LaunchedEffect`. The first app a person can run. Spec:
[`samples/hello`](../spec.md#sampleshello).

**Depends on:** [Text, buttons and the first finders](0047-text-buttons-and-first-finders.md).

## Deliverables

- [ ] `samples/hello` as a `singleWindowApplication` with the same look and behaviour as the C
      example.
- [ ] Runs on macOS (with MoltenVK) and Linux from `./gradlew -p compose :samples:hello:run...`
      (or the linked executable). Document how in `compose/README.md`.
- [ ] A headless test of its counter.
