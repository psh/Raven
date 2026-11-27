---
status: open
---

# Propose toast message ids upstream

> **On hold (2026-11-27).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose `Rtoast_show_message`, `Rtoast_hide_message` and the message ended callback to Raven's
`main`, on their own, as the way for any app to know when one message of a Toast ends and how. It
doesn't mention compose-raven. Split out of [Overlays and pagers: Toast](archive/0243-overlays-and-pagers-toast.md).

**Depends on:** [Raven: toast message ids](0255-raven-toast-message-ids.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying the functions and their documentation in `raven_controls.h`, the change in the Toast, its C test, and the bindings regenerated against upstream's headers.
