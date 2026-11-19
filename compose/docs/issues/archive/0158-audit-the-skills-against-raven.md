---
status: closed
wayfinder: research
parent: 0157
blocked_by: []
assignee: claude
---

# Audit the skills against Raven

## Question

For each of the 27 skills in mattpocock-skills' `engineering/` and `productivity/` folders, which
steps assume a particular stack, and what is the Raven equivalent of each?

Read every skill's own files (`SKILL.md` and the files it links). For each skill, record:

- whether it is **stack-neutral** (planning, writing, review: nothing to do), or has steps that need
  something from the stack;
- each such step: what it asks for (see the UI, drive it, test it, a dev server, a screenshot, a test
  runner, a package manager), where (file and line), and what Raven offers today in C,
  compose-raven, Python and Rust, or "missing";
- web-only wording that a neutral wording would fix, for the fog's proposals to the skills.

Raven facts to check against: `include/raven/` (C API), `bindings/` (generated bindings,
`bindings/README.md`), `compose/docs/spec.md` (compose-raven, its test module in section 9),
`compose/docs/mcp.md` (Raven MCP), headless Vulkan capture (`tests/test_render.c`,
`Rwindow_capture`), and the Python preview spike on the `spike/previews` branch.

Deliver a table, skill by skill, with the gaps ranked by how many skills each one blocks.

## Resolution

Researched on 2026-11-18. The findings are on the throwaway branch
`research/audit-the-skills-against-raven` (`a814c7d`), in
`compose/docs/research/0158-audit-the-skills-against-raven.md`. That file holds the 27-row table with
file:line for each step, Raven today per need and per language, the 12 web-only wordings, and what
couldn't be verified. Nothing was run for the audit: every result comes from cited code, and from
the results the earlier spikes recorded.

- **21 of the 27 skills are stack-neutral.** Seven of those have web or TypeScript wording only.
  `implement-spec` needs nothing itself, but runs `tdd`.
- **6 need the stack.** `implement` works today. `diagnosing-bugs`, `pr`, `prototype`, `tdd` and
  `triage` work in part.
- **The gaps, ranked by how many skills each blocks:**
  1. **Finders and actions for C, Python and Rust** (5 skills: `tdd`, `diagnosing-bugs`, `triage`,
     and through `tdd`, `implement` and `implement-spec`). Today it's `Rwidget_find` by name and
     `Rwindow_inject_*` at `Rwidget_get_rect`, written by hand.
  2. **Saving a capture as PNG in Raven's C** (3: `pr`, `prototype`, `diagnosing-bugs`). The
     examples write only PPM/PAM, and Rust has no encoder.
  3. **Driving an app that is already running**, the Playwright/curl step (3: `diagnosing-bugs`,
     `triage`, interactive `prototype`). It is missing in all four languages. compose's `ui.*` is
     planned (0151); the others are unplanned.
  4. **Headless capture in compose-raven** (2: `pr`, `prototype`). `RavenHost(headless = true)`
     always picks the null backend, and no Kotlin code calls `Rwindow_capture`.
  5. **Rendering several variants to images in one command** (1: `prototype`). Python has only the
     spike's script.
  6. **An input recorder** (1: option 5 of `diagnosing-bugs`). `Rwindow_inject_*` can replay
     input, but nothing records it.
  7. **Python type checking** (1, minor: `implement`). `raven.py` has no annotations.
- **The dev loop**: Raven's XML live reload stands in for a dev server in all four languages. Code
  changes need a rebuild, or a restart of the script.
- **Discrepancies**: `raven_core.h`'s comment on `RInitDesc.headless` says to combine it with
  `R_BACKEND_NULL`, but `tests/test_render.c` uses it with Vulkan. `compose/docs/spec.md`'s "no
  screenshot tests, because the null renderer draws nothing" no longer holds.
