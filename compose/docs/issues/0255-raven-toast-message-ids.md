---
status: open
---

# Raven: toast message ids

Give each message of Raven's Toast an id, a way to hide it, and a callback when it goes, so
`showSnackbar` can tell how its message ended. Spec:
[11: change 15](../spec.md#11-required-raven-changes). Planned in [Overlays and pagers: Toast](archive/0243-overlays-and-pagers-toast.md).

**Depends on:** [samples/carousel](archive/0254-samples-carousel.md), the ticket before it in phase 5's
implementation sequence.

## Deliverables

- [ ] `Rtoast_show_message(toast, text, kind, seconds)` returning the message's id, `Rtoast_hide_message(toast, id)` (hidden, or taken out of the queue), and `Rtoast_set_message_ended_callback` with `RToastEndedCallback(toast, id, by_action, user_data)`, documented in `raven_controls.h`.
- [ ] The callback called once for every message that goes: timed out, hidden, cleared, replaced, or ended by its button (`by_action`), in each queue mode.
- [ ] A C test: ids distinct; each way of ending reported once with the right id; hiding a waiting message; a stack of three ending out of order.
- [ ] One commit on `compose-raven`, the bindings regenerated, the reference updated, spec section 11's change 15 marked made.
