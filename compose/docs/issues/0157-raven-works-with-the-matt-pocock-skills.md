---
status: closed
wayfinder: map
---

# Raven works with the Matt Pocock skills

## Destination

A written design, `compose/docs/agent-skills.md`, under which every skill in mattpocock-skills'
`engineering/` and `productivity/` folders (27 skills) works on a Raven project as well as on a web
app. For every step a skill asks for (see the UI, drive it, test it, a dev loop, a screenshot), the
design names the Raven equivalent in C and main Raven's bindings, verified in C, Python and Rust. It covers what Raven gains
for that (API additions, proposed upstream), and where an agent working in a Raven project finds the
guidance. A skill counts as compatible when every step has a documented equivalent and running the
skill on a Raven sample shows it works. This map designs; implementation tickets are charted when it
closes.

## Notes

- **Why**: Paul's goal (2026-11-18) is for Raven to be 100% compatible with the Matt Pocock skills.
  Most of them don't care about the stack. The gaps are in skills that interact with a running UI,
  which a web app gets free from the browser:
  - **See** the UI: `prototype`, `pr` (before and after screenshots), `diagnosing-bugs`.
  - **Drive** it from outside and observe: `diagnosing-bugs` ("a headless browser script, Playwright,
    that drives the UI and asserts"), an interactive `prototype`.
  - **Test** it in the app's language: `tdd`, and `diagnosing-bugs`' red-capable loop.
- **What Raven has today**:
  - Seeing works in every binding. Headless Vulkan (`RInitDesc.headless` with `R_BACKEND_VULKAN`,
    `VK_EXT_headless_surface`) plus `Rwindow_capture` renders and reads back with no window, on
    macOS through MoltenVK too. The Python spike on `spike/previews` rendered three XML variants to
    PNGs with the standard library
    ([Prototyping Raven UI with an agent](archive/0156-prototyping-raven-ui-with-an-agent.md)).
  - Driving and testing exist only in compose-raven: `compose-raven-test` (headless, Kotlin), and
    the planned Raven MCP `ui.*` tools. Python, Rust and C apps have `Rwindow_inject_*`, but no
    finders or actions.
- **Settled while charting (2026-11-18)**:
  - Scope is mattpocock-skills' `engineering/` and `productivity/` skills. `misc/` (TypeScript
    tooling by design) and `in-progress/` are out.
  - The verified languages are **C, Python and Rust**. Main Raven's other ten bindings get every
    capability through Raven's C API and `tools/bindgen`, with generated guidance, but aren't
    checked skill by skill. **compose-raven is deferred**: later, if needed (Paul, 2026-11-18, in
    [Where agents find Raven's guidance](archive/0163-where-agents-find-ravens-guidance.md)). Its
    decisions so far (`renderPreview`, `runRavenTest`) stay in the design for then.
  - Compatible means a documented Raven equivalent for every step, shown to work by running the
    skill on a Raven sample (`samples/notepad` and its counterparts).
  - The answer is Raven's side first: capabilities, plus guidance an agent finds in the project.
    Changes proposed to the skills themselves are a small, separate output, only where a neutral
    wording helps everyone.
  - The UI driver goes into **Raven's C**, as a fork change proposed upstream
    ([ADR 0002](../adr/0002-rendering-in-c-bindings-thin.md)): one implementation for tests in every
    language and for Raven MCP in every binding.
  - compose-raven keeps its own finders over Compose semantics, as decided in
    [Finders and actions shared by tests and UI tools](archive/0150-shared-finders-and-actions.md).
    It is not reopened here.
- **Related map**: [MCP middleware for Raven apps](0145-mcp-middleware-for-raven-apps.md) designs
  Raven MCP, closed with its design in `compose/docs/mcp.md`. This map relies on its decisions (the `raven_mcp.h` contract, the UI tools) and doesn't
  reopen them.
- **The skills**: `~/.claude/plugins/cache/claude-plugins-official/mattpocock-skills/<version>/skills/`
  (1.3.1 when charted). Read a skill's own files before deciding anything about it.
- Skills: grilling tickets call `grilling` + `domain-modeling`. Call `codebase-design` for the UI
  driver and the test harnesses. Keep `compose/GLOSSARY.md` current.
- Standing preferences: **keep it slim**; **the heavy part goes in Raven's C, bindings stay thin**;
  in compose-raven, **favour Compose conventions**.
- Ticket conventions: `ISSUES-FORMAT.md` in this directory.

## Decisions so far

<!-- one line per closed ticket: [title](file): gist -->

- [Audit the skills against Raven](archive/0158-audit-the-skills-against-raven.md): 21 of 27 skills are stack-neutral. 6 need the stack: `implement` works, while `diagnosing-bugs`, `pr`, `prototype`, `tdd` and `triage` work in part. The gaps, most-blocking first: finders and actions for C/Python/Rust (5 skills), PNG from Raven's C (3), driving a running app (3), headless capture in compose-raven (2), a variants command (1), an input recorder (1), Python type checking (1). XML live reload is the dev loop. Findings on `research/audit-the-skills-against-raven`.
- [What Raven's C already offers a UI driver](archive/0159-what-ravens-c-offers-a-ui-driver.md): tree walking, `Rwidget_find`, popups, getters, and synchronous `Rwindow_inject_*` are all there. The internal screen-reader tree already finds, reads and acts on rows. A driver adds a window list, a public idle check, finding in popups and dialogs, the accessible description, row and entry rects, `Rbutton_get_text`, find-all, and a documented off-thread hop. Findings on `research/what-ravens-c-offers-a-ui-driver`.
- [Prototyping Raven UI with an agent](archive/0156-prototyping-raven-ui-with-an-agent.md): previews answer the UI branch. Variants are XML first, rendered by a new C tool, `raven_preview` (headless Vulkan, PNGs, `--scale`/`--theme`/`--size`, `--script` over the C driver, exit 77 without Vulkan). `Rwindow_save_preview` in C serves variants that need app code, and compose's `renderPreview` wraps it. The logic branch is unchanged (port by hand). Written into `agent-skills.md`.
- [Writing a capture to a file](archive/0160-writing-a-capture-to-a-file.md): `Rimage_encode_png` (bytes, for `ui.screenshot`) and `Rimage_save_png` (a file), thread-safe and named after `Rimage_decode`, over a vendored `stb_image_write`. PNG only, plus a new `R_ERROR_IO`, proposed upstream on their own. Scale and straight alpha belong to `Rwindow_save_preview`.
- [A UI driver in Raven's C](archive/0161-a-ui-driver-in-ravens-c.md): the screen-reader tree made public as **accessibles** (rows included). One string **selector** language everywhere. `raven/raven_driver.h` in libraven: `Raccessible_find_all`/`_find`/`_describe`, real-input `_click`/`_type`/`_press_key`, semantic `_act`, `_get_last_error`, a window list, `Ris_idle`/`Rwait_idle`. Failures are messages, there are row rects and small getters, `raven_preview` scripts use the same calls, and it goes upstream as one proposal.
- [UI tests in each language](archive/0162-ui-tests-in-each-language.md): each language adds only a harness and assertions over the driver. The harness is the same everywhere: headless, fixed time, errors fail, idle around steps, leak checks, PNGs on failure. C gets a header-only `raven_test.h`, Python a `raven_testing` context manager plus a pytest fixture, and Rust `raven::testing::run`. One `notepad.xml` is the worked example in all three.
- [Where agents find Raven's guidance](archive/0163-where-agents-find-ravens-guidance.md): a `raven` skill (a pointer description, a web-to-Raven translation table, only what can't be looked up, one disclosed file per language), shipped as a Claude Code plugin from the Raven repo's root and proposed upstream. There's one file per language main Raven supports (C and 12 bindings), generated by `tools/bindgen`. compose-raven is deferred, and the verified languages are now C, Python and Rust.
- [Neutral wordings for the skills](archive/0165-neutral-wordings-for-the-skills.md): nothing proposed up front, since `SCOPE.md` asks for observed failures. The `raven` skill's table covers the four wordings that change behaviour, and the eight cosmetic ones aren't tracked. Failures seen while verifying the skills on samples become issues with their evidence.
- [Verify the skills on Raven's samples](archive/0182-verify-the-skills-on-samples.md): the destination's proof. `tdd`, `diagnosing-bugs`, `triage`, `implement`, `prototype` and `pr` all work on `examples/notes` in C, Python and Rust, so all 27 skills are compatible; the table is in `agent-skills.md`. Nothing went wrong on the skills' side, so nothing goes upstream. The gaps were in the `raven` skill (serving `ui.*`, a client mid-session, typing appends, XML fixes and previews), fixed there. Reruns with the plugin installed (0189) and with the MCP wrappers (0190) follow.
- [Verify the skills with the Python and Rust MCP wrappers](archive/0190-verify-the-skills-with-the-mcp-wrappers.md): `diagnosing-bugs` and `triage` stay compatible in Python and Rust with `raven_mcp` and `raven-gui-mcp`. `python.md` and `rust.md` now use the wrappers, and `raven_mcp` stopped loading the library unasked. The triage run found a real bug, filed as [Blank notes from an agent](0192-blank-notes-from-an-agent.md).
- [Driving a running app in any language](archive/0164-driving-a-running-app-in-any-language.md): the `ui.*` tools on the C driver are built into the Raven MCP library, so every binding has them. They're switched on with `RMcpConfig.ui_tools` (`RAVEN_MCP_UI=1`, `mcp.start(ui_tools=True)`), and compose apps keep their own set, never two. They wait for idle frame by frame through `Rtask_post`.

## Not yet specified

Closed on 2026-11-18 with the design destination reached: every step the skills ask of the stack has
a Raven equivalent, written into `compose/docs/agent-skills.md` (and `mcp.md` for Raven MCP), and
every ticket is archived. Implementation is charted as tickets 0166 to 0182. **Verifying each skill
on a sample**, the destination's proof that a skill works, is their last:
[Verify the skills on Raven's samples](archive/0182-verify-the-skills-on-samples.md), since it can't run until the driver, `raven_preview`, the test harnesses and the
`raven` skill exist. Skills that go wrong there despite the guidance become issues on
`mattpocock/skills` with their evidence
([Neutral wordings for the skills](archive/0165-neutral-wordings-for-the-skills.md)). The patches
below are the fog that remains, seeding a later map.

- **An input recorder**: recording a user's input so it can be replayed with `Rwindow_inject_*`
  (option 5 of `diagnosing-bugs`). One skill needs it, so it waits.
- **Python type checking**: annotations or stubs for `raven.py`, so `implement` has something to
  check in Python. Minor.
- **Prototypes of behaviour in a running app**: flipping variants in a live window, now possible
  with the UI tools ([A running app](../agent-skills.md#a-running-app)). Nothing needs a switcher yet.
- **Comparing previews with stored images**: screenshot tests in Paparazzi's model (tolerances,
  baselines, differences between GPUs). No skill needs them yet.
- **A harness for the other ten bindings**: C, Python and Rust have one
  ([UI tests in each language](archive/0162-ui-tests-in-each-language.md)). The others drive the UI
  through the binding directly, as their `--test` examples do, until someone needs more.
- **compose-raven in the skill**: a `compose.md` beside the generated language files, and the
  previews and tests already designed for compose-raven, when compose-raven comes back into scope.
- **compose-raven's finders over the C driver**: whether compose's finders should someday be built
  on the C driver as well as on Compose semantics. Noted, not reopened.

## Out of scope

- mattpocock-skills' `misc/` skills (TypeScript tooling by design) and `in-progress/` skills.
- Verifying the bindings other than C, Python and Rust skill by skill. They get the capabilities
  through the C API, and generated guidance.
- Building it. Implementation tickets are charted when this map closes.
