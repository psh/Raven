---
status: closed
---

# UI tests in Python: raven_testing

A `raven_testing` module in the wheel: a context manager and a pytest fixture over the driver.
Design: [Python](../../agent-skills.md#python).

**Depends on:** [UI tests in C: raven_test.h, and the notepad example](0171-ui-tests-in-c.md).

## Deliverables

- [x] `raven_testing.app(xml=…)` context manager with the same harness guarantees as C's, and `type`, `click`, `key`, `find`, `expect_*`.
- [x] A pytest fixture `raven_ui` over it, with no pytest dependency in `raven.py`.
- [x] Shipped in the wheel by `build_wheel.py`.
- [x] The notepad test in Python (`examples/notes/notes.xml`, as `examples/notes/test_notes.c` tests it in C), run by the build (a `binding_python_ui` CTest).

## Resolution

Done on macOS (arm64), Python 3.9, on the `compose-raven` branch, against a shared libraven
(`build/shared`). These pass:

- `binding_python_ui`: the notes test, plain.
- `binding_python_testing`: the harness's own 9 unittest tests.
- The notes test with `RAVEN_TEST_CAPTURE=1` on headless Vulkan.
- The notes test under pytest 8.4 with the fixture, from a scratch venv: once with
  `-p raven_testing`, and once from the built wheel with nothing configured.

The full suite on the shared build fails only on `test_shell` and `test_themes`, which were failing
before this work.

- **`bindings/python/raven_testing.py`**: `app(xml=…, xml_file=…, capture=…, name=…, time_step=…,
  idle_frames=…, allow_leaks=…)`, an `App` for a with block (or `start()`/`stop()`).
  - Windows and steps: `load_xml`, `load_xml_file`, `click`, `type`, `key` (`"Ctrl+S"`, or an
    `RKey` and modifiers), `act` (`"expand"`...), `find` (an `Accessible`, with its states as a set
    of names) and `count`.
  - Checks: `expect_exists`, `_absent`, `_count`, `_text`, `_value`, `_checked`, `_enabled` and
    `_focused`.
  - Errors and waiting: `expect_errors()` (a context manager), `wait_idle` and `fail`.
  - The messages read like C's.
- **Errors**:
  - Raven's errors are collected from the callback and raised at the next step, or when the block
    ends.
  - A failure raises `RavenTestFailure`, an `AssertionError`, after capturing the windows when
    capture is on.
  - A test that already failed isn't reported twice or leak-checked.
- **`raven_ui`** is defined only when pytest imports, with `@pytest.mark.raven(capture=True, ...)`
  for options. A Raven error left at the end shows as a teardown ERROR, as pytest reports
  fixtures. The next test starts clean.
- **The wheel** ships `raven_testing.py` beside `raven/`, adds `raven_testing` to
  `top_level.txt`, and adds `[pytest11] raven_testing = raven_testing` to its entry points.
- **`examples/notes/notes_app.py`** is the notes logic in Python (also runnable as the app).
  **`test_notes.py`** is the same scenario as C: a pytest test through the fixture, and a `main()`
  through `raven_testing.app` (77 when skipped).
- **CTests** (in `bindings/CMakeLists.txt`, shared builds, not on Windows):
  - `binding_python_ui` and `binding_python_testing`.
  - `binding_python_ui_pytest` when the configured Python has pytest. Here it doesn't, so that
    test isn't registered; I ran the same command by hand.
- **Found and fixed:**
  - A start that failed while loading the XML left Raven running, and the next test's `Rinit`
    failure was then reported as a *skip*. Now `start()` shuts Raven down when loading fails, and
    only "no Vulkan with capture" skips.
  - C's `Rtest_begin` had the same skip, and is fixed the same way (`test_raven_test.c` covers it).
- **Docs**: the Python README (a UI tests section) and [Python](../../agent-skills.md#python).
- **Not here**: not tried on Linux, Windows, or a Python other than 3.9.
