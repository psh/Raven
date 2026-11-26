---
status: open
---

# Raven: token colours per editor

Add `Rtext_editor_set_token_color(editor, token, r, g, b, a)` and
`Rtext_editor_clear_token_colors(editor)` to Raven: a colour for one kind of token in one editor,
over its theme's, so `SyntaxTheme` can scope as a composition local. Raven's token colours are
otherwise only the global theme's. Spec: [11: change 13](../spec.md#11-required-raven-changes).
Planned in [Small widgets: the CodeEditor extras](archive/0212-small-widgets-the-codeeditor-extras.md).

**Depends on:** [Small widgets: Rating, Pagination, Breadcrumb and VerticalSlider](0228-small-widgets-rating-pagination-breadcrumb-and-verticalslider.md),
the ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] The two functions in `raven_text_editor.h`, documented: the editor's colour wins over its theme's (a theme file's `<Syntax>`, `Rtheme_set_token_color`, Raven's pick), and clearing gives the theme's back; an invalid token is an error.
- [ ] The editor's painting takes its own colour first (`rvn_syntax_color`).
- [ ] A C test: two editors with one language, one given a keyword colour, paint their keywords differently (read through the token and the colour used); clearing restores the theme's.
- [ ] One commit on `compose-raven`, the bindings regenerated, spec section 11's change 13 marked made, and an on-hold upstream ticket.
