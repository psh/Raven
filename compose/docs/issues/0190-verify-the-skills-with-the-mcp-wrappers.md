---
status: open
---

# Verify the skills with the Python and Rust MCP wrappers

[Verify the skills on Raven's samples](archive/0182-verify-the-skills-on-samples.md) drove the running
Python and Rust apps through the raw `Rmcp_*` calls, since
[Python and Rust MCP wrappers](0180-python-and-rust-mcp-wrappers.md) was still open. Once it closes,
the steps that use Raven MCP (`diagnosing-bugs` and `triage`, reproducing in the running app) are
run again in Python and Rust through `raven_mcp` (Python's module, and Rust's crate `raven-gui-mcp`), and the `raven` skill's language
files are checked against them. Asked for on 2026-11-24.

**Depends on:** [Python and Rust MCP wrappers](0180-python-and-rust-mcp-wrappers.md),
[Verify the skills on Raven's samples](archive/0182-verify-the-skills-on-samples.md).

## Deliverables

- [ ] `diagnosing-bugs` and `triage` run again on `examples/notes` in Python and Rust, with the wrappers.
- [ ] `python.md` and `rust.md` (through `tools/bindgen/skill/`) show the wrappers where they now show raw calls.
- [ ] The table in `agent-skills.md` updated if anything changed.
