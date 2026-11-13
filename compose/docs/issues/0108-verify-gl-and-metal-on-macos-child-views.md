---
status: open
wayfinder: task
parent: 0095
blocked_by: []
assignee: null
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
