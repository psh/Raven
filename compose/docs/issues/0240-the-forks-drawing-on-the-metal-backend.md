---
status: open
---

# The fork's drawing on the Metal backend

Upstream added a Metal backend, and ctest now runs `test_render` on it as well (`test_render_metal`).
Four of the fork's tests fail there, because what they draw was built only into the Vulkan renderer:
`test_meshes`, `test_rotated_text`, `test_image_rotated` and `test_line_caps`. Merging upstream into
compose-raven (2026-11-26), they were limited to Vulkan in `tests/test_render.c`, so the suite passes.
This ticket gives Metal the same drawing and runs them on both backends again.

## Deliverables

- [ ] Meshes, rotated text, rotated images and line caps (round, butt, square, with round joints)
  drawn by `src/render/metal/` as the Vulkan backend draws them.
- [ ] `test_meshes`, `test_rotated_text`, `test_image_rotated` and `test_line_caps` run in
  `test_render_metal` again, out of the Vulkan-only block in `tests/test_render.c`.
