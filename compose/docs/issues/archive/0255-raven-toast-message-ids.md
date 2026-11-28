---
status: closed
---

# Raven: toast message ids

Give each message of Raven's Toast an id, a way to hide it, and a callback when it goes, so
`showSnackbar` can tell how its message ended. Spec:
[11: change 15](../../spec.md#11-required-raven-changes). Planned in [Overlays and pagers: Toast](0243-overlays-and-pagers-toast.md).

**Depends on:** [samples/carousel](0254-samples-carousel.md), the ticket before it in phase 5's
implementation sequence.

## Deliverables

- [x] `Rtoast_show_message(toast, text, kind, seconds)` returning the message's id, `Rtoast_hide_message(toast, id)` (hidden, or taken out of the queue), and `Rtoast_set_message_ended_callback` with `RToastEndedCallback(toast, id, by_action, user_data)`, documented in `raven_controls.h`.
- [x] The callback called once for every message that goes: timed out, hidden, cleared, replaced, or ended by its button (`by_action`), in each queue mode.
- [x] A C test: ids distinct; each way of ending reported once with the right id; hiding a waiting message; a stack of three ending out of order.
- [x] One commit on `compose-raven`, the bindings regenerated, the reference updated, spec section 11's change 15 marked made.

## Resolution

Built on 2026-11-27, in commit `2df5ca5`.

- **`raven_controls.h`** gains:
  - `Rtoast_show_message(toast, text, kind, seconds)`, returning the message's id: from 1, never
    reused for a toast, and 0 on an error;
  - `Rtoast_hide_message(toast, id)`, which hides the message, takes it out of the queue, or does
    nothing once it has gone;
  - `RToastEndedCallback(toast, id, by_action, user_data)` with
    `Rtoast_set_message_ended_callback`.

  The messages of `Rtoast_show` and the notifications get ids too.
- **`toast.c`**: each message carries its id and whether its end was reported, so it is reported
  once only.
  - Every way of going reports it: `rvn_toast_dismiss` (the timer, `Rtoast_hide`,
    `Rtoast_hide_message`, a click, a notification closed), `Rtoast_clear`, and the replacement in
    `R_TOAST_REPLACE`.
  - The ends are queued and delivered in `rvn_toast_report_flush`, before the notifications'
    reports, once the toast is done changing. The flush checks between callbacks that the toast
    is still alive.
  - A button's end comes after the action callback.
  - `rvn_toast_close_notification` now flushes too.
- **`test_toast_message_ids`** in `tests/test_more_widgets.c` checks:
  - distinct ids;
  - replacements reported in order;
  - hiding by id (twice, and an unknown id, reporting nothing more) and as the newest;
  - the button reported with `by_action`, after the action callback;
  - a 50 ms time out;
  - a waiting message taken out of a sequence, then the rest cleared;
  - a stack of three hidden out of order;
  - an invalid kind, giving an error and no id.

  All 108 C tests pass, with `test_pdf` skipped as before; `test_more_widgets` has 326 checks.
- **Bindings** are regenerated (20 files), and `bindgen.py --check` passes. `docs/reference.html`
  gains the three functions and their callback, and the tree expand callback that 0237 left out.
- **Spec** section 11's change 15 is marked made. The upstream proposal is
  [Propose toast message ids upstream](../on-hold/0256-propose-toast-message-ids-upstream.md), on
  hold, with its commit noted.
