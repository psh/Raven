---
status: open
wayfinder: task
parent: 0095
blocked_by: [0106]
assignee: null
---

# Upstream changes and the fog notes

## Question

The upstream changes pulled in on 2026-11-12 leave two fog notes on
[compose-raven phase 3 spec](0023-compose-raven-phase-3-spec.md#not-yet-specified) out of date.
**Popups** says Raven has only menu and tooltip popups (now `Rpopup_open`; update it as
[Menus opened from a button, and Raven's popups](0106-menus-opened-from-a-button-and-ravens-popups.md)
decides). **App bundling** expects a fork change so Raven finds MoltenVK in
`Contents/Frameworks`; upstream's loader now finds MoltenVK in the app bundle and honours
`RAVEN_VULKAN_LIBRARY`, so check whether that need is gone and rewrite the note. Also note
whether `Rwindow_focus` (and borderless windows that can become key) gives phase 3's
`application { Window { } }` anything it lacks, such as bringing a window to the front, and add it
to the spec or the fog. Update the roadmap's Later section to match.
