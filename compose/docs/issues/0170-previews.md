---
status: open
---

# Previews: Rwindow_save_preview and raven_preview

Render UI to PNGs with no window shown: a call any binding can use, and a command-line tool that
renders XML variants for the `prototype` skill. Design: [Previews](../agent-skills.md#previews).

**Depends on:** [PNG encoding in Raven's C](0166-png-encoding-in-ravens-c.md),
[The UI driver in Raven's C](0169-the-ui-driver.md).

## Deliverables

- [ ] `Rwindow_save_preview(window, png_path, scale)`: waits for idle, draws at the scale through `Rset_ui_scale` (restored after), captures, turns a transparent window's premultiplied alpha straight, and writes the PNG.
- [ ] `tools/raven_preview`, built by CMake with the other tools: one or many XML files to PNGs on headless Vulkan, `--scale`, `--theme`, `--size`, `-o`, `--help`; exit 77 with no Vulkan.
- [ ] `--script`: `click`, `type`, `key`, `act`, `wait-idle`, separated by `;`, with the driver's selectors.
- [ ] Tests: the notes variants from `spike/previews` render, at 1x and 2x; a script's state shows in the capture; exit 77 on the null backend.
