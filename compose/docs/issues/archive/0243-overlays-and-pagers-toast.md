---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
---

# Overlays and pagers: Toast

## Question

How is Raven's Toast (transient messages: `Rtoast_*`) bound? Compose has no Toast; Material 3 has
`Snackbar` shown through a `SnackbarHostState` and `showSnackbar(message, actionLabel)`, which
suspends until dismissed. Decide whether compose-raven follows that shape over Raven's toast, or
offers Raven's under its own name, and where it shows (window, stacked). Proved by a test app. The
answer is the spec section and its implementation tickets.

## Resolution

Resolved on 2026-11-27, with the user, in four rounds: designed into
[spec section 8: Snackbars](../../spec.md#snackbars-phase-5), section 11's change 15 and section 12.

- **Material 3's shape and names**: `SnackbarHostState`, a `SnackbarHost` the app places, and
  `suspend showSnackbar(...)` returning `SnackbarResult` (`ActionPerformed`, `Dismissed`), with
  `SnackbarDuration` and Material's default duration.
- **Raven's kinds kept** (`SnackbarKind`: Info, Success, Warning, Error); `maxVisible = 1` queues,
  as Material does, and above 1 stacks; Raven's replace mode dropped.
- **Cancelling** a call hides its message or takes it out of the queue, so there is no
  `currentSnackbarData`; no `withDismissAction` and no `snackbar` slot.
- **A Raven change** (15): message ids, `Rtoast_hide_message`, and a callback when each message
  ends, since Raven reported only the button and messages had no identity.
- **A headless test**, no sample; the XML translator turns `<Toast>` into a `SnackbarHost`.

Filed as [Raven: toast message ids](0255-raven-toast-message-ids.md),
[Propose toast message ids upstream](../on-hold/0256-propose-toast-message-ids-upstream.md) (on hold) and
[Overlays and pagers: SnackbarHost](0257-overlays-and-pagers-snackbarhost.md).
