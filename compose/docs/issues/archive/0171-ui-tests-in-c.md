---
status: closed
---

# UI tests in C: raven_test.h, and the notepad example

A header-only harness for C apps' headless UI tests, and the `notepad.xml` every language's example
tests. Design: [UI tests in each language](../../agent-skills.md#ui-tests-in-each-language).

**Depends on:** [The UI driver in Raven's C](0169-the-ui-driver.md),
[PNG encoding in Raven's C](0166-png-encoding-in-ravens-c.md).

## Deliverables

- [x] `raven/raven_test.h`, header-only and not included by `Raven.h`: `Rtest_begin(options)`/`Rtest_end()`, `RTEST_*` macros over selectors, a failure hook (printed and counted by default), `RTEST_SKIPPED` (77).
- [x] The harness: null backend by default, headless Vulkan when capturing (skipping without Vulkan), a fixed time step, Raven's errors failing the test, `Rwait_idle` around every step, leak checks on by default, PNGs of each window on failure into `RAVEN_TEST_CAPTURES`.
- [x] Assertions: exists, doesn't exist, text, checked, enabled, focused, count, value.
- [x] `examples/notepad.xml` (or beside an existing example) and a C test of it under ctest: add a note, check the list, delete it.

## Resolution

Done on macOS (arm64), on the `compose-raven` branch. `example_notes` passes on the null backend and,
with `RAVEN_TEST_CAPTURE=1`, on headless Vulkan (22 checks each). `test_raven_test` passes (64
checks). The header compiles without warnings as C99, C11 and C++17 under `-Wall -Wextra`
(`-pedantic` for C). The full C suite has the same two failures as before this change:
`test_shell` and `test_themes`.

- **`include/raven/raven_test.h`.** bindgen follows `Raven.h`, so the header stays out of the
  bindings.
  - **Setup.** `RTestOptions` holds `name`, `capture`, `time_step` (1/60), `idle_frames` (600),
    `allow_leaks`, `on_failure` and `user_data`.
  - **Steps.** `RTEST_CLICK`, `_TYPE`, `_KEY` and `_ACT` act; `RTEST_DESCRIBE` reads; `RTEST_CHECK`
    checks the app's own state.
  - **Checks.** `RTEST_EXPECT_EXISTS` (exactly one), `_ABSENT` (the "doesn't exist" check),
    `_COUNT`, `_TEXT` (the driver's `text=` rule), `_VALUE` (a range's number), and `_CHECKED`,
    `_ENABLED` and `_FOCUSED`, each given the state it expects.
  - **Return values.** Every step returns `R_TRUE`, or `R_FALSE` after reporting the failure, and
    the test goes on.
  - **Failure messages** name the macro, the selector, what was found and what was expected, or the
    driver's own reason.
  - **Raven's errors** fail the test, warnings included, since an XML attribute Raven doesn't know
    is only a warning. Weak warnings don't. `Rtest_expect_errors` counts the ones a test means to
    cause.
  - **A busy app** fails a step when it isn't idle within `idle_frames`.
  - **Leaks.** `Rtest_end` shuts Raven down, checks the allocation count and the heap, prints
    `name: N checks, M failures` and returns the exit code.
  - **Captures** use `Rwindow_save_preview` (0170) into `RAVEN_TEST_CAPTURES`, as
    `<name>-<failure>-window<n>.png`. None is taken for a failure that is one of Raven's own
    errors, because Raven is in the middle of something then.
- **`examples/notes/`.** The design said `notepad.xml`, but `examples/notepad` is the text editor,
  so the shared example is `examples/notes/`. It holds:
  - `notes.xml`: a text field with an accessible name, Add, a list, the count and Delete.
  - `notes_app.c`: what `notes.add` and `notes.delete` do, shared by the app (`raven_notes`) and
    its test.
  - `test_notes.c`, the `example_notes` ctest. It adds two notes (with the button, and with Enter),
    checks the list, the field and the count, then selects one, deletes it, and checks that Delete
    turns itself off and that an empty note isn't added.

  The Python and Rust tests (0172, 0173) load the same `notes.xml`, with the logic in their own
  language.
- **`tests/test_raven_test.c`** tests the harness itself through a hook that collects messages:
  - every check passing, and each one failing with its exact message;
  - the driver's reasons for missing, ambiguous and disabled;
  - Raven's errors failing the test, and expected errors counted instead;
  - a PNG left on failure on headless Vulkan (said and left out without Vulkan);
  - steps before `Rtest_begin`.
- **Docs**: the README (a UI tests section and the example's row), and the decisions in
  [UI tests in each language](../../agent-skills.md#c). The design's C example passed no size to
  `Rwindow_create_from_xml`, which doesn't take null-terminated text (size 0 builds nothing). It now
  loads the notes example.
- **Not here**:
  - Not tried on Linux or Windows. Under MSVC, `getenv` in the header may warn (C4996) unless the
    test defines `_CRT_SECURE_NO_WARNINGS`.
  - The design's Python and Rust examples still say `NOTEPAD_XML`. 0172 and 0173 now point at
    `examples/notes/notes.xml`.
