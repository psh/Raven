---
status: open
---

# UI scale

`LocalDensity` follows a change to Raven's UI scale
([Layout offset and density](archive/0118-layout-offset-and-density.md)), but an app can't read or set
it: `Rget_ui_scale` and `Rset_ui_scale` are unbound. Expose them so an app can offer a zoom
setting. One of the everyday features in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [ ] A UI scale an app reads and sets (an app-level property or composition local), calling `Rset_ui_scale`.
- [ ] Tests: setting it changes `LocalDensity` and the layout.
