---
status: closed
wayfinder: grilling
parent: 0157
blocked_by: [0159]
assignee: Paul Hawke
---

# A UI driver in Raven's C

## Question

What does a UI driver in Raven's C look like, so that tests in every language and Raven MCP in every
binding can find widgets and drive them as a user would? Use `codebase-design`: this is a deep
module that every binding sees.

Starting from [What Raven's C already offers a UI driver](0159-what-ravens-c-offers-a-ui-driver.md),
whose list of ten additions is the driver's raw material. Its biggest finding is that the internal
screen-reader tree (`src/core/accessibility.{h,c}`) already finds, reads and acts on widgets and rows.
Should the driver be that tree made public, rather than a new walker?


- **Finding**: by name, accessible name, role, text, or a combination; one match, all matches, the
  nth match; across every window, popup and dialog. A matcher as a struct, a query string, or
  separate calls?
- **Reading and acting**: what the driver adds over the existing getters and `Rwindow_inject_*`
  (click a widget's centre, type into it, choose a menu item through its menus), mirroring
  compose-raven-test's actions where Raven's C can do them.
- **Idle**: a C notion of idle that a test or a tool can wait for (posted tasks, timers, pending
  layout), and how a headless test advances frames with the animation time step.
- **Failures**: messages a test reports and an agent reads (`no widget with text "Save"`), as
  compose-raven's `NodeException` does, through return values and Raven's error callback.
- **Naming**: an `Rtest_`/`Rui_`/`Rdriver_` prefix, its header, and whether it is part of libraven
  or a separate library.
- **Used by previews**: `raven_preview --script '…'` runs the driver's finders and actions before a
  capture, and `Rwindow_save_preview` waits on its idle check
  ([Prototyping Raven UI with an agent](0156-prototyping-raven-ui-with-an-agent.md)). The
  script's syntax is decided here.
- **Upstream**: designed to be proposed to Raven's `main`; it serves all eleven bindings.

## Resolution

Decided with Paul on 2026-11-18, and written into
[`compose/docs/agent-skills.md`](../../agent-skills.md#driving-a-ui).

- **The model**: the screen-reader tree (`src/core/accessibility.{h,c}`) made public. Its things are
  **accessibles**: windows, meaningful widgets, and items such as rows and tabs. Rows come for free.
- **One selector language**, as strings, everywhere: `role=`, `name=`/`name~=`, `text=`,
  `widget=` (XML Name), state words, and `[n]`. Role names are `RAccessibleRole`'s in kebab case.
- **The calls**, in a new `raven/raven_driver.h`, part of libraven: `RAccessible` (64-bit),
  `Raccessible_find_all`/`_find`, `_describe`, `_click`/`_type`/`_press_key` (real input), `_act`
  (semantic, for what input can't reach), and `_get_last_error`. Plus `Rwindow_get_count`/`_get`,
  `Ris_idle(count_timers)` and `Rwait_idle(max_frames)`. UI-thread only.
- **Failures are messages**: finding nothing is normal. A failed action returns `R_FALSE` with a
  readable reason. The error callback is for misuse.
- **Fixes**: rows carry their own rects, plus `Rbutton_get_text` and
  `Rwidget_get_accessible_description`. Finding searches popups and dialogs.
- **Scripts**: `click`, `type`, `key`, `act` and `wait-idle`, separated by `;`, with the same
  selectors, for `raven_preview --script`.
- **Upstream**: one proposal.
- **Glossary**: **Accessible** (avoid node, element, control) and **Selector** (avoid locator,
  query, matcher).
