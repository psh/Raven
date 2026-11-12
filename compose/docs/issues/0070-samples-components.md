---
status: open
---

# samples/components

Port Raven's `examples/components`: cards, badges and stats, which the C example declares as XML
`<Widget>`s, become ordinary composables, and "Add a card" adds one. Spec:
[10. Samples](../spec.md#10-samples),
[8: Surface, FlowRow and Expander](../spec.md#phase-2-surface-flowrow-and-expander).

**Depends on:** [Surface, FlowRow and Expander](0064-surface-flowrow-and-expander.md).

## Deliverables

- [ ] `samples/components` with the C example's look: `Card`, `Badge` and `Stat` composables
      built on `Surface`, cards laid out in a `FlowRow` inside a `VerticalScroll`, an
      `Expander`, and the source pane.
- [ ] `<Repeat>`, `<ForEach>` and `If=` become Kotlin loops and `if`s; "Add a card" adds an
      entry to the app's state.
- [ ] Runs from Gradle like `samples/hello`, documented in `compose/README.md`.
- [ ] A headless test: "Add a card" adds a card with the next title.
- [ ] Each gap below is settled by [compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md)
      before this ticket starts: brought into phase 2, or a port gap naming the phase that
      completes it.

## Gaps

- **Phase 3:** the cards' `Appear="Drop"` transition (`Modifier.appearTransition`,
  [Island and appear transitions](archive/0036-island-and-appear-transitions.md)).
- **No Compose counterpart:** building an XML document from code and listing the registered
  `<Widget>` tags. The source pane could show the composable source of a card instead.
