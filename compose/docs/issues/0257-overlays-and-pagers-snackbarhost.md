---
status: open
---

# Overlays and pagers: SnackbarHost

Bind Raven's Toast as Material 3's `SnackbarHost` and `SnackbarHostState`, with kinds and
stacking. Spec: [8: Snackbars](../spec.md#snackbars-phase-5) and section 12. Planned in [Overlays and pagers: Toast](archive/0243-overlays-and-pagers-toast.md).

**Depends on:** [Raven: toast message ids](0255-raven-toast-message-ids.md).

## Deliverables

- [ ] `SnackbarHost(hostState, modifier, maxVisible = 1)` over a Toast: sequence at 1, stack above it.
- [ ] `SnackbarHostState.showSnackbar(message, actionLabel, kind, duration): SnackbarResult` over `Rtoast_show_message` and the ended callback; cancelling hides the message or takes it out of the queue.
- [ ] `SnackbarKind`, `SnackbarDuration` (Short 4 s, Long 10 s, Indefinite, Material's default) and `SnackbarResult`.
- [ ] Headless tests: timing out (`Dismissed`), the action (`ActionPerformed`), queuing, a stack of three, cancelling a shown and a waiting message, the kinds.
- [ ] The XML translator's `<Toast>` as a `SnackbarHost` with a remembered state and a comment for the app's show calls.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the Toast row and the numbers).
