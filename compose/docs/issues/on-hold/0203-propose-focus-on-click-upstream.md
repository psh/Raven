---
status: on-hold
---

# Propose focus on click upstream

> **On hold (2026-11-19).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose `Rwidget_set_focus_on_click` and `Rwidget_get_focus_on_click` to Raven's `main`, on their
own, as the way for a button that acts on another widget (a toolbar button, a password's eye over
its field) to be clicked without taking the keyboard focus, while Tab still reaches it. It doesn't
mention compose-raven. Split out of
[Leading and trailing icons on TextField](../archive/0200-leading-and-trailing-icons-on-textfield.md),
which built it (spec section 11, change 11; commit `113cc66`).

**Depends on:** [Leading and trailing icons on TextField](../archive/0200-leading-and-trailing-icons-on-textfield.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying the two functions and their documentation in `raven_widget.h`, `RVN_WF_NO_CLICK_FOCUS` and the window's press in `src/widget/`, `test_focus_on_click` in `tests/test_widgets.c`, and the bindings regenerated against upstream's headers.
