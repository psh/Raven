---
status: closed
---

# Small fixes the skill runs found

Running the skills on `examples/notes`
([Verify the skills on Raven's samples](0182-verify-the-skills-on-samples.md)) turned up small
snags in Raven's tools. Nothing blocked a skill. Found on 2026-11-24.

## Deliverables

- [x] `raven_preview` doesn't pass over Raven's XML warnings. An unknown attribute is reported, but the PNG is written and it exits 0, while the same warning fails a test. It should exit 1, or offer a `--strict` that does.
- [x] `raven::testing` reports a failure at the test's line, not at `src/testing.rs`: `#[track_caller]` on the checks and actions. C names the test's file and line already.
- [x] The C examples can serve Raven MCP from CMake. `libraven_mcp` needs the shared libraven, and the examples link the static one, so the skill runs built `examples/notes` by hand behind `NOTES_WITH_MCP`.

## Resolution (2026-11-25)

- `raven_preview` exits 1 when Raven warned about a file's XML, the PNG still written, and says so:
  "written, but Raven reported 1 problem with it". No `--strict`: a preview for a test should fail
  as the test would. ctest `preview_xml_warning` (`tools/preview/examples/warning.xml`).
- `raven::testing`'s checks and actions are `#[track_caller]`, so a failure is reported at the
  test's line. `tests/testing_location.rs` checks it, in its own file since it sets the panic hook;
  without the attribute it reports `src/testing.rs:218`.
- `-DRAVEN_BUILD_SHARED=ON -DRAVEN_MCP_LIB_DIR=<folder of libraven_mcp>` (the same variable as
  Rust's, the environment's by default) builds `raven_notes_mcp`, the notes example with
  `NOTES_WITH_MCP`, rpaths to both libraries. Started from another folder with `RAVEN_MCP=1
  RAVEN_MCP_UI=1`, it served the six `ui.*` tools. `c.md` says so. No ctest: it needs a window.
