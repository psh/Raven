---
status: open
---

# The UI driver in Raven's C

`raven/raven_driver.h`: the screen-reader tree made public as accessibles, found with selectors and
driven with real input, for tests in every language, `raven_preview` scripts and Raven MCP's UI tools.
Design: [Driving a UI](../agent-skills.md#driving-a-ui).

**Depends on:** [Idle and the window list in Raven's C](archive/0168-idle-and-the-window-list.md).

## Deliverables

- [ ] `RAccessible` and `RAccessibleInfo` over `src/core/accessibility.{h,c}`: windows, meaningful widgets, and items (list, tree and tab rows).
- [ ] The selector language: `role=` (`RAccessibleRole` in kebab case, listed in the header), `name=`/`name~=`, `text=`, `widget=`, state words, `[n]`. A selector that doesn't parse is reported through the error callback.
- [ ] `Raccessible_find_all`/`_find` across every window, island, popup and dialog; `_describe`; `_click`, `_type`, `_press_key` as real input (menus opened through their menus); `_act`; `_get_last_error` with messages like compose-raven's.
- [ ] Rows carry their own rects (today they carry their widget's); `Rbutton_get_text`; `Rwidget_get_accessible_description`.
- [ ] Tests in `tests/` over the examples' XML: finding by each key, clicking a row, typing, a disabled button refusing a click, the failure messages.
- [ ] The bindings regenerated with `tools/bindgen`.
- [ ] The upstream proposal opened (the header, the window list and idle, and the fixes), as what makes every binding testable.
