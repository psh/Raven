---
status: closed
wayfinder: grilling
parent: 0157
blocked_by: []
assignee: Paul Hawke
---

# Writing a capture to a file

## Question

What call does Raven's C gain so that any binding can save what a window shows as an image file in
one line, for previews, PR screenshots and bug reports?

- **Shape**: `Rwindow_capture_png(window, path)`, a general `Rimage_save_png(pixels, width, height,
  path)`, or both? Which formats: PNG only, or also PPM/PAM, which the examples write today
  (`examples/common/example.h`, `example_capture`)?
- **Encoding**: Raven decodes images (`src/render/image_decode.c`), but does it carry an encoder? A
  small PNG writer (stored or deflated), or one from `third_party/`?
- **Scale**: a capture is the framebuffer's pixels (1x headless). Does the call take a scale, or
  does the window's UI scale decide (`RInitDesc.ui_scale`)?
- **Errors**: return `R_TRUE`/`R_FALSE`, reported through `Rerror_set_callback`, and no capture on
  the null backend.
- **Upstream**: proposed to Raven's `main` like the drawing additions
  ([0143](../0143-propose-the-drawing-additions-upstream.md)).

`Rwindow_save_preview(window, png_path, scale)` and the `raven_preview` tool are built on this
call ([Prototyping Raven UI with an agent](0156-prototyping-raven-ui-with-an-agent.md)), so its
encoder must suit them: a scale, and errors reported.

Evidence: the Python spike on `spike/previews` wrote PNGs with the standard library in about 8
lines. Every other binding would otherwise repeat that.

## Resolution

Decided with Paul on 2026-11-18, and written into
[`compose/docs/agent-skills.md`](../../agent-skills.md#images-to-files).

- **Two calls, mirroring `Rimage_decode`**: `Rimage_encode_png(rgba, w, h, &size)` returns PNG bytes
  (freed with `Rpixels_free`), and `Rimage_save_png(rgba, w, h, path)` writes a file. Both are
  thread-safe and take straight-alpha RGBA8. The in-memory form serves Raven MCP's `ui.screenshot`.
  There is no `Rwindow_capture_png`; `Rwindow_save_preview` covers windows.
- **The encoder**: `stb_image_write.h`, vendored beside `stb_image`, compiled `static` in the same
  file under `RAVEN_WITH_STB_IMAGE`, and listed in `THIRD_PARTY.md`. It compresses, unlike a
  hand-written stored-deflate writer.
- **PNG only.** The examples' PPM/PAM output stays.
- **Scale belongs to `Rwindow_save_preview`**, which lays out and draws at that UI scale
  (`Rset_ui_scale` around the capture) for crisp text. It also turns a transparent window's
  premultiplied capture into straight alpha before encoding.
- **`R_ERROR_IO`** is added at the end of `RError`.
- **Upstream**: one small proposal (the two calls, the encoder, `R_ERROR_IO`), separate from and
  ahead of `Rwindow_save_preview`.
