---
status: open
---

# Small fixes the skill runs found

Running the skills on `examples/notes`
([Verify the skills on Raven's samples](archive/0182-verify-the-skills-on-samples.md)) turned up small
snags in Raven's tools. Nothing blocked a skill. Found on 2026-11-24.

## Deliverables

- [ ] `raven_preview` doesn't pass over Raven's XML warnings. An unknown attribute is reported, but the PNG is written and it exits 0, while the same warning fails a test. It should exit 1, or offer a `--strict` that does.
- [ ] `raven::testing` reports a failure at the test's line, not at `src/testing.rs`: `#[track_caller]` on the checks and actions. C names the test's file and line already.
- [ ] The C examples can serve Raven MCP from CMake. `libraven_mcp` needs the shared libraven, and the examples link the static one, so the skill runs built `examples/notes` by hand behind `NOTES_WITH_MCP`.
