---
status: closed
---

# UI tests in Rust: raven::testing

A `raven::testing` module in the crate. Design: [Rust](../../agent-skills.md#rust).

**Depends on:** [UI tests in C: raven_test.h, and the notepad example](0171-ui-tests-in-c.md).

## Deliverables

- [x] `raven::testing::run(xml, |ui| …)` with the same harness guarantees as C's, and `type_text`, `click`, `key`, `find`, `expect_*`, panicking with the driver's message.
- [x] The notepad test as a `cargo test` (`examples/notes/notes.xml`, as `examples/notes/test_notes.c` tests it in C), run by the build (a `binding_rust_ui` CTest).

## Resolution

Done on macOS (arm64), Rust 1.100, on the `compose-raven` branch, against a shared libraven
(`build/shared`). `binding_rust_ui` passes. It runs `cargo test --test notes --test testing`: the
notes scenario, plus 5 tests of the harness itself, one of which checks that a capture is written
on headless Vulkan. `binding_rust` and `bindings_up_to_date` pass too.

- **`bindings/rust/src/testing.rs`** is written by hand. bindgen's `lib.rs` template declares
  `pub mod testing;`.
  - **Entry points:** `run(xml, |ui| …)`, with `""` for no window, and
    `run_with(Options { capture, name, time_step, idle_frames, allow_leaks, xml }, …)`.
  - **On `Ui`:**
    - Windows and steps: `load_xml`, `load_xml_file`, `click`, `type_text`, `key` (`"Ctrl+S"`),
      `act` (`Action::Expand`...), `find` (an `Accessible`) and `count`.
    - Checks: `expect_exists`, `_absent`, `_count`, `_text`, `_value`, `_checked`, `_enabled` and
      `_focused`.
    - Errors and waiting: `expect_errors(|ui| …) -> Vec<String>`, `wait_idle` and `fail`.
    - `parse_key`.
  - **Failures panic** with the same messages as C and Python.
  - **One at a time.** A global lock takes the tests one by one, since cargo runs them on several
    threads and Raven is one per process. A panic is caught, Raven is shut down, and then the panic
    resumes, so the next test starts clean (tested by 14 failing runs in a row).
  - **Raven's errors** go into a static list, since a C callback can't capture.
  - **Without Vulkan**, a capture test prints that it is skipped and returns, because cargo has no
    skipped outcome. Raven failing to start headless panics.
- **`examples/notes/notes_app.rs`** is the notes logic in Rust. `bindings/rust/tests/notes.rs`
  includes it by `#[path]` and runs the scenario the C and Python tests run.
- **Found and fixed: Rust binaries couldn't find libraven on macOS.** libraven's install name is
  `@rpath/libraven.0.dylib`, the binaries had no rpath, and `DYLD_LIBRARY_PATH` didn't reach them.
  So the existing `binding_rust` CTest failed on macOS too. The generated `build.rs` now adds
  `-Wl,-rpath,$RAVEN_LIB_DIR` on macOS and Linux when linking the shared library. That reaches
  only this crate's own examples and tests, not the binaries of crates that depend on it.
- **Docs**: the Rust README (a UI tests section) and [Rust](../../agent-skills.md#rust).
- **Not here**:
  - `cargo test` with doctests still fails, on a C snippet in `Rdraw_spring`'s generated doc
    comment. That predates this work, and the CTest runs only the two test targets.
  - Not tried on Linux or Windows.
