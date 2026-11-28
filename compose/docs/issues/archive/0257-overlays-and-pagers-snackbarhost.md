---
status: closed
---

# Overlays and pagers: SnackbarHost

Bind Raven's Toast as Material 3's `SnackbarHost` and `SnackbarHostState`, with kinds and
stacking. Spec: [8: Snackbars](../../spec.md#snackbars-phase-5) and section 12. Planned in [Overlays and pagers: Toast](0243-overlays-and-pagers-toast.md).

**Depends on:** [Raven: toast message ids](0255-raven-toast-message-ids.md).

## Deliverables

- [x] `SnackbarHost(hostState, modifier, maxVisible = 1)` over a Toast: sequence at 1, stack above it.
- [x] `SnackbarHostState.showSnackbar(message, actionLabel, kind, duration): SnackbarResult` over `Rtoast_show_message` and the ended callback; cancelling hides the message or takes it out of the queue.
- [x] `SnackbarKind`, `SnackbarDuration` (Short 4 s, Long 10 s, Indefinite, Material's default) and `SnackbarResult`.
- [x] Headless tests: timing out (`Dismissed`), the action (`ActionPerformed`), queuing, a stack of three, cancelling a shown and a waiting message, the kinds.
- [x] The XML translator's `<Toast>` as a `SnackbarHost` with a remembered state and a comment for the app's show calls.
- [x] The spec's sections checked against what was built; `api-coverage.md` updated (the Toast row and the numbers).

## Resolution

Built on 2026-11-27.

- **`SnackbarHost(hostState, modifier, maxVisible = 1)`**, **`SnackbarHostState`**,
  **`SnackbarKind`**, **`SnackbarDuration`** and **`SnackbarResult`** are in foundation's
  `Snackbar.kt`. With `maxVisible` 1 the queue is `R_TOAST_SEQUENCE`; above 1 it is `R_TOAST_STACK`
  with `Rtoast_set_max_visible`.
- **`showSnackbar`** suspends on a request.
  - The message's button text is set just before `Rtoast_show_message`; each message keeps its
    own.
  - The request is resumed when ui's `ToastMessageEnded` (over change 15's callback) reports the
    message's id: `ActionPerformed` by its button, `Dismissed` otherwise.
  - Cancelling calls `Rtoast_hide_message`, which hides the message or takes it out of the
    queue, and the call returns nothing.
  - A call made before any host shows once one attaches. A host that leaves takes its messages
    with it, and the next host shows them again.
  - The durations' seconds go through an internal `secondsOf`, so the tests can time a message
    out quickly.
- **Tests** are `SnackbarTest` (6):
  - a timeout giving `Dismissed`, and the button, clicked, giving `ActionPerformed`;
  - three messages waiting their turn, a waiting one and the shown one cancelled;
  - a stack of three with one cancelled;
  - a call made before the host;
  - the kinds' and durations' Raven values.
- **The XML translator** maps `<Toast>` to a `SnackbarHost` with a remembered state and a comment
  for the app's calls.
  - `Queue="Stack"` gives `maxVisible`, and `Queue="Replace"` is a warning.
  - A `Text` becomes a `LaunchedEffect` showing it, with its `Kind`, `ActionText` and `Duration`.
    It passes `SnackbarDuration.Short` with a button, because Raven shows such a message for 4 s.
  - The example is in `TranslatorTest`, and the examples check now compiles `notifications.xml`'s
    and `showcase.xml`'s toasts.
- **Docs**: the spec's section records what was built. Section 12's row stands.
- **`api-coverage.md`**: 602 of 1,708 functions (35%), `raven_controls.h` at 184, 46 of 58 widget
  types, and the Toast row marked built. The toast's getters and its replace mode stay unbound,
  as `showSnackbar` stands for them.
- **Suites**: every module's suite and the samples' pass (745 tests).
