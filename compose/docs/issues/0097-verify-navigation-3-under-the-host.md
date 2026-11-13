---
status: open
wayfinder: task
parent: 0095
blocked_by: []
assignee: null
---

# Verify Navigation 3 under the host

## Question

The last open part of a verify-first fact on `roadmap.md`: `rememberNavBackStack` and the
saved-state decorator of `navigation3-runtime` work under compose-raven's host and window owners,
with the kotlinx-serialization plugin. `navigationevent` already passed; the rest waited for the
host, which now exists. Check it in `../spikes/` (or a test on the host), record the result in the
roadmap's milestone 0 table, and, if it fails, apply its fallback (windows call registered back
handlers directly; any other gap is fixed in `compose-raven-navigation3`).
