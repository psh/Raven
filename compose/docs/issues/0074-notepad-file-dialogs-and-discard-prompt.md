---
status: open
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: null
---

# Notepad's file dialogs and discard prompt

## Question

`notepad` can't open or save a file without Raven's file dialogs, nor ask "discard the changes?"
without its message box. Phase 3 has them designed as suspend functions
([Multiple windows, dialogs and popups](archive/0024-multiple-windows.md)), built on the nested
frames that [Verify recomposition inside nested frames](archive/0053-verify-recomposition-in-nested-frames.md)
found safe. Do the suspend message and file dialogs move into phase 2 (as a new phase 2 ticket,
whole or a subset), or does `notepad` port in phase 2 as an editor without files, completed in
phase 3? Either way, settle what the phase 2 `notepad` does for New, Open, Save, Save as and Quit
with unsaved changes.
