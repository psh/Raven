---
status: open
---

# Idle and the window list in Raven's C

Let code know when an app has nothing pending, and list its windows: the base the UI driver,
previews and Raven MCP's live tools wait on. Design: [The calls](../agent-skills.md#the-calls).

**Depends on:** nothing open.

## Deliverables

- [ ] `Ris_idle(count_timers)`: nothing pending (posted tasks, layout, paint, animations, and timers when asked), from what `Rwait_events` already works out (`context.c`, `window.c`).
- [ ] `Rwait_idle(max_frames)`: draws frames until idle, `R_FALSE` at the limit, refused (and reported) from inside a callback.
- [ ] `Rwindow_get_count()` and `Rwindow_get(index)`.
- [ ] `raven_core.h`'s comment on `RInitDesc.headless` corrected: it renders with Vulkan too (`tests/test_render.c`), not only with `R_BACKEND_NULL`.
- [ ] Tests, headless: idle after a change settles, not idle while an animation runs, timers counted only when asked.
- [ ] The bindings regenerated with `tools/bindgen`.
