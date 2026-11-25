---
status: open
---

# UI tests in Python: raven_testing

A `raven_testing` module in the wheel: a context manager and a pytest fixture over the driver.
Design: [Python](../agent-skills.md#python).

**Depends on:** [UI tests in C: raven_test.h, and the notepad example](archive/0171-ui-tests-in-c.md).

## Deliverables

- [ ] `raven_testing.app(xml=…)` context manager with the same harness guarantees as C's, and `type`, `click`, `key`, `find`, `expect_*`.
- [ ] A pytest fixture `raven_ui` over it, with no pytest dependency in `raven.py`.
- [ ] Shipped in the wheel by `build_wheel.py`.
- [ ] The notepad test in Python (`examples/notes/notes.xml`, as `examples/notes/test_notes.c` tests it in C), run by the build (a `binding_python_ui` CTest).
