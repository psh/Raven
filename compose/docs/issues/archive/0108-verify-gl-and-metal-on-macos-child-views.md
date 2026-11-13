---
status: closed
wayfinder: task
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# Verify GL and Metal on macOS child views

## Question

Two verify-first facts on `roadmap.md` wait for a real window, and the samples now open real
windows on macOS: a GL context (`NSOpenGLContext`) works on Raven's macOS child view, which
carries a Metal layer (if not, macOS gets `OpenGLTexture` only), and an app can render Metal into
the child view's `CAMetalLayer` while Raven uses MoltenVK in the parent (if not, Metal content goes
through a texture). They decide macOS details of phase 3's graphics modules
(`compose-raven-opengl`, `NativeViewport`), not the five samples. Check both and record the
results in the roadmap's milestone 0 table.

## Resolution

Done 2026-11-12: **both pass**, so neither fallback is needed: `OpenGLViewport` keeps macOS, and
Metal goes through `NativeViewport`. The spike is [`spikes/macos-child-views/`](../../../spikes/macos-child-views/)
(`run.sh`: a real window with Raven on MoltenVK from Homebrew, at `97f8aa5`); its result is in
[`spikes/README.md`](../../../spikes/README.md) and the roadmap's milestone 0 table.

- Both viewports get a native child window; each handle is a Cocoa `CAMetalLayer`.
- **GL**: an `NSOpenGLContext` (3.2 core) attached to the left child view clears and reads back
  green with no GL error.
- **Metal**: frames rendered into the right child's `CAMetalLayer` read back magenta from the
  drawable.
- **On screen**: a capture of the window shows Raven's label drawn through MoltenVK, the green and
  the magenta, so both composite beside Raven.

One finding, written into spec section 8: **Raven reports a child's `CAMetalLayer`, not its
`NSView`**, and the layer has no delegate, so compose-raven finds the view in the window's view tree
by its layer (no fork change), and the macOS `NativeWindowHandle` carries both. `NSOpenGLContext` is
deprecated since macOS 10.14 but works.
