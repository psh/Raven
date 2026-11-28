---
status: closed
---

# Previews: Rwindow_save_preview and raven_preview

Render UI to PNGs with no window shown: a call any binding can use, and a command-line tool that
renders XML variants for the `prototype` skill. Design: [Previews](../../agent-skills.md#previews).

**Depends on:** [PNG encoding in Raven's C](0166-png-encoding-in-ravens-c.md),
[The UI driver in Raven's C](0169-the-ui-driver.md).

## Deliverables

- [x] `Rwindow_save_preview(window, png_path, scale)`: waits for idle, draws at the scale through `Rset_ui_scale` (restored after), captures, turns a transparent window's premultiplied alpha straight, and writes the PNG.
- [x] `tools/raven_preview`, built by CMake with the other tools: one or many XML files to PNGs on headless Vulkan, `--scale`, `--theme`, `--size`, `-o`, `--help`; exit 77 with no Vulkan.
- [x] `--script`: `click`, `type`, `key`, `act`, `wait-idle`, separated by `;`, with the driver's selectors.
- [x] Tests: the notes variants from `spike/previews` render, at 1x and 2x; a script's state shows in the capture; exit 77 on the null backend.

## Resolution

Done on macOS (arm64, MoltenVK), on the `compose-raven` branch. `test_preview` (66 checks) and the
9 `preview_*` ctest entries pass on headless Vulkan. The other C tests pass too, except
`test_shell` and `test_themes`, which fail on window maximize/minimize and on the default look's
name (`MacOSGlassLight`), not on anything this changes. I looked at the PNGs, not only their sizes.

- **`Rwindow_save_preview`** (`raven_window.h`, `src/widget/window.c`). It works out the UI scale
  that gives `scale` device pixels per UI pixel on the window's screen and sets it. It then gives
  the window back its size in UI pixels, so the framebuffer grows. It waits up to 600 frames for
  idle, captures, then restores the scale and size. Last, it unpremultiplies and writes through
  `Rimage_save_png`. Like `Rwait_idle`, it is refused from inside a callback.
  - **Found: the null backend captures.** Its capture is the clear color, and always has been, not
    NULL as the design assumed. `Rwindow_save_preview` refuses the null backend explicitly
    (`R_ERROR_BACKEND`), rather than write a blank PNG.
  - Changing the scale closes the window's popups.
- **`raven_preview`** (`tools/preview/`, installed with the other tools).
  - Usage:
    - `-o` is a file, or a folder (existing, ending in `/`, or because several files were given).
      The default is `FILE.png` beside each file, and the tool makes the folders it needs.
    - `--theme` takes `dark`, `light`, `glass-*`, `high-contrast-*`, a look name or a theme file.
    - `--null` is there for the 77 path.
  - Behavior:
    - It starts Raven at the scale (`RInitDesc.ui_scale`), so a popup a script opened survives the
      capture.
    - Animations are off.
    - It prints `out.png  WxH` per file.
    - Exit codes: 0 when every file was written, 1 when one failed, 2 for the options, 77 without
      Vulkan.
- **Scripts** (`tools/preview/script.c`, also compiled into `test_preview`):
  - `click SEL`, `type [SEL] "text"`, `key [SEL] Ctrl+S`, `act expand|collapse|select|focus SEL`
    and `wait-idle`, each followed by `Rwait_idle`.
  - A bare quoted word is `name="..."`. `type` with no selector goes to the deepest focused
    accessible, and `key` with no selector goes to the window.
  - A failure says which step and the driver's reason: `step 1, type "New note" "buy milk": no
    accessible with name="New note"`.
  - Key names are the menus' shortcut names, kept in the tool because `rvn_parse_shortcut` is
    internal.
- **The variants**: the spike's three, as `tools/preview/examples/notes-{A,B,C}.xml`.
  `Style="Title"` became `TextSize="22"`, since a Label has no Style.
- **Tests**:
  - `tests/test_preview.c` covers:
    - the null backend's refusal and misuse;
    - each variant at 1x (420x260) and at 2x (840x520), with the 2x drawn, not pixel-doubled;
    - the window's size and the UI scale restored afterwards;
    - a transparent window's straight alpha;
    - a script's typing in the capture and in the field's value;
    - script failures.
  - In `tools/CMakeLists.txt`: `preview_help`, `preview_bad_option` (2), `preview_null` (77),
    `preview_variants` (1x), `preview_variants_2x` (light theme), `preview_size` (glass-dark),
    `preview_script` and `preview_script_stops` (1), the exit codes checked by
    `preview/expect_exit.cmake`.
- **Docs**: the README (a feature bullet and a Previews section), and the decisions in
  [Previews](../../agent-skills.md#previews). The design's example script `type "New note" ...`
  didn't work, because a placeholder isn't a name, so it is now `type role=text-field ...`.
- **Bindings regenerated.** That also brought in doc-comment changes from `raven_mcp.h` made in
  0176 and 0179 that hadn't been regenerated (Rust's `sys/mcp.rs`).
- **Not here**:
  - compose-raven's `renderPreview`. It is deferred with the rest of compose-raven's side of the
    skills ([0157](../maps/0157-raven-works-with-the-matt-pocock-skills.md), 2026-11-18). It also needs
    `RavenHost` to offer headless Vulkan.
  - In the capture, the mouse stays where a script's last click left it, so that button shows hovered.
  - Not tried on Linux or Windows.
  - The upstream proposal waits with the others: nothing is released beyond this machine.
