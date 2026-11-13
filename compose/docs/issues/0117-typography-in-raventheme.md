---
status: open
---

# Typography in RavenTheme

Eight text roles sized from Raven's base size, and the components' defaults. Spec: [8: Typography](../spec.md#typography-phase-3).

**Depends on:** [Fonts and text styling](0116-fonts-and-text-styling.md).

## Deliverables

- [ ] `Typography.fromTheme()` with `display` to `code`, sized by Raven's Markdown factors and recalculated on theme or look changes; `RavenTheme(typography)` and `RavenTheme.typography`; `copy(...)`.
- [ ] Defaults: `Text` and `TextField` use `body`, button content `label`, `CodeEditor` `code`.
- [ ] Tests: role sizes follow a theme's font size, and a nested typography changes a subtree.
