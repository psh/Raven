---
status: closed
---

# Raven: token colours per editor

Add `Rtext_editor_set_token_color(editor, token, r, g, b, a)` and
`Rtext_editor_clear_token_colors(editor)` to Raven: a colour for one kind of token in one editor,
over its theme's, so `SyntaxTheme` can scope as a composition local. Raven's token colours are
otherwise only the global theme's. Spec: [11: change 13](../../spec.md#11-required-raven-changes).
Planned in [Small widgets: the CodeEditor extras](0212-small-widgets-the-codeeditor-extras.md).

**Depends on:** [Small widgets: Rating, Pagination, Breadcrumb and VerticalSlider](0228-small-widgets-rating-pagination-breadcrumb-and-verticalslider.md),
the ticket before it in phase 5's implementation sequence.

## Deliverables

- [x] The two functions in `raven_text_editor.h`, documented: the editor's colour wins over its theme's (a theme file's `<Syntax>`, `Rtheme_set_token_color`, Raven's pick), and clearing gives the theme's back; an invalid token is an error.
- [x] The editor's painting takes its own colour first (`rvn_syntax_color`).
- [x] A C test: two editors with one language, one given a keyword colour, paint their keywords differently (read through the token and the colour used); clearing restores the theme's.
- [x] One commit on `compose-raven`, the bindings regenerated, spec section 11's change 13 marked made, and an on-hold upstream ticket.

## Resolution

Built on 2026-11-27, in commit `c3d5a2b`.

- **`raven_text_editor.h`** gains three functions, beside the theme's token colours:
  - `Rtext_editor_set_token_color(editor, token, r, g, b, a)`;
  - `Rtext_editor_clear_token_colors(editor)`;
  - `Rtext_editor_get_token_color(editor, token, r, g, b, a)`, the colour the editor paints a kind
    of token in.

  The getter wasn't in the plan. The C test and compose-raven's tests need to read the colour back,
  and no getter existed: `Rtheme_get_token_color` gives only the global one. The editor's colour
  wins over its theme's (a theme file's `<Syntax>`, `Rtheme_set_token_color`, Raven's pick). An
  invalid kind of token, `R_TOKEN_TEXT` among them, is an error and changes nothing.
- **Painting**: the editor keeps `tokens[]` and a `token_mask`, which its paint metrics carry.
  `rvn_ed_token_color` takes the editor's colour first, keeping the text's opacity as the theme's
  does, else `rvn_syntax_color`. `rvn_token_internal` in `syntax.c` is now shared through `common.h`.
- **`test_token_colors`** in `tests/test_text_editor.c`: two C editors, with `return` a keyword in
  both.
  - Given its own keyword colour, one editor paints keywords in it, and the other keeps the theme's.
  - Strings keep the theme's colour in both.
  - The editor's colour also wins over `Rtheme_set_token_color`.
  - Clearing gives the theme's colour back.
  - Two invalid kinds are errors that change nothing.

  All 108 C tests pass (1,356 checks in the editor's).
- **Bindings** regenerated (20 files), and `bindgen.py --check` passes.
- **Spec** section 11's change 13 is marked made. The upstream proposal is
  [Propose token colours per editor upstream](../0310-propose-token-colours-per-editor-upstream.md),
  on hold.
