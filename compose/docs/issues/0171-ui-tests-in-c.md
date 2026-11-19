---
status: open
---

# UI tests in C: raven_test.h, and the notepad example

A header-only harness for C apps' headless UI tests, and the `notepad.xml` every language's example
tests. Design: [UI tests in each language](../agent-skills.md#ui-tests-in-each-language).

**Depends on:** [The UI driver in Raven's C](archive/0169-the-ui-driver.md),
[PNG encoding in Raven's C](archive/0166-png-encoding-in-ravens-c.md).

## Deliverables

- [ ] `raven/raven_test.h`, header-only and not included by `Raven.h`: `Rtest_begin(options)`/`Rtest_end()`, `RTEST_*` macros over selectors, a failure hook (printed and counted by default), `RTEST_SKIPPED` (77).
- [ ] The harness: null backend by default, headless Vulkan when capturing (skipping without Vulkan), a fixed time step, Raven's errors failing the test, `Rwait_idle` around every step, leak checks on by default, PNGs of each window on failure into `RAVEN_TEST_CAPTURES`.
- [ ] Assertions: exists, doesn't exist, text, checked, enabled, focused, count, value.
- [ ] `examples/notepad.xml` (or beside an existing example) and a C test of it under ctest: add a note, check the list, delete it.
