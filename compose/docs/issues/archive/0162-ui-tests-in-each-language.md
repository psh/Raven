---
status: closed
wayfinder: grilling
parent: 0157
blocked_by: [0161]
assignee: Paul Hawke
---

# UI tests in each language

## Question

How does an app write a headless UI test in C (ctest), Python (pytest) and Rust (`cargo test`) over
the C driver, and what does each binding add on top, kept thin?

- **The harness**: starting and stopping Raven headless per test (null backend, or headless Vulkan
  when the test captures), a fixed animation time step, and errors reported by Raven failing the
  test, as `runRavenTest` does for compose-raven.
- **Each language's idiom**: a pytest fixture, a Rust helper or macro, and plain C with Raven's own
  `tests/test.h` style. Assertions in the language's test framework, over the driver's failure
  messages.
- **Screenshots in tests**: captures saved on failure, or compared with a stored image, or neither
  for now.
- **The worked example**: a test of the notepad sample, or its counterpart in each language, that
  `tdd` and `diagnosing-bugs` can follow.

## Resolution

Decided with Paul on 2026-11-18, and written into
[`compose/docs/agent-skills.md`](../../agent-skills.md#ui-tests-in-each-language).

- **Thin per language**: each adds a harness and assertions over `raven_driver.h`. Finding, acting
  and idle stay in C.
- **The harness, everywhere**: null-backend headless by default, or headless Vulkan when capturing
  (skipping with 77 without Vulkan); a fixed animation time step; Raven's errors fail the test;
  `Rwait_idle` around every step; leak checks on by default; on headless Vulkan, PNGs of each
  window on failure into `RAVEN_TEST_CAPTURES`.
- **One small assertion set** (exists or not, text, checked, enabled, focused, count, value) plus
  `find`, with compose-raven-test's meanings.
- **C**: a header-only `raven/raven_test.h` (`Rtest_begin`/`Rtest_end`, `RTEST_*` macros, a failure
  hook, `RTEST_SKIPPED` = 77). Raven's own `tests/test.h` stays.
- **Python**: `raven_testing` in the wheel. A context manager, plus a pytest fixture over it. No
  pytest dependency in `raven.py`.
- **Rust**: `raven::testing::run(xml, |ui| …)`, panicking with the driver's message.
- **The worked example**: the same `notepad.xml` in C, Python and Rust. compose's notepad tests stay.
