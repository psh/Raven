# Raven and the Matt Pocock skills

How the skills in mattpocock-skills' `engineering/` and `productivity/` folders work on a Raven
project, in C and the languages main Raven binds (verified in C, Python and Rust; compose-raven
later): for every step a skill asks of the stack, Raven's equivalent. Most skills need nothing (the [audit](issues/archive/0158-audit-the-skills-against-raven.md)
found 21 of 27 stack-neutral). The others need to **see**, **drive** or **test** a UI.

This design is being written under the map
[Raven works with the Matt Pocock skills](issues/0157-raven-works-with-the-matt-pocock-skills.md).
Sections arrive as its tickets close. Words are defined in [`GLOSSARY.md`](../GLOSSARY.md).

## Guidance for agents

How an agent running one of the skills in a Raven project learns the Raven equivalent of a web step.
This document is the design, for people. The guidance is written from it, for agents.

### The `raven` skill

- **A skill, behind a pointer.** Most turns in a Raven app don't need the guidance, so it is a
  skill whose description is the pointer: *"Raven apps: preview, drive and test a Raven UI in C or
  any language Raven binds."* An `AGENTS.md` or `CLAUDE.md` holds at most one line pointing at it,
  never the guidance itself.
- **It opens with a translation table** from the skills' web words to Raven's, in the glossary's
  leading words, **preview**, **accessible**, **selector** and **idle**:

  | A skill says | On Raven |
  |---|---|
  | Playwright, a headless browser script | the driver: selectors, `raven_preview --script`, the test harness |
  | a screenshot | a preview: `raven_preview`, `Rwindow_save_preview` |
  | a dev server | XML live reload |
  | curl or a script against a running dev server | driving the running app: Raven MCP's `ui.*` tools ([A running app](#a-running-app)) |
  | a route, `?variant=` and a floating switcher | previews: XML variants rendered by `raven_preview`, compared side by side |
  | lift the demo's `<script>` module into the code | port the validated logic by hand (see [The logic branch](#the-logic-branch)) |
  | the test runner | ctest with `raven_test.h`, pytest with `raven_testing`, `cargo test`, or the binding's own |

- **It caches only what can't be looked up**: which tool answers which skill step, and the gotchas
  (headless Vulkan to capture, the null backend otherwise; exit 77 means skipped; `Rwait_idle` never
  inside a callback; variants are XML first). Signatures and flags stay with their sources:
  `raven_driver.h`, `raven_test.h`, `raven_preview --help`.
- **One file per language, disclosed**, loaded only for the language in use: C and each of main
  Raven's 12 bindings (C++, Python, Rust, Go, C#, Zig, LuaJIT, Java, Odin, Bun, Node.js, C3).
  compose-raven is left out for now; a `compose.md` can join later.

### Generated, like the bindings

`tools/bindgen` writes the 13 language files from one template, as it writes the bindings: each
`gen_*.py` already knows its language's names and idioms (loading the library, calling
`Raccessible_click`, passing a selector). The files can't drift from the bindings or from each
other. A small hand-written snippet per language adds only what differs by hand:

- **C, Python and Rust** show their harness and assertions ([UI tests in each language](#ui-tests-in-each-language)).
- **The other ten** show the pattern their build-tested `--test` example already uses (start headless,
  act through the driver, check, shut down), calling the driver directly through the binding.

### Shipped as a plugin

The skill is published as a **Claude Code plugin**, the way mattpocock-skills is: someone working on
any Raven app installs it once, for every project and language. Its source lives at the Raven
repository's root, `agents/raven/`, with the marketplace manifest in `.claude-plugin/`. It is
versioned with Raven, and proposed upstream with the driver.

### The skills themselves

Nothing is proposed to mattpocock-skills up front. Its `SCOPE.md` keeps an issue open only for an
**observed failure** in a real session, and sends anything else to a project's own guidance, which
is what the `raven` skill is. When verifying the skills on Raven's samples shows a skill going wrong
despite the guidance, that session (what was run, what the skill did, what was expected) becomes an
issue on `mattpocock/skills`, judged against `SCOPE.md` like anyone's. The audit's web-only wordings
stay in its findings for reference.

### Keeping it true

Reviewing the skill is a checkbox here: whenever this design or `raven_driver.h` changes, the skill's
table, gotchas and template are checked against it.

- [ ] The `raven` skill matches this design and `raven_driver.h`.

Decided in [Where agents find Raven's guidance](issues/archive/0163-where-agents-find-ravens-guidance.md).

## Driving a UI

What `tdd`, `diagnosing-bugs` and `triage` need, and what Playwright gives a web app: find what's on
screen, act on it as a user would, and know when the app has settled. Raven's C gains a **UI
driver**, so tests in every language, `raven_preview`'s scripts and Raven MCP's tools in every
binding share one implementation. compose-raven keeps its own finders over Compose semantics
([Finders and actions shared by tests and UI tools](issues/archive/0150-shared-finders-and-actions.md)).

### Accessibles

The driver works on the tree screen readers already see (`src/core/accessibility.{h,c}`), made
public. Its things are **accessibles** (see `GLOSSARY.md`): a window, a widget that means something
(not one that only lays others out), or an item of a widget, such as a list row, a tree row or a tab.
So a test or an agent finds what the user perceives, and rows are as easy to click as buttons.

### Selectors

One string language says which accessibles to find, the same in the C API, in `raven_preview`
scripts, in Raven MCP's tools and in every language's tests:

```
role=button name="Save"          a role, and the accessible name
name~="milk"                     ~= : contains
text="buy milk"                  the text it shows
widget=save_button               the widget's XML Name
role=checkbox checked            states: checked, selected, expanded, focused, disabled
role=list-item [2]               the third match
```

Role names are `RAccessibleRole`'s, in kebab case (`button`, `list-item`, `text-field`), listed in
the header.

### The calls

In a new header, `raven/raven_driver.h`, part of libraven. UI-thread only, like the rest of Raven;
Raven MCP reaches it from its own threads with `Rtask_post`.

```c
typedef uint64_t RAccessible;   /* 0: none */

/* Finding: nothing found is a normal result, not an error. window 0: every window. */
RAVEN_API int         Raccessible_count(Rid window, const char* selector);
RAVEN_API RAccessible Raccessible_find(Rid window, const char* selector);      /* exactly one, else 0 */
RAVEN_API RAccessible Raccessible_get(Rid window, const char* selector, int index);  /* the index-th, else 0 */
/* The tree, for an outline: a window's root, a parent, its children. */
RAVEN_API RAccessible Raccessible_get_root(Rid window);
RAVEN_API RAccessible Raccessible_get_parent(RAccessible accessible);
RAVEN_API int         Raccessible_get_child_count(RAccessible accessible);
RAVEN_API RAccessible Raccessible_get_child(RAccessible accessible, int index);

/* Reading: role, name, description, value, number and range, states, rect. */
RAVEN_API int Raccessible_describe(RAccessible accessible, RAccessibleInfo* info);
RAVEN_API const char* Raccessible_get_role_name(RAccessibleRole role);       /* "list-item" */

/* Acting as a user would: real input, at the accessible's centre; menus opened through their menus. */
RAVEN_API int Raccessible_click(RAccessible accessible);
RAVEN_API int Raccessible_type(RAccessible accessible, const char* text);    /* focuses it first */
RAVEN_API int Raccessible_press_key(RAccessible accessible, RKey key, unsigned modifiers);
/* Semantic actions, for what input can't reach reliably: expand, collapse, select, focus. */
RAVEN_API int Raccessible_act(RAccessible accessible, RAccessibleAction action);

/* Why the last find or action failed: "no accessible matches role=button name=\"Save\"",
   "2 accessibles match role=list-item, expected one". */
RAVEN_API const char* Raccessible_get_last_error(void);

/* Every open window. */
RAVEN_API int Rwindow_get_count(void);
RAVEN_API Rid Rwindow_get(int index);

/* Idle: nothing pending (posted tasks, layout, paint, animations), and timers if asked. */
RAVEN_API int Ris_idle(int count_timers);
/* Draws frames until idle: for tests, raven_preview and Rwindow_save_preview, never from inside a
   callback. R_FALSE after max_frames. */
RAVEN_API int Rwait_idle(int max_frames);
```

- **A count and an index, not an array.** Every binding is generated by `tools/bindgen`, which reads
  a writable pointer as one value written back, so `Raccessible_find_all(..., out, max)` would
  overflow in Python. `Raccessible_count` and `Raccessible_get(selector, index)` give the same list
  in every language. Decided while building [The UI driver in Raven's C](issues/archive/0169-the-ui-driver.md).
- **Failures are messages.** Finding nothing returns 0 and reports nothing, since tests also check
  that something is gone. Acting on something missing or ambiguous returns `R_FALSE`, and
  `Raccessible_get_last_error` says why, in words a test reports and an agent reads. The error
  callback is kept for misuse (the wrong thread, a selector that doesn't parse).
- **Input first.** Actions are real input, so a disabled button can't be pressed by a test or an
  agent either. `Raccessible_act` is there for what input can't reach reliably.
- **Idle and timers.** `Ris_idle` is what `Rwait_events` already works out. Timers count only when
  asked, because a repeating timer would otherwise keep an app busy for ever. For the same reason a
  continuous window (or a realtime viewport) is idle once its frame is drawn. A live tool waits by
  checking `Ris_idle` after each frame, as compose-raven's `awaitIdle` does.
- **Fixes that come with it.** List, tree and tab rows carry their own rects (today they carry their
  widget's), there are `Rbutton_get_text` and `Rwidget_get_accessible_description`, and finding
  searches popups and dialogs too, which `Rwidget_find` never does.
- **Upstream**: one proposal to Raven's `main` (`raven_driver.h`, the window list, idle and the
  fixes), as what makes every binding testable.

### Scripts

`raven_preview --script` is the driver's calls written down, with the same selectors, separated by
`;`:

```
click role=button name="Add"; type role=text-field "buy milk"; key Enter; act expand role=tree-item name="Inbox"; wait-idle
```

The verbs are `click`, `type`, `key`, `act <action>` and `wait-idle`, one to one with the C calls.

Decided in [A UI driver in Raven's C](issues/archive/0161-a-ui-driver-in-ravens-c.md).

## A running app

What `diagnosing-bugs` ("curl a dev server", "a headless browser script"), `triage` and an
interactive `prototype` need: an agent driving the app the user is actually running, not a headless
copy. Raven MCP's **UI tools** do it (`mcp.md`, "The UI tools"): `ui.tree`, `ui.find`, `ui.click`,
`ui.type`, `ui.key` and `ui.screenshot`, addressed by selectors, replying with an outline. The
contract is the same for every Raven app.

- **Every app but compose-raven gets them from the Raven MCP library itself**, built on the UI
  driver (`raven_driver.h`) and added through its own registry. Every binding has them with no code
  of its own.
- **Switched on with `RMcpConfig.ui_tools`**, as compose-raven's `McpConfig.uiTools`:
  `RAVEN_MCP_UI=1` sets it through the `fromEnvironment` counterparts, and in Python it is
  `mcp.start(ui_tools=True)`. It is off by default, apart from the app's own tools.
- **Never two sets.** A compose-raven app leaves `ui_tools` off and adds its own set over Compose
  semantics. If a `ui.*` name is already taken when `Rmcp_start` would add the library's, the library
  leaves it and logs it.
- **Waiting inside a live app**: an action acts through the driver, then holds its reply and checks
  `Ris_idle(R_FALSE)` once a frame, reposting itself with `Rtask_post`, until idle or 2 s have
  passed on Raven's clock.
  `Rwait_idle` is never called inside the app's loop.

Decided in [Driving a running app in any language](issues/archive/0164-driving-a-running-app-in-any-language.md).

## UI tests in each language

What `tdd` and `diagnosing-bugs`' red-capable loop need: a headless UI test, written in the app's
language and run by its usual test runner. Each language adds only two things over the driver
([Driving a UI](#driving-a-ui)): a **harness** that starts Raven for a test and shuts it down, and
**assertions** that turn the driver's `R_FALSE` and message into that framework's failure. Finding,
acting and waiting stay in C, so a test means the same in every language. compose-raven keeps
`runRavenTest` (`compose/docs/spec.md`, section 9), which already works this way.

### The harness, in every language

- **Headless**: the null backend with no window system by default. When a test asks to capture, it
  uses headless Vulkan instead, and skips cleanly (exit 77 under ctest) when there is no Vulkan.
- **Deterministic**: a fixed animation time step (`Rset_animation_time_step`).
- **Raven's errors fail the test**, through `Rerror_set_callback`.
- **Idle around every step**: `Rwait_idle` before every find and after every action.
- **Leak checks on by default**: after `Rshutdown`, Raven must hold no blocks (`Rmemory_get_stats`).
- **Captures on failure**: on headless Vulkan, a failing test saves a PNG of each open window
  (`Rimage_save_png`) into the folder named by `RAVEN_TEST_CAPTURES`. On the null backend it does
  nothing. Comparing with stored images is not part of this.

### The assertions

One small set, named in each language's style, meaning what compose-raven-test's assertions mean:
exists, doesn't exist, text equals, checked, enabled, focused, count and value. `find` returns the
driver's description for anything else.

### C

A header-only `raven/raven_test.h`, shipped with Raven and not included by `Raven.h`. Raven's own
`tests/test.h` stays as it is.

```c
#include <Raven.h>
#include <raven/raven_test.h>

int main(void) {
    if (!Rtest_begin(NULL)) return RTEST_SKIPPED;         /* 77 */
    notes_open(RAVEN_EXAMPLE_DIR "/notes.xml");           /* the app's own code binds the actions */
    RTEST_TYPE("role=text-field", "buy milk");
    RTEST_CLICK("role=button name=\"Add\"");
    RTEST_EXPECT_TEXT("role=list-item [0]", "buy milk");
    return Rtest_end();                                   /* failures counted, leaks checked */
}
```

A hook decides how a failure is reported (by default, printed and counted), so the macros work with
any C test runner.

Built in [UI tests in C](issues/archive/0171-ui-tests-in-c.md):

- **Every step returns** `R_TRUE`, or `R_FALSE` after reporting, and the test goes on, as C has no
  exceptions: `if (!RTEST_CLICK(...)) return Rtest_end();` stops it. The hook can `longjmp` too.
- **The checks**: `RTEST_EXPECT_EXISTS` (exactly one), `_ABSENT`, `_COUNT`, `_TEXT` (the driver's
  `text=`), `_VALUE` (a range's number), and `_CHECKED`, `_ENABLED` and `_FOCUSED` with the state
  expected. `RTEST_DESCRIBE` reads the rest, and `RTEST_CHECK` checks the app's own state. A message
  names the macro, the selector, what it found and what was expected:
  `RTEST_EXPECT_ENABLED(role=button name="Off"): disabled, expected enabled`.
- **A step fails when the app is still busy** after the idle wait (600 frames by default).
- **Raven's warnings fail a test too**, not only errors, since an XML attribute Raven doesn't know is
  a warning. `Rtest_expect_errors` counts the ones a test means to cause.
- **Captures** go through `Rwindow_save_preview`, named `<name>-<failure>-window<n>.png`. They are
  not taken for a failure that is one of Raven's errors, since Raven is in the middle of something
  then.
- **The state is static, in the file that includes the header**: one test file, one `Rtest_begin`
  ... `Rtest_end` at a time.

### Python

A `raven_testing` module in the wheel. `raven.py` itself gains no dependency on pytest.

```python
import raven_testing

with raven_testing.app(xml=NOTEPAD_XML) as ui:            # unittest, plain scripts
    ui.type("role=text-field", "buy milk")
    ui.click('role=button name="Add"')
    ui.expect_text("role=list-item [0]", "buy milk")

def test_adds_a_note(raven_ui):                           # pytest: a fixture over the same
    raven_ui.load_xml(NOTEPAD_XML)
    ...
```

Built in [UI tests in Python](issues/archive/0172-ui-tests-in-python.md):

- **`raven_testing.py`** sits beside `raven.py`, and the wheel ships both. It registers itself as a
  pytest plugin (`[pytest11]` in the wheel's entry points), so `raven_ui` needs no `conftest.py`.
  Outside the wheel, use `-p raven_testing`. The fixture is defined only when pytest imports, so
  neither module needs pytest.
- **A failure raises `RavenTestFailure`**, an `AssertionError`, so pytest and unittest report it as
  a failure. Without Vulkan, `capture=True` raises `unittest.SkipTest`, which both report as a
  skip. Raven failing to start headless is a failure, not a skip.
- **Raven's errors can't be raised from its callback** (ctypes swallows exceptions there). They are
  collected and raised at the next step, or when the block or fixture ends. `ui.expect_errors()`
  is a context manager that collects them instead.
- **A test that already failed** isn't reported a second time when its block or fixture ends, and
  isn't leak-checked.
- **Keys** are `"Enter"`, `"Ctrl+S"` or `"a"`, or an `RKey` with modifiers. Actions are
  `"expand"`, `"collapse"`, `"select"` and `"focus"`. `ui.find` returns an `Accessible`, whose
  states are a set of names.

### Rust

A `raven::testing` module in the crate. Failures panic with the driver's message, as `cargo test`
reports them.

```rust
#[test]
fn adds_a_note() {
    raven::testing::run(NOTEPAD_XML, |ui| {
        ui.type_text("role=text-field", "buy milk");
        ui.click(r#"role=button name="Add""#);
        ui.expect_text("role=list-item [0]", "buy milk");
    });
}
```

Built in [UI tests in Rust](issues/archive/0173-ui-tests-in-rust.md):

- **`src/testing.rs`** is written by hand. bindgen's `lib.rs` declares it.
- **Tests take turns.** cargo runs tests on several threads, and Raven is one per process, so
  `run` holds a global lock. It catches a test's panic, shuts Raven down, then resumes the panic,
  so the next test starts clean.
- **Raven's errors** go into a static list, since a C callback can't capture. They fail the next
  step, or the end of `run`. `ui.expect_errors(|ui| ...)` returns them instead.
- **No skipped outcome in cargo**: without Vulkan, a capture test prints that it is skipped and
  returns.
- **`run(xml, ...)`**, with `""` for no window, and `run_with(Options { ... })`.
- **`build.rs` gives the crate's own examples and tests an rpath** to `RAVEN_LIB_DIR`, because
  libraven's install name on macOS is `@rpath/...` and the `DYLD_*` variables don't reach them.
  Before this, `binding_rust` failed on macOS.

### The worked example

The same `notes.xml` (`examples/notes/`, since `examples/notepad` is the text editor) is tested in C, Python and Rust: add a note, check the list, delete it. XML is
first, so one file serves all three, and `tdd` and `diagnosing-bugs` have one pattern to follow in
each language. compose-raven's `samples/notepad` tests stay as they are.

Decided in [UI tests in each language](issues/archive/0162-ui-tests-in-each-language.md).

## Images to files

What `pr` (before and after screenshots), `prototype` and `diagnosing-bugs` need: pixels saved as an
image that GitHub shows in a PR and an agent can read. Raven's C gains a PNG encoder beside its
decoder (`Rimage_decode`, `raven_resources.h`), so every binding saves an image in one line, rather
than each writing its own encoder:

```c
/**
 * @brief Encodes tightly packed RGBA8 pixels, straight alpha (as Rimage_decode gives them), as PNG.
 * Thread-safe.
 * @return The PNG bytes, to release with Rpixels_free; NULL, reported, on failure.
 */
RAVEN_API uint8_t* Rimage_encode_png(const uint8_t* rgba, int width, int height, size_t* png_size);

/**
 * @brief Writes tightly packed RGBA8 pixels, straight alpha, to a PNG file. Thread-safe.
 * @return R_FALSE, reported (R_ERROR_IO when the file can't be written), on failure.
 */
RAVEN_API int Rimage_save_png(const uint8_t* rgba, int width, int height, const char* path);
```

- **In memory, and to a file.** The in-memory form is what Raven MCP's `ui.screenshot` needs, since
  it replies with image bytes. The file form is the one-liner for everyone else. There is no
  `Rwindow_capture_png`: `Rwindow_save_preview` covers windows.
- **PNG only.** The examples' PPM/PAM output (`examples/common/example.h`) stays as it is.
- **The encoder** is `stb_image_write.h` (Sean Barrett, public domain or MIT, as `stb_image`),
  vendored in `third_party/stb/` and compiled `static` in the same file as `stb_image`, under the
  same `RAVEN_WITH_STB_IMAGE` switch. With the switch off, the calls fail and say so. It is listed in
  `THIRD_PARTY.md`. It compresses: a 420x260 preview is tens of kilobytes, not the 437 KB of
  uncompressed pixels.
- **`R_ERROR_IO`** joins `RError`, at the end, for a file that can't be written.
- **Upstream**: proposed to Raven's `main` as one small change (the two calls, the encoder,
  `R_ERROR_IO`), on its own and ahead of `Rwindow_save_preview`.

Decided in [Writing a capture to a file](issues/archive/0160-writing-a-capture-to-a-file.md).

## Previews

What the `prototype` skill's UI branch needs: several radically different variants of a screen,
which the user compares and picks from. On the web, variants are switched on a route. On Raven, each
variant is rendered to a **preview**, an image file, with no window shown. Previews answer "what
should this look like". Prototypes of behaviour in a running app wait on driving a running app.

### Variants are XML first

Raven's XML is built by Raven's C, so the same file renders identically from every binding. A
prototype written as XML belongs to no language, and needs no compile and no runtime. So variants
are drafted as XML files, whatever the app is written in. The winner is folded in:

- in an XML app, as it is;
- in compose-raven, through `raven-xml2compose`;
- in code (C, Python, Rust), by hand.

A variant that needs the app's own code (a custom composable, custom drawing, real data) is written
in the app's language instead, and rendered with `Rwindow_save_preview` (below).

### `raven_preview`

A command-line tool in Raven's `tools/`, built by CMake with the other tools and proposed upstream:

```sh
raven_preview variant-A.xml -o variant-A.png [--scale 2] [--theme dark] [--size 420x260]
raven_preview variants/*.xml -o previews/            # one PNG per file
raven_preview notes.xml -o after-add.png --script 'type role=text-field "buy milk"; click "Add"'
```

- It renders on headless Vulkan (`RInitDesc.headless` with `R_BACKEND_VULKAN`), with no window
  system and nothing on screen. With no usable Vulkan driver, it exits with 77 and says so, as Raven's
  `test_render` does.
- `--size` and `--theme` override the XML window's `Width`/`Height` and Raven's theme. `--scale` is
  device pixels per UI pixel (1 by default).
- `--script` runs actions before the capture, so a preview can show a state ("after adding a
  note"). It is the driver's calls written down, with the same selectors (see
  [Driving a UI](#driving-a-ui)).
- Built in [Previews](issues/archive/0170-previews.md):
  - `--null` takes the null backend and exits with 77, for tests of that path. The null backend's
    capture is the clear color, a picture of nothing, so the tool and `Rwindow_save_preview`
    refuse it rather than write a blank PNG.
  - The tool starts Raven at the scale (`RInitDesc.ui_scale`), so `Rwindow_save_preview` has nothing
    to change and a popup a script opened is still open in the capture.
  - Animations are off, so what a script does shows at once.
  - In a script, a bare quoted word is short for `name="..."` (`click "Add"`). `type` without a
    selector types where the focus is, and `key` without one goes to the window. A placeholder is
    not a name, so a text field is found by `role=text-field`.
  - `-o` makes the folders it needs. Exit codes: 0 when every file was written, 1 when one failed
    (a script step that fails says which and why), 2 for the options, 77 without Vulkan.

### `Rwindow_save_preview`

```c
/**
 * @brief Lays the window out, draws frames until nothing is pending, captures it and writes a PNG.
 * The size is the window's, and the theme Raven's (Rtheme_apply before building the window).
 * @param scale Device pixels per UI pixel; 0 = 1.
 * @return R_FALSE, reported through the error callback, when it can't capture (the null backend)
 * or write the file.
 */
RAVEN_API int Rwindow_save_preview(Rid window, const char* png_path, float scale);
```

`raven_preview` is built on it, and any binding can call it for a window it built from code. It
joins three parts: waiting until nothing is pending (`Rwait_idle`, see
[Driving a UI](#driving-a-ui)), the capture, and writing PNG ([Images to files](#images-to-files)). Each part stays available on its own.

- **Scale** is drawn, not resampled: the window is laid out and drawn at that UI scale
  (`Rset_ui_scale` around the capture, restored afterwards), so text and edges are crisp at 2x.
- **Alpha**: a transparent window's capture is premultiplied. It is turned into straight alpha
  before encoding, as PNG stores it.
- **Not from a callback**, like `Rwait_idle`: it draws frames. Changing the scale closes the
  window's popups, so a program that wants a popup in a 2x preview starts Raven at that scale.

### compose-raven

`compose-raven-test` wraps `Rwindow_save_preview` around a composition, from ordinary tests:

```kotlin
@Test fun variants() {
    renderPreview("build/previews/notes-A.png", width = 420.dp, height = 260.dp) { NotesVariantA() }
    renderPreview("build/previews/notes-B.png", width = 420.dp, height = 260.dp, scale = 2f, darkTheme = true) { NotesVariantB() }
}
```

It needs `RavenHost` to offer headless Vulkan; today `headless = true` always takes the null backend,
which can't capture. Without Vulkan it skips the preview with a message. A preview is for looking
at, not a check, so it never fails a test for that reason.

### The logic branch

`prototype`'s logic branch (`LOGIC.md`) is used as it is. The one HTML file lets anyone drive the
state model. Its promise that the `<script>` module lifts straight into the code holds only for
JavaScript, so on Raven the validated module is ported by hand into Kotlin, C or Python.

### Not yet

- **Comparing previews with stored images** (screenshot tests, Paparazzi's model): tolerances,
  baselines and differences between GPUs are a design of their own. compose-raven-test's earlier
  "no screenshot tests, because the null renderer draws nothing" has lost its reason.
- **Prototypes of behaviour in a running app**, flipping variants in a live window: possible with the
  UI tools ([A running app](#a-running-app)), with no switcher designed until one is needed.

Decided in [Prototyping Raven UI with an agent](issues/archive/0156-prototyping-raven-ui-with-an-agent.md).
