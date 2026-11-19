---
status: closed
---

# Idle and the window list in Raven's C

Let code know when an app has nothing pending, and list its windows: the base the UI driver,
previews and Raven MCP's live tools wait on. Design: [The calls](../../agent-skills.md#the-calls).

**Depends on:** nothing open.

## Deliverables

- [x] `Ris_idle(count_timers)`: nothing pending (posted tasks, layout, paint, animations, and timers when asked), from what `Rwait_events` already works out (`context.c`, `window.c`).
- [x] `Rwait_idle(max_frames)`: draws frames until idle, `R_FALSE` at the limit, refused (and reported) from inside a callback.
- [x] `Rwindow_get_count()` and `Rwindow_get(index)`.
- [x] `raven_core.h`'s comment on `RInitDesc.headless` corrected: it renders with Vulkan too (`tests/test_render.c`), not only with `R_BACKEND_NULL`.
- [x] Tests, headless: idle after a change settles, not idle while an animation runs, timers counted only when asked.
- [x] The bindings regenerated with `tools/bindgen`.

## Resolution

Done, on macOS (arm64), headless. `tests/test_idle.c` (48 checks) passes, and so do
`bindings_up_to_date` and `./gradlew :raven-mcp:check`.

- **`Ris_idle(count_timers)`** (`raven_core.h`, `context.c`): no posted task, no `redraw_all`, and
  no window busy, plus no timer when `count_timers`. It is built on the same window state
  `Rwait_events` reads (`rvn_window_busy` in `window.c`, beside `rvn_window_frame_in`): layout or
  layout springs pending, or, for a window that can paint and is visible, a paint, an animation, a
  touch animation or a requested redraw.
  - **One difference from `Rwait_events`**: a continuous window (`Rwindow_set_continuous_redraw`)
    and a realtime viewport don't count. They repaint for ever, so, like a repeating timer, they
    would keep an app busy for ever. Recorded in `agent-skills.md`.
  - Work on worker threads (`Rtask_run_async`) isn't seen until it posts a task.
- **`Rwait_idle(max_frames)`**: checks `Ris_idle(R_FALSE)` and draws a frame
  (`Rwait_frame(0.1)`) until idle, up to `max_frames`. It is refused with
  `R_ERROR_INVALID_OPERATION` inside an update or a dispatch (any callback), and it stops if the app
  quits.
- **`Rwindow_get_count()` and `Rwindow_get(index)`** (`raven_window.h`): the open windows, hidden
  ones included. They are in the order of creation until one closes, after which a new window may
  take its slot. `R_INVALID_ID` is returned out of range.
- **`RInitDesc.headless`**: the comment now says it renders off screen with Vulkan too.
- **Tests**: a change settles; a posted task is pending; not idle while a spring animates,
  `Rwait_idle(3)` is false mid-way and `Rwait_idle(600)` settles it; timers only when asked; a
  continuous window is idle; `Rwait_idle` inside a posted task is refused and reported; the window
  list, through hiding and closing.
- **Bindings**: regenerated (1157 functions).
- **Not from here**: `test_shell` (9 failures) and `test_themes` (1) fail the same way without
  this change.
