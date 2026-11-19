---
status: closed
wayfinder: grilling
parent: 0157
blocked_by: []
assignee: Paul Hawke
---

# Prototyping Raven UI with an agent

## Question

What does the `prototype` skill (mattpocock-skills, `engineering/prototype`) need in order to build
Raven UI prototypes: several radically different variants of a screen, which the user compares and
picks from? Where does each part live: a preview renderer in compose-raven, Raven MCP's UI tools, or
a Raven version of the skill?

### The route: previews, as on Android

On an Android project the skill writes each variant as a `@Composable` with a `@Preview`, and the
variants are rendered to images without running the app (Android Studio, or a screenshot test such
as Paparazzi or Roborazzi). compose-raven can do the same, because **Raven renders for real without a
window system** (checked 2026-11-18):

- `RInitDesc.headless = R_TRUE` with `R_BACKEND_VULKAN` renders through `VK_EXT_headless_surface`, and
  `Rwindow_capture(window, &w, &h)` (`raven_window.h`) reads the frame back as RGBA8. Raven's own
  `tests/test_render.c` works this way, and reports "Vulkan without a window system (headless
  surface)" on macOS arm64 with MoltenVK. The examples already save captures to files
  (`examples/common/example.h`, `example_capture`).
- compose-raven's tests use the **null** backend, whose capture is only the clear colour. That is
  why [Public test module for apps](0029-app-testing-module.md) ruled out screenshot tests
  ("the null renderer draws nothing"). Headless Vulkan removes that reason, so it can be revisited.

So a variant is a composable, and a **preview renderer** hosts it in a `RavenHost` on headless
Vulkan, waits until it is **idle**, captures the window, and writes a PNG. Kotlin/Native has zlib on
macOS and Linux for the encoding. The agent reads the PNGs to check its own work, and the user
looks at them side by side. No app is launched, nothing touches the user's screen, and no Raven MCP
is needed.

### Already possible in every binding (checked 2026-11-18)

Previews need no new code. `render_variants.py` (kept on the `spike/previews` branch, `764b4da`, with
its PNGs) renders three XML variants of a notes screen to PNGs using Raven's **Python** binding and
the standard library only. It starts Raven with `R_BACKEND_VULKAN` and `headless = 1`, loads each
variant with `Rwindow_create_from_xml`, draws a few frames, takes `Rwindow_capture`, and writes the
PNG with `zlib` and `struct` (about 8 lines). No window opened, and nothing outside the app was
captured. The three images came out as designed (a stacked form, a sidebar layout, a minimal quick
entry), and the agent checked each one by reading the PNG.

Every binding has the same calls, since they are plain C API. So the `prototype` skill can already
work this way in a Python Raven project: write the variants, render them with a script like this,
and look at the files.

What the spike showed for the design:

- **Scale**: captures are 1x (420x260 for a 420x260 window), not Retina. A preview renderer should
  take a scale.
- **Mistakes pass silently**: an invented `Style="Title"` rendered as plain text with nothing
  reported. A preview loop should turn on Raven's error callback, so that an unknown style or
  attribute shows up.
- **PNG writing could be Raven's.** By [ADR 0002](../../adr/0002-rendering-in-c-bindings-thin.md),
  `Rwindow_capture_png(window, path)` or `Rimage_save_png(pixels, w, h, path)` in Raven's C would
  give every binding previews, instead of each one writing its own encoder. That is a fork change to
  propose upstream.
- **Previews are not specific to compose-raven or Raven MCP.** They are a Raven capability every
  binding has, which strengthens the case for charting the preview renderer on its own (see Scope
  below).

From [the audit](0158-audit-the-skills-against-raven.md): compose-raven can't capture
yet. `RavenHost(headless = true)` always picks the null backend, and no Kotlin code calls
`Rwindow_capture`. A compose preview renderer needs `RavenHost` to offer headless Vulkan. In Rust,
capture works through `raven::sys`, but there is no PNG encoder
([Writing a capture to a file](0160-writing-a-capture-to-a-file.md)).

### What the skill's UI branch assumes, and the Raven counterpart

| The skill assumes | In Raven |
|---|---|
| Variants as components on a route, switched by `?variant=` | Variants as `@Composable` functions, each rendered to its own PNG (`VariantA.png`, `VariantB.png`, ...) |
| The user flips with a floating bottom bar | The user compares images. Interactive flipping in a running app, if wanted, comes later (below) |
| Sub-shape A: variants inside an existing page with real data | The preview hosts the variant inside the real screen's composables (a sample's screen, a Navigation 3 destination) with fake data |
| Sub-shape B: a throwaway route | A throwaway preview file next to the screen it prototypes |
| One command to run | One Gradle task, or a test, that renders every preview, with no release link and no window |
| Hidden in production | Previews live in a test or preview source set, never in the app's binary |

### To settle

- **The preview renderer**: a `@Preview`-style annotation that a Gradle task collects, or plain
  calls (`renderPreview("VariantA", width, height) { VariantA() }`) in a test? Which module holds it
  (`compose-raven-ui-test`, `compose-raven-test`, or its own)? What size, scale and theme (light,
  dark) does a preview take? What happens on a machine with no Vulkan driver: skip, as `test_render`
  does (exit code 77)?
- **Screenshot tests**: once previews render, comparing them with a stored image is a small step
  (Paparazzi's model). Is that part of this, or left in the fog? It reopens the "no screenshots"
  limit of [Public test module for apps](0029-app-testing-module.md).
- **Interactive prototypes**: for "how does it behave" rather than "how does it look", a running app
  is still needed. With [The UI tools](0151-the-ui-tools.md) and a `ui.screenshot` over the same
  `Rwindow_capture`, an agent can launch it, flip variants and capture them. Is that worth having
  beside previews, and does it need a switcher (an overlay, the window's Island, or its own window)?
- **XML**: Raven's live reload (`Rxml_set_live_reload`) and `raven-xml2compose` offer another way to
  draft variants, and the natural one for C and Python apps. Do previews render XML too
  (`Rwindow_create_from_xml` in the same headless host)?
- **The logic branch** (`LOGIC.md`): its one HTML file suits non-developers, but its "pure module to
  lift into the code" would be JavaScript, where Raven's code is Kotlin or C. Keep the HTML and port
  the module by hand?
- **Where the Raven version of the skill lives**: a project skill in this repository
  (`.claude/skills/`) adapting `UI.md` to previews, notes in `CLAUDE.md`, or nothing, if the skill's
  "adapt to the project's framework" is enough once previews exist.
- **Scope**: moved from [MCP middleware for Raven apps](../0145-mcp-middleware-for-raven-apps.md) to
  [Raven works with the Matt Pocock skills](../0157-raven-works-with-the-matt-pocock-skills.md) on
  2026-11-18. Previews belong here. The interactive part leans on the MCP map's UI tools.

## Resolution

Decided with Paul on 2026-11-18, and written into [`compose/docs/agent-skills.md`](../../agent-skills.md#previews).

- **Previews answer the UI branch** ("what should this look like"). Prototypes of behaviour in a
  running app go to the fog until
  [Driving a running app in any language](0164-driving-a-running-app-in-any-language.md).
- **Generic, as the skill is**: variants are **XML first** for every Raven project, because Raven's C
  builds XML identically from every binding. A variant uses the app's own language only when it
  needs the app's code. The winner is folded in as XML, through `raven-xml2compose`, or by hand.
  Python isn't needed, and neither is a prototype written in C.
- **`raven_preview`**, a new C tool in `tools/`, built by CMake and proposed upstream. It renders
  one or many XML files to PNGs on headless Vulkan, takes `--scale`, `--theme` and `--size`, and
  takes `--script` actions run before the capture, using the C driver's finders and actions (syntax
  decided with the driver). It exits 77 with no Vulkan.
- **`Rwindow_save_preview(window, png_path, scale)`** in Raven's C draws until nothing is pending,
  captures and writes the PNG. Size comes from the window, theme from `Rtheme_apply`. The tool is
  built on it, and any binding can call it.
- **compose-raven**: `renderPreview(file, width, height, scale, darkTheme) { … }` in
  `compose-raven-test`, from ordinary tests, wraps it. It skips without Vulkan. It needs `RavenHost`
  to offer headless Vulkan.
- **Comparing with stored images** goes to the fog. The "no screenshots" limit of
  [Public test module for apps](0029-app-testing-module.md) has lost its reason.
- **The logic branch** is used as it is. The HTML demo is for people, and the validated module is
  ported by hand.
- **Guidance**: this ticket supplies the content;
  [Where agents find Raven's guidance](0163-where-agents-find-ravens-guidance.md) decides where it
  lives.
- **Glossary**: **Preview** (avoid "snapshot", "screenshot", "mock-up").
