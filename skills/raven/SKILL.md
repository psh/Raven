---
name: raven
description: "Raven apps: preview, drive and test a Raven UI in C or any language Raven binds."
---

# Raven

Raven is a C GUI library with bindings for twelve languages, its interfaces often written in XML.
Skills written for the web (`tdd`, `diagnosing-bugs`, `prototype`, `triage`) name browser tools; on
Raven each has an equivalent built on one **UI driver**: it finds an **accessible** (what a screen
reader sees: a window, a button, a row of a list) by a **selector**, acts on it as a user would,
and waits until the app is **idle**.

| A skill says | On Raven |
|---|---|
| Playwright, a headless browser script | the driver: a test through the language's harness, or `raven_preview --script` |
| a screenshot | a **preview**: `raven_preview`, or `Rwindow_save_preview` from code |
| a dev server | XML live reload: save the `.xml`, the running window follows |
| curl or a script against a running dev server | Raven MCP's `ui.*` tools, on the app the user runs |
| a route, `?variant=`, a floating switcher | XML variants rendered side by side by `raven_preview` |
| lift the demo's `<script>` module into the code | port the validated logic by hand into the app's language |
| the test runner | ctest with `raven/raven_test.h`, pytest with `raven_testing`, `cargo test` with `raven::testing`, or the binding's own (see the language file) |

## Which tool for which step

- **A test** (`tdd`'s red and green, a regression test that reproduces a bug): headless, in the
  app's language, through its harness. The language file shows one.
- **A preview** (what a screen looks like, before and after a change):

  ```sh
  raven_preview screen.xml -o screen.png --scale 2 --theme light
  raven_preview screen.xml -o after.png --script 'type role=text-field "buy milk"; click "Add"'
  ```

  `raven_preview --help` has every option and the script's verbs. Look at the PNG to judge it. A
  window built by code (a custom widget, real data) is saved with
  `Rwindow_save_preview(window, "out.png", scale)`.
- **Variants** (`prototype`'s UI branch): write each variant as its own XML file, render them all
  (`raven_preview variants/*.xml -o previews/`), and compare the PNGs side by side. The winner is
  folded in as it is in an XML app, through `raven-xml2compose` in compose-raven, by hand in code.
- **The running app** (`diagnosing-bugs` reproducing what the user sees, `triage`): Raven MCP's
  `ui.tree`, `ui.find`, `ui.click`, `ui.type`, `ui.key` and `ui.screenshot`, each replying with the
  window's outline. An app wired with `Rmcp_config_from_environment` serves them when started with
  `RAVEN_MCP=1 RAVEN_MCP_UI=1`. Connect with the URL and token in `~/.raven/mcp/<app name>.json`:

  ```sh
  claude mcp add --transport http <app> http://127.0.0.1:8765/mcp \
      --header "Authorization: Bearer $(jq -r .token ~/.raven/mcp/<app>.json)"
  ```

  An app without Raven MCP is reproduced in a headless test instead.

## Selectors

`role=button name="Save"`, `name~="sav"` (contains), `text="buy milk"` (what it shows: a text
field's value, else its name), `widget=save_button` (the XML `Name`), states (`checked`,
`disabled`, `focused`...), `[2]` (the third match). The grammar and the role names are at the top
of `raven/raven_driver.h`. To see what is on screen: `ui.tree`, or a preview. A text field is found
by `role=text-field` or by its `AccessibleName`; its placeholder is not its name.

## Gotchas

- **Headless**: the null backend runs anywhere and draws nothing, so tests use it. Whatever
  captures (previews, a test's PNGs on failure) needs Vulkan without a window system, and exits
  with 77 where there is none: 77 means skipped, as ctest reads it.
- **Idle**: after an action, wait for idle (`Rwait_idle`) before checking; the harnesses do it at
  every step. `Rwait_idle` and `Rwindow_save_preview` run from the app's loop or a test, outside
  Raven's callbacks; inside one, post the work with `Rtask_post`.
- **Real input**: a click is a mouse click, so a disabled button stays unpressed for a test too.
  `Raccessible_act` (expand, collapse, select, focus) covers what input can't reach.
- **Raven's warnings fail a test**: an XML attribute Raven doesn't know is a warning, and the
  message names it.
- **Variants are XML first**: one file renders the same from every language, with no build.

## The language in use

Read the one file for the app's language, in `languages/`: `c.md`, `cpp.md`, `python.md`,
`rust.md`, `go.md`, `csharp.md`, `zig.md`, `luajit.md`, `java.md`, `odin.md`, `bun.md`, `node.md`,
`c3.md`. Each shows a headless test and the driver's calls as that binding spells them.
