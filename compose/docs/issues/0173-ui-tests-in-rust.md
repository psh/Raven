---
status: open
---

# UI tests in Rust: raven::testing

A `raven::testing` module in the crate. Design: [Rust](../agent-skills.md#rust).

**Depends on:** [UI tests in C: raven_test.h, and the notepad example](archive/0171-ui-tests-in-c.md).

## Deliverables

- [ ] `raven::testing::run(xml, |ui| …)` with the same harness guarantees as C's, and `type_text`, `click`, `key`, `find`, `expect_*`, panicking with the driver's message.
- [ ] The notepad test as a `cargo test` (`examples/notes/notes.xml`, as `examples/notes/test_notes.c` tests it in C), run by the build (a `binding_rust_ui` CTest).
